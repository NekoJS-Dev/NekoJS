package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.recipe.filter.RecipeMatchContext;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public record TagKeyComponent<T>(RecipeComponentType<?> typeOverride, ResourceKey<? extends Registry<T>> registry,
        TypeInfo registryType, Codec<TagKey<T>> codec, TypeInfo typeInfo, boolean hashed) implements RecipeComponent<TagKey<T>> {
    public static final TypeInfo TAG_KEY_TYPE = TypeInfo.of(TagKey.class);
    public static final RecipeComponentType<TagKey<Block>> BLOCK = create("block_tag", Registries.BLOCK, Block.class, false);
    public static final RecipeComponentType<TagKey<Item>> ITEM = create("item_tag", Registries.ITEM, Item.class, false);
    public static final RecipeComponentType<TagKey<EntityType<?>>> ENTITY_TYPE = create("entity_type_tag", Registries.ENTITY_TYPE, EntityType.class, false);
    public static final RecipeComponentType<TagKey<Block>> HASHED_BLOCK = create("hashed_block_tag", Registries.BLOCK, Block.class, true);
    public static final RecipeComponentType<TagKey<Item>> HASHED_ITEM = create("hashed_item_tag", Registries.ITEM, Item.class, true);
    public static final RecipeComponentType<TagKey<EntityType<?>>> HASHED_ENTITY_TYPE = create("hashed_entity_type_tag", Registries.ENTITY_TYPE, EntityType.class, true);
    private static <T> RecipeComponentType<TagKey<T>> create(String name, ResourceKey<? extends Registry<T>> registry, Class<?> valueType, boolean hashed) {
        return RecipeComponentType.unit(ResourceLocation.fromNamespaceAndPath("kubejs", name), type -> new TagKeyComponent<>(type, registry, TypeInfo.of(valueType), hashed));
    }
    public TagKeyComponent(RecipeComponentType<?> type, ResourceKey<? extends Registry<T>> registry, TypeInfo registryType, boolean hashed) {
        this(type, registry, registryType, hashed ? TagKey.hashedCodec(registry) : TagKey.codec(registry), TAG_KEY_TYPE.withParams(registryType), hashed);
    }
    @Override public RecipeComponentType<?> type() { return typeOverride; }
    @Override public TagKey<T> wrap(RecipeScriptContext cx, Object from) {
        from = NativeComponentValues.scalar(from);
        if (from instanceof TagKey<?> tag) {
            return tag.cast(registry).orElseThrow(() -> new IllegalArgumentException("[NEKO-5362] Recipe tag belongs to a different registry: " + tag));
        }
        if (from instanceof ResourceLocation location) return TagKey.create(registry, location);
        if (!(from instanceof CharSequence text)) throw new IllegalArgumentException("[NEKO-5363] Expected a recipe tag identifier");
        String id = text.toString();
        return TagKey.create(registry, ResourceLocation.parse(id.startsWith("#") ? id.substring(1) : id));
    }
    @Override public boolean hasPriority(RecipeMatchContext cx, Object from) {
        from = NativeComponentValues.scalar(from);
        return from instanceof TagKey<?> || from instanceof CharSequence text && text.toString().startsWith("#");
    }
    @Override public boolean isEmpty(TagKey<T> value) { return false; }
    @Override public void buildUniqueId(UniqueIdBuilder builder, TagKey<T> value) { builder.append(value.location()); }
    @Override public String toString() { return typeOverride.toString(); }
}
