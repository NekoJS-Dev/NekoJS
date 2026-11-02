package com.tkisor.nekojs.authoring;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import java.io.IOException;
import java.nio.file.*;

/** Explicit local authoring configuration. Invalid settings never enable a listener. */
public record AuthoringConfig(boolean documentation, boolean agents, boolean api, boolean http, boolean mcp, int port,
                              AuthoringControl.Policy controls, String directory, boolean editorAgents) {
    public static final String DEFAULT_TEXT = """
            # Generated AI material is contained in a game-relative directory. Restart after changes.
            [output]
            directory = ".github/neko_ai"
            editor_agents = false

            # English authoring documentation is exported without scanning classes or opening a port.
            [documentation]
            enabled = true
            agents = "create-if-missing"

            [api]
            enabled = true

            # Both protocols share one loopback listener and the same read-only query core.
            [http]
            enabled = false

            [mcp]
            enabled = false
            host = "127.0.0.1"
            port = 8767

            # Live MCP actions require a matching runtime owner and explicit authorization.
            [control]
            commands = false
            reload = false
            command_allowlist = []
            """;

    public static AuthoringConfig load(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        if (!Files.exists(file)) {
            try { Files.writeString(file, DEFAULT_TEXT, StandardOpenOption.CREATE_NEW); }
            catch (FileAlreadyExistsException ignored) { }
        }
        try (CommentedFileConfig config = CommentedFileConfig.builder(file).sync().build()) {
            config.load();
            boolean docs = bool(config.getOrElse("documentation.enabled", true));
            String agents = config.getOrElse("documentation.agents", "create-if-missing");
            if (!agents.equals("create-if-missing") && !agents.equals("off")) throw new IllegalArgumentException();
            if (!"127.0.0.1".equals(config.getOrElse("mcp.host", "127.0.0.1"))) throw new IllegalArgumentException();
            Object portValue = config.getOrElse("mcp.port", 8767);
            if (!(portValue instanceof Number number) || number.doubleValue() != number.intValue() || number.intValue() < 1 || number.intValue() > 65535) throw new IllegalArgumentException();
            Object directory = config.getOrElse("output.directory", AuthoringPaths.DEFAULT_DIRECTORY);
            if (!(directory instanceof String output) || output.isBlank()) throw new IllegalArgumentException("Expected an authoring output directory.");
            return new AuthoringConfig(docs, agents.equals("create-if-missing"), bool(config.getOrElse("api.enabled", true)),
                    bool(config.getOrElse("http.enabled", false)), bool(config.getOrElse("mcp.enabled", false)), number.intValue(),
                    new AuthoringControl.Policy(bool(config.getOrElse("control.commands", false)),
                            bool(config.getOrElse("control.reload", false)), commandRoots(config.getOrElse("control.command_allowlist", java.util.List.of()))),
                    output, bool(config.getOrElse("output.editor_agents", false)));
        } catch (RuntimeException e) {
            throw new IOException("[NEKO-1019] Invalid ai.toml; authoring initialization was rejected.", e);
        }
    }
    private static java.util.Set<String> commandRoots(Object value) {
        if (!(value instanceof java.util.List<?> values)) throw new IllegalArgumentException("Expected a command root array.");
        java.util.Set<String> roots = new java.util.LinkedHashSet<>();
        for (Object entry : values) {
            if (!(entry instanceof String root)) throw new IllegalArgumentException("Expected a command root string.");
            roots.add(root);
        }
        return roots;
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) return b;
        throw new IllegalArgumentException("[NEKO-1019] Expected a boolean authoring setting.");
    }
}
