package com.tkisor.nekojs.api.catalog;

import com.tkisor.nekojs.api.surface.ApiTypeRef;
import java.util.List;

/** One executable native handler overload, including its actual result type. */
public record RecipeHandlerMethodEntry(String methodName, List<HandlerParam> params, int minArgs, ApiTypeRef returnType) {
    public RecipeHandlerMethodEntry {
        params = List.copyOf(params);
    }

    /** Legacy descriptors without result metadata retain their base recipe-builder contract. */
    public RecipeHandlerMethodEntry(String methodName, List<HandlerParam> params, int minArgs) {
        this(methodName, params, minArgs, null);
    }

    /** The neutral type is authoritative; legacy display/signature fields remain available to older contributors. */
    public record HandlerParam(String name, String type, String qualifiedType, String genericType,
                               boolean optional, ApiTypeRef typeRef) {
        public HandlerParam(String name, String type, String qualifiedType, String genericType, boolean optional) {
            this(name, type, qualifiedType, genericType, optional, null);
        }
    }
}
