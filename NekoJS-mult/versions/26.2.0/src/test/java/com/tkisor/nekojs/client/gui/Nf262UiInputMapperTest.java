package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiProps;
import com.tkisor.nekojs.api.ui.UiRect;
import com.tkisor.nekojs.api.ui.UiSize;
import com.tkisor.nekojs.api.ui.UiTextMeasure;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 26.2 UI Adapter 中<b>不依赖真实客户端</b>的那部分契约测试（jsx-ui-spec Testing Decisions「输入测试」
 * 「布局测试」）：焦点顺序、Tab/Shift+Tab、Enter/Space/Escape 激活、disabled/hover 命中、
 * 下标稳定性与事件对象字段（AC2/AC3/AC4）。
 *
 * <p>本测试<b>不是</b>真实客户端 smoke：它只覆盖 {@link Nf262UiInputMapper} 与 host node 的
 * 可观察事实。真实 Screen 打开/绘制/输入/关闭的 in-game 证据见票据 Closure record 的
 * MCP smoke 小节（AC8）。
 */
class Nf262UiInputMapperTest {

    // ---- 焦点顺序与下标稳定性 ----

    @Test
    void focusTraversalWrapsInBothDirections() {
        assertEquals(0, Nf262UiInputMapper.nextFocusIndex(-1, 3, false),
                "无焦点时 Tab 从第一个开始");
        assertEquals(2, Nf262UiInputMapper.nextFocusIndex(-1, 3, true),
                "无焦点时 Shift+Tab 从最后一个开始");
        assertEquals(1, Nf262UiInputMapper.nextFocusIndex(0, 3, false));
        assertEquals(2, Nf262UiInputMapper.nextFocusIndex(1, 3, false));
        assertEquals(0, Nf262UiInputMapper.nextFocusIndex(2, 3, false), "Tab 必须循环");
        assertEquals(2, Nf262UiInputMapper.nextFocusIndex(0, 3, true), "Shift+Tab 必须循环");
        assertEquals(-1, Nf262UiInputMapper.nextFocusIndex(0, 0, false), "空顺序不产生下标");
    }

    @Test
    void focusOrderIsDepthFirstAndStableAcrossRepeatedCollection() {
        Nf262UiHostAdapter adapter = new Nf262UiHostAdapter();
        var screen = adapter.create(new com.tkisor.nekojs.api.ui.UiHostCreate(
                UiPrimitive.SCREEN, null, UiProps.empty(), "screen"));
        var column = adapter.create(new com.tkisor.nekojs.api.ui.UiHostCreate(
                UiPrimitive.COLUMN, null, UiProps.empty(), "screen/column[0]"));
        var first = adapter.create(new com.tkisor.nekojs.api.ui.UiHostCreate(
                UiPrimitive.BUTTON, "a", props("text", "a"), "screen/column[0]/button"));
        var second = adapter.create(new com.tkisor.nekojs.api.ui.UiHostCreate(
                UiPrimitive.BUTTON, "b", props("text", "b"), "screen/column[0]/button"));
        adapter.reorder(screen, List.of(column));
        adapter.reorder(column, List.of(first, second));

        List<Nf262UiHostAdapter.Nf262UiNode> order = Nf262UiInputMapper.collectFocusOrder(screen);
        assertEquals(List.of("a", "b"), order.stream().map(n -> n.key()).toList(),
                "焦点顺序必须按已提交子节点顺序，且 keyed 身份稳定");
        assertEquals(order.stream().map(n -> n.key()).toList(),
                Nf262UiInputMapper.collectFocusOrder(screen).stream().map(n -> n.key()).toList(),
                "重复收集必须给出同一下标（下标稳定性）");
    }

    @Test
    void disabledAndInvisibleNodesAreExcludedFromFocusOrder() {
        Nf262UiHostAdapter adapter = new Nf262UiHostAdapter();
        var screen = adapter.create(create(UiPrimitive.SCREEN, "screen"));
        var enabled = adapter.create(create(UiPrimitive.BUTTON, "p/a"));
        var disabled = adapter.create(new com.tkisor.nekojs.api.ui.UiHostCreate(
                UiPrimitive.BUTTON, "b", props("disabled", Boolean.TRUE), "p/b"));
        var invisible = adapter.create(new com.tkisor.nekojs.api.ui.UiHostCreate(
                UiPrimitive.BUTTON, "c", props("visible", Boolean.FALSE), "p/c"));
        adapter.reorder(screen, List.of(enabled, disabled, invisible));

        assertEquals(List.of("p/a"),
                Nf262UiInputMapper.collectFocusOrder(screen).stream().map(n -> n.path()).toList(),
                "disabled 与 visible=false 的控件不进入 Tab 顺序");
    }

    // ---- Enter / Space / Escape / Tab ----

