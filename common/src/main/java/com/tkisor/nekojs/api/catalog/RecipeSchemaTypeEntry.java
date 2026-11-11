package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.AdapterInputShape;
import com.tkisor.nekojs.api.recipe.definition.RecipeTypeDefinition;
import com.tkisor.nekojs.api.surface.ApiTypeRef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Describes one authoring profile from the canonical recipe schema registry. */
public record RecipeSchemaTypeEntry(
        String namespace,
        String type,
        String idPrefix,
        List<SchemaField> fields,
        List<List<String>> constructors,
        Map<String, String> fieldAliases,
        List<String> functions,
        String unsupportedReason,
        Map<String, String> fieldFunctions,
        Class<?> executionType,
        boolean hasConstructorOverrides
) {
    public RecipeSchemaTypeEntry {
        fields = List.copyOf(fields);
        constructors = constructors.stream().map(List::copyOf).toList();
        fieldAliases = Collections.unmodifiableMap(new LinkedHashMap<>(fieldAliases));
        functions = List.copyOf(functions);
        if (fieldFunctions != null) fieldFunctions = Collections.unmodifiableMap(new LinkedHashMap<>(fieldFunctions));
        executionType = executionType == null ? Object.class : executionType;
    }

    public RecipeSchemaTypeEntry(String namespace, String type, String idPrefix,
                                 List<SchemaField> fields, List<List<String>> constructors,
                                 Map<String, String> fieldAliases, List<String> functions, String unsupportedReason,
                                 Map<String, String> fieldFunctions, Class<?> executionType) {
        this(namespace, type, idPrefix, fields, constructors, fieldAliases, functions, unsupportedReason,
                fieldFunctions, executionType, false);
    }

    public RecipeSchemaTypeEntry(String namespace, String type, String idPrefix,
                                 List<SchemaField> fields, List<List<String>> constructors,
                                 Map<String, String> fieldAliases, List<String> functions, String unsupportedReason) {
        this(namespace, type, idPrefix, fields, constructors, fieldAliases, functions, unsupportedReason, null, Object.class);
    }

    public RecipeSchemaTypeEntry(String namespace, String type, String idPrefix,
                                 List<SchemaField> fields, List<List<String>> constructors) {
        this(namespace, type, idPrefix, fields, constructors, Map.of(), List.of(), null);
    }

    /** Nominal component input types drive signatures; shapes remain the fallback for structural codecs. */
    public record SchemaField(String name, String kind, boolean required, boolean array, String path,
                              AdapterInputShape inputShape, ApiTypeRef inputType) {
        public SchemaField(String name, String kind, boolean required, boolean array, String path,
                           AdapterInputShape inputShape) {
            this(name, kind, required, array, path, inputShape, null);
        }
        public SchemaField(String name, String kind, boolean required, boolean array, String path) {
            this(name, kind, required, array, path, null, null);
        }
    }

    public static RecipeSchemaTypeEntry from(RecipeTypeDefinition def) {
        List<SchemaField> fields = new ArrayList<>();
        for (var field : def.fields().values()) {
            fields.add(new SchemaField(field.name(), field.kind().name(), field.required(), field.array(), field.path(),
                    field.valueCodec() == null ? null : field.valueCodec().inputShape(),
                    field.valueCodec() == null ? null : field.valueCodec().inputType()));
        }
        return new RecipeSchemaTypeEntry(def.namespace(), def.name(), def.prefix(), fields, def.constructors(),
                def.fieldAliases(), new ArrayList<>(def.functions().keySet()), def.unsupportedReason(),
                def.fieldFunctions(), def.executionFactory() == null ? Object.class : def.executionFactory().exposedType(),
                !def.constructorOverrides().isEmpty());
    }
}
