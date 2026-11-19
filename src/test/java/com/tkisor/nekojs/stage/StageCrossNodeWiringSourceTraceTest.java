package com.tkisor.nekojs.stage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-trace invariants verifying cross-node stage subsystem wiring across NeoForge and Fabric
 * core plugins, client network sync handlers, shared Mixin manifests, and command entrypoints.
 */
class StageCrossNodeWiringSourceTraceTest {

    private static final Pattern STAGE_GATE_MIXIN_ENTRY =
            Pattern.compile("^stage\\.Mixin[A-Za-z0-9_]+StageGate$");

    private static final Pattern BUILD_STAGES_LITERAL_CALL =
            Pattern.compile("StageCommandsHelper\\.buildStagesLiteral\\s*\\(");

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("stonecutter.gradle.kts"))
                    && Files.isDirectory(dir.resolve("src"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("cannot locate repo root from " + Path.of("").toAbsolutePath());
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    /**
     * Retains only executable code lines, dropping package/import headers, javadoc lines,
     * Stonecutter disabled block-comment lines, and line comments.
     */
    private static String executableCodeLinesOnly(String source) {
        StringBuilder out = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*")
                    || trimmed.startsWith("/*")
                    || trimmed.startsWith("//")
                    || trimmed.startsWith("import ")
                    || trimmed.startsWith("package ")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static Set<String> extractStageGateMixins(Path manifestPath) {
        String raw = read(manifestPath);
        String strippedComments = raw.lines()
                .filter(line -> !line.trim().startsWith("//"))
                .collect(Collectors.joining("\n"));
        JsonObject root = JsonParser.parseString(strippedComments).getAsJsonObject();
        assertEquals("com.tkisor.nekojs.mixin", root.get("package").getAsString(),
                manifestPath + " must keep base mixin package com.tkisor.nekojs.mixin");

        Set<String> stageGateEntries = new LinkedHashSet<>();
        for (String section : List.of("mixins", "client", "server")) {
            JsonElement sectionElement = root.get(section);
            if (sectionElement == null || !sectionElement.isJsonArray()) {
                continue;
            }
            JsonArray array = sectionElement.getAsJsonArray();
            for (JsonElement element : array) {
                String entry = element.getAsString();
                if (entry.startsWith("stage.")) {
                    assertTrue(STAGE_GATE_MIXIN_ENTRY.matcher(entry).matches(),
                            manifestPath + " contains unexpected stage mixin naming: " + entry);
                    assertTrue(stageGateEntries.add(entry),
                            manifestPath + " lists duplicate stage gate mixin entry: " + entry);
                }
            }
        }
        return stageGateEntries;
    }

    @Test
    void corePluginsRegisterStageEventsBindingAndTypeAdapters() {
        Path root = repoRoot();
        List<Path> pluginFiles = List.of(
                root.resolve("src/main/java/com/tkisor/nekojs/core/NekoJSCorePlugin.java"),
                root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricCorePlugin.java"));

        List<String> requiredTokens = List.of(
                "StageEvents.GROUP",
                "StagesBinding",
                "StagesFacade.class",
                "NekoIdAdapter",
                "StagePlayerRefAdapter",
                "StageNotifyOptionsAdapter");

        for (Path pluginFile : pluginFiles) {
            assertTrue(Files.isRegularFile(pluginFile), "Missing core plugin file: " + pluginFile);
            String code = executableCodeLinesOnly(read(pluginFile));
            for (String token : requiredTokens) {
                assertTrue(code.contains(token),
                        pluginFile.getFileName() + " must register stage wiring token '" + token + "'");
            }
        }
    }

    @Test
    void clientNetworkHandlersInterceptStageWireProtocolChannelKey() {
        Path root = repoRoot();
        List<Path> networkHandlers = List.of(
                root.resolve("src/main/java/com/tkisor/nekojs/network/ClientDataMessageHandler.java"),
                root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricPlayNetwork.java"));

        for (Path handlerFile : networkHandlers) {
            assertTrue(Files.isRegularFile(handlerFile), "Missing client network handler file: " + handlerFile);
            String code = executableCodeLinesOnly(read(handlerFile));
            assertTrue(code.contains("StageWireProtocol.CHANNEL_KEY"),
                    handlerFile.getFileName() + " must intercept StageWireProtocol.CHANNEL_KEY");
        }
    }

    @Test
    void allThreeSharedMixinManifestsIncludeEverySharedStageGateMixin() {
        Path root = repoRoot();
        Path stageMixinDir = root.resolve("src/main/java/com/tkisor/nekojs/mixin/stage");
        assertTrue(Files.isDirectory(stageMixinDir),
                "Shared stage gate mixin package directory must exist: " + stageMixinDir);
        Set<String> sourceMixins;
        try (Stream<Path> walk = Files.list(stageMixinDir)) {
            sourceMixins = walk
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("Mixin") && name.endsWith("StageGate.java"))
                    .map(name -> "stage." + name.substring(0, name.length() - ".java".length()))
                    .sorted()
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            throw new IllegalStateException("cannot list " + stageMixinDir, e);
        }

        assertEquals(27, sourceMixins.size(), "Expected all shared stage gate mixins under " + stageMixinDir);
        for (Path manifest : List.of(root.resolve("src/main/resources-legacy/nekojs.mixins.json"),
                root.resolve("src/main/resources-modern/nekojs.mixins.json"),
                root.resolve("src/fabric/resources/nekojs-fabric-shared.mixins.json"))) {
            assertEquals(sourceMixins, extractStageGateMixins(manifest),
                    manifest + " must register every shared stage gate mixin");
        }
    }

    @Test
    void allThreeCommandEntrypointsMountStagesLiteral() {
        Path root = repoRoot();
        List<Path> commandEntrypoints = List.of(
                root.resolve("src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("versions/1.21.1/src/main/java/com/tkisor/nekojs/command/NekoJSCommands.java"),
                root.resolve("src/fabric/java/com/tkisor/nekojs/fabric/FabricNekoJSCommands.java"));

        for (Path commandFile : commandEntrypoints) {
            assertTrue(Files.isRegularFile(commandFile), "Missing command entrypoint: " + commandFile);
            String code = executableCodeLinesOnly(read(commandFile));
            assertTrue(BUILD_STAGES_LITERAL_CALL.matcher(code).find(),
                    commandFile + " must mount StageCommandsHelper.buildStagesLiteral()");
        }
    }
}