    @Test
    void enterAndSpaceActivateThroughStableEventTypes() {
        assertEquals(UiEventType.KEY_DOWN, Nf262UiInputMapper.activationType(GLFW.GLFW_KEY_ENTER));
        assertEquals(UiEventType.KEY_DOWN, Nf262UiInputMapper.activationType(GLFW.GLFW_KEY_KP_ENTER));
        assertEquals(UiEventType.CLICK, Nf262UiInputMapper.activationType(GLFW.GLFW_KEY_SPACE),
                "Space 以 click 语义激活（与 fake Adapter contract 一致）");
        assertTrue(Nf262UiInputMapper.activates(GLFW.GLFW_KEY_ENTER));
        assertTrue(Nf262UiInputMapper.activates(GLFW.GLFW_KEY_SPACE));
        assertFalse(Nf262UiInputMapper.activates(GLFW.GLFW_KEY_A));
    }

    @Test
    void escapeAndTabAreRecognised() {
        assertTrue(Nf262UiInputMapper.escapes(GLFW.GLFW_KEY_ESCAPE));
        assertFalse(Nf262UiInputMapper.escapes(GLFW.GLFW_KEY_E));
        assertTrue(Nf262UiInputMapper.tabs(GLFW.GLFW_KEY_TAB));
        assertFalse(Nf262UiInputMapper.tabs(GLFW.GLFW_KEY_ENTER));
    }

    // ---- 事件对象字段（AC2：稳定、无原生类型） ----

    @Test
    void mouseEventCarriesOnlyStableFields() {
        var event = Nf262UiInputMapper.mouse(UiEventType.CLICK, 12.5, 30.25, 0,
                GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL);
        assertEquals(UiEventType.CLICK, event.type());
        assertEquals(12.5, event.mouseX());
        assertEquals(30.25, event.mouseY());
        assertEquals(0, event.button());
        assertEquals(0, event.scrollDelta(), "非滚轮事件没有滚轮增量");
        assertEquals(-1, event.keyCode(), "非键盘事件键码为 -1");
        assertTrue(event.shift());
        assertTrue(event.control());
        assertFalse(event.alt());
        assertNull(event.key());
        assertNull(event.character());
    }

    @Test
    void scrollEventKeepsDeltaAndConsumesWheel() {
        var event = Nf262UiInputMapper.scroll(4, 8, 1.5);
        assertEquals(UiEventType.SCROLL, event.type());
        assertEquals(1.5, event.scrollDelta());
        assertEquals(-1, event.button(), "滚轮事件没有鼠标按键");
        assertTrue(event.hasPointer(), "滚轮事件携带指针位置");
    }

    @Test
    void keyEventCarriesStableKeyNamesNotVersionObjects() {
        for (int key : new int[]{GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_ESCAPE,
                GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN}) {
            String name = Nf262UiInputMapper.keyName(key);
            assertNotNull(name, "稳定键名必须可解析: " + key);
            assertFalse(name.startsWith("0x"), "不得回传版本特有的原始常量文本: " + name);
        }
        var event = Nf262UiInputMapper.key(UiEventType.KEY_DOWN, GLFW.GLFW_KEY_SPACE, false, false, false);
        assertEquals("Space", event.key());
        assertEquals(GLFW.GLFW_KEY_SPACE, event.keyCode());
        assertNull(Nf262UiInputMapper.key(UiEventType.KEY_DOWN, GLFW.GLFW_KEY_F13, false, false, false).key(),
                "未登记键名返回 null，而不是伪造一个版本特有名字");
    }

    @Test
    void characterEventCarriesTextOnly() {
        var event = Nf262UiInputMapper.character("好");
        assertEquals(UiEventType.CHAR, event.type());
        assertEquals("好", event.character());
        assertEquals(-1, event.keyCode());
        assertFalse(event.hasPointer());
    }

    // ---- 命中测试与 disabled ----

    @Test
    void hitTestPrefersDeepestInteractiveNode() {
        Nf262UiHostAdapter adapter = new Nf262UiHostAdapter();
        var screen = adapter.create(create(UiPrimitive.SCREEN, "screen"));
        var panel = adapter.create(create(UiPrimitive.PANEL, "screen/panel[0]"));
        var button = adapter.create(create(UiPrimitive.BUTTON, "screen/panel[0]/button[0]"));
        adapter.reorder(screen, List.of(panel));
        adapter.reorder(panel, List.of(button));
        adapter.arrange(screen, new UiRect(0, 0, 100, 100));
        adapter.arrange(panel, new UiRect(0, 0, 100, 100));
        adapter.arrange(button, new UiRect(10, 10, 40, 20));
        adapter.bindEvents(button, java.util.Set.of(UiEventType.CLICK));

        assertEquals(button, Nf262UiInputMapper.hitTest(screen, 20, 15));
        assertNull(Nf262UiInputMapper.hitTest(screen, 90, 90),
                "无指针事件的区域不产生命中");
    }

