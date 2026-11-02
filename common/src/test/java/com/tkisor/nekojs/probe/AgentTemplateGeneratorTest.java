package com.tkisor.nekojs.probe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class AgentTemplateGeneratorTest {
    @TempDir Path game;

    @Test void rolesRouteToExplicitRootWithoutDuplicatingTheHandbook() throws Exception {
        Path agents = game.resolve(".github/agents");
        Path root = game.resolve("custom/ai docs");
        AgentTemplateGenerator.generate(agents, root);
        try (var files = Files.list(agents)) {
            assertEquals(3, files.count());
        }
        for (String role : new String[]{"explore", "planner", "survey"}) {
            String text = Files.readString(agents.resolve("nekojs-" + role + ".agent.md"));
            assertTrue(text.contains("<../../custom/ai%20docs/current.json>"));
            assertTrue(text.contains("handbook/scripting-examples.md"));
            assertTrue(text.contains("tools: ['search', 'read']"));
            assertFalse(text.contains("MAXIMUM PARALLELISM"));
            assertFalse(text.contains("DO NOT explore anything under"));
            assertTrue(text.length() < 2000);
        }
        assertFalse(Files.exists(root));
    }

    @Test void unchangedGeneratedRolesRefreshTheirRootButEditedRolesDoNot() throws Exception {
        Path agents = game.resolve(".github/agents");
        AgentTemplateGenerator.generate(agents, game.resolve("ai-first"));
        Path edited = agents.resolve("nekojs-planner.agent.md");
        String custom = Files.readString(edited) + "User addition\n";
        Files.writeString(edited, custom);
        AgentTemplateGenerator.generate(agents, game.resolve("ai#next%25"));
        String explore = Files.readString(agents.resolve("nekojs-explore.agent.md"));
        assertTrue(explore.contains("<../../ai%23next%2525/current.json>"));
        assertFalse(explore.contains("ai-first"));
        assertEquals(custom, Files.readString(edited));
        AgentTemplateGenerator.generate(agents, game.resolve("ai-first"));
        assertTrue(Files.readString(agents.resolve("nekojs-explore.agent.md")).contains("../../ai-first/current.json"));
    }

    @Test void customAndOversizedFilesRemainUserOwned() throws Exception {
        Path agents = game.resolve("agents");
        Files.createDirectories(agents);
        Path custom = agents.resolve("nekojs-planner.agent.md");
        Path large = agents.resolve("nekojs-explore.agent.md");
        Files.writeString(custom, "User-authored role\n");
        Files.writeString(large, "x".repeat(70_000));
        AgentTemplateGenerator.generate(agents, game.resolve("ai"));
        assertEquals("User-authored role\n", Files.readString(custom));
        assertEquals(70_000, Files.size(large));
        assertFalse(Files.exists(agents.resolve("nekojs-planner.agent.md.pre-handbook")));
    }

    @Test void exactLegacyPayloadIsBackedUpBeforeOneTimeUpgrade() throws Exception {
        Path agents = game.resolve("agents");
        Files.createDirectories(agents);
        String name = "nekojs-explore.agent.md";
        String legacy = "Known old template\n";
        Files.writeString(agents.resolve(name), legacy);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(legacy.getBytes(StandardCharsets.UTF_8)));
        AgentTemplateGenerator.write(agents, name, "Replacement\n", hash);
        assertEquals("Replacement\n", Files.readString(agents.resolve(name)));
        assertEquals(legacy, Files.readString(agents.resolve(name + ".pre-handbook")));
        Files.writeString(agents.resolve(name), legacy);
        AgentTemplateGenerator.write(agents, name, "Do not overwrite again\n", hash);
        assertEquals(legacy, Files.readString(agents.resolve(name)));
    }

    @Test void editedLegacyAndOccupiedDestinationsAreNeverReplaced() throws Exception {
        Path agents = game.resolve("agents");
        Files.createDirectories(agents);
        String name = "nekojs-explore.agent.md";
        Files.createDirectory(agents.resolve(name));
        AgentTemplateGenerator.write(agents, name, "new", "irrelevant");
        assertTrue(Files.isDirectory(agents.resolve(name)));
        Path edited = agents.resolve("nekojs-planner.agent.md");
        Files.writeString(edited, "Edited legacy role");
        AgentTemplateGenerator.write(agents, edited.getFileName().toString(), "new", "wrong-hash");
        assertEquals("Edited legacy role", Files.readString(edited));
    }
}
