package com.tkisor.nekojs.core.dynamic.plan;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.script.ScriptContextRegistry;
import com.tkisor.nekojs.testfixture.TestPlatformInit;
import graal.graalvm.polyglot.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime behavior test on real GraalJS: an explicit setter call ({@code b.setMaxStackSize(16)}) and
 * a JavaBean-style property write ({@code b.maxStackSize = 16}) are forwarded by
 * {@link DynamicBuilderSurface} to the <b>same</b> setter {@code Method}, so both take one
 * validation, normalization and definition fingerprint path.
 *
 * <p>A property write on a Graal host object does not reach the setter, as earlier characterization
 * runs proved; a temporary property experiment on GraalMC cannot count as integration evidence, so
 * this test pins the behavior in the GraalJS runtime itself. Rerun it after a GraalJS upgrade.
 */
class DynamicBuilderSurfaceParityTest {

    @BeforeAll
    static void initPlatform() {
        TestPlatformInit.ensureInitialized();
    }

    /** Runs a script against the item surface in real GraalJS and returns the normalized readings. */
    private static List<String> readingsOf(String script) {
        DynamicItemBuilder builder = new DynamicItemBuilder();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("b", DynamicBuilderSurface.of(builder));
                context.eval("js", script);
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
        return DynamicBuilderContract.of(DynamicItemBuilder.class).normalizedPropertyReadings(builder);
    }

    /** Runs a script against the item builder in real GraalJS and returns the fingerprint for the given id. */
    private static String itemFingerprint(String id, String script) {
        DynamicItemBuilder builder = new DynamicItemBuilder();
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("b", DynamicBuilderSurface.of(builder));
                context.eval("js", script);
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
        return DynamicDefinition.of(DynamicDefinitionType.ITEM, id, builder).fingerprint();
    }

    /** Runs a script that should throw in real GraalJS and returns the exception message. */
    private static String errorOf(String script) {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                context.getBindings("js").putMember("b", DynamicBuilderSurface.of(new DynamicItemBuilder()));
                context.getBindings("js").putMember("s",
                        DynamicBuilderSurface.of(new DynamicSoundEventBuilder()));
                context.getBindings("js").putMember("m",
                        DynamicBuilderSurface.of(new DynamicMobEffectBuilder()));
                RuntimeException error = assertThrows(RuntimeException.class,
                        () -> context.eval("js", script), "script must fail: " + script);
                return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
    }

    @Test
    void propertyAssignmentAndExplicitSetterProduceSameNormalizedReadingsAndFingerprint() {
        List<String> viaProperty = readingsOf("""
                b.maxStackSize = 16;
                b.rarity = 'EPIC';
                b.fireResistant = true;
                b.mode = 'reloadable';
                """);
        List<String> viaSetter = readingsOf("""
                b.setMaxStackSize(16).setRarity('epic').setFireResistant(true).setMode('reloadable');
                """);
        assertEquals(viaSetter, viaProperty, "两种写法必须产生同一份规范化读数");
        // Case and default normalization: 'EPIC' and 'epic' are the same, normalized inside the setter.
        assertEquals(List.of(
                "fireResistant=true",
                "maxStackSize=16",
                "mode=reloadable",
                "rarity=epic"), viaProperty, "读数是全量可写属性、字典序、规范化值");

        String fpProperty = itemFingerprint("mymod:via_property", """
                b.maxStackSize = 16; b.rarity = 'epic'; b.fireResistant = true;
                """);
        String fpSetter = itemFingerprint("mymod:via_property", """
                b.setMaxStackSize(16); b.setRarity('epic'); b.setFireResistant(true);
                """);
        assertEquals(fpSetter, fpProperty, "同 id 同定义：两种写法 fingerprint 相同（AC4/AC5）");
        assertEquals(64, fpSetter.length(), "SHA-256 hex 指纹");
    }

