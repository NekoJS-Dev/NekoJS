package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.nbt.NbtBinaryCodec;
import com.tkisor.nekojs.api.nbt.NbtBinaryException;
import com.tkisor.nekojs.api.nbt.NbtBinaryLimits;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LoaderVersion;
import com.tkisor.nekojs.api.surface.RuntimeDist;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.api.registry.RegistryQueryService;
import com.tkisor.nekojs.platform.IModInfo;
import com.tkisor.nekojs.platform.IPlatform;
import com.tkisor.nekojs.platform.PlatformCapability;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("nbt-smoke")
class Phase3AFacadeIntegrationTest {
    @TempDir
    Path gameDir;

    @Test
    void contractFacadesAndMarshallerWorkAsOneRestrictedSurface() {
        CoreManagedApiBootstrap.CoreManagedApi core = CoreManagedApiBootstrap.load(
                new FixturePlatform(gameDir), URI.create("test:///phase3a.jar"));

        FrozenApiRegistry registry = JsApiSurfaceResolver.resolve(
                serverEnvironment(), core.contracts(), List.of(core.contributions()), List.of());

        try (Context context = Context.newBuilder("js")
                .allowHostAccess(HostAccess.NONE)
                .allowHostClassLookup(name -> false)
                .build()) {
            ApiGuestErrorFactory errors = ApiGuestErrorFactory.create(context);
            context.getBindings("js").putMember("ID",
                    ApiFacadeProxy.global(registry, CoreManagedApiBootstrap.ID_GLOBAL,
                            core.globalImplementations().get(CoreManagedApiBootstrap.ID_GLOBAL), errors));
            context.getBindings("js").putMember("Platform",
                    ApiFacadeProxy.global(registry, CoreManagedApiBootstrap.PLATFORM_GLOBAL,
                            core.globalImplementations().get(CoreManagedApiBootstrap.PLATFORM_GLOBAL), errors));
            context.getBindings("js").putMember("Text",
                    ApiFacadeProxy.global(registry, CoreManagedApiBootstrap.TEXT_GLOBAL,
                            core.globalImplementations().get(CoreManagedApiBootstrap.TEXT_GLOBAL), errors));
            context.getBindings("js").putMember("JsonIO",
                    ApiFacadeProxy.global(registry, CoreManagedApiBootstrap.JSON_IO_GLOBAL,
                            core.globalImplementations().get(CoreManagedApiBootstrap.JSON_IO_GLOBAL), errors));
            context.getBindings("js").putMember("NBT",
                    ApiFacadeProxy.global(registry, CoreManagedApiBootstrap.NBT_GLOBAL,
                            core.globalImplementations().get(CoreManagedApiBootstrap.NBT_GLOBAL), errors));
            context.getBindings("js").putMember("Registry",
                    ApiFacadeProxy.global(registry, CoreManagedApiBootstrap.REGISTRY_GLOBAL,
                            core.globalImplementations().get(CoreManagedApiBootstrap.REGISTRY_GLOBAL), errors));

            assertEquals("minecraft:stone", context.eval("js", "ID.asString(ID.of('minecraft:stone'))").asString());
            assertEquals("nekojs:script", context.eval("js", "ID.of('script').asString()").asString());
            assertEquals("custom:path", context.eval("js", "ID.of('custom', 'path').asString()").asString());
            assertEquals("custom", context.eval("js", "ID.namespace(ID.of('custom:path'))").asString());
            assertEquals("path", context.eval("js", "ID.path(ID.of('custom:path'))").asString());
            assertEquals("custom", context.eval("js", "ID.of('custom:path').namespace()").asString());
            assertEquals("path", context.eval("js", "ID.of('custom:path').path()").asString());
            assertFalse(context.eval("js", "Platform.isClient()").asBoolean());
            assertEquals(true, context.eval("js", "Platform.isDevelopment()").asBoolean());
            assertEquals("1.21.1", context.eval("js", "Platform.getMcVersion()").asString());
            assertEquals("neoforge", context.eval("js", "Platform.getLoaderId()").asString());
            assertEquals("21.1.0", context.eval("js", "Platform.getLoaderVersion()").asString());
            assertEquals(true, context.eval("js", "Platform.isLoaded('nekojs')").asBoolean());
            assertEquals("nekojs", context.eval("js", "Platform.getInfo('nekojs').id()").asString());
            assertEquals("NekoJS", context.eval("js", "Platform.getInfo('nekojs').name()").asString());
            assertEquals("1.1.0", context.eval("js", "Platform.getInfo('nekojs').version()").asString());
            assertEquals("alpha,nekojs", context.eval("js", "Platform.getList().join(',')").asString());
            assertEquals("nbt-binary-io,network-custom-channel,recipe-hot-reload",
                    context.eval("js", "Platform.capabilities().join(',')").asString());
            assertFalse(context.eval("js", "'getGameDir' in Platform").asBoolean());
            assertFalse(context.eval("js", "'platform' in ID").asBoolean());
            assertEquals(true, context.eval("js", "Text.empty().isEmpty()").asBoolean());
            assertFalse(context.eval("js", "Text.of('value').isEmpty()").asBoolean());
            assertFalse(context.eval("js", "Text.of('a').append('b', 2, true).isEmpty()").asBoolean());
            assertFalse(context.eval("js", "Text.translatable('nekojs.test', 'value').isEmpty()").asBoolean());
            assertFalse(context.eval("js", "'text' in Text.of('hidden')").asBoolean());
            assertEquals("rejected", context.eval("js", """
                    (() => {
                        'use strict';
                        const value = Text.of('immutable');
                        try {
                            value.isEmpty = false;
                            return 'accepted';
                        } catch (error) {
                            return 'rejected';
                        }
                    })()
                    """).asString());
            assertEquals("NO_MATCHING_SIGNATURE", context.eval("js", """
                    (() => {
                        try {
                            Text.ofValues({ unsupported: true });
                            return 'missing-error';
                        } catch (error) {
                            return error.code;
                        }
                    })()
                    """).asString());
            assertEquals("{\"a\":[1,true,null]}",
                    context.eval("js", "JsonIO.parse('{\"a\":[1,true,null]}').toString()").asString());
            assertEquals("{\"b\":2,\"a\":\"value\"}",
                    context.eval("js", "JsonIO.toString({ b: 2, a: 'value' })").asString());
            assertEquals("{\n  \"a\": 1\n}",
                    context.eval("js", "JsonIO.toPrettyString({ a: 1 })").asString());
            assertEquals("null", context.eval("js", "JsonIO.toString(null)").asString());
            assertEquals(true, context.eval("js", "JsonIO.read('missing.json') === null").asBoolean());
            context.eval("js", "JsonIO.write('settings/ui.json', { enabled: true, count: 2 })");
            assertEquals("{\"enabled\":true,\"count\":2}",
                    context.eval("js", "JsonIO.read('settings/ui.json').toString()").asString());
            context.eval("js", "JsonIO.write('settings/null.json', null)");
            assertEquals("null", context.eval("js", "JsonIO.read('settings/null.json').toString()").asString());
            assertEquals("JSON_PATH_FORBIDDEN|../escape.json|write|member:JsonIO.write|neoforge|1.21.1",
                    context.eval("js", """
                            (() => {
                                try {
                                    JsonIO.write('../escape.json', {});
                                    return 'missing-error';
                                } catch (error) {
                                    return [error.code, error.path, error.operation, error.symbolId,
                                        error.platform, error.minecraftVersion].join('|');
                                }
                            })()
                            """).asString());
            assertEquals("INVALID_JSON", context.eval("js", """
                    (() => {
                        try {
                            JsonIO.parse('{\"a\":1,\"a\":2}');
                            return 'missing-error';
                        } catch (error) {
                            return error.code;
                        }
                    })()
                    """).asString());
            assertEquals("TYPE_MISMATCH", context.eval("js", """
                    (() => {
                        try {
                            JsonIO.toString({ invalid: NaN });
                            return 'missing-error';
                        } catch (error) {
                            return error.code;
                        }
                    })()
                    """).asString());
            assertEquals("TYPE_MISMATCH", context.eval("js", """
                    (() => {
                        try {
                            JsonIO.toString(String.fromCharCode(0xD800));
                            return 'missing-error';
                        } catch (error) {
                            return error.code;
                        }
                    })()
                    """).asString());
            assertEquals("TYPE_MISMATCH", context.eval("js", """
                    (() => {
                        try {
                            JsonIO.toString({ [String.fromCharCode(0xD800)]: 1 });
                            return 'missing-error';
                        } catch (error) {
                            return error.code;
                        }
                    })()
                    """).asString());
            assertFalse(context.eval("js", "'values' in JsonIO.parse('{}')").asBoolean());
            assertEquals("{name:\"neko\",count:2,items:[1,2]}",
                    context.eval("js", "NBT.of({ name: 'neko', count: 2, items: [1, 2] }).toSnbt()").asString());
            assertEquals("127b", context.eval("js", "NBT.byte(127).toSnbt()").asString());
            assertEquals("TYPE_MISMATCH", context.eval("js", """
                    (() => {
                        try {
                            NBT.float(3.5e38);
                            return 'missing-error';
                        } catch (error) {
                            return error.code;
                        }
                    })()
                    """).asString());
            assertEquals("9223372036854775807", context.eval("js", "NBT.long('9223372036854775807').scalar()").asString());
            assertEquals("first", context.eval("js", "NBT.of({ first: 1 }).entries()[0].key()").asString());
            assertEquals("TYPE_MISMATCH", context.eval("js", """
                    (() => {
                        try {
                            NBT.of([1, 'mixed']);
                            return 'missing-error';
                        } catch (error) {
                            return error.code;
                        }
                    })()
                    """).asString());
            assertFalse(context.eval("js", "'elementKind' in NBT.of([])").asBoolean());
            assertEquals("NBT_LIMIT_EXCEEDED", context.eval("js", """
                    (() => {
                        try {
                            NBT.byteArray(Array(10001).fill(1));
                            return 'missing-error';
                        } catch (error) {
                            return error.code;
                        }
                    })()
                    """).asString());
            assertEquals(true, context.eval("js", "NBT.read('missing.nbt') === null").asBoolean());
            context.eval("js", "NBT.write('players/neko.nbt', { name: 'neko', count: 2 })");
            assertEquals("{name:\"neko\",count:2}",
                    context.eval("js", "NBT.read('players/neko.nbt').toSnbt()").asString());
            assertEquals("NBT_PATH_FORBIDDEN|../escape.nbt|read|member:NBT.read|neoforge|1.21.1",
                    context.eval("js", """
                            (() => {
                                try {
                                    NBT.read('../escape.nbt');
                                    return 'missing-error';
                                } catch (error) {
                                    return [error.code, error.path, error.operation, error.symbolId,
                                        error.platform, error.minecraftVersion].join('|');
                                }
                            })()
                            """).asString());
            assertEquals("TYPE_MISMATCH", context.eval("js", """
                    (() => {
                        try {
                            NBT.write('invalid.nbt', 1);
                            return 'missing-error';
                        } catch (error) {
                            return error.code;
                        }
                    })()
                    """).asString());
            assertEquals("minecraft:stone,minecraft:dirt",
                    context.eval("js", "Registry.get('minecraft:item').all().join(',')").asString());
            assertEquals(true, context.eval("js", "Registry.get('minecraft:item').exists()").asBoolean());
            assertEquals(true, context.eval("js", "Registry.get('minecraft:item').has('minecraft:stone')").asBoolean());
            assertEquals(false, context.eval("js", "Registry.get('minecraft:item').has('minecraft:netherite_block')").asBoolean());
            assertEquals("minecraft:stone",
                    context.eval("js", "Registry.get('minecraft:item').tag('minecraft:planks').join(',')").asString());
            assertEquals(false, context.eval("js", "Registry.get('minecraft:not_a_registry').exists()").asBoolean());
            assertEquals("neoforge:furnace_fuels",
                    context.eval("js", "Registry.get('minecraft:item').dataMapIds().join(',')").asString());
            assertEquals("{\"burn_time\":1600}",
                    context.eval("js", "Registry.get('minecraft:item').dataMapValue('neoforge:furnace_fuels', 'minecraft:coal')").asString());
            assertEquals(true, context.eval("js",
                    "Registry.get('minecraft:item').dataMapValue('neoforge:furnace_fuels', 'minecraft:stone') === null").asBoolean());
            assertEquals("TYPE_MISMATCH", context.eval("js", """
                    (() => {
                        try {
                            Registry.get('  ');
                            return 'missing-error';
                        } catch (error) {
                            return error.code;
                        }
                    })()
                    """).asString());
        }
    }

