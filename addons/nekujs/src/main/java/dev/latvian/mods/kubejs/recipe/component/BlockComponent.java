package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import com.tkisor.nekojs.js.type_adapter.BlockAdapter;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public record BlockComponent(boolean allowEmpty) implements RecipeComponent<Block> {
    private static final BlockAdapter CONVERTER = new BlockAdapter();
    private static final Codec<Block> CODEC = BuiltInRegistries.BLOCK.byNameCodec();
    public static final RecipeComponentType<Block> BLOCK = RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", "block"), new BlockComponent(false));
    public static final RecipeComponentType<Block> OPTIONAL_BLOCK = RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", "optional_block"), new BlockComponent(true));
    @Override public RecipeComponentType<?> type() { return allowEmpty ? OPTIONAL_BLOCK : BLOCK; }
    @Override public Codec<Block> codec() { return CODEC; }
    @Override public TypeInfo typeInfo() { return TypeInfo.of(Block.class); }
    @Override public Block wrap(RecipeScriptContext cx, Object from) {
        from = NativeComponentValues.scalar(from);
        return from instanceof BlockState state ? state.getBlock() : CONVERTER.apply(NativeComponentValues.value(from));
    }
    @Override public boolean isEmpty(Block value) { return value == Blocks.AIR; }
    @Override public void buildUniqueId(UniqueIdBuilder builder, Block value) { builder.append(BuiltInRegistries.BLOCK.getKey(value)); }
    @Override public String toString() { return allowEmpty ? "optional_block" : "block"; }
}
