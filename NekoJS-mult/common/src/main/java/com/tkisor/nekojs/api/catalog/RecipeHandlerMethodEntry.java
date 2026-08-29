package com.tkisor.nekojs.api.catalog;

import java.util.List;

/**
 * Describes a single handler method overload for NekoProbe type generation.
 *
 * @param methodName   the method name (e.g. "crafting_shaped")
 * @param params       ordered parameter descriptors
 * @param minArgs      minimum required args (non-Optional params)
 */
public record RecipeHandlerMethodEntry(
        String methodName,
        List<HandlerParam> params,
        int minArgs
) {
    public record HandlerParam(String name, String type, String qualifiedType, String genericType, boolean optional) {
        /**
         * @param name           parameter name
         * @param type           simple type name for TS primitive mapping (e.g. "ItemStack", "string")
         * @param qualifiedType  fully-qualified Java type name (e.g. "net.minecraft.item.ItemStack");
         *                       the probe uses it to emit version-correct {@code java:...} imports
         *                       rather than hard-coding a MC-version-specific package. {@code null}
         *                       for primitives / {@code String} / numbers (no alias import needed).
         * @param genericType    generic signature preserving type arguments (e.g.
         *                       "java.util.List<net.minecraft.item.crafting.Ingredient>"), so the probe
         *                       renders {@code $Ingredient_[]} / {@code { [key: string]: $Ingredient_ }}
         *                       instead of a bare {@code $List_}. {@code null} for primitives.
         * @param optional       whether the parameter is optional
         */
        public HandlerParam(String name, String type, String qualifiedType, String genericType, boolean optional) {
            this.name = name;
            this.type = type;
            this.qualifiedType = qualifiedType;
            this.genericType = genericType;
            this.optional = optional;
        }
    }
}
