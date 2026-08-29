package com.tkisor.nekojs.core.api;

import com.tkisor.nekojs.api.capability.ActiveCapability;
import com.tkisor.nekojs.api.capability.CapabilityDefinition;
import com.tkisor.nekojs.api.capability.CapabilityImplementationMode;
import com.tkisor.nekojs.api.capability.CapabilityProviderContribution;
import com.tkisor.nekojs.api.capability.CapabilityResolution;
import com.tkisor.nekojs.api.capability.CapabilityResolver;
import com.tkisor.nekojs.api.contract.ApiContractIdentity;
import com.tkisor.nekojs.api.contract.ApiContractKind;
import com.tkisor.nekojs.api.contract.NormativeApiContract;
import com.tkisor.nekojs.api.contract.VerifiedApiContract;
import com.tkisor.nekojs.api.contract.VerifiedContractSet;
import com.tkisor.nekojs.api.module.ActiveModule;
import com.tkisor.nekojs.api.module.ApiModuleDescriptor;
import com.tkisor.nekojs.api.module.ApiModuleResolver;
import com.tkisor.nekojs.api.module.ModuleResolution;
import com.tkisor.nekojs.api.surface.ApiContribution;
import com.tkisor.nekojs.api.surface.ApiContributionRegistry;
import com.tkisor.nekojs.api.surface.ApiResolutionException;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTier;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiVersion;
import com.tkisor.nekojs.api.surface.EnvironmentKey;
import com.tkisor.nekojs.api.surface.LegacyGlobalReservation;
import com.tkisor.nekojs.api.surface.ScriptTypeId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JsApiSurfaceResolver {

    private JsApiSurfaceResolver() {
    }

    public static FrozenApiRegistry resolve(
            EnvironmentKey environmentKey,
            VerifiedContractSet contracts,
            List<ApiContributionRegistry> contributionRegistries,
            List<LegacyGlobalReservation> legacyReservations) {

        Objects.requireNonNull(environmentKey, "environmentKey");
        Objects.requireNonNull(contracts, "contracts");
        Objects.requireNonNull(contributionRegistries, "contributionRegistries");
        Objects.requireNonNull(legacyReservations, "legacyReservations");

        validateContractTypeClosure(contracts);
        validateLegacyReservations(contributionRegistries, legacyReservations);

        List<ApiContribution> allContributions = new ArrayList<>();
        List<CapabilityProviderContribution> allProviders = new ArrayList<>();
        for (ApiContributionRegistry registry : contributionRegistries) {
            allContributions.addAll(registry.symbolContributions());
            allProviders.addAll(registry.capabilityProviders());
        }

        List<CapabilityDefinition> definitions = collectCapabilityDefinitions(contracts);
        CapabilityResolution capabilityResolution = CapabilityResolver.resolve(
                environmentKey, definitions, allProviders);

        List<ActiveCapability> activeCapabilities = capabilityResolution.active();

        List<ApiModuleDescriptor> descriptors = collectModuleDescriptors(contracts);
        ApiVersion portableApiVersion = getPortableApiVersion(contracts);
        ModuleResolution moduleResolution = ApiModuleResolver.resolve(
                environmentKey, portableApiVersion, descriptors, activeCapabilities);

        Map<ApiSymbolId, List<ApiContribution>> contributionsBySymbol = new LinkedHashMap<>();
        for (ApiContribution contrib : allContributions) {
            contributionsBySymbol
                    .computeIfAbsent(contrib.symbolId(), k -> new ArrayList<>())
                    .add(contrib);
        }

        Map<ApiSymbolId, List<ApiSignature>> contractSignaturesBySymbol = collectContractSignatures(contracts);

        validateContributionsAgainstContracts(contributionsBySymbol, contractSignaturesBySymbol, environmentKey);

        Map<ApiSymbolId, ApiSymbol> mergedSymbols = mergeOverloads(contributionsBySymbol, contractSignaturesBySymbol);

        validateRawTypeLeaks(mergedSymbols);

        Map<String, ApiSymbol> globals = new LinkedHashMap<>();
        Map<String, Map<String, ApiSymbol>> moduleExports = new LinkedHashMap<>();
        Map<ApiSymbolId, Map<String, ApiInvoker>> invokerIndex = new LinkedHashMap<>();

        for (Map.Entry<ApiSymbolId, ApiSymbol> entry : mergedSymbols.entrySet()) {
            ApiSymbolId symbolId = entry.getKey();
            ApiSymbol symbol = entry.getValue();

            if (symbolId.kind().equals("global")) {
                String jsName = symbolId.qualifiedName();
                globals.put(jsName, symbol);
            }

            List<ApiContribution> contribs = contributionsBySymbol.getOrDefault(symbolId, List.of());
            for (ApiContribution contrib : contribs) {
                ApiInvoker invoker = createInvoker(contrib, environmentKey);
                invokerIndex.computeIfAbsent(symbolId, k -> new LinkedHashMap<>())
                        .put(contrib.signatures().getFirst().callKey(), invoker);
            }
        }

        List<ApiSymbol> allSymbols = List.copyOf(mergedSymbols.values());
        Set<String> activeCapNames = new HashSet<>();
        for (ActiveCapability cap : activeCapabilities) {
            activeCapNames.add(cap.name());
        }

        String portableContractHash = findPortableContractHash(contracts);
        Map<String, String> moduleContractHashes = new HashMap<>();
        for (VerifiedApiContract contract : contracts.all()) {
            if (contract.identity().kind() == ApiContractKind.FEATURE
                    || contract.identity().kind() == ApiContractKind.ADDON) {
                moduleContractHashes.put(contract.identity().contractId(), contract.compatibilitySha256());
            }
        }

        com.tkisor.nekojs.api.surface.ApiContractHashes contractHashes =
                new com.tkisor.nekojs.api.surface.ApiContractHashes(
                        portableApiVersion.toString(),
                        portableContractHash,
                        moduleContractHashes);

        com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot surfaceSnapshot =
                new com.tkisor.nekojs.api.surface.ApiSurfaceSnapshot(
                        allSymbols,
                        activeCapNames,
                        moduleResolution.active(),
                        moduleResolution.inactive(),
                        environmentKey);

        com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot snapshot =
                new com.tkisor.nekojs.api.surface.ApiEnvironmentSnapshot(
                        environmentKey,
                        surfaceSnapshot,
                        contractHashes);

        return new FrozenApiRegistry(
                environmentKey,
                snapshot,
                mergedSymbols,
                globals,
                moduleExports,
                invokerIndex);
    }

    private static void validateLegacyReservations(
            List<ApiContributionRegistry> registries,
            List<LegacyGlobalReservation> reservations) {
        if (reservations.isEmpty()) return;

        Set<String> reservedNames = new HashSet<>();
        for (LegacyGlobalReservation reservation : reservations) {
            reservedNames.add(reservation.globalName());
        }

        for (ApiContributionRegistry registry : registries) {
            for (ApiContribution contrib : registry.symbolContributions()) {
                if (contrib.tier() == ApiTier.GLOBAL && reservedNames.contains(contrib.jsName())) {
                    throw new ApiResolutionException("LEGACY_NAME_COLLISION",
                            "Managed global '" + contrib.jsName()
                                    + "' collides with legacy reservation",
                            Map.of("name", contrib.jsName()));
                }
            }
        }
    }

    private static void validateContractTypeClosure(VerifiedContractSet contracts) {
        List<ApiSymbol> symbols = new ArrayList<>();
        for (VerifiedApiContract contract : contracts.all()) {
            symbols.addAll(contract.contract().symbols());
            contract.contract().modules().forEach(module -> symbols.addAll(module.symbols()));
        }
        Set<String> declaredTypes = new HashSet<>();
        for (ApiSymbol symbol : symbols) {
            if ("member".equals(symbol.id().kind())) {
                String name = symbol.id().qualifiedName();
                int separator = name.lastIndexOf('.');
                if (separator > 0) declaredTypes.add(name.substring(0, separator));
            }
        }
        for (ApiSymbol symbol : symbols) {
            for (ApiSignature signature : symbol.signatures()) {
                signature.parameters().forEach(parameter ->
                        validateResolvedType(parameter.type(), symbol.id(), declaredTypes));
                validateResolvedType(signature.returnType(), symbol.id(), declaredTypes);
            }
        }
    }

    private static void validateResolvedType(
            ApiTypeRef type,
            ApiSymbolId symbolId,
            Set<String> declaredTypes) {
        if (type.kind() == ApiTypeRef.Kind.SYMBOL) {
            ApiSymbolId reference = ApiSymbolId.parse(type.name());
            if (!"type".equals(reference.kind()) || !declaredTypes.contains(reference.qualifiedName())) {
                throw new ApiResolutionException(
                        "UNRESOLVED_TYPE_REFERENCE",
                        "Unresolved type reference '" + type.name() + "' in " + symbolId,
                        Map.of("symbolId", symbolId.value(), "type", type.name()));
            }
        }
        type.arguments().forEach(argument -> validateResolvedType(argument, symbolId, declaredTypes));
        if (type.callbackSignature() != null) {
            type.callbackSignature().parameters().forEach(parameter ->
                    validateResolvedType(parameter.type(), symbolId, declaredTypes));
            validateResolvedType(type.callbackSignature().returnType(), symbolId, declaredTypes);
        }
    }

    private static List<CapabilityDefinition> collectCapabilityDefinitions(VerifiedContractSet contracts) {
        List<CapabilityDefinition> definitions = new ArrayList<>();
        for (VerifiedApiContract contract : contracts.all()) {
            NormativeApiContract normative = contract.contract();
            for (NormativeApiContract.ContractCapability cap : normative.capabilities()) {
                definitions.add(new CapabilityDefinition(
                        cap.id(),
                        contract.identity().version(),
                        CapabilityImplementationMode.SINGLE,
                        com.tkisor.nekojs.api.capability.ProviderPolicy.CORE_ONLY,
                        Set.of(),
                        null,
                        Set.of(),
                        Set.of()));
            }
        }
        return definitions;
    }

    private static List<ApiModuleDescriptor> collectModuleDescriptors(VerifiedContractSet contracts) {
        List<ApiModuleDescriptor> descriptors = new ArrayList<>();
        for (VerifiedApiContract contract : contracts.all()) {
            NormativeApiContract normative = contract.contract();
            for (NormativeApiContract.ContractModule module : normative.modules()) {
                List<com.tkisor.nekojs.api.module.ApiModuleDependency> deps = new ArrayList<>();
                for (NormativeApiContract.ContractModuleDependency dep : module.dependencies()) {
                    deps.add(new com.tkisor.nekojs.api.module.ApiModuleDependency(
                            dep.moduleId(),
                            null,
                            com.tkisor.nekojs.api.module.ApiModuleDependency.DependencyType.MODULE));
                }
                descriptors.add(new ApiModuleDescriptor(
                        module.id(),
                        module.tier(),
                        module.contractVersion(),
                        module.moduleRevision(),
                        contract.identity(),
                        deps));
            }
        }
        return descriptors;
    }

    private static ApiVersion getPortableApiVersion(VerifiedContractSet contracts) {
        VerifiedApiContract portable = contracts.requirePortable("nekojs-core");
        return portable.identity().version();
    }

    private static Map<ApiSymbolId, List<ApiSignature>> collectContractSignatures(
            VerifiedContractSet contracts) {
        Map<ApiSymbolId, List<ApiSignature>> result = new LinkedHashMap<>();
        for (VerifiedApiContract contract : contracts.all()) {
            NormativeApiContract normative = contract.contract();
            for (ApiSymbol symbol : normative.symbols()) {
                result.computeIfAbsent(symbol.id(), k -> new ArrayList<>())
                        .addAll(symbol.signatures());
            }
            for (NormativeApiContract.ContractModule module : normative.modules()) {
                for (ApiSymbol symbol : module.symbols()) {
                    result.computeIfAbsent(symbol.id(), k -> new ArrayList<>())
                            .addAll(symbol.signatures());
                }
            }
        }
        return result;
    }

    private static void validateContributionsAgainstContracts(
            Map<ApiSymbolId, List<ApiContribution>> contributions,
            Map<ApiSymbolId, List<ApiSignature>> contractSignatures,
            EnvironmentKey environmentKey) {

        for (Map.Entry<ApiSymbolId, List<ApiContribution>> entry : contributions.entrySet()) {
            ApiSymbolId symbolId = entry.getKey();
            List<ApiContribution> contribs = entry.getValue();

            if (!contractSignatures.containsKey(symbolId)) {
                throw new ApiResolutionException("CONTRIBUTION_NO_CONTRACT",
                        "No contract found for symbol " + symbolId,
                        Map.of("symbolId", symbolId.value()));
            }

            Set<String> seenCallKeys = new HashSet<>();
            for (ApiContribution contrib : contribs) {
                for (ApiSignature sig : contrib.signatures()) {
                    if (!seenCallKeys.add(sig.callKey())) {
                        throw new ApiResolutionException("DUPLICATE_CALL_KEY",
                                "Duplicate callKey " + sig.callKey() + " in symbol " + symbolId,
                                Map.of("symbolId", symbolId.value(), "callKey", sig.callKey()));
                    }
                }

                if (contrib.nativeReturn() && contrib.tier() != ApiTier.VERSION) {
                    throw new ApiResolutionException("NATIVE_TYPE_LEAK",
                            "nativeReturn is only valid for VERSION tier, got " + contrib.tier(),
                            Map.of("symbolId", symbolId.value(), "tier", contrib.tier().name()));
                }
            }
        }
    }

    private static Map<ApiSymbolId, ApiSymbol> mergeOverloads(
            Map<ApiSymbolId, List<ApiContribution>> contributions,
            Map<ApiSymbolId, List<ApiSignature>> contractSignatures) {

        Map<ApiSymbolId, ApiSymbol> result = new LinkedHashMap<>();

        for (Map.Entry<ApiSymbolId, List<ApiSignature>> entry : contractSignatures.entrySet()) {
            ApiSymbolId symbolId = entry.getKey();
            List<ApiSignature> contractSigs = entry.getValue();

            List<ApiContribution> contribs = contributions.getOrDefault(symbolId, List.of());
            List<ApiSignature> allSignatures = new ArrayList<>(contractSigs);

            for (ApiContribution contrib : contribs) {
                for (ApiSignature sig : contrib.signatures()) {
                    boolean found = false;
                    for (ApiSignature existing : allSignatures) {
                        if (existing.callKey().equals(sig.callKey())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        allSignatures.add(sig);
                    }
                }
            }

            result.put(symbolId, new ApiSymbol(symbolId, allSignatures));
        }

        return result;
    }

    private static void validateRawTypeLeaks(Map<ApiSymbolId, ApiSymbol> symbols) {
        for (Map.Entry<ApiSymbolId, ApiSymbol> entry : symbols.entrySet()) {
            ApiSymbolId symbolId = entry.getKey();
            ApiSymbol symbol = entry.getValue();
            for (ApiSignature sig : symbol.signatures()) {
                if (isRawNativeType(sig.returnType())) {
                    throw new ApiResolutionException("RAW_TYPE_LEAK",
                            "Raw native type leak detected in symbol " + symbolId,
                            Map.of("symbolId", symbolId.value()));
                }
            }
        }
    }

    private static boolean isRawNativeType(ApiTypeRef type) {
        if (type.kind() != ApiTypeRef.Kind.SYMBOL || type.name() == null) {
            return false;
        }
        String name = type.name();
        return name.startsWith("net.minecraft.")
                || name.startsWith("net.neoforged.")
                || name.startsWith("net.minecraftforge.")
                || name.startsWith("graal.graalvm.");
    }

    private static String findPortableContractHash(VerifiedContractSet contracts) {
        return contracts.requirePortable("nekojs-core").compatibilitySha256();
    }

    private static ApiInvoker createInvoker(ApiContribution contribution, EnvironmentKey environmentKey) {
        return (receiver, args) -> {
            com.tkisor.nekojs.api.surface.ApiCallContext context =
                    new com.tkisor.nekojs.api.surface.ApiCallContext(
                            environmentKey,
                            contribution.symbolId(),
                            contribution.signatures().getFirst());
            return contribution.handler().invoke(context, receiver, args);
        };
    }
}
