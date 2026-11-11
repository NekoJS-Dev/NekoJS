package dev.latvian.mods.kubejs.recipe.schema;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.latvian.mods.kubejs.recipe.RecipeTypeRegistryContext;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.util.RegistryAccessContainer;
import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Component, factory and programmatic schema metadata; execution uses the native recipe engine. */
public class RecipeSchemaStorage implements RecipeComponentTypeRegistry {
    private final Map<ResourceLocation, RecipeComponentType<?>> types = new LinkedHashMap<>();
    private final RecipeTypeRegistryContext context;
    private final Map<ResourceLocation, KubeRecipeFactory> factories = new LinkedHashMap<>();
    private final Map<ResourceLocation, RecipeSchema> schemas = new LinkedHashMap<>();
    public final Map<ResourceLocation, KubeRecipeFactory> recipeTypes = Collections.unmodifiableMap(factories);
    private Map<ResourceLocation, MapCodec<RecipeComponent<?>>> codecs;
    public final Codec<RecipeComponent<?>> recipeComponentCodec;

    public RecipeSchemaStorage(RegistryAccessContainer registries) {
        context = new RecipeTypeRegistryContext(registries, this);
        Codec<RecipeComponentType<?>> typeCodec = Codec.STRING.comapFlatMap(name -> {
            ResourceLocation id = ResourceLocation.tryParse(name.contains(":") ? name : "kubejs:" + name);
            RecipeComponentType<?> type = types.get(id);
            return type == null ? DataResult.error(() -> "NEKO-4241: Unknown recipe component type: " + name) : DataResult.success(type);
        }, type -> type.toString());
        Codec<RecipeComponent<?>> direct = typeCodec.partialDispatch("type", component -> {
            requireFrozen();
            RecipeComponentType<?> type = types.get(component.type().id());
            return type == component.type() ? DataResult.success(type)
                    : DataResult.error(() -> "NEKO-4243: Recipe component type is not registered: " + component.type());
        }, type -> { requireFrozen(); return DataResult.success(codecs.get(type.id())); });
        recipeComponentCodec = Codec.either(typeCodec, direct).comapFlatMap(
                either -> either.map(type -> {
                    requireFrozen();
                    return type.isUnit() ? DataResult.success(type.instance())
                            : DataResult.error(() -> "NEKO-4239: Dynamic recipe component requires an object: " + type);
                }, DataResult::success),
                component -> {
                    requireFrozen();
                    return component.type().isUnit() ? Either.left(requireRegistered(component.type())) : Either.right(component);
                });
    }

    @Override public void register(RecipeComponentType<?> type) {
        Objects.requireNonNull(type);
        if (codecs != null) throw new IllegalStateException("NEKO-4240: Recipe component catalog is frozen");
        if (types.putIfAbsent(type.id(), type) != null) {
            throw new IllegalArgumentException("NEKO-4238: Duplicate recipe component type: " + type.id());
        }
    }
    public Map<ResourceLocation, RecipeComponentType<?>> componentTypes() { return Collections.unmodifiableMap(types); }
    public void freezeComponents() {
        if (codecs != null) return;
        Map<ResourceLocation, MapCodec<RecipeComponent<?>>> prepared = new LinkedHashMap<>();
        for (RecipeComponentType<?> type : types.values()) prepared.put(type.id(), type.mapCodec(context));
        codecs = Map.copyOf(prepared);
    }
    public void registerFactory(KubeRecipeFactory factory) {
        Objects.requireNonNull(factory);
        if (factories.putIfAbsent(factory.id(), factory) != null) {
            throw new IllegalArgumentException("[NEKO-5321] Duplicate recipe factory: " + factory.id());
        }
    }
    public Map<ResourceLocation, KubeRecipeFactory> recipeFactories() { return recipeTypes; }
    public void registerSchema(ResourceLocation id, RecipeSchema schema) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(schema);
        if (schemas.putIfAbsent(id, schema) != null) {
            throw new IllegalArgumentException("[NEKO-5320] Duplicate programmatic recipe schema: " + id);
        }
    }
    public Map<ResourceLocation, RecipeSchema> programmaticSchemas() { return Collections.unmodifiableMap(schemas); }
    public RegistryAccessContainer registries() { return context.registries(); }
    private void requireFrozen() {
        if (codecs == null) throw new IllegalStateException("NEKO-4242: Recipe component catalog has not been frozen");
    }
    private RecipeComponentType<?> requireRegistered(RecipeComponentType<?> type) {
        if (types.get(type.id()) != type) throw new IllegalArgumentException("NEKO-4244: Unit recipe component type is not registered: " + type);
        return type;
    }
}
