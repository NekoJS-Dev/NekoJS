package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyIterable;
import graal.graalvm.polyglot.proxy.ProxyIterator;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class ApiFacadeProxy implements ProxyObject, ProxyIterable {

    private final ApiRuntimeView runtimeView;
    private final ApiSymbolId typeId;
    private final Object implementation;
    private final Set<String> memberNames;
    private final Map<String, ApiSymbol> memberSymbols;
    private final ApiValueMarshaller marshaller;
    private final boolean nativeReturn;
    private final ApiGuestErrorFactory guestErrorFactory;

    /**
     * Cache of the executables returned by getMember. Every {@code facade.member(...)} access in a
     * script calls {@link #getMember}, and the lambda captures only final fields of this instance
     * and immutable {@code memberSymbols} entries, with no per-call mutable state. Reusing one
     * ProxyExecutable per member key is therefore safe, and it avoids allocating a closure on every
     * property access.
     */
    private final Map<String, ProxyExecutable> memberExecutables = new ConcurrentHashMap<>();

    private ApiFacadeProxy(
            ApiRuntimeView runtimeView,
            ApiSymbolId typeId,
            Object implementation,
            Set<String> memberNames,
            Map<String, ApiSymbol> memberSymbols,
            ApiValueMarshaller marshaller,
            boolean nativeReturn,
            ApiGuestErrorFactory guestErrorFactory) {
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView");
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        this.implementation = implementation;
        this.memberNames = Set.copyOf(memberNames);
        this.memberSymbols = Map.copyOf(memberSymbols);
        this.marshaller = Objects.requireNonNull(marshaller, "marshaller");
        this.nativeReturn = nativeReturn;
        this.guestErrorFactory = guestErrorFactory;
    }

    public static ApiFacadeProxy global(ApiRuntimeView runtimeView, ApiSymbolId typeId, Object implementation) {
        return of(runtimeView, typeId, implementation, false, null);
    }

    public static ApiFacadeProxy global(
            ApiRuntimeView runtimeView,
            ApiSymbolId typeId,
            Object implementation,
            ApiGuestErrorFactory guestErrorFactory) {
        return of(runtimeView, typeId, implementation, false, Objects.requireNonNull(guestErrorFactory, "guestErrorFactory"));
    }

    public static ApiFacadeProxy value(ApiRuntimeView runtimeView, ApiSymbolId typeId, Object implementation) {
        return of(runtimeView, typeId, implementation, false, null);
    }

    public static ApiFacadeProxy withNativeReturn(ApiRuntimeView runtimeView, ApiSymbolId typeId, Object implementation) {
        return of(runtimeView, typeId, implementation, true, null);
    }

    private static ApiFacadeProxy of(
            ApiRuntimeView runtimeView,
            ApiSymbolId typeId,
            Object implementation,
            boolean nativeReturn,
            ApiGuestErrorFactory guestErrorFactory) {
        Objects.requireNonNull(runtimeView, "runtimeView");
        Objects.requireNonNull(typeId, "typeId");

        // memberSymbols depends only on typeId (facade registration is process-wide), so cache it by
        // typeId instead of re-running the stream and findSymbol reflection whenever an API returns
        // a SYMBOL type. This is the hot path of marshalReturn.
        MemberLayout layout = layoutOf(runtimeView, typeId);
        Set<String> memberNames = layout.memberNames();
        Map<String, ApiSymbol> memberSymbols = layout.memberSymbols();

        ApiValueMarshaller marshaller = new ApiValueMarshaller(runtimeView, guestErrorFactory);

        return new ApiFacadeProxy(
                runtimeView, typeId, implementation, memberNames, memberSymbols, marshaller, nativeReturn,
                guestErrorFactory);
    }

    /**
     * Process-wide cache from runtimeView to typeId to (memberNames, memberSymbols). memberSymbols
     * depends on both the typeId and the symbol table of that runtimeView, because the same typeId
     * can have a different member layout under a different registry instance, so the outer map is
     * keyed by runtimeView and typeId alone is not enough. The outer WeakHashMap lets a registry be
     * collected by the GC after reload or when a test ends. Facades are registered at startup and do
     * not change on script reload, so each registry's layout is stable for its lifetime.
     */
    private static final Map<ApiRuntimeView, ConcurrentHashMap<ApiSymbolId, MemberLayout>> LAYOUT_BY_VIEW =
            Collections.synchronizedMap(new WeakHashMap<>());

    private record MemberLayout(Set<String> memberNames, Map<String, ApiSymbol> memberSymbols) {}

    private static MemberLayout layoutOf(ApiRuntimeView runtimeView, ApiSymbolId typeId) {
        // The synchronizedMap wrapper makes the outer computeIfAbsent atomic. It runs only the first
        // time a registry is seen.
        ConcurrentHashMap<ApiSymbolId, MemberLayout> forView =
                LAYOUT_BY_VIEW.computeIfAbsent(runtimeView, rv -> new ConcurrentHashMap<>());
        return forView.computeIfAbsent(typeId, id -> {
            Set<String> names = runtimeView.memberNames(id);
            Map<String, ApiSymbol> symbols = names.stream()
                    .map(name -> {
                        ApiSymbolId memberId = new ApiSymbolId("member", id.qualifiedName() + "." + name);
                        return runtimeView.findSymbol(memberId).map(s -> Map.entry(name, s));
                    })
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            return new MemberLayout(Set.copyOf(names), Map.copyOf(symbols));
        });
    }

    ApiSymbolId typeId() {
        return typeId;
    }

    Object implementation() {
        return implementation;
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

        return memberExecutables.computeIfAbsent(key, memberKey -> (ProxyExecutable) arguments -> {
            List<Object> rawArgs = new ArrayList<>();
            for (Value arg : arguments) {
                rawArgs.add(arg);
            }

            try {
                ApiSignature signature = marshaller.selectSignature(symbol, rawArgs);
                String signatureKey = signature.callKey();
                String memberId = symbol.id().value();
                List<Object> marshalledArgs = marshaller.marshalArguments(signature, rawArgs, memberId);
                Object rawReturn = runtimeView.invoke(symbol.id(), signatureKey, implementation, marshalledArgs);
                return marshaller.marshalReturn(rawReturn, signature.returnType(), nativeReturn, memberId);
            } catch (ApiInvocationException e) {
                ApiRuntimeException normalized = normalize(e, symbol.id());
                if (guestErrorFactory != null) {
                    return guestErrorFactory.raise(normalized);
                }
                throw normalized;
            } catch (Exception e) {
                ApiRuntimeException normalized = new ApiRuntimeException(
                        ApiErrorCodes.INVOCATION_ERROR,
                        "Failed to invoke " + symbol.id(),
                        symbol.id().value(), platform(), minecraftVersion(), null, null, e);
                if (guestErrorFactory != null) {
                    return guestErrorFactory.raise(normalized);
                }
                throw normalized;
            }
        });
    }

    static ApiFacadeProxy value(
            ApiRuntimeView runtimeView,
            ApiSymbolId typeId,
            Object implementation,
            ApiGuestErrorFactory guestErrorFactory) {
        return of(runtimeView, typeId, implementation, false, guestErrorFactory);
    }

    private ApiRuntimeException normalize(ApiInvocationException error, ApiSymbolId memberId) {
        Map<String, String> details = new java.util.LinkedHashMap<>(error.details());
        String requiredCapability = details.remove("requiredCapability");
        String replacement = details.remove("replacement");
        details.remove("symbolId");
        details.remove("platform");
        details.remove("minecraftVersion");
        return new ApiRuntimeException(
                error.code(),
                error.getMessage(),
                memberId.value(),
                platform(),
                minecraftVersion(),
                requiredCapability,
                replacement,
                details,
                error);
    }

    private String platform() {
        return runtimeView.environmentSnapshot() == null
                ? null
                : runtimeView.environmentSnapshot().environmentKey().loaderId();
    }

    private String minecraftVersion() {
        return runtimeView.environmentSnapshot() == null
                ? null
                : runtimeView.environmentSnapshot().environmentKey().minecraftVersion();
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Managed API values are read-only");
    }

    /**
     * Makes the proxy visible to a script's {@code for...of} when the implementation under it is
     * itself iterable, for example the entry id list of {@code RegistryView}. Elements pass through
     * unchanged; the only iterable implementation today yields String ids.
     */
    @Override
    public ProxyIterator getIterator() {
        if (!(implementation instanceof Iterable<?> iterable)) {
            return null;
        }
        java.util.Iterator<?> iterator = iterable.iterator();
        return new ProxyIterator() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Object getNext() {
                return iterator.next();
            }
        };
    }

    @Override
    public boolean removeMember(String key) {
        return false;
    }
}
