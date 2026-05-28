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
    public record HandlerParam(String name, String type, boolean optional) {}
}