    private static EnvironmentKey serverEnvironment() {
        return new EnvironmentKey(
                ScriptTypeId.SERVER, RuntimeDist.DEDICATED_SERVER, "neoforge", "21.1.0",
                LoaderVersion.parse("21.1.0"), "1.21.1", Map.of());
    }

    private static final class FixturePlatform implements IPlatform {
        private final Map<String, IModInfo> mods = new LinkedHashMap<>();
        private final Path gameDir;

        private FixturePlatform(Path gameDir) {
            this.gameDir = gameDir;
            mods.put("nekojs", new FixtureModInfo("nekojs", "NekoJS", "1.1.0"));
            mods.put("alpha", new FixtureModInfo("alpha", "Alpha", "1.0.0"));
        }

        @Override public boolean isClient() { return false; }
        @Override public boolean isDevelopment() { return true; }
        @Override public String getMcVersion() { return "1.21.1"; }
        @Override public Path getGameDir() { return gameDir; }
        @Override public Map<String, IModInfo> getMods() { return mods; }
        @Override public IModInfo getInfo(String modID) { return mods.get(modID); }
        @Override public Set<PlatformCapability> capabilities() {
            return Set.of(PlatformCapability.RECIPE_HOT_RELOAD, PlatformCapability.NETWORK_CUSTOM_CHANNEL,
                    PlatformCapability.NBT_BINARY_IO);
        }
        @Override public NbtBinaryCodec nbtBinaryCodec() { return new FixtureNbtCodec(); }
        @Override public RegistryQueryService registryQueryService() { return new FixtureRegistryQueryService(); }
        @Override public String getLoaderId() { return "neoforge"; }
        @Override public String getLoaderVersion() { return "21.1.0"; }
    }

