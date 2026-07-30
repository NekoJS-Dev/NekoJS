package com.tkisor.nekojs.api.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTier;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiVersion;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class ApiContractReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> PORTABLE_PRIMITIVES = Set.of("string", "number", "boolean", "null", "object", "json", "nbt");
    private static final JsonSchema SCHEMA;

    static {
        try (var is = ApiContractReader.class.getResourceAsStream(
                "/nekojs/api-contract/api-contract.schema.json")) {
            if (is == null) {
                throw new ExceptionInInitializerError("Schema resource not found");
            }
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            SCHEMA = factory.getSchema(MAPPER.readTree(is));
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ApiContractReader() {
    }

    public static VerifiedApiContract readVerified(
            Reader reader, URI codeSource, String resourceName,
            ApiContractIdentity expectedIdentity, String expectedIntegritySha256) {

        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(codeSource, "codeSource");
        Objects.requireNonNull(resourceName, "resourceName");
        Objects.requireNonNull(expectedIdentity, "expectedIdentity");

        JsonNode root;
        try {
            root = MAPPER.readTree(reader);
        } catch (IOException e) {
            throw new ApiContractException(
                    new ApiContractViolation("INVALID_JSON", "/", "Failed to parse JSON: " + e.getMessage()), e);
        }

        Set<ValidationMessage> errors = SCHEMA.validate(root);
        if (!errors.isEmpty()) {
            String details = errors.stream()
                    .map(ValidationMessage::toString)
                    .collect(Collectors.joining("; "));
            throw new ApiContractException(
                    new ApiContractViolation("SCHEMA_VALIDATION_FAILED", "/", details));
        }

        NormativeApiContract contract;
        try {
            contract = convertToDto(root);
        } catch (IllegalArgumentException e) {
            throw new ApiContractException(new ApiContractViolation(
                    "INVALID_CONTRACT_MODEL", "/", e.getMessage()), e);
        }

        validateSemantics(contract, expectedIdentity);

        String rawJson = canonicalJson(root);
        String integritySha256 = sha256Hex(rawJson.getBytes(StandardCharsets.UTF_8));

        if (expectedIntegritySha256 != null && !expectedIntegritySha256.equals(integritySha256)) {
            throw new ApiContractException(new ApiContractViolation(
                    "INTEGRITY_MISMATCH", "/",
                    "Integrity hash mismatch: expected " + expectedIntegritySha256 + " but got " + integritySha256));
        }

        String compatJson = canonicalJson(stripDocs(root));
        String compatibilitySha256 = sha256Hex(compatJson.getBytes(StandardCharsets.UTF_8));

        return new VerifiedApiContract(expectedIdentity, contract, codeSource, resourceName,
                integritySha256, compatibilitySha256);
    }

    public static VerifiedApiContract emptyVerifiedCorePreview(URI nekojsCodeSource) {
        Objects.requireNonNull(nekojsCodeSource, "nekojsCodeSource");

        ApiContractIdentity identity = new ApiContractIdentity(
                "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.0.0"));

        NormativeApiContract contract = new NormativeApiContract(
                1,
                new NormativeApiContract.ContractIdentity(
                        "nekojs-core", ApiContractKind.PORTABLE, "portable-core", ApiVersion.parse("0.0.0")),
                null,
                List.of(),
                List.of(),
                List.of());

        String hash = sha256Hex(contract.toString().getBytes(StandardCharsets.UTF_8));
        return new VerifiedApiContract(identity, contract, nekojsCodeSource,
                "nekojs/api-contract/preview", hash, hash);
    }

    private static NormativeApiContract convertToDto(JsonNode root) {
        int schemaVersion = root.get("schemaVersion").asInt();
        NormativeApiContract.ContractIdentity identity = convertIdentity(root.get("identity"));
        String docs = root.has("docs") ? root.get("docs").asText() : null;
        List<ApiSymbol> symbols = convertSymbols(root.get("symbols"));
        List<NormativeApiContract.ContractCapability> capabilities = root.has("capabilities")
                ? convertCapabilities(root.get("capabilities"))
                : List.of();
        List<NormativeApiContract.ContractModule> modules = root.has("modules")
                ? convertModules(root.get("modules"))
                : List.of();
        List<NormativeApiContract.ContractError> errors = root.has("errors")
                ? convertErrors(root.get("errors"))
                : List.of();

        return new NormativeApiContract(schemaVersion, identity, docs, symbols, capabilities, modules, errors);
    }

    private static NormativeApiContract.ContractIdentity convertIdentity(JsonNode node) {
        String owner = node.get("owner").asText();
        ApiContractKind kind = ApiContractKind.valueOf(node.get("kind").asText());
        String contractId = node.get("contractId").asText();
        ApiVersion version = ApiVersion.parse(node.get("version").asText());
        return new NormativeApiContract.ContractIdentity(owner, kind, contractId, version);
    }

    private static List<ApiSymbol> convertSymbols(JsonNode node) {
        List<ApiSymbol> result = new ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (JsonNode symNode : node) {
            result.add(convertSymbol(symNode));
        }
        return result;
    }

    private static ApiSymbol convertSymbol(JsonNode node) {
        ApiSymbolId id = ApiSymbolId.parse(node.get("id").asText());
        List<ApiSignature> signatures = new ArrayList<>();
        for (JsonNode sigNode : node.get("signatures")) {
            signatures.add(convertSignature(sigNode));
        }
        return new ApiSymbol(id, signatures);
    }

    private static ApiSignature convertSignature(JsonNode node) {
        List<ApiParameter> parameters = new ArrayList<>();
        for (JsonNode paramNode : node.get("parameters")) {
            parameters.add(convertParameter(paramNode));
        }
        ApiTypeRef returnType = convertTypeRef(node.get("returnType"));
        boolean isConstructor = node.has("isConstructor") && node.get("isConstructor").asBoolean();
        List<String> errorCodes = new ArrayList<>();
        if (node.has("errorCodes")) {
            node.get("errorCodes").forEach(code -> errorCodes.add(code.asText()));
        }
        return new ApiSignature(parameters, returnType, isConstructor, errorCodes);
    }

    private static ApiParameter convertParameter(JsonNode node) {
        String name = node.get("name").asText();
        ApiTypeRef type = convertTypeRef(node.get("type"));
        boolean optional = node.has("optional") && node.get("optional").asBoolean();
        boolean varargs = node.has("varargs") && node.get("varargs").asBoolean();
        return new ApiParameter(name, type, optional, varargs);
    }

    private static ApiTypeRef convertTypeRef(JsonNode node) {
        ApiTypeRef.Kind kind = ApiTypeRef.Kind.valueOf(node.get("kind").asText());
        String name = node.has("name") ? node.get("name").asText() : null;
        List<ApiTypeRef> arguments = new ArrayList<>();
        if (node.has("arguments")) {
            for (JsonNode argNode : node.get("arguments")) {
                arguments.add(convertTypeRef(argNode));
            }
        }
        ApiSignature callbackSignature = null;
        if (node.has("callbackSignature")) {
            callbackSignature = convertSignature(node.get("callbackSignature"));
        }
        return new ApiTypeRef(kind, name, arguments, callbackSignature);
    }

    private static List<NormativeApiContract.ContractCapability> convertCapabilities(JsonNode node) {
        List<NormativeApiContract.ContractCapability> result = new ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (JsonNode capNode : node) {
            String id = capNode.get("id").asText();
            String range = capNode.get("contractVersionRange").asText();
            String docs = capNode.has("docs") ? capNode.get("docs").asText() : null;
            result.add(new NormativeApiContract.ContractCapability(id, range, docs));
        }
        return result;
    }

    private static List<NormativeApiContract.ContractError> convertErrors(JsonNode node) {
        List<NormativeApiContract.ContractError> result = new ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (JsonNode errorNode : node) {
            List<String> fields = new ArrayList<>();
            if (errorNode.has("fields")) {
                errorNode.get("fields").forEach(field -> fields.add(field.asText()));
            }
            result.add(new NormativeApiContract.ContractError(
                    errorNode.get("code").asText(),
                    fields,
                    errorNode.has("docs") ? errorNode.get("docs").asText() : null));
        }
        return result;
    }

    private static List<NormativeApiContract.ContractModule> convertModules(JsonNode node) {
        List<NormativeApiContract.ContractModule> result = new ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (JsonNode modNode : node) {
            result.add(convertModule(modNode));
        }
        return result;
    }

    private static NormativeApiContract.ContractModule convertModule(JsonNode node) {
        String id = node.get("id").asText();
        ApiTier tier = ApiTier.valueOf(node.get("tier").asText());
        ApiVersion contractVersion = node.has("contractVersion")
                ? ApiVersion.parse(node.get("contractVersion").asText())
                : null;
        int moduleRevision = node.has("moduleRevision") ? node.get("moduleRevision").asInt() : 0;
        String docs = node.has("docs") ? node.get("docs").asText() : null;
        List<ApiSymbol> symbols = node.has("symbols") ? convertSymbols(node.get("symbols")) : List.of();
        List<NormativeApiContract.ContractModuleDependency> deps = node.has("dependencies")
                ? convertDependencies(node.get("dependencies"))
                : List.of();
        return new NormativeApiContract.ContractModule(id, tier, contractVersion, moduleRevision, docs, symbols, deps);
    }

    private static List<NormativeApiContract.ContractModuleDependency> convertDependencies(JsonNode node) {
        List<NormativeApiContract.ContractModuleDependency> result = new ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (JsonNode depNode : node) {
            String moduleId = depNode.get("moduleId").asText();
            String versionRange = depNode.has("versionRange") ? depNode.get("versionRange").asText() : null;
            ApiTier targetTier = depNode.has("targetTier")
                    ? ApiTier.valueOf(depNode.get("targetTier").asText())
                    : null;
            result.add(new NormativeApiContract.ContractModuleDependency(moduleId, versionRange, targetTier));
        }
        return result;
    }

    private static void validateSemantics(NormativeApiContract contract, ApiContractIdentity expected) {
        NormativeApiContract.ContractIdentity actual = contract.identity();

        if (!actual.owner().equals(expected.owner())
                || actual.kind() != expected.kind()
                || !actual.contractId().equals(expected.contractId())
                || !actual.version().equals(expected.version())) {
            throw new ApiContractException(new ApiContractViolation(
                    "IDENTITY_MISMATCH", "/identity",
                    "Contract identity does not match expected: expected " + expected + " but got "
                            + new ApiContractIdentity(actual.owner(), actual.kind(), actual.contractId(), actual.version())));
        }

        if (actual.version().prerelease() != null) {
            throw new ApiContractException(new ApiContractViolation(
                    "CONTRACT_VERSION_PRERELEASE", "/identity/version",
                    "Contract version must not have prerelease: " + actual.version()));
        }

        if (actual.kind() == ApiContractKind.PORTABLE) {
            if (!contract.modules().isEmpty()) {
                throw new ApiContractException(new ApiContractViolation(
                        "PORTABLE_WITH_MODULES", "/modules",
                        "PORTABLE contract must not have modules"));
            }
            if (!"portable-core".equals(actual.contractId())) {
                throw new ApiContractException(new ApiContractViolation(
                        "PORTABLE_INVALID_CONTRACT_ID", "/identity/contractId",
                        "PORTABLE contract must have contractId 'portable-core'"));
            }
        }

        if (actual.kind() == ApiContractKind.FEATURE
                || actual.kind() == ApiContractKind.PLATFORM
                || actual.kind() == ApiContractKind.ADDON) {
            if (contract.modules().size() != 1) {
                throw new ApiContractException(new ApiContractViolation(
                        "MODULE_COUNT_MISMATCH", "/modules",
                        actual.kind() + " contract must have exactly 1 module, found " + contract.modules().size()));
            }
            NormativeApiContract.ContractModule module = contract.modules().getFirst();
            if (!module.id().equals(actual.contractId())) {
                throw new ApiContractException(new ApiContractViolation(
                        "MODULE_ID_MISMATCH", "/modules/0/id",
                        "Module id '" + module.id() + "' does not match contractId '" + actual.contractId() + "'"));
            }
            if (module.contractVersion() != null && !module.contractVersion().equals(actual.version())) {
                throw new ApiContractException(new ApiContractViolation(
                        "MODULE_VERSION_MISMATCH", "/modules/0/contractVersion",
                        "Module contractVersion '" + module.contractVersion()
                                + "' does not match identity version '" + actual.version() + "'"));
            }
        }

        Set<String> errorCodes = new HashSet<>();
        for (int i = 0; i < contract.errors().size(); i++) {
            NormativeApiContract.ContractError error = contract.errors().get(i);
            if (!errorCodes.add(error.code())) {
                throw new ApiContractException(new ApiContractViolation(
                        "DUPLICATE_ERROR_CODE", "/errors/" + i + "/code",
                        "Duplicate error code '" + error.code() + "'"));
            }
        }

        List<ApiSymbol> allSymbols = new ArrayList<>(contract.symbols());
        contract.modules().forEach(module -> allSymbols.addAll(module.symbols()));
        validateSignatureErrors(contract.symbols(), errorCodes);
        validateSymbolTypes(allSymbols, actual.kind() == ApiContractKind.PORTABLE);

        for (NormativeApiContract.ContractModule module : contract.modules()) {
            validateSignatureErrors(module.symbols(), errorCodes);
            validateModuleSemantics(module, actual.owner(), actual.kind());
        }
    }

    private static void validateSignatureErrors(List<ApiSymbol> symbols, Set<String> declaredErrors) {
        for (ApiSymbol symbol : symbols) {
            for (ApiSignature signature : symbol.signatures()) {
                validateSignatureErrors(signature, symbol.id(), declaredErrors);
            }
        }
    }

    private static void validateSignatureErrors(
            ApiSignature signature,
            ApiSymbolId symbolId,
            Set<String> declaredErrors) {
        for (String code : signature.errorCodes()) {
            if (!declaredErrors.contains(code)) {
                throw new ApiContractException(new ApiContractViolation(
                        "UNKNOWN_SIGNATURE_ERROR", "/symbols",
                        "Signature " + symbolId + " references undeclared error '" + code + "'"));
            }
        }
        signature.parameters().forEach(parameter ->
                validateCallbackErrors(parameter.type(), symbolId, declaredErrors));
        validateCallbackErrors(signature.returnType(), symbolId, declaredErrors);
    }

    private static void validateCallbackErrors(
            ApiTypeRef type,
            ApiSymbolId symbolId,
            Set<String> declaredErrors) {
        type.arguments().forEach(argument -> validateCallbackErrors(argument, symbolId, declaredErrors));
        if (type.callbackSignature() != null) {
            validateSignatureErrors(type.callbackSignature(), symbolId, declaredErrors);
        }
    }

    private static void validateSymbolTypes(List<ApiSymbol> symbols, boolean requireLocalTypeReferences) {
        for (ApiSymbol symbol : symbols) {
            if ("member".equals(symbol.id().kind()) && !symbol.id().qualifiedName().contains(".")) {
                throw new ApiContractException(new ApiContractViolation(
                        "INVALID_MEMBER_SYMBOL_ID", "/symbols",
                        "Member symbol must contain an owner and member name: " + symbol.id()));
            }
        }
        Set<String> declaredTypes = symbols.stream()
                .filter(symbol -> "member".equals(symbol.id().kind()))
                .map(symbol -> symbol.id().qualifiedName())
                .map(name -> name.substring(0, name.lastIndexOf('.')))
                .collect(Collectors.toSet());
        for (ApiSymbol symbol : symbols) {
            for (ApiSignature signature : symbol.signatures()) {
                signature.parameters().forEach(parameter ->
                        validateType(parameter.type(), symbol.id(), declaredTypes, requireLocalTypeReferences));
                validateType(signature.returnType(), symbol.id(), declaredTypes, requireLocalTypeReferences);
            }
        }
    }

    private static void validateType(
            ApiTypeRef type,
            ApiSymbolId symbolId,
            Set<String> declaredTypes,
            boolean requireLocalTypeReferences) {
        if (type.kind() == ApiTypeRef.Kind.PRIMITIVE && !PORTABLE_PRIMITIVES.contains(type.name())) {
            throw new ApiContractException(new ApiContractViolation(
                    "INVALID_PRIMITIVE_TYPE", "/symbols",
                    "Unsupported primitive '" + type.name() + "' in " + symbolId));
        }
        if (type.kind() == ApiTypeRef.Kind.SYMBOL) {
            ApiSymbolId reference = ApiSymbolId.parse(type.name());
            if (!"type".equals(reference.kind())
                    || requireLocalTypeReferences && !declaredTypes.contains(reference.qualifiedName())) {
                throw new ApiContractException(new ApiContractViolation(
                        "UNRESOLVED_TYPE_REFERENCE", "/symbols",
                        "Unresolved type reference '" + type.name() + "' in " + symbolId));
            }
        }
        type.arguments().forEach(argument ->
                validateType(argument, symbolId, declaredTypes, requireLocalTypeReferences));
        if (type.callbackSignature() != null) {
            type.callbackSignature().parameters().forEach(parameter ->
                    validateType(parameter.type(), symbolId, declaredTypes, requireLocalTypeReferences));
            validateType(type.callbackSignature().returnType(), symbolId, declaredTypes, requireLocalTypeReferences);
        }
    }

    private static void validateModuleSemantics(NormativeApiContract.ContractModule module, String owner, ApiContractKind contractKind) {
        if (module.id().startsWith("@nekojs/") && contractKind == ApiContractKind.ADDON) {
            throw new ApiContractException(new ApiContractViolation(
                    "RESERVED_MODULE_NAMESPACE", "/modules",
                    "Addon module '" + module.id() + "' must not use @nekojs/ namespace"));
        }

        if (!module.id().startsWith("@nekojs/") && (contractKind == ApiContractKind.PORTABLE
                || contractKind == ApiContractKind.FEATURE
                || contractKind == ApiContractKind.PLATFORM)) {
            String expectedPrefix = "@" + owner.replace("-", "_") + "/";
            if (!module.id().startsWith("@nekojs/") && !module.id().startsWith(expectedPrefix)) {
                throw new ApiContractException(new ApiContractViolation(
                        "MODULE_NAMESPACE_MISMATCH", "/modules",
                        "Module '" + module.id() + "' does not belong to owner '" + owner + "'"));
            }
        }

        switch (module.tier()) {
            case FEATURE, PLATFORM, ADDON -> {
                if (module.contractVersion() == null) {
                    throw new ApiContractException(new ApiContractViolation(
                            "MISSING_MODULE_CONTRACT_VERSION", "/modules",
                            module.tier() + " module '" + module.id() + "' must have contractVersion"));
                }
                if (module.contractVersion().prerelease() != null) {
                    throw new ApiContractException(new ApiContractViolation(
                            "MODULE_VERSION_PRERELEASE", "/modules",
                            "Module contractVersion must not have prerelease: " + module.contractVersion()));
                }
            }
            case VERSION, UNSAFE_NATIVE -> {
                if (module.contractVersion() != null) {
                    throw new ApiContractException(new ApiContractViolation(
                            "INVALID_MODULE_VERSION_DISCRIMINATOR", "/modules",
                            module.tier() + " module '" + module.id() + "' must not have contractVersion"));
                }
                if (module.moduleRevision() < 1) {
                    throw new ApiContractException(new ApiContractViolation(
                            "INVALID_MODULE_VERSION_DISCRIMINATOR", "/modules",
                            module.tier() + " module '" + module.id() + "' must have positive moduleRevision"));
                }
            }
            default -> {}
        }
    }

    private static JsonNode stripDocs(JsonNode node) {
        if (node.isObject()) {
            var mutable = MAPPER.createObjectNode();
            node.fields().forEachRemaining(entry -> {
                if (!"docs".equals(entry.getKey())) {
                    mutable.set(entry.getKey(), stripDocs(entry.getValue()));
                }
            });
            return mutable;
        } else if (node.isArray()) {
            var mutable = MAPPER.createArrayNode();
            for (JsonNode child : node) {
                mutable.add(stripDocs(child));
            }
            return mutable;
        }
        return node;
    }

    private static String canonicalJson(JsonNode node) {
        return canonicalize(node, null, null).toString();
    }

    private static JsonNode canonicalize(JsonNode node, String fieldName, JsonNode parent) {
        if (node.isObject()) {
            var result = MAPPER.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> result.set(key, canonicalize(value, key, node)));
            return result;
        }
        if (node.isArray()) {
            List<JsonNode> values = new ArrayList<>();
            node.forEach(child -> values.add(canonicalize(child, null, node)));
            if (isSetLikeArray(fieldName, parent)) {
                values.sort(java.util.Comparator.comparing(JsonNode::toString));
            }
            var result = MAPPER.createArrayNode();
            values.forEach(result::add);
            return result;
        }
        return node;
    }

    private static boolean isSetLikeArray(String fieldName, JsonNode parent) {
        if (fieldName == null) return false;
        if (Set.of("symbols", "signatures", "capabilities", "modules", "errors", "fields", "dependencies", "errorCodes")
                .contains(fieldName)) {
            return true;
        }
        return "arguments".equals(fieldName)
                && parent != null
                && parent.has("kind")
                && "UNION".equals(parent.get("kind").asText());
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
