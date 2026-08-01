package com.tkisor.nekojs.api.contract;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tkisor.nekojs.api.surface.ApiParameter;
import com.tkisor.nekojs.api.surface.ApiSignature;
import com.tkisor.nekojs.api.surface.ApiSymbol;
import com.tkisor.nekojs.api.surface.ApiSymbolId;
import com.tkisor.nekojs.api.surface.ApiTier;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.api.surface.ApiVersion;
import dev.harrel.jsonschema.ValidatorFactory;
import dev.harrel.jsonschema.providers.GsonNode;

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

    private static final Set<String> PORTABLE_PRIMITIVES = Set.of("string", "number", "boolean", "null", "object", "json", "nbt");
    private static final String SCHEMA_JSON;
    private static final ValidatorFactory VALIDATOR_FACTORY;

    static {
        try (var is = ApiContractReader.class.getResourceAsStream(
                "/nekojs/api-contract/api-contract.schema.json")) {
            if (is == null) {
                throw new ExceptionInInitializerError("Schema resource not found");
            }
            SCHEMA_JSON = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        // dev.harrel 使用内置 Gson provider 校验 Draft 2020-12 schema，
        // 避免 networknt 强绑的 jackson 依赖链被内嵌进平台 jar。
        VALIDATOR_FACTORY = new ValidatorFactory().withJsonNodeFactory(new GsonNode.Factory());
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

        JsonElement root;
        String rawContractJson;
        try {
            StringBuilder buffer = new StringBuilder();
            char[] chunk = new char[8192];
            int read;
            while ((read = reader.read(chunk)) >= 0) {
                buffer.append(chunk, 0, read);
            }
            rawContractJson = buffer.toString();
            root = JsonParser.parseString(rawContractJson);
        } catch (Exception e) {
            throw new ApiContractException(
                    new ApiContractViolation("INVALID_JSON", "/", "Failed to parse JSON: " + e.getMessage()), e);
        }

        var validationResult = VALIDATOR_FACTORY.validate(SCHEMA_JSON, rawContractJson);
        if (!validationResult.isValid()) {
            String details = validationResult.getErrors().stream()
                    .map(error -> error.getInstanceLocation() + ": " + error.getError())
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
                2,
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

    private static NormativeApiContract convertToDto(JsonElement root) {
        int schemaVersion = root.getAsJsonObject().get("schemaVersion").getAsInt();
        NormativeApiContract.ContractIdentity identity = convertIdentity(root.getAsJsonObject().get("identity"));
        JsonObject rootObj = root.getAsJsonObject();
        String docs = rootObj.has("docs") ? rootObj.get("docs").getAsString() : null;
        List<ApiSymbol> symbols = convertSymbols(rootObj.get("symbols"));
        List<NormativeApiContract.ContractCapability> capabilities = rootObj.has("capabilities")
                ? convertCapabilities(rootObj.get("capabilities"))
                : List.of();
        List<NormativeApiContract.ContractModule> modules = rootObj.has("modules")
                ? convertModules(rootObj.get("modules"))
                : List.of();
        List<NormativeApiContract.ContractError> errors = rootObj.has("errors")
                ? convertErrors(rootObj.get("errors"))
                : List.of();
        List<NormativeApiContract.ContractEvent> events = rootObj.has("events")
                ? convertEvents(rootObj.get("events"))
                : List.of();

        return new NormativeApiContract(schemaVersion, identity, docs, symbols, capabilities, modules, errors, events);
    }

    private static NormativeApiContract.ContractIdentity convertIdentity(JsonElement node) {
        JsonObject obj = node.getAsJsonObject();
        String owner = obj.get("owner").getAsString();
        ApiContractKind kind = ApiContractKind.valueOf(obj.get("kind").getAsString());
        String contractId = obj.get("contractId").getAsString();
        ApiVersion version = ApiVersion.parse(obj.get("version").getAsString());
        return new NormativeApiContract.ContractIdentity(owner, kind, contractId, version);
    }

    private static List<ApiSymbol> convertSymbols(JsonElement node) {
        List<ApiSymbol> result = new ArrayList<>();
        if (node == null || !node.isJsonArray()) return result;
        for (JsonElement symNode : node.getAsJsonArray()) {
            result.add(convertSymbol(symNode));
        }
        return result;
    }

    private static ApiSymbol convertSymbol(JsonElement node) {
        JsonObject obj = node.getAsJsonObject();
        ApiSymbolId id = ApiSymbolId.parse(obj.get("id").getAsString());
        List<ApiSignature> signatures = new ArrayList<>();
        for (JsonElement sigNode : obj.get("signatures").getAsJsonArray()) {
            signatures.add(convertSignature(sigNode));
        }
        return new ApiSymbol(id, signatures);
    }

    private static ApiSignature convertSignature(JsonElement node) {
        JsonObject obj = node.getAsJsonObject();
        List<ApiParameter> parameters = new ArrayList<>();
        for (JsonElement paramNode : obj.get("parameters").getAsJsonArray()) {
            parameters.add(convertParameter(paramNode));
        }
        ApiTypeRef returnType = convertTypeRef(obj.get("returnType"));
        boolean isConstructor = obj.has("isConstructor") && obj.get("isConstructor").getAsBoolean();
        List<String> errorCodes = new ArrayList<>();
        if (obj.has("errorCodes")) {
            obj.get("errorCodes").getAsJsonArray().forEach(code -> errorCodes.add(code.getAsString()));
        }
        return new ApiSignature(parameters, returnType, isConstructor, errorCodes);
    }

    private static ApiParameter convertParameter(JsonElement node) {
        JsonObject obj = node.getAsJsonObject();
        String name = obj.get("name").getAsString();
        ApiTypeRef type = convertTypeRef(obj.get("type"));
        boolean optional = obj.has("optional") && obj.get("optional").getAsBoolean();
        boolean varargs = obj.has("varargs") && obj.get("varargs").getAsBoolean();
        return new ApiParameter(name, type, optional, varargs);
    }

    private static ApiTypeRef convertTypeRef(JsonElement node) {
        JsonObject obj = node.getAsJsonObject();
        ApiTypeRef.Kind kind = ApiTypeRef.Kind.valueOf(obj.get("kind").getAsString());
        String name = obj.has("name") ? obj.get("name").getAsString() : null;
        List<ApiTypeRef> arguments = new ArrayList<>();
        if (obj.has("arguments")) {
            for (JsonElement argNode : obj.get("arguments").getAsJsonArray()) {
                arguments.add(convertTypeRef(argNode));
            }
        }
        ApiSignature callbackSignature = null;
        if (obj.has("callbackSignature")) {
            callbackSignature = convertSignature(obj.get("callbackSignature"));
        }
        return new ApiTypeRef(kind, name, arguments, callbackSignature);
    }

    private static List<NormativeApiContract.ContractCapability> convertCapabilities(JsonElement node) {
        List<NormativeApiContract.ContractCapability> result = new ArrayList<>();
        if (node == null || !node.isJsonArray()) return result;
        for (JsonElement capNode : node.getAsJsonArray()) {
            JsonObject obj = capNode.getAsJsonObject();
            String id = obj.get("id").getAsString();
            String range = obj.get("contractVersionRange").getAsString();
            String docs = obj.has("docs") ? obj.get("docs").getAsString() : null;
            result.add(new NormativeApiContract.ContractCapability(id, range, docs));
        }
        return result;
    }

    private static List<NormativeApiContract.ContractError> convertErrors(JsonElement node) {
        List<NormativeApiContract.ContractError> result = new ArrayList<>();
        if (node == null || !node.isJsonArray()) return result;
        for (JsonElement errorNode : node.getAsJsonArray()) {
            JsonObject obj = errorNode.getAsJsonObject();
            List<String> fields = new ArrayList<>();
            if (obj.has("fields")) {
                obj.get("fields").getAsJsonArray().forEach(field -> fields.add(field.getAsString()));
            }
            result.add(new NormativeApiContract.ContractError(
                    obj.get("code").getAsString(),
                    fields,
                    obj.has("docs") ? obj.get("docs").getAsString() : null));
        }
        return result;
    }

    private static List<NormativeApiContract.ContractModule> convertModules(JsonElement node) {
        List<NormativeApiContract.ContractModule> result = new ArrayList<>();
        if (node == null || !node.isJsonArray()) return result;
        for (JsonElement modNode : node.getAsJsonArray()) {
            result.add(convertModule(modNode));
        }
        return result;
    }

    private static List<NormativeApiContract.ContractEvent> convertEvents(JsonElement node) {
        List<NormativeApiContract.ContractEvent> result = new ArrayList<>();
        if (node == null || !node.isJsonArray()) return result;
        for (JsonElement eventNode : node.getAsJsonArray()) {
            result.add(convertEvent(eventNode));
        }
        return result;
    }

    private static NormativeApiContract.ContractEvent convertEvent(JsonElement node) {
        JsonObject obj = node.getAsJsonObject();
        String group = obj.get("group").getAsString();
        String name = obj.get("name").getAsString();
        NormativeApiContract.EventTier tier =
                NormativeApiContract.EventTier.valueOf(obj.get("tier").getAsString());
        NormativeApiContract.Dispatch dispatch = convertDispatch(obj.get("dispatch"));
        String dispatchKeyType = obj.has("dispatch")
                && obj.get("dispatch").getAsJsonObject().has("keyType")
                ? obj.get("dispatch").getAsJsonObject().get("keyType").getAsString()
                : null;
        Boolean cancellable = obj.has("cancellable") ? obj.get("cancellable").getAsBoolean() : null;
        List<NormativeApiContract.ContractEventField> payload = obj.has("payload")
                ? convertEventFields(obj.get("payload"))
                : List.of();
        String docs = obj.has("docs") ? obj.get("docs").getAsString() : null;
        return new NormativeApiContract.ContractEvent(
                group, name, tier, dispatch, dispatchKeyType, cancellable, payload, docs);
    }

    private static NormativeApiContract.Dispatch convertDispatch(JsonElement node) {
        JsonObject obj = node.getAsJsonObject();
        return NormativeApiContract.Dispatch.valueOf(obj.get("kind").getAsString());
    }

    private static List<NormativeApiContract.ContractEventField> convertEventFields(JsonElement node) {
        List<NormativeApiContract.ContractEventField> result = new ArrayList<>();
        if (node == null || !node.isJsonArray()) return result;
        for (JsonElement fieldNode : node.getAsJsonArray()) {
            JsonObject obj = fieldNode.getAsJsonObject();
            String name = obj.get("name").getAsString();
            NormativeApiContract.FieldKind kind =
                    NormativeApiContract.FieldKind.valueOf(obj.get("kind").getAsString());
            ApiTypeRef portType = obj.has("portType") ? convertTypeRef(obj.get("portType")) : null;
            String docs = obj.has("docs") ? obj.get("docs").getAsString() : null;
            result.add(new NormativeApiContract.ContractEventField(name, kind, portType, docs));
        }
        return result;
    }

    private static NormativeApiContract.ContractModule convertModule(JsonElement node) {
        JsonObject obj = node.getAsJsonObject();
        String id = obj.get("id").getAsString();
        ApiTier tier = ApiTier.valueOf(obj.get("tier").getAsString());
        ApiVersion contractVersion = obj.has("contractVersion")
                ? ApiVersion.parse(obj.get("contractVersion").getAsString())
                : null;
        int moduleRevision = obj.has("moduleRevision") ? obj.get("moduleRevision").getAsInt() : 0;
        String docs = obj.has("docs") ? obj.get("docs").getAsString() : null;
        List<ApiSymbol> symbols = obj.has("symbols") ? convertSymbols(obj.get("symbols")) : List.of();
        List<NormativeApiContract.ContractModuleDependency> deps = obj.has("dependencies")
                ? convertDependencies(obj.get("dependencies"))
                : List.of();
        return new NormativeApiContract.ContractModule(id, tier, contractVersion, moduleRevision, docs, symbols, deps);
    }

    private static List<NormativeApiContract.ContractModuleDependency> convertDependencies(JsonElement node) {
        List<NormativeApiContract.ContractModuleDependency> result = new ArrayList<>();
        if (node == null || !node.isJsonArray()) return result;
        for (JsonElement depNode : node.getAsJsonArray()) {
            JsonObject obj = depNode.getAsJsonObject();
            String moduleId = obj.get("moduleId").getAsString();
            String versionRange = obj.has("versionRange") ? obj.get("versionRange").getAsString() : null;
            ApiTier targetTier = obj.has("targetTier")
                    ? ApiTier.valueOf(obj.get("targetTier").getAsString())
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
        validateEvents(contract, allSymbols, errorCodes);

        for (NormativeApiContract.ContractModule module : contract.modules()) {
            validateSignatureErrors(module.symbols(), errorCodes);
            validateModuleSemantics(module, actual.owner(), actual.kind());
        }
    }

    private static void validateEvents(
            NormativeApiContract contract,
            List<ApiSymbol> allSymbols,
            Set<String> declaredErrors) {
        if (contract.events().isEmpty()) {
            return;
        }
        Set<String> declaredTypes = allSymbols.stream()
                .filter(symbol -> "member".equals(symbol.id().kind()))
                .map(symbol -> symbol.id().qualifiedName())
                .map(name -> name.substring(0, name.lastIndexOf('.')))
                .collect(Collectors.toSet());
        boolean requireLocalTypeReferences = contract.identity().kind() == ApiContractKind.PORTABLE;

        Set<String> seenEvents = new HashSet<>();
        for (int i = 0; i < contract.events().size(); i++) {
            NormativeApiContract.ContractEvent event = contract.events().get(i);
            String eventKey = event.group() + "." + event.name();
            if (!seenEvents.add(eventKey)) {
                throw new ApiContractException(new ApiContractViolation(
                        "DUPLICATE_EVENT", "/events/" + i,
                        "Duplicate event '" + eventKey + "'"));
            }
            ApiSymbolId eventSymbolId = ApiSymbolId.parse("event:" + eventKey);
            for (NormativeApiContract.ContractEventField field : event.payload()) {
                if (field.portType() == null) {
                    continue;
                }
                validateType(field.portType(), eventSymbolId, declaredTypes, requireLocalTypeReferences);
                validateCallbackErrors(field.portType(), eventSymbolId, declaredErrors);
            }
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

    private static JsonElement stripDocs(JsonElement node) {
        if (node.isJsonObject()) {
            JsonObject mutable = new JsonObject();
            node.getAsJsonObject().entrySet().forEach(entry -> {
                if (!"docs".equals(entry.getKey())) {
                    mutable.add(entry.getKey(), stripDocs(entry.getValue()));
                }
            });
            return mutable;
        } else if (node.isJsonArray()) {
            JsonArray mutable = new JsonArray();
            for (JsonElement child : node.getAsJsonArray()) {
                mutable.add(stripDocs(child));
            }
            return mutable;
        }
        return node;
    }

    private static String canonicalJson(JsonElement node) {
        return canonicalize(node, null, null).toString();
    }

    private static JsonElement canonicalize(JsonElement node, String fieldName, JsonElement parent) {
        if (node.isJsonObject()) {
            JsonObject result = new JsonObject();
            Map<String, JsonElement> fields = new TreeMap<>();
            node.getAsJsonObject().entrySet().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> result.add(key, canonicalize(value, key, node)));
            return result;
        }
        if (node.isJsonArray()) {
            List<JsonElement> values = new ArrayList<>();
            node.getAsJsonArray().forEach(child -> values.add(canonicalize(child, null, node)));
            if (isSetLikeArray(fieldName, parent)) {
                values.sort(java.util.Comparator.comparing(JsonElement::toString));
            }
            JsonArray result = new JsonArray();
            values.forEach(result::add);
            return result;
        }
        return node;
    }

    private static boolean isSetLikeArray(String fieldName, JsonElement parent) {
        if (fieldName == null) return false;
        if (Set.of("symbols", "signatures", "capabilities", "modules", "errors", "fields", "dependencies", "errorCodes", "events", "payload")
                .contains(fieldName)) {
            return true;
        }
        return "arguments".equals(fieldName)
                && parent != null
                && parent.isJsonObject()
                && parent.getAsJsonObject().has("kind")
                && "UNION".equals(parent.getAsJsonObject().get("kind").getAsString());
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
