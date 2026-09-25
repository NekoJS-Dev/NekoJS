// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
//? if >=26 {
package com.tkisor.nekojs.bindings.event.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tkisor.nekojs.api.ScriptType;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Pure-logic coverage for {@link KeyBindEvents}: group/bus wiring, id/key/category parsing
 * and idempotent binding creation. Everything here runs in bare JUnit — {@code KeyBindEvents}
 * class init subscribes to the NeoForge buses defensively, and vanilla client classes
 * ({@code KeyMapping}/{@code InputConstants}) initialize without FML when their usual
 * compile-scope libraries are present; {@link Assumptions} skips the vanilla-dependent cases
 * when they are not.
 */
class KeyBindEventsTest {

    /** Probes whether the vanilla client key classes initialize in this JVM. */
    private static boolean vanillaKeyClassesAvailable() {
        try {
            return KeyMapping.Category.MISC != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * KeyBindEvents 的静态初始化会注册事件组/探测可取消性，需要 FML 运行时；裸 JUnit 跳过。
     * 探测前必须先立好 Platform 桩：ScriptType 的静态初始化要 NekoJSPaths.get()，先于
     * Platform.init 触碰会让 ScriptType 在本 JVM 里永久损坏（NoClassDefFoundError 连坐）。
     */
    @org.junit.jupiter.api.BeforeAll
    static void initPlatformStub() {
        try {
            com.tkisor.nekojs.platform.Platform.init(new com.tkisor.nekojs.platform.IPlatform() {
                @Override
                public boolean isClient() {
                    return false;
                }

                @Override
                public boolean isDevelopment() {
                    return true;
                }

                @Override
                public String getMcVersion() {
                    return "test";
                }

                @Override
                public java.nio.file.Path getGameDir() {
                    return com.tkisor.nekojs.TestGameDirs.unique("nekojs-kb-test");
                }

                @Override
                public String getLoaderId() {
                    return "test";
                }

                @Override
                public String getLoaderVersion() {
                    return "0";
                }

                @Override
                public java.util.Map<String, com.tkisor.nekojs.platform.IModInfo> getMods() {
                    return java.util.Map.of();
                }

                @Override
                public com.tkisor.nekojs.platform.IModInfo getInfo(String modID) {
                    return null;
                }
            });
        } catch (IllegalStateException alreadyInitialized) {
            // 同 JVM 内其它测试已初始化平台时复用现有实例
        }
    }

    private static boolean groupInitializable() {
        try {
            Class.forName("com.tkisor.nekojs.bindings.event.client.KeyBindEvents", true,
                    KeyBindEventsTest.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    void busesAreRegisteredUnderExpectedNames() {
        org.junit.jupiter.api.Assumptions.assumeTrue(groupInitializable(),
                "KeyBindEvents group registration needs an FML runtime");

        assertEquals("KeyBindEvents", KeyBindEvents.GROUP.name());
        for (String busName : new String[] {"pressed", "released", "tick", "register"}) {
            var holder = KeyBindEvents.GROUP.getBusHolder(busName);
            assertNotNull(holder, "missing bus '" + busName + "'");
            assertNotNull(holder.getBus(ScriptType.CLIENT), "bus '" + busName + "' must be client-visible");
        }
    }

    @Test
    void identifierParsingDefaultsToNekojsNamespace() {
        assumeTrue(vanillaKeyClassesAvailable(), "Identifier unavailable in bare JUnit");

        Identifier bare = KeyBindIds.parseIdentifier("my_key");
        assertEquals("nekojs", bare.getNamespace());
        assertEquals("my_key", bare.getPath());

        Identifier namespaced = KeyBindIds.parseIdentifier("mymod:my_key");
        assertEquals("mymod", namespaced.getNamespace());
        assertEquals("my_key", namespaced.getPath());
    }

    @Test
    void invalidIdentifiersFailWithActionableError() {
        assumeTrue(vanillaKeyClassesAvailable(), "Identifier unavailable in bare JUnit");

        assertEquals("invalid id 'bad id!': expected 'namespace:path' or 'path'",
                assertThrows(IllegalArgumentException.class, () -> KeyBindIds.parseIdentifier("bad id!"))
                        .getMessage());
        assertEquals("invalid id 'bad:id!': expected 'namespace:path' or 'path'",
                assertThrows(IllegalArgumentException.class, () -> KeyBindIds.parseIdentifier("bad:id!"))
                        .getMessage());
    }

    @Test
    void bindingNameDerivesTranslationKey() {
        assumeTrue(vanillaKeyClassesAvailable(), "Identifier unavailable in bare JUnit");

        assertEquals("key.mymod.my_key",
                KeyBindIds.bindingName(KeyBindIds.parseIdentifier("mymod:my_key")));
        assertEquals("key.nekojs.my_key",
                KeyBindIds.bindingName(KeyBindIds.parseIdentifier("my_key")));
    }

    @Test
    void keyParsingAcceptsVanillaNamesAndNull() {
        assumeTrue(vanillaKeyClassesAvailable(), "InputConstants unavailable in bare JUnit");

        InputConstants.Key g = KeyBindIds.parseKey("key.keyboard.g");
        assertEquals(InputConstants.Type.KEYSYM, g.getType());
        assertEquals(71, g.getValue());

        InputConstants.Key mouse = KeyBindIds.parseKey("key.mouse.left");
        assertEquals(InputConstants.Type.MOUSE, mouse.getType());
        assertEquals(0, mouse.getValue());

        assertSame(InputConstants.UNKNOWN, KeyBindIds.parseKey(null));
        assertSame(InputConstants.UNKNOWN, KeyBindIds.parseKey("  "));

        assertEquals("unknown key 'g': expected a name like 'key.keyboard.g' or 'key.mouse.left'",
                assertThrows(IllegalArgumentException.class, () -> KeyBindIds.parseKey("g")).getMessage());
    }

    @Test
    void categoryResolutionMapsVanillaAndCustomSpellings() {
        assumeTrue(vanillaKeyClassesAvailable(), "KeyMapping.Category unavailable in bare JUnit");

        assertSame(KeyMapping.Category.MISC, KeyBindIds.resolveCategory(null));
        assertSame(KeyMapping.Category.MISC, KeyBindIds.resolveCategory("  "));
        assertSame(KeyMapping.Category.MOVEMENT, KeyBindIds.resolveCategory("key.categories.movement"));
        assertSame(KeyMapping.Category.MOVEMENT, KeyBindIds.resolveCategory("movement"));
        // legacy 1.21 spelling; 26.x has no UI category — degrades to misc
        assertSame(KeyMapping.Category.MISC, KeyBindIds.resolveCategory("key.categories.ui"));

        KeyMapping.Category custom = KeyBindIds.resolveCategory("mymod:my_keys");
        assertEquals(Identifier.fromNamespaceAndPath("mymod", "my_keys"), custom.id());
        // single cached instance per id so the controls screen groups headers correctly
        assertSame(custom, KeyBindIds.resolveCategory("mymod:my_keys"));
    }

    @Test
    void registerBindingIsIdempotentAndNamesTheMapping() {
        org.junit.jupiter.api.Assumptions.assumeTrue(groupInitializable(),
                "KeyBindEvents group registration needs an FML runtime");

        assumeTrue(vanillaKeyClassesAvailable(), "KeyMapping unavailable in bare JUnit");

        Identifier id = KeyBindIds.parseIdentifier("mymod:kbtest_once");
        KeyMapping first = KeyBindEvents.registerBinding(
                id, KeyBindIds.parseKey("key.keyboard.h"), KeyMapping.Category.GAMEPLAY);
        KeyMapping second = KeyBindEvents.registerBinding(
                id, KeyBindIds.parseKey("key.keyboard.j"), KeyMapping.Category.MISC);

        assertSame(first, second, "re-registering an id must return the existing mapping");
        assertEquals("key.mymod.kbtest_once", first.getName());
        assertEquals("key.keyboard.h", first.saveString());
        assertEquals(false, first.isDown());
    }

    @Test
    void eventObjectExposesDocumentedGetters() {
        KeyBindEvents.KeyBindEventJS pressed = new KeyBindEvents.KeyBindEventJS("mymod:my_key", null, true);
        assertEquals("mymod:my_key", pressed.getId());
        assertEquals(true, pressed.isDown());

        KeyBindEvents.KeyBindEventJS released = new KeyBindEvents.KeyBindEventJS("mymod:my_key", null, false);
        assertEquals(false, released.isDown());
        assertTrue(pressed.getKeyMapping() == null, "getter must pass the mapping through");
    }

    /**
     * Ticket 26 AC5: held state is observable through the public handle. The event object
     * passes the registered {@link KeyMapping} through, and its public {@code isDown()} reflects
     * the vanilla public mutator {@code setDown(boolean)}. Uses only public methods; reads no
     * private binding table and asserts no callback identity.
     */
    @Test
    void keyHandleHeldStateIsObservableThroughPublicMethods() {
        org.junit.jupiter.api.Assumptions.assumeTrue(groupInitializable(),
                "KeyBindEvents group registration needs an FML runtime");

        assumeTrue(vanillaKeyClassesAvailable(), "KeyMapping unavailable in bare JUnit");

        Identifier id = KeyBindIds.parseIdentifier("mymod:kbtest_handle_state");
        KeyMapping mapping = KeyBindEvents.registerBinding(
                id, KeyBindIds.parseKey("key.keyboard.o"), KeyMapping.Category.MISC);
        try {
            assertEquals(false, mapping.isDown());

            mapping.setDown(true);
            KeyBindEvents.KeyBindEventJS event =
                    new KeyBindEvents.KeyBindEventJS(id.toString(), mapping, mapping.isDown());
            assertSame(mapping, event.getKeyMapping(), "the event must pass the handle through");
            assertEquals(true, event.isDown());
        } finally {
            mapping.setDown(false);
        }
        assertEquals(false, mapping.isDown());
    }

    /**
     * Ticket 26 AC5: click consumption through the public handle. A fresh headless handle has no
     * click source so {@code consumeClick()} is false. The vanilla public static click source
     * {@code KeyMapping.click(Key)} cannot supply one here: it dereferences
     * {@code Minecraft.getInstance()} (live client only) and throws NullPointerException in a
     * headless JVM (observed 2026-09-25, kept as red evidence in the follow-up record). So a real
     * key press driving click counts still needs a live client (ticket 34 smoke). Uses only
     * public methods; reads no private binding table and asserts no callback identity.
     */
    @Test
    void consumeClickWithNoClickSourceConsumesNothing() {
        org.junit.jupiter.api.Assumptions.assumeTrue(groupInitializable(),
                "KeyBindEvents group registration needs an FML runtime");

        assumeTrue(vanillaKeyClassesAvailable(), "KeyMapping unavailable in bare JUnit");

        Identifier id = KeyBindIds.parseIdentifier("mymod:kbtest_click_source");
        KeyMapping mapping = KeyBindEvents.registerBinding(
                id, KeyBindIds.parseKey("key.keyboard.p"), KeyMapping.Category.MISC);

        assertEquals(false, mapping.consumeClick(),
                "no click source in a headless JVM, so a fresh handle consumes nothing");
    }
}
//?}
//?}