    private static final class FixtureNbtCodec implements NbtBinaryCodec {
        private static final byte[] COMPRESSED = {31, -117, 8, 0};

        @Override
        public NbtValue.CompoundValue decodeCompressed(byte[] compressed, NbtBinaryLimits limits)
                throws NbtBinaryException {
            if (!java.util.Arrays.equals(COMPRESSED, compressed)) {
                throw new NbtBinaryException(NbtBinaryException.Reason.INVALID, "Unexpected fixture bytes");
            }
            return value();
        }

        @Override
        public byte[] encodeCompressed(NbtValue.CompoundValue root, NbtBinaryLimits limits)
                throws NbtBinaryException {
            if (!value().equals(root)) {
                throw new NbtBinaryException(NbtBinaryException.Reason.INVALID, "Unexpected fixture value");
            }
            return COMPRESSED.clone();
        }

        private static NbtValue.CompoundValue value() {
            Map<String, NbtValue> values = new LinkedHashMap<>();
            values.put("name", NbtValue.string("neko"));
            values.put("count", NbtValue.intValue(2));
            return NbtValue.compound(values);
        }
    }

    private record FixtureModInfo(String id, String name, String version) implements IModInfo {
        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public void setName(String name) { throw new UnsupportedOperationException(); }
        @Override public String getVersion() { return version; }
        @Override public String getCustomName() { return name; }
    }

