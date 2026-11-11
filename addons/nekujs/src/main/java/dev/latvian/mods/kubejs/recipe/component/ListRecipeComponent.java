package dev.latvian.mods.kubejs.recipe.component;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.latvian.mods.kubejs.recipe.RecipeScriptContext;
import dev.latvian.mods.kubejs.util.IntBounds;
import dev.latvian.mods.rhino.type.TypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ListRecipeComponent<T>(RecipeComponent<T> component, boolean canWriteSelf, TypeInfo listTypeInfo,
        Codec<List<T>> listCodec, boolean conditional, IntBounds bounds,
        Optional<RecipeComponent<?>> spread, Optional<RecipeComponent<?>> spreadWrap) implements RecipeComponent<List<T>> {
    public static final RecipeComponentType<?> TYPE = RecipeComponentType.<ListRecipeComponent<?>>dynamic(
            ResourceLocation.fromNamespaceAndPath("kubejs", "list"), (type, ctx) -> RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ctx.recipeComponentCodec().fieldOf("component").forGetter(ListRecipeComponent::component),
                    Codec.BOOL.optionalFieldOf("can_write_self", false).forGetter(ListRecipeComponent::canWriteSelf),
                    Codec.BOOL.optionalFieldOf("conditional", false).forGetter(ListRecipeComponent::conditional),
                    IntBounds.MAP_CODEC.forGetter(ListRecipeComponent::bounds),
                    ctx.recipeComponentCodec().optionalFieldOf("spread").forGetter(ListRecipeComponent::spread)
            ).apply(instance, ListRecipeComponent::create)));

    public static <T> ListRecipeComponent<T> create(RecipeComponent<T> component, boolean canWriteSelf, boolean conditional) {
        return create(component, canWriteSelf, conditional, IntBounds.DEFAULT, Optional.empty());
    }

    public static <T> ListRecipeComponent<T> create(RecipeComponent<T> component, boolean canWriteSelf, boolean conditional,
            IntBounds bounds, Optional<RecipeComponent<?>> spread) {
        if (spread.isPresent()) throw new UnsupportedOperationException("[NEKO-5326] Programmatic list spreading is unsupported");
        if (bounds.min() < 0 || bounds.max() < bounds.min()) {
            throw new IllegalArgumentException("[NEKO-5325] Invalid recipe list bounds: " + bounds);
        }
        Codec<T> element = component.codec();
        Codec<List<T>> list = conditional
                ? NeoForgeExtraCodecs.listWithOptionalElements(ConditionalOps.createConditionalCodec(element)) : element.listOf();
        if (canWriteSelf) {
            list = Codec.either(element, list).xmap(value -> value.map(List::of, values -> values),
                    values -> values.size() == 1 ? Either.left(values.getFirst()) : Either.right(values));
        }
        Codec<List<T>> bounded = list.validate(values -> values.size() < bounds.min() || values.size() > bounds.max()
                ? DataResult.error(() -> "[NEKO-5329] Recipe list size is outside bounds: " + values.size() + " not in " + bounds)
                : DataResult.success(values));
        TypeInfo elementType = component.typeInfo();
        TypeInfo listType = TypeInfo.of(List.class).withParams(elementType);
        if (canWriteSelf) listType = listType.or(elementType);
        return new ListRecipeComponent<>(component, canWriteSelf, listType,
                bounded, conditional, bounds, spread, Optional.empty());
    }

    @Override public RecipeComponentType<?> type() { return TYPE; }
    @Override public Codec<List<T>> codec() { return listCodec; }
    @Override public TypeInfo typeInfo() { return listTypeInfo; }
    @Override public boolean allowEmpty() { return bounds.min() <= 0; }
    @Override public boolean isEmpty(List<T> value) { return value.isEmpty(); }

    public static <T> List<T> wrap0(RecipeScriptContext cx, RecipeComponent<T> component, Object from) {
        from = NativeComponentValues.scalar(from);
        List<T> values = new ArrayList<>();
        if (from instanceof graal.graalvm.polyglot.Value value && value.hasArrayElements()) {
            long length = value.getArraySize();
            for (long i = 0; i < length; i++) values.add(component.wrap(cx, value.getArrayElement(i)));
        } else if (from instanceof Iterable<?> iterable) {
            for (Object value : iterable) values.add(component.wrap(cx, value));
        } else if (from != null && from.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(from); i++) values.add(component.wrap(cx, Array.get(from, i)));
        } else values.add(component.wrap(cx, from));
        return values;
    }

    @Override public List<T> wrap(RecipeScriptContext cx, Object from) {
        List<T> values = wrap0(cx, component, from);
        if (values.size() < bounds.min() || values.size() > bounds.max()) {
            throw new IllegalArgumentException("[NEKO-5366] Recipe list size is outside bounds: " + values.size() + " not in " + bounds);
        }
        return values;
    }
    @Override public void buildUniqueId(UniqueIdBuilder builder, List<T> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i != 0) builder.appendSeparator();
            component.buildUniqueId(builder, values.get(i));
        }
    }
    public ListRecipeComponent<T> withBounds(IntBounds bounds) { return create(component, canWriteSelf, conditional, bounds, spread); }
    public ListRecipeComponent<T> orSelf() { return create(component, true, conditional, bounds, spread); }
    public ListRecipeComponent<T> asConditional() { return create(component, canWriteSelf, true, bounds, spread); }
    public ListRecipeComponent<T> withSpread(Optional<RecipeComponent<?>> spread) { return create(component, canWriteSelf, conditional, bounds, spread); }
    @Override public String toString() { return component + (canWriteSelf ? "[?]" : "[]") + (conditional ? "?" : ""); }
}
