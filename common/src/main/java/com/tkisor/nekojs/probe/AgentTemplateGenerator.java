package com.tkisor.nekojs.probe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** Small optional editor roles; API and scripting rules remain in the versioned handbook. */
public final class AgentTemplateGenerator {
    private static final int MAX_LEGACY_BYTES = 64 * 1024;
    // Only exact pre-handbook generated payloads may be upgraded; edited files remain user-owned.
    private static final Map<String, String> LEGACY_HASHES = Map.of(
            "nekojs-explore.agent.md", "26a46638583d2f9773021b1d2119b03648d1bb95ca61ded1bc1027aa7aa03e80",
            "nekojs-planner.agent.md", "351c6c3d9567b20cdf2b610f6d0e5903231234c64e8a28b32e28c756d28abdc3",
            "nekojs-survey.agent.md", "c1c0133794bc6ca250a10237a8b6217b8cb14fddf4ab3fd0e9f85e80761b858b");

    private AgentTemplateGenerator() {}

    /** Compatibility entry for the standard game/.github/agents editor discovery directory. */
    public static void generate(Path agentsDir) throws IOException {
        Path absolute = agentsDir.toAbsolutePath().normalize();
        generate(absolute, absolute.getParent().getParent().resolve("nekojs/ai"));
    }

    /** Generates missing roles and upgrades exact legacy templates without replacing customized files. */
    public static void generate(Path agentsDir, Path authoringDirectory) throws IOException {
        Path directory = agentsDir.toAbsolutePath().normalize();
        Path root = authoringDirectory.toAbsolutePath().normalize();
        String relative = directory.relativize(root.resolve("current.json")).toString().replace('\\', '/');
        String pointer;
        try { pointer = new java.net.URI(null, null, relative, null).toASCIIString(); }
        catch (java.net.URISyntaxException invalidPath) { throw new IOException("Invalid authoring document URI.", invalidPath); }
        Files.createDirectories(directory);
        for (String name : java.util.List.of("nekojs-explore.agent.md", "nekojs-planner.agent.md", "nekojs-survey.agent.md")) {
            write(directory, name, template(name, pointer));
        }
    }

    private static String template(String file, String pointer) {
        return switch (file) {
            case "nekojs-explore.agent.md" -> role("Explore (NekoJS)",
                    "Find exact declarations and evidence for a NekoJS scripting question.", pointer,
                    "Resolve the requested Binding, Event Group or class, then inspect only relevant overloads and parameters. "
                            + "Report exact signatures, source locations, snapshot coverage and unresolved uncertainty. Do not edit scripts.");
            case "nekojs-planner.agent.md" -> role("Plan (NekoJS)",
                    "Plan a bounded NekoJS script change before implementation.", pointer,
                    "Identify the Script Type, platform prerequisites, exact APIs and the smallest applicable scripting example. "
                            + "Plan the install location, trigger, expected result, cleanup and validation. Do not implement unless the user requests it.");
            case "nekojs-survey.agent.md" -> role("Survey (NekoJS)",
                    "Summarize evidence and validation gaps for a NekoJS script task.", pointer,
                    "Compare the task with the current handbook and instance declarations. "
                            + "Separate supported behavior, incomplete index coverage, isolated checks and live-game acceptance. Do not claim unrun tests passed.");
            default -> throw new IllegalArgumentException("Unknown generated agent role.");
        };
    }

    private static String role(String name, String description, String pointer, String task) {
        return """
                ---
                name: %s
                description: %s
                target: vscode
                tools: ['search', 'read']
                ---
                <!-- nekojs-generated-role: 2 -->

                %s

                Read the [current documentation pointer](<%s>); resolve its bundle and open
                handbook/index.md, then handbook/scripting-examples.md for the relevant scenario.
                Follow that version's handbook instead of copying API rules into this role.
                If the pointer is missing, request documentation export from this instance;
                do not invent a bundle ID or treat another instance's declarations as authoritative.

                Use context, capabilities and schema before precise queries. Keep overloads and
                same-parameter conditions correlated. Read only the needed declarations; do not
                preload whole generated trees. Live mutations require explicit user authorization
                and enabled server policy. A timeout after execution starts is not proof of rollback.
                """.formatted(name, description, task, pointer);
    }

    private static void write(Path directory, String name, String text) throws IOException {
        Path target = directory.resolve(name);
        byte[] bytes = readLegacy(target);
        if (bytes == null) { write(directory, name, text, LEGACY_HASHES.get(name)); return; }
        String existing = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (existing.equals(text)) return;
        String hash = sha256(existing);
        if (hash.equals(LEGACY_HASHES.get(name))) { write(directory, name, text, hash); return; }
        var link = java.util.regex.Pattern.compile(
                java.util.regex.Pattern.quote("Read the [current documentation pointer](<")
                        + "([^<>\r\n]+)" + java.util.regex.Pattern.quote(">)")).matcher(existing);
        if (link.find() && template(name, link.group(1)).equals(existing)) {
            write(directory, name, text, hash, false);
        }
    }

    static void write(Path directory, String name, String text, String legacyHash) throws IOException {
        write(directory, name, text, legacyHash, true);
    }

    private static void write(Path directory, String name, String text, String legacyHash, boolean legacy) throws IOException {
        Path target = directory.resolve(name);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            try {
                Files.writeString(target, text, StandardOpenOption.CREATE_NEW);
            } catch (java.nio.file.FileAlreadyExistsException racedCreation) {
                // Another writer owns the newly created file.
            }
            return;
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) return;
        byte[] bytes = readLegacy(target);
        if (bytes == null || !sha256(new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n")).equals(legacyHash)) return;
        Path backup = directory.resolve(name + (legacy ? ".pre-handbook" : ".previous-" + java.util.UUID.randomUUID()));
        try {
            // Move rather than overwrite: concurrent custom content remains recoverable.
            Files.move(target, backup);
        } catch (java.nio.file.FileAlreadyExistsException | java.nio.file.NoSuchFileException racedMove) {
            return;
        }
        boolean installed = false;
        try {
            byte[] moved = readLegacy(backup);
            if (moved == null || !java.util.Arrays.equals(bytes, moved)) return;
            Files.writeString(target, text, StandardOpenOption.CREATE_NEW);
            installed = true;
        } catch (java.nio.file.FileAlreadyExistsException racedCreation) {
            // Keep both the new owner's file and the recovery copy.
        } finally {
            if (!installed && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                try { Files.move(backup, target); }
                catch (java.nio.file.FileAlreadyExistsException racedRestore) {
                    // The recovery copy remains available without replacing the new owner's file.
                }
            }
        }
    }

    private static byte[] readLegacy(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null;
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_LEGACY_BYTES + 1);
            return bytes.length > MAX_LEGACY_BYTES ? null : bytes;
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
