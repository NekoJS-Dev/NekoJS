package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.error.ApiErrorCodes;
import com.tkisor.nekojs.api.error.ApiInvocationException;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiRuntimeView;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
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

public final class ApiFacadeProxy implements ProxyObject {

    private final ApiRuntimeView runtimeView;
    private final ApiSymbolId typeId;
    private final Object implementation;
    private final Set<String> memberNames;
    private final Map<String, ApiSymbol> memberSymbols;
    private final ApiValueMarshaller marshaller;
    private final boolean nativeReturn;
    private final ApiGuestErrorFactory guestErrorFactory;

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

        // memberSymbols 只依赖 typeId（facade 注册是进程全局的），按 typeId 缓存，避免每次
        // API 返回 SYMBOL 类型都重跑 stream + findSymbol 反射——这是 marshalReturn 的热路径。
        MemberLayout layout = layoutOf(runtimeView, typeId);
        Set<String> memberNames = layout.memberNames();
        Map<String, ApiSymbol> memberSymbols = layout.memberSymbols();

        ApiValueMarshaller marshaller = new ApiValueMarshaller(runtimeView, guestErrorFactory);

        return new ApiFacadeProxy(
                runtimeView, typeId, implementation, memberNames, memberSymbols, marshaller, nativeReturn,
                guestErrorFactory);
    }

    /**
     * runtimeView → typeId → (memberNames, memberSymbols) 的进程级缓存。memberSymbols 同时依赖
     * typeId 与该 runtimeView 的符号表——不同 registry 实例下同名 typeId 的成员布局可能不同，
     * 故外层按 runtimeView 分桶（不能只按 typeId 缓存）。外层用 WeakHashMap 使 registry 可被 GC
     * （reload / 测试结束即回收）；facade 注册在启动期完成、脚本重载不变，故每个 registry 的布局
     * 在其生命周期内稳定。
     */
    private static final Map<ApiRuntimeView, ConcurrentHashMap<ApiSymbolId, MemberLayout>> LAYOUT_BY_VIEW =
            Collections.synchronizedMap(new WeakHashMap<>());

    private record MemberLayout(Set<String> memberNames, Map<String, ApiSymbol> memberSymbols) {}

    private static MemberLayout layoutOf(ApiRuntimeView runtimeView, ApiSymbolId typeId) {
        // 外层 computeIfAbsent 由 synchronizedMap 包装保证原子；仅首次访问某 registry 时进入。
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

        return (ProxyExecutable) arguments -> {
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
        };
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

    @Override
    public boolean removeMember(String key) {
        return false;
    }
}
