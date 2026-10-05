//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ticket41NativeAccessibilityTest {
    @Test
    void nativeNarrationCollectorSpeaksFocusedTextAndSkipsTheHiddenContainer() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.visible = UI.createSignal(true);
                    globalThis.root = UI.createRoot(() => UI.element('column', {
                      id: 'controls', width: 100, height: 80, children: [
                        UI.element('button', { id: 'button', text: 'Healthy button', width: 80, height: 22 }),
                        UI.element('column', { id: 'editor-parent', visible: visible.get(), width: 90, height: 24,
                          children: UI.element('input', { id: 'editor', value: 'Narrated input', width: 80, height: 24 }) })
                      ]
                    }), host, { id: 'narration-root', viewport: { width: 100, height: 100 } });
                    host.bindRoot(root);
                    """);
            fixture.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0));
            assertTrue(fixture.narrate().stream().anyMatch(message -> message.contains("Healthy button")));
            fixture.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0));
            assertTrue(fixture.narrate().stream().anyMatch(message -> message.contains("Narrated input")));
            fixture.context.eval("js", "visible.set(false)");
            assertFalse(fixture.narrate().stream().anyMatch(message -> message.contains("Narrated input")));
            fixture.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_TAB, 0, 0));
            assertTrue(fixture.narrate().stream().anyMatch(message -> message.contains("Healthy button")));
        }
    }

    @Test
    void nativeTooltipRespectsHoverClipDisabledAndHiddenAncestor() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.visible = UI.createSignal(true);
                    globalThis.root = UI.createRoot(() => UI.element('column', {
                      id: 'controls', visible: visible.get(), width: 80, height: 22,
                      children: UI.element('button', { id: 'button', text: 'Disabled', disabled: true,
                        tooltip: 'Disabled tooltip', width: 100, height: 22 })
                    }), host, { id: 'tooltip-root', viewport: { width: 100, height: 100 } });
                    host.bindRoot(root);
                    """);
            assertEquals("Disabled tooltip", fixture.tooltipAt(10, 10));
            assertNull(fixture.tooltipAt(90, 10));
            assertNull(fixture.tooltipAt(10, 30));
            fixture.context.eval("js", "visible.set(false)");
            assertNull(fixture.tooltipAt(10, 10));
        }
    }
}
//?}