    @Test
    void fingerprintIsStableAcrossInstancesAndReloadsAndChangesWithAnyField() {
        // The same definition built twice from new builder instances gives the same fingerprint; any field
        // change shows up there.
        String first = itemFingerprint("mymod:ruby", "b.setMaxStackSize(16); b.setRarity('rare');");
        String repeat = itemFingerprint("mymod:ruby", "b.setMaxStackSize(16); b.setRarity('rare');");
        assertEquals(first, repeat, "相同定义跨实例/跨轮次得到相同 fingerprint（不依赖对象身份）");

        assertNotEquals(first, itemFingerprint("mymod:ruby", "b.setMaxStackSize(32); b.setRarity('rare');"),
                "字段变化（maxStackSize）可识别");
        assertNotEquals(first, itemFingerprint("mymod:ruby", "b.setMaxStackSize(16); b.setRarity('epic');"),
                "字段变化（rarity）可识别");
        assertNotEquals(first, itemFingerprint("mymod:ruby",
                        "b.setMaxStackSize(16); b.setRarity('rare'); b.setMode('reloadable');"),
                "连带声明（mode）变化可识别");
        assertNotEquals(first, itemFingerprint("mymod:other", "b.setMaxStackSize(16); b.setRarity('rare');"),
                "id 参与指纹（不同 key 不碰撞）");
    }

    @Test
    void invalidValuesFailWithTheSameMemberNamedErrorFromBothWriteStyles() {
        String viaProperty = errorOf("b.maxStackSize = 200");
        String viaSetter = errorOf("b.setMaxStackSize(200)");
        assertTrue(viaProperty.contains("Invalid maxStackSize 200"), viaProperty);
        assertTrue(viaSetter.contains("Invalid maxStackSize 200"), viaSetter);

        assertEquals(viaProperty.contains("must be between 1 and 99"),
                viaSetter.contains("must be between 1 and 99"),
                "两种写法的错误面一致（同一 setter 抛出）");

        String rarityProperty = errorOf("b.rarity = 'legendary'");
        String raritySetter = errorOf("b.setRarity('legendary')");
        assertTrue(rarityProperty.contains("Unknown rarity 'legendary'"), rarityProperty);
        assertTrue(raritySetter.contains("Unknown rarity 'legendary'"), raritySetter);

        String categoryProperty = errorOf("m.category = 'scary'");
        String categorySetter = errorOf("m.setCategory('scary')");
        assertTrue(categoryProperty.contains("Unknown mob effect category 'scary'"), categoryProperty);
        assertTrue(categorySetter.contains("Unknown mob effect category 'scary'"), categorySetter);

        String globalMode = errorOf("b.setMode('global')");
        assertTrue(globalMode.contains("RegistryEvents"), "运行期 global 指回启动期入口: " + globalMode);
    }

    @Test
    void unknownMemberWritesAreRejectedWithTheMemberDirectory() {
        String error = errorOf("b.stackSize = 16");
        assertTrue(error.contains("has no member 'stackSize'"), error);
        assertTrue(error.contains("maxStackSize"), "错误信息带成员目录: " + error);

        String methodError = errorOf("b.notAMethod(1)");
        assertTrue(methodError.contains("Unknown identifier: notAMethod")
                        || methodError.contains("has no member 'notAMethod'"),
                "未知成员读取被拒绝: " + methodError);
    }

    @Test
    void propertyReadsReflectWritesAndFixedRangeNullIsSuppression() {
        try (Context context = Context.newBuilder("js").allowAllAccess(true).build()) {
            ScriptContextRegistry.bind(context, ScriptType.SERVER);
            try {
                DynamicSoundEventBuilder builder = new DynamicSoundEventBuilder();
                context.getBindings("js").putMember("s", DynamicBuilderSurface.of(builder));
                context.eval("js", """
                        s.fixedRange = 16;
                        if (s.fixedRange !== 16) throw new Error('read must reflect write: ' + s.fixedRange);
                        s.fixedRange = null;
                        """);
                assertNull(builder.getFixedRange(), "b.fixedRange = null 与从未写入是同一规范化状态（抑制）");

                // The explicit setter form takes the same path.
                context.eval("js", "s.setFixedRange(32)");
                assertEquals(32f, builder.getFixedRange());
            } finally {
                ScriptContextRegistry.unbind(context);
            }
        }
    }

    @Test
    void builderSurfaceDoesNotWrapStartupDrainBuilders() {
        // Structural guarantee: a dynamic builder does not implement the startup RegistryObjectBuilder, so it
        // never enters the drain.
        for (DynamicDefinitionBuilder builder : List.of(
                new DynamicItemBuilder(), new DynamicSoundEventBuilder(), new DynamicMobEffectBuilder())) {
            Class<?> superclass = builder.getClass().getSuperclass();
            while (superclass != null && superclass != Object.class) {
                assertTrue(!superclass.getName().contains("RegistryObjectBuilder"),
                        "dynamic builders must not extend the startup drain builders");
                superclass = superclass.getSuperclass();
            }
        }
    }
}
