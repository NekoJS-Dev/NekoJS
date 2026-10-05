//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ticket41HiddenContainerIntegrationTest {
    @Test
    void canonicalHiddenContainerPublishesWithoutDroppingRetainedChildrenAndTheirCapture() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.installRuntime();
            fixture.context.eval("js", """
                    globalThis.visible = UI.createSignal(true);
                    globalThis.root = UI.createRoot(() => UI.element('column', {
                      id: 'parent', visible: visible.get(), width: 80, height: 24,
                      children: UI.element('input', { id: 'input', value: 'Neko', width: 70, height: 24 })
                    }), host, { id: 'hidden-root', viewport: { width: 100, height: 100 } });
                    host.bindRoot(root);
                    """);
            assertTrue(fixture.screen.mouseClicked(new MouseButtonEvent(5, 5, new MouseButtonInfo(0, 0)), false));
            fixture.context.eval("js", "visible.set(false)");
            assertTrue(fixture.paint().isEmpty());
            var hidden = fixture.adapter.inspect().nodes().getFirst();
            assertFalse(hidden.visible());
            assertEquals(1, hidden.children().size());
            assertFalse(hidden.children().getFirst().visible());
            assertFalse(fixture.screen.charTyped(new CharacterEvent('X')));
            fixture.context.eval("js", "visible.set(true)");
            assertFalse(fixture.screen.mouseDragged(new MouseButtonEvent(90, 90, new MouseButtonInfo(0, 0)), 85, 85));
            assertFalse(fixture.screen.mouseReleased(new MouseButtonEvent(90, 90, new MouseButtonInfo(0, 0))));
            assertTrue(fixture.screen.mouseClicked(new MouseButtonEvent(5, 5, new MouseButtonInfo(0, 0)), false));
            assertTrue(fixture.screen.charTyped(new CharacterEvent('X')));
            assertEquals("XNeko", fixture.paint().getFirst().text());
        }
    }
}
//?}
