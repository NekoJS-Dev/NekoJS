package com.tkisor.nekojs.wrapper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.tkisor.nekojs.api.recipe.RecipeJsonBuilder;
import com.tkisor.nekojs.api.recipe.RecipeJsonValue;
import com.tkisor.nekojs.api.recipe.RecipeJsonValueConverter;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeFieldKind;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import com.tkisor.nekojs.js.type_adapter.ItemStackAdapter;
import com.tkisor.nekojs.wrapper.event.server.RecipeEventJS;
import com.tkisor.nekojs.wrapper.fluid.FluidResolver;
import com.tkisor.nekojs.wrapper.item.IngredientResolver;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataDrivenRecipeNamespaceProxy implements ProxyObject {
    private final RecipeEventJS event;
    private final String namespace;
    private final RecipeTypeDefinitionRegistry definitions;

    public DataDrivenRecipeNamespaceProxy(RecipeEventJS event, String namespace, RecipeTypeDefinitionRegistry definitions) {
        this.event = event;
        this.namespace = namespace;
        this.definitions = definitions;
    }

    @Override
    public Object getMember(String recipeType) {
        RecipeTypeDefinition definition = definitions.get(namespace, recipeType);
        if (definition == null) {
            return new FallbackNamespaceProxy(event, namespace).getMember(recipeType);
        }
        return (ProxyExecutable) arguments -> invoke(definition, arguments);
    }

    @Override
    public Object getMemberKeys() {
        return definitions.types(namespace).toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return true;
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Recipe namespace definitions are read-only");
    }

    private RecipeJsonBuilder invoke(RecipeTypeDefinition definition, Value[] arguments) {
        Map<String, Value> values = values(definition, arguments);
        RecipeJsonBuilder builder = new RecipeJsonBuilder(event, definition.type(), definition.prefix());
        for (RecipeFieldDefinition field : definition.fields().values()) {
            if (values.containsKey(field.name())) {
                builder.setPath(field.path(), new RecipeJsonValue(convert(field, values.get(field.name()))));
            } else if (field.defaultValue() != null) {
                builder.setPath(field.path(), new RecipeJsonValue(field.defaultValue().deepCopy()));
            } else if (field.required()) {
                throw new IllegalArgumentException("Missing required recipe field '" + field.name() + "' for " + definition.key());
            }
        }
        return builder;
    }

    private Map<String, Value> values(RecipeTypeDefinition definition, Value[] arguments) {
        if (arguments.length == 1 && arguments[0].hasMembers() && hasDefinitionField(definition, arguments[0])) {
            Map<String, Value> values = new LinkedHashMap<>();
            for (String field : definition.fields().keySet()) {
                if (arguments[0].hasMember(field)) {
                    values.put(field, arguments[0].getMember(field));
                }
            }
            return values;
        }
        for (List<String> constructor : definition.constructors()) {
            if (constructor.size() == arguments.length) {
                Map<String, Value> values = new LinkedHashMap<>();
                for (int i = 0; i < constructor.size(); i++) {
                    values.put(constructor.get(i), arguments[i]);
                }
                return values;
            }
        }
        throw new IllegalArgumentException("No constructor for " + definition.key() + " accepts " + arguments.length + " arguments");
    }

    private boolean hasDefinitionField(RecipeTypeDefinition definition, Value value) {
        for (String field : definition.fields().keySet()) {
            if (value.hasMember(field)) {
                return true;
            }
        }
        return false;
    }

    private JsonElement convert(RecipeFieldDefinition field, Value value) {
        if (!field.array()) {
            return convertOne(field.kind(), value);
        }
        JsonArray array = new JsonArray();
        if (value.hasArrayElements()) {
            for (long i = 0; i < value.getArraySize(); i++) {
                array.add(convertOne(field.kind(), value.getArrayElement(i)));
            }
        } else {
            array.add(convertOne(field.kind(), value));
        }
        return array;
    }

    private JsonElement convertOne(RecipeFieldKind kind, Value value) {
        return switch (kind) {
            case JSON -> RecipeJsonValueConverter.toJson(event, value);
            case STRING -> new JsonPrimitive(value.asString());
            case INT -> new JsonPrimitive(value.asInt());
            case NUMBER -> new JsonPrimitive(value.asDouble());
            case BOOLEAN -> new JsonPrimitive(value.asBoolean());
            case INGREDIENT -> event.serializeIngredient(IngredientResolver.fromValue(value));
            case ITEM_STACK -> event.serializeResult(new ItemStackAdapter().convert(value));
            case FLUID_STACK -> event.serializeFluidStack(FluidResolver.stackFromValue(value));
            case FLUID_INGREDIENT -> event.serializeFluidIngredient(FluidResolver.ingredientFromValue(value));
            case SIZED_FLUID_INGREDIENT -> event.serializeSizedFluidIngredient(FluidResolver.sizedFromValue(value));
        };
    }
}
