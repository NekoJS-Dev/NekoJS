package com.tkisor.nekojs.api.manifest;

import com.google.gson.*;
import com.tkisor.nekojs.api.module.ActiveModule;
import com.tkisor.nekojs.api.module.InactiveModule;
import com.tkisor.nekojs.api.surface.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public final class CanonicalJson {

    private CanonicalJson() {}

    public static String serialize(ApiManifestBundle bundle) {
        JsonObject root = new JsonObject();
        root.addProperty("catalogSchemaVersion", bundle.catalogSchemaVersion());
        root.addProperty("nekojsVersion", bundle.nekojsVersion());
        root.addProperty("apiVersion", bundle.apiVersion().toString());
        root.addProperty("spiVersion", bundle.spiVersion().toString());
        root.addProperty("runtimeContractVersion", bundle.runtimeContractVersion().toString());
        root.addProperty("portableContractHash", bundle.portableContractHash());
        root.add("moduleContractHashes", serializeStringMap(bundle.moduleContractHashes()));
        root.addProperty("portableSurfaceHash", bundle.portableSurfaceHash());

        JsonObject envs = new JsonObject();
        List<String> envKeys = new ArrayList<>(bundle.environments().keySet());
        Collections.sort(envKeys);
        for (String key : envKeys) {
            envs.add(key, serializeEnvironmentManifest(bundle.environments().get(key)));
        }
        root.add("environments", envs);

        return root.toString();
    }

    public static String serializeSurfaceForHash(ApiSurfaceSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.add("symbols", serializeSymbols(snapshot.symbols()));
        root.add("activeCapabilities", serializeStringSet(snapshot.activeCapabilityNames()));
        root.add("activeModules", serializeModuleIds(snapshot.activeModules()));
        root.add("inactiveModules", serializeInactiveModuleIds(snapshot.inactiveModules()));
        return root.toString();
    }

    public static String sha256hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static JsonObject serializeEnvironmentManifest(ApiEnvironmentManifest manifest) {
        JsonObject obj = new JsonObject();
        obj.addProperty("scriptType", manifest.scriptType().name());
        obj.add("environmentKey", serializeEnvironmentKey(manifest.environmentKey()));
        obj.addProperty("portableSurfaceHash", manifest.portableSurfaceHash());
        obj.addProperty("environmentSurfaceHash", manifest.environmentSurfaceHash());
        obj.add("activeCapabilities", serializeStringSet(manifest.activeCapabilities()));
        obj.add("activeModuleIds", serializeStringList(manifest.activeModuleIds()));
        obj.add("inactiveModuleIds", serializeStringList(manifest.inactiveModuleIds()));
        obj.add("symbols", serializeSymbols(manifest.symbols()));
        return obj;
    }

    private static JsonObject serializeEnvironmentKey(EnvironmentKey key) {
        JsonObject obj = new JsonObject();
        obj.addProperty("scriptType", key.scriptType().name());
        obj.addProperty("dist", key.dist().name());
        obj.addProperty("loaderId", key.loaderId());
        obj.addProperty("loaderVersionRaw", key.loaderVersionRaw());
        obj.addProperty("minecraftVersion", key.minecraftVersion());
        return obj;
    }

    private static JsonArray serializeSymbols(List<ApiSymbol> symbols) {
        List<ApiSymbol> sorted = new ArrayList<>(symbols);
        sorted.sort(Comparator.comparing(s -> s.id().value()));

        JsonArray arr = new JsonArray();
        for (ApiSymbol symbol : sorted) {
            arr.add(serializeSymbol(symbol));
        }
        return arr;
    }

    private static JsonObject serializeSymbol(ApiSymbol symbol) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", symbol.id().value());
        obj.add("signatures", serializeSignatures(symbol.signatures()));
        return obj;
    }

    private static JsonArray serializeSignatures(List<ApiSignature> signatures) {
        List<ApiSignature> sorted = new ArrayList<>(signatures);
        sorted.sort(Comparator.comparing(ApiSignature::callKey));

        JsonArray arr = new JsonArray();
        for (ApiSignature sig : sorted) {
            arr.add(serializeSignature(sig));
        }
        return arr;
    }

    private static JsonObject serializeSignature(ApiSignature sig) {
        JsonObject obj = new JsonObject();
        obj.add("parameters", serializeParameters(sig.parameters()));
        obj.add("returnType", serializeTypeRef(sig.returnType()));
        obj.addProperty("isConstructor", sig.isConstructor());
        return obj;
    }

    private static JsonArray serializeParameters(List<ApiParameter> params) {
        JsonArray arr = new JsonArray();
        for (ApiParameter p : params) {
            arr.add(serializeParameter(p));
        }
        return arr;
    }

    private static JsonObject serializeParameter(ApiParameter p) {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", p.name());
        obj.add("type", serializeTypeRef(p.type()));
        obj.addProperty("optional", p.optional());
        obj.addProperty("varargs", p.varargs());
        return obj;
    }

    private static JsonObject serializeTypeRef(ApiTypeRef type) {
        JsonObject obj = new JsonObject();
        obj.addProperty("kind", type.kind().name());
        if (type.name() != null) {
            obj.addProperty("name", type.name());
        }
        if (!type.arguments().isEmpty()) {
            JsonArray args = new JsonArray();
            for (ApiTypeRef arg : type.arguments()) {
                args.add(serializeTypeRef(arg));
            }
            obj.add("arguments", args);
        }
        if (type.callbackSignature() != null) {
            obj.add("callbackSignature", serializeSignature(type.callbackSignature()));
        }
        return obj;
    }

    private static JsonObject serializeStringMap(Map<String, String> map) {
        JsonObject obj = new JsonObject();
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            obj.addProperty(key, map.get(key));
        }
        return obj;
    }

    private static JsonArray serializeStringSet(Set<String> set) {
        List<String> sorted = new ArrayList<>(set);
        Collections.sort(sorted);
        JsonArray arr = new JsonArray();
        for (String s : sorted) {
            arr.add(s);
        }
        return arr;
    }

    private static JsonArray serializeStringList(List<String> list) {
        JsonArray arr = new JsonArray();
        for (String s : list) {
            arr.add(s);
        }
        return arr;
    }

    private static JsonArray serializeModuleIds(List<ActiveModule> modules) {
        List<String> ids = modules.stream()
                .map(m -> m.descriptor().moduleId())
                .sorted()
                .toList();
        JsonArray arr = new JsonArray();
        for (String id : ids) {
            arr.add(id);
        }
        return arr;
    }

    private static JsonArray serializeInactiveModuleIds(List<InactiveModule> modules) {
        List<String> ids = modules.stream()
                .map(m -> m.descriptor().moduleId())
                .sorted()
                .toList();
        JsonArray arr = new JsonArray();
        for (String id : ids) {
            arr.add(id);
        }
        return arr;
    }
}
