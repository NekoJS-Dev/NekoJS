package com.tkisor.nekojs.core.pack.sync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.UUID;

/** Creates owned signed resource fixtures; signing keys never enter the distributed directory. */
public final class SignedDirectoryFixture {
    public static final String ID = "signed_resources";

    private SignedDirectoryFixture() {}

    public static void main(String[] arguments) throws Exception {
        Path server = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path output = Path.of(arguments[1]).toAbsolutePath().normalize();
        Path secrets = server.resolve("acceptance-private");
        if (Files.exists(secrets) || Files.exists(server.resolve("nekojs/packs/" + ID))) {
            throw new IllegalStateException("Preserve existing signing fixtures");
        }
        Files.createDirectories(secrets);
        for (String name : new String[]{"original", "rotated"}) {
            var pair = PackSigner.generateKeyPair();
            Files.write(secrets.resolve(name + ".pk8"), pair.getPrivate().getEncoded(), StandardOpenOption.CREATE_NEW);
            Files.write(secrets.resolve(name + ".spki"), pair.getPublic().getEncoded(), StandardOpenOption.CREATE_NEW);
        }
        phase(server, "restore");
        Files.writeString(output.resolve("remote-fixture.json"), new Gson().toJson(Map.of(
                "host", "127.0.0.1", "keyId", keyId(server),
                "publicKey", PackSigner.encodePublicKey(key(server, "original").getPublic()),
                "privateKeyDistributed", false)) + "\n", StandardOpenOption.CREATE_NEW);
    }

    public static void phase(Path server, String phase) throws Exception {
        if (!phase.equals("restore") && !phase.equals("tampered") && !phase.equals("rotated")) {
            throw new IllegalArgumentException("Unknown fixture phase: " + phase);
        }
        Path pack = server.resolve("nekojs/packs/" + ID);
        if (phase.equals("tampered")) {
            Files.writeString(pack.resolve("assets/neko_acceptance/marker.txt"), "TAMPERED");
            return;
        }
        Files.createDirectories(pack.resolve("client_scripts"));
        Files.createDirectories(pack.resolve("assets/neko_acceptance/lang"));
        Files.writeString(pack.resolve("client_scripts/main.js"),
                "console.info('NEKO_DIRECTORY_REMOTE signed resources activated');\n");
        Files.writeString(pack.resolve("assets/neko_acceptance/marker.txt"), "REMOTE");
        Files.writeString(pack.resolve("assets/neko_acceptance/remote-only.txt"), "REMOTE_ONLY");
        Files.writeString(pack.resolve("assets/neko_acceptance/lang/en_us.json"), new Gson().toJson(Map.of(
                "neko.acceptance.shared", "REMOTE", "neko.acceptance.remote", "REMOTE")));
        JsonObject manifest = new JsonObject();
        manifest.addProperty("formatVersion", 1);
        manifest.addProperty("id", ID);
        manifest.addProperty("version", "1.0.0");
        manifest.addProperty("clientSync", true);
        manifest.add("entries", new Gson().toJsonTree(Map.of("client", new String[]{"main.js"})));
        manifest.add("signature", PackSigner.sign(keyId(server), key(server, phase.equals("rotated") ? "rotated" : "original"),
                "packs:" + ID, "GLOBAL", manifest.toString(), PackHasher.readContentFiles(pack)));
        Files.writeString(pack.resolve("manifest.json"), new Gson().toJson(manifest) + "\n");
    }

    private static KeyPair key(Path server, String name) throws Exception {
        var factory = KeyFactory.getInstance("Ed25519");
        Path secrets = server.resolve("acceptance-private");
        return new KeyPair(factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(secrets.resolve(name + ".spki")))),
                factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(secrets.resolve(name + ".pk8")))));
    }

    private static String keyId(Path server) {
        return "neko-directory-acceptance-" + UUID.nameUUIDFromBytes(
                server.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
    }
}
