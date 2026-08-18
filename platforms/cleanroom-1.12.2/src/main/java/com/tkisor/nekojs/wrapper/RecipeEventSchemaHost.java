package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import graal.graalvm.polyglot.Value;

/**
 * 1.12.2 implementation of {@link RecipeSchemaHost}.
 *
 * <p>Schema 字段经 {@link #applyField} 以 typed 值注入 {@link ReflectiveRecipeBuilder}
 * （1.12.2 无 JSON 管道，不走 JSON setPath）；builder 在 event flush 时反射构造并注册。
 */
@Doc("1.12.2 recipe schema host: injects typed field values into reflective builders instead of writing JSON.")
public final class RecipeEventSchemaHost implements RecipeSchemaHost {
    private final RecipeEventJS event;

    /** Wraps the recipes event. */
    public RecipeEventSchemaHost(RecipeEventJS event) {
        this.event = event;
    }

    /** Creates a reflective builder for a schema type. */
    @Doc("Creates a reflective recipe builder for a schema type.")
    @Param(name = "type", value = "the recipe type name within its namespace")
    @Param(name = "prefix", value = "the generated recipe id prefix")
    @Return("a new ReflectiveRecipeBuilder registered with the event")
    @Override
    public RecipeBuilder builder(String type, String prefix) {
        return new ReflectiveRecipeBuilder(event, type, prefix);
    }

    /** Not supported on 1.12.2 (no JSON pipeline). */
    @Doc("Not supported on 1.12.2 (no JSON recipe pipeline).")
    @Doc("Use event.registerSchema + event.recipes.<ns>.<type>(...) or a Java plugin handler.")
    @Param(name = "json", value = "the custom recipe JSON")
    @Return("never returns; always throws UnsupportedOperationException")
    @Override
    public Object custom(JsonObject json) {
        // 1.12.2 无运行时 JSON 注册管道：明确失败，指引 schema 路径
        throw new UnsupportedOperationException(
                "event.custom(...) is not supported on 1.12.2 (no JSON recipe pipeline). "
                        + "Use event.registerSchema + event.recipes.<ns>.<type>(...) or a Java plugin handler.");
    }

    /** Converts a polyglot value to JSON. */
    @Doc("Converts a polyglot value into a JSON element.")
    @Param(name = "value", value = "the polyglot value to convert")
    @Return("the converted JsonElement")
    @Override
    public JsonElement toJson(Value value) {
        return RecipeJsonValueConverter.toJson(value);
    }

    /** Converts a field value to JSON. */
    @Doc("Encodes a schema field value into JSON.")
    @Param(name = "kind", value = "the declared field kind")
    @Param(name = "value", value = "the field value to encode")
    @Return("the converted JsonElement")
    @Override
    public JsonElement encodeField(RecipeFieldKind kind, Value value) {
        return RecipeJsonValueConverter.toJson(value);
    }

    /** Injects a typed field into the reflective builder. */
    @Doc("Injects a typed field value into a reflective recipe builder.")
    @Param(name = "builder", value = "the target ReflectiveRecipeBuilder")
    @Param(name = "field", value = "the field definition being applied")
    @Param(name = "value", value = "the field's value")
    @Override
    public void applyField(RecipeBuilder builder, RecipeFieldDefinition field, Value value) {
        ((ReflectiveRecipeBuilder) builder).setTyped(field.name(), field.kind(), value);
    }
}