    @Test
    void disabledNodeDoesNotReceivePointerOrFocus() {
        Nf262UiHostAdapter adapter = new Nf262UiHostAdapter();
        var screen = adapter.create(create(UiPrimitive.SCREEN, "screen"));
        var button = adapter.create(new com.tkisor.nekojs.api.ui.UiHostCreate(
                UiPrimitive.BUTTON, "a", props("disabled", Boolean.TRUE), "p/a"));
        adapter.reorder(screen, List.of(button));
        adapter.arrange(screen, new UiRect(0, 0, 100, 100));
        adapter.arrange(button, new UiRect(0, 0, 50, 20));
        adapter.bindEvents(button, java.util.Set.of(UiEventType.CLICK, UiEventType.KEY_DOWN));

        assertFalse(Nf262UiInputMapper.canReceivePointer(button));
        assertNull(Nf262UiInputMapper.hitTest(screen, 10, 10), "disabled 节点不可命中");
        assertTrue(Nf262UiInputMapper.collectFocusOrder(screen).isEmpty(),
                "disabled 节点不进入焦点顺序");
    }

    // ---- hover / pressed 的 prop 词表 ----

    @Test
    void statePropsAreReadableFromCommittedProps() {
        UiProps state = props("hovered", Boolean.TRUE, "pressed", Boolean.FALSE,
                "disabled", Boolean.FALSE, "focused", Boolean.TRUE);
        assertTrue(state.bool(UiProp.HOVERED, false));
        assertFalse(state.bool(UiProp.PRESSED, true));
        assertFalse(state.bool(UiProp.DISABLED, true));
        assertTrue(state.bool(UiProp.FOCUSED, false));
    }

    // ---- 文本与 narration ----

    @Test
    void inputDisplayTextPrefersValueThenPlaceholder() {
        assertEquals("hi", Nf262UiInputMapper.displayText(
                props("value", "hi", "placeholder", "type here"), UiPrimitive.INPUT));
        assertEquals("type here", Nf262UiInputMapper.displayText(
                props("placeholder", "type here"), UiPrimitive.INPUT));
        assertEquals("", Nf262UiInputMapper.displayText(UiProps.empty(), UiPrimitive.INPUT));
    }

    @Test
    void narrationPrefersNarrationThenTextAndSkipsHiddenNodes() {
        Nf262UiHostAdapter adapter = new Nf262UiHostAdapter();
        var screen = adapter.create(new com.tkisor.nekojs.api.ui.UiHostCreate(
                UiPrimitive.SCREEN, null, props("title", "Demo"), "screen"));
        var label = adapter.create(new com.tkisor.nekojs.api.ui.UiHostCreate(
                UiPrimitive.LABEL, null, props("text", "Hello"), "screen/label[0]"));
        var hidden = adapter.create(new com.tkisor.nekojs.api.ui.UiHostCreate(
                UiPrimitive.LABEL, null, props("text", "Hidden", "visible", Boolean.FALSE), "screen/label[1]"));
        var narrated = adapter.create(new com.tkisor.nekojs.api.ui.UiHostCreate(
                UiPrimitive.BUTTON, null, props("text", "OK", "narration", "Confirm"), "screen/button[0]"));
        adapter.reorder(screen, List.of(label, hidden, narrated));

        String narration = Nf262UiInputMapper.narration(screen);
        assertTrue(narration.contains("Hello"), narration);
        assertTrue(narration.contains("Confirm"), "narration 优先于 text: " + narration);
        assertFalse(narration.contains("Hidden"), "visible=false 的节点不参与 narration: " + narration);
    }

    // ---- 颜色解析（视觉属性） ----

    @Test
    void colorsAcceptHexFormsAndFallBackOnGarbage() {
        assertEquals(0xFFFF0000, Nf262UiScreen.color("#FF0000", -1));
        assertEquals(0x80FF0000, Nf262UiScreen.color("#80FF0000", -1));
        assertEquals(0xFFFF0000, Nf262UiScreen.color("FFFF0000", -1));
        assertEquals(-1, Nf262UiScreen.color("not-a-color", -1));
        assertEquals(-1, Nf262UiScreen.color(null, -1));
        assertEquals(-1, Nf262UiScreen.color("#12345", -1), "位数非法的颜色回退，不静默截断");
    }

    // ---- 文本测量 ----

    @Test
    void textMeasurerWithoutClientReturnsZeroInsteadOfThrowing() {
        var measurer = new Nf262UiTextMeasurer(null);
        assertEquals(UiSize.zero(), measurer.measure(new UiTextMeasure("hello", null, 8, false, 100)));
    }

    // ---- helpers ----

    private static com.tkisor.nekojs.api.ui.UiHostCreate create(UiPrimitive primitive, String path) {
        return new com.tkisor.nekojs.api.ui.UiHostCreate(primitive, null, UiProps.empty(), path);
    }

    private static UiProps props(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return UiProps.of(map);
    }
}
