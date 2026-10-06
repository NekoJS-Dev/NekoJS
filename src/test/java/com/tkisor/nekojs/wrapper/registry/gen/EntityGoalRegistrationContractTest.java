package com.tkisor.nekojs.wrapper.registry.gen;

import com.tkisor.nekojs.wrapper.entity.GoalRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract checks for the Issue #4 entity and goal registration surface. */
class EntityGoalRegistrationContractTest {

    @Test
    void entityBuilderExposesNativeFactoryAndConstructorClassEntrypoints() throws Exception {
        Method factory = EntityTypeBuilder.class.getMethod("factory", BiFunction.class);
        Method entityClass = EntityTypeBuilder.class.getMethod("entityClass", Class.class);

        assertEquals(EntityTypeBuilder.class, factory.getReturnType());
        assertEquals(EntityTypeBuilder.class, entityClass.getReturnType());
        assertTrue(java.util.Arrays.stream(EntityTypeBuilder.class.getMethods())
                .anyMatch(method -> method.getName().equals("registeredEntityClass")));
        assertNotNull(EntityTypeBuilder.class.getDeclaredMethod("build"));
    }

    @Test
    void goalBuilderExposesOrdinaryAndTargetCustomFactories() throws Exception {
        Method ordinary = GoalRegistry.GoalBuilderJS.class.getMethod("custom", int.class, Function.class);
        Method target = GoalRegistry.GoalBuilderJS.class.getMethod("customTarget", int.class, Function.class);

        assertEquals(GoalRegistry.GoalBuilderJS.class, ordinary.getReturnType());
        assertEquals(GoalRegistry.GoalBuilderJS.class, target.getReturnType());
    }

    private static Path repoRoot() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            if (Files.isRegularFile(directory.resolve("stonecutter.gradle.kts"))
                    && Files.isDirectory(directory.resolve("src"))) {
                return directory;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("cannot locate repository root");
    }

    @Test
    void fabricClientRegistersACompletedEntityRendererForScriptTypes() throws Exception {
        Path source = repoRoot().resolve("src/fabric/java/com/tkisor/nekojs/fabric/NekoJSFabricClient.java");
        String text = Files.readString(source, StandardCharsets.UTF_8);
        assertTrue(text.contains("EntityTypeBuilder.registeredEntityTypes()"));
        assertTrue(text.contains("EntityRenderers.register"),
                "Fabric must install a renderer for every script-defined entity type");
    }
}
