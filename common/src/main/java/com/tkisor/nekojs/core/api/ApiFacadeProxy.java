package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ApiFacadeProxy implements ProxyObject {

    private final ApiRuntimeView runtimeView;
    private final ApiSymbolId typeId;
    private final Object implementation;
    private final Set<String> memberNames;
    private final Map<String, ApiSymbol> memberSymbols;
    private final ApiValueMarshaller marshaller;
    private final boolean nativeReturn;

    private ApiFacadeProxy(
            ApiRuntimeView runtimeView,
            ApiSymbolId typeId,
            Object implementation,
            Set<String> memberNames,
            Map<String, ApiSymbol> memberSymbols,
            ApiValueMarshaller marshaller,
            boolean nativeReturn) {
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView");
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        this.implementation = implementation;
        this.memberNames = Set.copyOf(memberNames);
        this.memberSymbols = Map.copyOf(memberSymbols);
        this.marshaller = Objects.requireNonNull(marshaller, "marshaller");
        this.nativeReturn = nativeReturn;
    }

    public static ApiFacadeProxy global(ApiRuntimeView runtimeView, ApiSymbolId typeId, Object implementation) {
        return of(runtimeView, typeId, implementation, false);
    }

    public static ApiFacadeProxy value(ApiRuntimeView runtimeView, ApiSymbolId typeId, Object implementation) {
        return of(runtimeView, typeId, implementation, false);
    }

    public static ApiFacadeProxy withNativeReturn(ApiRuntimeView runtimeView, ApiSymbolId typeId, Object implementation) {
        return of(runtimeView, typeId, implementation, true);
    }

    private static ApiFacadeProxy of(
            ApiRuntimeView runtimeView,
            ApiSymbolId typeId,
            Object implementation,
            boolean nativeReturn) {
        Objects.requireNonNull(runtimeView, "runtimeView");
        Objects.requireNonNull(typeId, "typeId");

        Set<String> memberNames = runtimeView.memberNames(typeId);

        Map<String, ApiSymbol> memberSymbols = memberNames.stream()
                .map(name -> {
                    ApiSymbolId memberId = new ApiSymbolId("member", typeId.qualifiedName() + "." + name);
                    return runtimeView.findSymbol(memberId)
                            .map(s -> Map.entry(name, s));
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Set<ApiSymbolId> allSymbolIds = memberSymbols.values().stream()
                .map(ApiSymbol::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        ApiValueMarshaller marshaller = new ApiValueMarshaller(runtimeView, allSymbolIds);

        return new ApiFacadeProxy(runtimeView, typeId, implementation, memberNames, memberSymbols, marshaller, nativeReturn);
    }

    @Override
    public Object getMemberKeys() {
        return memberNames.toArray();
    }

    @Override
    public boolean hasMember(String key) {
        Objects.requireNonNull(key, "key");
        return memberNames.contains(key);
    }

    @Override
    public Object getMember(String key) {
        Objects.requireNonNull(key, "key");

        if (!memberNames.contains(key)) {
            return null;
        }

        ApiSymbol symbol = memberSymbols.get(key);
        if (symbol == null) {
            return null;
        }

        return (ProxyExecutable) arguments -> {
            List<Object> rawArgs = new ArrayList<>();
            for (Value arg : arguments) {
                rawArgs.add(arg);
            }

            ApiSignature signature = marshaller.selectSignature(symbol, rawArgs);
            String signatureKey = signature.callKey();

            List<Object> marshalledArgs = marshaller.marshalArguments(signature, rawArgs, typeId.value());
            try {
                Object rawReturn = runtimeView.invoke(symbol.id(), signatureKey, implementation, marshalledArgs);
                return marshaller.marshalReturn(rawReturn, signature.returnType(), nativeReturn, typeId.value());
            } catch (ApiRuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new ApiRuntimeException(
                        "INVOCATION_ERROR",
                        "Failed to invoke " + symbol.id() + ": " + e.getMessage(),
                        typeId.value(), null, null, null, null);
            }
        };
    }

    @Override
    public void putMember(String key, Value value) {
        // Read-only proxy, ignore writes
    }

    @Override
    public boolean removeMember(String key) {
        return false;
    }
}
