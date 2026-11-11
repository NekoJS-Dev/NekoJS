package dev.latvian.mods.kubejs.recipe;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeBuilderSession;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaType;
import dev.latvian.mods.kubejs.script.SourceLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;

/** A legacy fluent target borrowing one native builder; it never publishes its own recipe runtime. */
public class KubeRecipe {
    public SourceLine sourceLine = SourceLine.UNKNOWN;
    private RecipeSchemaType schemaType;
    private RecipeBuilder builder;
    private RecipeBuilderSession session;
    private RegistryOps<JsonElement> ops;

    @HideFromJS
    public final void attach(RecipeSchemaType type, RecipeBuilder builder, RecipeSchemaHost host, RecipeBuilderSession session) {
        if (this.session != null) throw new IllegalStateException("[NEKO-5331] Legacy recipe is already attached");
        this.schemaType = Objects.requireNonNull(type);
        this.builder = Objects.requireNonNull(builder);
        this.session = Objects.requireNonNull(session);
        ops = ((HolderLookup.Provider) host.registryAccess()).createSerializationContext(JsonOps.INSTANCE);
        session.beforeEncode(this::serialize);
    }

    @HideFromJS
    public <T> T getValue(RecipeKey<T> key) {
        requireKey(key);
        JsonElement json = session.snapshot().get(key.name);
        T value = json == null ? defaultValue(key) : key.codec.parse(ops, json)
                .getOrThrow(message -> new IllegalArgumentException("[NEKO-5332] Legacy recipe field decode failed: " + key.name + ": " + message));
        // Legacy fluent methods append to list values before assigning them back.
        if (value instanceof List<?> list) {
            @SuppressWarnings("unchecked") T mutable = (T) new ArrayList<>(list);
            return mutable;
        }
        return value;
    }

    @HideFromJS
    public <T> KubeRecipe setValue(RecipeKey<T> key, T value) {
        requireKey(key);
        JsonElement json = key.codec.encodeStart(ops, value).getOrThrow(message ->
                new IllegalArgumentException("[NEKO-5333] Legacy recipe field encode failed: " + key.name + ": " + message));
        builder.setPath(key.name, new RecipeJsonValue(json));
        return this;
    }

    @HideFromJS
    public ResourceLocation getOrCreateId() {
        requireAttached();
        return ResourceLocation.parse(session.currentId());
    }

    /** Subtypes validate their fields immediately before the native encode operation. */
    @HideFromJS
    public void serialize() { requireAttached(); }

    private <T> T defaultValue(RecipeKey<T> key) {
        var override = schemaType.schema.keyOverrides.get(key);
        if (override != null) {
            @SuppressWarnings("unchecked") T value = (T) override.getDefaultValue(schemaType);
            return value;
        }
        return key.optional == null ? null : key.optional.getDefaultValue(schemaType);
    }

    private void requireKey(RecipeKey<?> key) {
        requireAttached();
        if (!schemaType.schema.keys.contains(key)) throw new IllegalArgumentException("[NEKO-5334] Recipe key belongs to another schema: " + key.name);
    }

    private void requireAttached() {
        if (session == null) throw new IllegalStateException("[NEKO-5335] Legacy recipe has no native builder session");
    }
}