    private static final class FixtureRegistryQueryService implements RegistryQueryService {
        @Override
        public boolean hasRegistry(String registryId) {
            return "minecraft:item".equals(registryId);
        }

        @Override
        public List<String> all(String registryId) {
            return "minecraft:item".equals(registryId)
                    ? List.of("minecraft:stone", "minecraft:dirt")
                    : List.of();
        }

        @Override
        public boolean has(String registryId, String id) {
            return "minecraft:item".equals(registryId)
                    && Set.of("minecraft:stone", "minecraft:dirt").contains(id);
        }

        @Override
        public List<String> tag(String registryId, String tagId) {
            return "minecraft:item".equals(registryId) && "minecraft:planks".equals(tagId)
                    ? List.of("minecraft:stone")
                    : List.of();
        }

        @Override
        public List<String> dataMapIds(String registryId) {
            return "minecraft:item".equals(registryId)
                    ? List.of("neoforge:furnace_fuels")
                    : List.of();
        }

        @Override
        public String dataMapValue(String registryId, String dataMapTypeId, String id) {
            return "minecraft:item".equals(registryId)
                    && "neoforge:furnace_fuels".equals(dataMapTypeId)
                    && "minecraft:coal".equals(id)
                    ? "{\"burn_time\":1600}"
                    : null;
        }
    }
}
