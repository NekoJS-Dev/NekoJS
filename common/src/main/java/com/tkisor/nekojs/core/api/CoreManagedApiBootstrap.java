package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.ApiContractReader;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.data.TextValue;
import com.tkisor.nekojs.api.data.JsonValue;
import com.tkisor.nekojs.api.data.NbtValue;
import com.tkisor.nekojs.api.data.NbtEntry;
import com.tkisor.nekojs.api.facade.ModInfoValue;
import com.tkisor.nekojs.api.plugin.PluginIdentity;
import com.tkisor.nekojs.api.surface.ApiCallHandler;
import com.tkisor.nekojs.api.surface.ApiContribution;
import com.tkisor.nekojs.api.surface.ApiContributionRegistry;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTier;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.ScriptTypeId;
import com.tkisor.nekojs.core.api.facade.DefaultIdFacade;
import com.tkisor.nekojs.core.api.facade.DefaultPlatformFacade;
import com.tkisor.nekojs.core.api.facade.DefaultTextFacade;
import com.tkisor.nekojs.core.api.facade.DefaultJsonFacade;
import com.tkisor.nekojs.core.api.facade.DefaultNbtFacade;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.platform.IPlatform;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public final class CoreManagedApiBootstrap {
    public static final String RESOURCE = "/nekojs/api-contract/portable-core-0.6.0.json";
    public static final ApiSymbolId ID_GLOBAL = ApiSymbolId.parse("global:ID");
    public static final ApiSymbolId PLATFORM_GLOBAL = ApiSymbolId.parse("global:Platform");
    public static final ApiSymbolId TEXT_GLOBAL = ApiSymbolId.parse("global:Text");
    public static final ApiSymbolId JSON_IO_GLOBAL = ApiSymbolId.parse("global:JsonIO");
    public static final ApiSymbolId NBT_GLOBAL = ApiSymbolId.parse("global:NBT");

    private CoreManagedApiBootstrap() {
    }

    public static CoreManagedApi load(IPlatform platform, URI codeSource) {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(codeSource, "codeSource");

        VerifiedApiContract contract = readContract(codeSource);
        VerifiedContractSet contracts = VerifiedContractSet.of(contract);
        Map<ApiSymbolId, ApiSymbol> symbols = contract.contract().symbols().stream()
                .collect(Collectors.toMap(ApiSymbol::id, symbol -> symbol, (left, right) -> left, LinkedHashMap::new));
        ApiContributionRegistry contributions = ApiContributionRegistry.ownedBy(
                new PluginIdentity("nekojs-core", CoreManagedApiBootstrap.class.getName(), codeSource),
                contracts);

        DefaultIdFacade id = new DefaultIdFacade();
        DefaultPlatformFacade platformFacade = new DefaultPlatformFacade(platform);
        DefaultTextFacade text = new DefaultTextFacade();
        DefaultJsonFacade json = new DefaultJsonFacade(NekoJSPaths.fromGameDir(platform.getGameDir()).data());
        DefaultNbtFacade nbt = new DefaultNbtFacade(
                NekoJSPaths.fromGameDir(platform.getGameDir()).data(), platform.nbtBinaryCodec());

        register(contributions, symbols, "global:ID", (receiver, args) -> id);
        register(contributions, symbols, "global:Platform", (receiver, args) -> platformFacade);
        register(contributions, symbols, "member:ID.of", (receiver, args) ->
                args.size() == 1
                        ? id.of((String) args.get(0))
                        : id.of((String) args.get(0), (String) args.get(1)));
        register(contributions, symbols, "member:ID.namespace", (receiver, args) ->
                id.namespace((NekoId) args.get(0)));
        register(contributions, symbols, "member:ID.path", (receiver, args) ->
                id.path((NekoId) args.get(0)));
        register(contributions, symbols, "member:ID.asString", (receiver, args) ->
                id.asString((NekoId) args.get(0)));
        register(contributions, symbols, "member:NekoId.namespace", (receiver, args) ->
                ((NekoId) receiver).namespace());
        register(contributions, symbols, "member:NekoId.path", (receiver, args) ->
                ((NekoId) receiver).path());
        register(contributions, symbols, "member:NekoId.asString", (receiver, args) ->
                ((NekoId) receiver).asString());

        register(contributions, symbols, "member:Platform.isClient", (receiver, args) -> platformFacade.isClient());
        register(contributions, symbols, "member:Platform.isDevelopment", (receiver, args) -> platformFacade.isDevelopment());
        register(contributions, symbols, "member:Platform.getMcVersion", (receiver, args) -> platformFacade.getMcVersion());
        register(contributions, symbols, "member:Platform.getLoaderId", (receiver, args) -> platformFacade.getLoaderId());
        register(contributions, symbols, "member:Platform.getLoaderVersion", (receiver, args) -> platformFacade.getLoaderVersion());
        register(contributions, symbols, "member:Platform.isLoaded", (receiver, args) ->
                platformFacade.isLoaded((String) args.get(0)));
        register(contributions, symbols, "member:Platform.getInfo", (receiver, args) ->
                platformFacade.getInfo((String) args.get(0)));
        register(contributions, symbols, "member:Platform.getList", (receiver, args) -> platformFacade.getList());
        register(contributions, symbols, "member:Platform.capabilities", (receiver, args) -> platformFacade.capabilities());

        register(contributions, symbols, "member:ModInfo.id", (receiver, args) -> ((ModInfoValue) receiver).id());
        register(contributions, symbols, "member:ModInfo.name", (receiver, args) -> ((ModInfoValue) receiver).name());
        register(contributions, symbols, "member:ModInfo.version", (receiver, args) -> ((ModInfoValue) receiver).version());

        register(contributions, symbols, "global:Text", (receiver, args) -> text);
        register(contributions, symbols, "member:Text.of", (receiver, args) -> text.of((String) args.getFirst()));
        register(contributions, symbols, "member:Text.empty", (receiver, args) -> text.empty());
        register(contributions, symbols, "member:Text.translatable", (receiver, args) ->
                text.translatable((String) args.getFirst(), args.subList(1, args.size())));
        register(contributions, symbols, "member:Text.ofValues", (receiver, args) -> text.ofValues(args));
        register(contributions, symbols, "member:TextValue.append", (receiver, args) ->
                text.append((TextValue) receiver, args));
        register(contributions, symbols, "member:TextValue.isEmpty", (receiver, args) ->
                ((TextValue) receiver).isEmpty());

        register(contributions, symbols, "global:JsonIO", (receiver, args) -> json);
        register(contributions, symbols, "member:JsonIO.parse", (receiver, args) ->
                json.parse((String) args.getFirst()));
        register(contributions, symbols, "member:JsonIO.toString", (receiver, args) ->
                json.toString((JsonValue) args.getFirst()));
        register(contributions, symbols, "member:JsonIO.toPrettyString", (receiver, args) ->
                json.toPrettyString((JsonValue) args.getFirst()));
        register(contributions, symbols, "member:JsonIO.read", (receiver, args) ->
                json.read((String) args.getFirst()));
        register(contributions, symbols, "member:JsonIO.write", (receiver, args) -> {
            json.write((String) args.getFirst(), (JsonValue) args.get(1));
            return null;
        });
        register(contributions, symbols, "member:JsonValue.toString", (receiver, args) ->
                json.toString((JsonValue) receiver));
        register(contributions, symbols, "member:JsonValue.toPrettyString", (receiver, args) ->
                json.toPrettyString((JsonValue) receiver));

        register(contributions, symbols, "global:NBT", (receiver, args) -> nbt);
        register(contributions, symbols, "member:NBT.of", (receiver, args) -> nbt.of((NbtValue) args.getFirst()));
        register(contributions, symbols, "member:NBT.byte", (receiver, args) -> nbt.byteValue((Number) args.getFirst()));
        register(contributions, symbols, "member:NBT.short", (receiver, args) -> nbt.shortValue((Number) args.getFirst()));
        register(contributions, symbols, "member:NBT.int", (receiver, args) -> nbt.intValue((Number) args.getFirst()));
        register(contributions, symbols, "member:NBT.long", (receiver, args) -> nbt.longValue((String) args.getFirst()));
        register(contributions, symbols, "member:NBT.float", (receiver, args) -> nbt.floatValue((Number) args.getFirst()));
        register(contributions, symbols, "member:NBT.double", (receiver, args) -> nbt.doubleValue((Number) args.getFirst()));
        register(contributions, symbols, "member:NBT.byteArray", (receiver, args) -> nbt.byteArray(numberList(args.getFirst())));
        register(contributions, symbols, "member:NBT.intArray", (receiver, args) -> nbt.intArray(numberList(args.getFirst())));
        register(contributions, symbols, "member:NBT.toSnbt", (receiver, args) -> nbt.toSnbt((NbtValue) args.getFirst()));
        register(contributions, symbols, "member:NBT.read", (receiver, args) -> nbt.read((String) args.getFirst()));
        register(contributions, symbols, "member:NBT.write", (receiver, args) -> {
            nbt.write((String) args.getFirst(), (NbtValue) args.get(1));
            return null;
        });
        register(contributions, symbols, "member:NbtValue.kind", (receiver, args) -> nbt.kind((NbtValue) receiver));
        register(contributions, symbols, "member:NbtValue.scalar", (receiver, args) -> nbt.scalar((NbtValue) receiver));
        register(contributions, symbols, "member:NbtValue.values", (receiver, args) -> nbt.values((NbtValue) receiver));
        register(contributions, symbols, "member:NbtValue.entries", (receiver, args) -> nbt.entries((NbtValue) receiver));
        register(contributions, symbols, "member:NbtValue.toSnbt", (receiver, args) -> nbt.toSnbt((NbtValue) receiver));
        register(contributions, symbols, "member:NbtEntry.key", (receiver, args) -> ((NbtEntry) receiver).key());
        register(contributions, symbols, "member:NbtEntry.value", (receiver, args) -> ((NbtEntry) receiver).value());

        return new CoreManagedApi(
                contracts,
                contributions,
                Map.of(
                        ID_GLOBAL, id,
                        PLATFORM_GLOBAL, platformFacade,
                        TEXT_GLOBAL, text,
                        JSON_IO_GLOBAL, json,
                        NBT_GLOBAL, nbt));
    }

    private static VerifiedApiContract readContract(URI codeSource) {
        var stream = CoreManagedApiBootstrap.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Core managed API contract not found: " + RESOURCE);
        }
        ApiContractIdentity identity = new ApiContractIdentity(
                "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.6.0"));
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return ApiContractReader.readVerified(reader, codeSource, RESOURCE, identity, null);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close core managed API contract", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Number> numberList(Object value) {
        return (List<Number>) value;
    }

    private static void register(
            ApiContributionRegistry registry,
            Map<ApiSymbolId, ApiSymbol> symbols,
            String rawId,
            BiFunction<Object, List<Object>, Object> implementation) {
        ApiSymbolId symbolId = ApiSymbolId.parse(rawId);
        ApiSymbol symbol = Objects.requireNonNull(symbols.get(symbolId), "Missing contract symbol " + rawId);
        Set<ScriptTypeId> scriptTypes = Set.of(ScriptTypeId.values());
        for (ApiSignature signature : symbol.signatures()) {
            ApiCallHandler handler = (context, receiver, arguments) -> implementation.apply(receiver, arguments);
            registry.registerSymbol(ApiContribution.symbol(
                    symbolId,
                    ApiTier.GLOBAL,
                    symbolId.qualifiedName(),
                    scriptTypes,
                    List.of(signature),
                    handler));
        }
    }

    public record CoreManagedApi(
            VerifiedContractSet contracts,
            ApiContributionRegistry contributions,
            Map<ApiSymbolId, Object> globalImplementations) {
        public CoreManagedApi {
            Objects.requireNonNull(contracts, "contracts");
            Objects.requireNonNull(contributions, "contributions");
            globalImplementations = Map.copyOf(globalImplementations);
        }
    }
}
