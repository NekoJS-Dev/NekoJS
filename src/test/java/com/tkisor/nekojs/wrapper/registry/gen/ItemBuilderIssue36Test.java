package com.tkisor.nekojs.wrapper.registry.gen;
//~ mc_legacy_api

import graal.graalvm.polyglot.Context;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ItemBuilderIssue36Test {
    private static ItemBuilder item() { return new ItemBuilder(Identifier.parse("test:issue36")); }
    private static void eval(ItemBuilder builder, String source) {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("build", BuilderSurface.of(builder));
            context.eval("js", source);
        }
    }

    @Test void realGuestBeanMethodsAndBulkConfigurationHaveTheSameFingerprint() {
        ItemBuilder bean = item();
        ItemBuilder methods = item();
        eval(bean, "build.components = {'minecraft:custom_data':{z:[1,2],a:true}};"
                + "build.textures = {layer0:'test:item/gem'}; build.parentModel='minecraft:item/generated';"
                + "build.jukeboxPlayable='cat';");
        eval(methods, "build.component('custom_data',{a:true,z:[1,2]});"
                + "build.texture('test:item/gem'); build.setParentModel('minecraft:item/generated');"
                + "build.setJukeboxPlayable('minecraft:cat');");
        StartupRegistryRuntime runtime = new StartupRegistryRuntime("contract-test");
        assertEquals(runtime.definitionFingerprint(bean), runtime.definitionFingerprint(methods));
        assertNotEquals(runtime.definitionFingerprint(bean), runtime.definitionFingerprint(item()));
        assertEquals(bean.assetDefinition(), methods.assetDefinition());
    }

    @Test void retainedGuestAliasesAndFunctionsCannotAlterRegisteredConfiguration() {
        ItemBuilder builder = item();
        eval(builder, "const data={nested:[1]}; build.component('custom_data',data); data.nested.push(2);");
        assertEquals("{nested=[1]}", builder.getComponents().get("minecraft:custom_data").toString());
        assertThrows(RuntimeException.class, () -> eval(builder, "build.component('custom_data',()=>1)"));
        assertThrows(RuntimeException.class, () -> eval(builder, "build.components={'custom_data':{f:()=>1}}"));
        RegistryRepository repository = new RegistryRepository();
        repository.add(Registries.ITEM, builder);
        assertThrows(RuntimeException.class, () -> eval(builder, "build.texture('test:item/later')"));
        assertThrows(IllegalStateException.class, () -> builder.setMaxStackSize(16));
    }

    @Test void invalidBulkWriteRetainsEarlierStateAndNestedItemIsFrozenAtCollection() {
        ItemBuilder builder = item();
        builder.component("rarity", "epic");
        assertThrows(RuntimeException.class, () -> eval(builder,
                "build.components={'max_stack_size':16,'custom_data':null}"));
        assertEquals(Map.of("minecraft:rarity", "epic"), builder.getComponents());
        BlockBuilder block = new BlockBuilder(Identifier.parse("test:issue36_block"));
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            context.getBindings("js").putMember("block", BuilderSurface.of(block));
            context.eval("js", "block.item(item => { item.texture('test:item/gem'); }); block.item.maxStackSize=16; block.setItem(block.item);");
        }
        assertEquals(16, block.getItem().getMaxStackSize());
        new RegistryRepository().add(Registries.BLOCK, block);
        assertThrows(IllegalStateException.class, () -> block.getItem().texture("test:item/later"));
        assertThrows(IllegalStateException.class, () -> block.setItem(null));
    }

    @Test void rejectedPlatformRegistrationAndUnexecutedSupplierPublishNoAssets() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime("neoforge:test");
        ItemBuilder builder = item(); builder.texture("test:item/gem");
        runtime.repository().add(Registries.ITEM, builder);
        var result = runtime.drainFor(Registries.ITEM, (registry, id, supplier) -> {
            throw new IllegalStateException("fixture registration rejected");
        });
        assertEquals(1, result.errors().size()); assertTrue(runtime.itemAssetDefinitions().isEmpty());
        StartupRegistryRuntime deferred = new StartupRegistryRuntime("neoforge:test");
        ItemBuilder pending = item(); pending.texture("test:item/gem");
        deferred.repository().add(Registries.ITEM, pending);
        deferred.drainFor(Registries.ITEM, (registry, id, supplier) -> {});
        assertTrue(deferred.itemAssetDefinitions().isEmpty());
    }

    @Test void fabricRejectsResourceConfigurationBeforeExecutingTheNativeSupplier() {
        StartupRegistryRuntime runtime = new StartupRegistryRuntime("fabric:26.1.2");
        ItemBuilder builder = item(); builder.texture("test:item/gem");
        runtime.repository().add(Registries.ITEM, builder);
        var result = runtime.drainFor(Registries.ITEM, (registry, id, supplier) -> fail("must reject before native registration"));
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().getFirst().message().contains("NEKO-4037"));
        assertTrue(runtime.itemAssetDefinitions().isEmpty());
    }
}
