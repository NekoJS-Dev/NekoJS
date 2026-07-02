package com.tkisor.nekojs.wrapper;

import com.tkisor.nekojs.api.recipe.RecipeBuilder;
import com.tkisor.nekojs.api.recipe.RecipeSchemaHost;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinitionRegistry;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataDrivenRecipeNamespaceProxy implements ProxyObject {
    private final RecipeSchemaHost host;
    private final String namespace;
    private final RecipeTypeDefinitionRegistry definitions;

    public DataDrivenRecipeNamespaceProxy(RecipeSchemaHost host, String namespace, RecipeTypeDefinitionRegistry definitions) {
        this.host = host;
        this.namespace = namespace;
        this.definitions = definitions;
    }

    @Override
    public Object getMember(String recipeType) {
        RecipeTypeDefinition definition = definitions.get(namespace, recipeType);
        if (definition == null) {
            return new FallbackNamespaceProxy(host, namespace).getMember(recipeType);
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

    private SchemaRecipeBuilder invoke(RecipeTypeDefinition definition, Value[] arguments) {
        Map<String, Value> values = values(definition, arguments);
        RecipeBuilder builder = host.builder(definition.type(), definition.prefix());
        return new SchemaRecipeBuilder(builder, definition, host, values);
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
}
