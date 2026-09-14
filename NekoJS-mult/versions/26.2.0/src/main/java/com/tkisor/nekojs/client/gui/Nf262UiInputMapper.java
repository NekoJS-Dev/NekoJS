//? if neoforge {
package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.api.ui.UiEvent;
import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiProps;

import org.lwjgl.glfw.GLFW;

/**
 * 原生输入 → 稳定脚本事件 + 焦点顺序的纯映射层（jsx-ui-spec「输入与焦点」）。
 *
 * <p>本类<b>不含任何 Minecraft 类型</b>：它只消费基本量（坐标、按键码、修饰键）与
 * {@link UiProps}/{@link Nf262UiHostAdapter.Nf262UiNode}，因此焦点顺序、Tab/Shift+Tab、
 * Enter/Space/Escape、disabled、hover 与事件对象字段可以在脱离真实客户端的单测里被钉住。
 * 原生 {@code KeyEvent}/{@code MouseButtonEvent}/{@code CharacterEvent} 的拆包留在
 * {@link Nf262UiScreen} 里，是唯一持有原生输入类型的地方（AC2）。
 *
 * <p>本类不持有状态：全部方法都是静态纯函数，避免在 host 层引入第二个可观察状态源。
 */
final class Nf262UiInputMapper {

    private Nf262UiInputMapper() {
    }

    // ---- 焦点顺序 ----

    /** 收集可聚焦节点：已提交树、visible、非 disabled，且是 button/input 或绑定了键盘/焦点事件。 */
    static java.util.List<Nf262UiHostAdapter.Nf262UiNode> collectFocusOrder(
            com.tkisor.nekojs.api.ui.UiHostNode root) {
        java.util.List<Nf262UiHostAdapter.Nf262UiNode> out = new java.util.ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(com.tkisor.nekojs.api.ui.UiHostNode node,
                                java.util.List<Nf262UiHostAdapter.Nf262UiNode> out) {
        if (node.disposed() || !node.props().bool(UiProp.VISIBLE, true)
                || node.props().bool(UiProp.DISABLED, false)) {
            return;
        }
        if (node instanceof Nf262UiHostAdapter.Nf262UiNode nf && isFocusable(nf)) {
            out.add(nf);
        }
        for (com.tkisor.nekojs.api.ui.UiHostNode child : node.children()) {
            collect(child, out);
        }
    }

    /** button/input 天然可聚焦；其余节点只有声明了键盘/焦点事件才进入 Tab 顺序。 */
    static boolean isFocusable(Nf262UiHostAdapter.Nf262UiNode node) {
        var primitive = node.primitive();
        if (primitive == com.tkisor.nekojs.api.ui.UiPrimitive.BUTTON
                || primitive == com.tkisor.nekojs.api.ui.UiPrimitive.INPUT) {
            return true;
        }
        return node.boundEvents().contains(UiEventType.KEY_DOWN)
                || node.boundEvents().contains(UiEventType.FOCUS);
    }

    /** Tab / Shift+Tab 的下一个焦点下标（确定性遍历，循环）。 */
    static int nextFocusIndex(int currentIndex, int size, boolean backwards) {
        if (size <= 0) {
            return -1;
        }
        if (currentIndex < 0) {
            return backwards ? size - 1 : 0;
        }
        return Math.floorMod(currentIndex + (backwards ? -1 : 1), size);
    }

    // ---- 事件构造 ----

    /** 鼠标点击（down/click/up 共用坐标与修饰键）。 */
    static UiEvent mouse(UiEventType type, double x, double y, int button, int modifiers) {
        return new UiEvent(type, x, y, button, 0, -1, null, null,
                shift(modifiers), control(modifiers), alt(modifiers), null, null, -1, 0);
    }

    /** 滚轮：{@code scrollY} 正负号保持原生方向，{@code scrollX} 不回传（稳定事件只有一维增量）。 */
    static UiEvent scroll(double x, double y, double scrollY) {
        return new UiEvent(UiEventType.SCROLL, x, y, -1, scrollY, -1, null, null,
                false, false, false, null, null, -1, 0);
    }

    /** 键盘事件（key 名由原生键码解析；修饰键原样带入）。 */
    static UiEvent key(UiEventType type, int keyCode, boolean shift, boolean control, boolean alt) {
        return UiEvent.key(type, keyCode, keyName(keyCode), shift, control, alt);
    }

    /** 文本输入：只带字符，不带键码/坐标。 */
    static UiEvent character(String text) {
        return UiEvent.character(text);
    }

    /** Enter/Space 激活：Enter 走 keyDown+submit，Space 走 click（与 fake contract 一致）。 */
    static UiEventType activationType(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_SPACE ? UiEventType.CLICK : UiEventType.KEY_DOWN;
    }

    /** Escape 是否应关闭 Screen（Screen 选项优先，契约默认 true）。 */
    static boolean escapes(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ESCAPE;
    }

    static boolean tabs(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_TAB;
    }

    static boolean activates(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE;
    }

    /** 稳定键名（不暴露版本特有的 InputConstants/GLFW 常量对象）。 */
    static String keyName(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> "Enter";
            case GLFW.GLFW_KEY_ESCAPE -> "Escape";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_UP -> "ArrowUp";
            case GLFW.GLFW_KEY_DOWN -> "ArrowDown";
            case GLFW.GLFW_KEY_LEFT -> "ArrowLeft";
            case GLFW.GLFW_KEY_RIGHT -> "ArrowRight";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_HOME -> "Home";
            case GLFW.GLFW_KEY_END -> "End";
            default -> null;
        };
    }

    static boolean shift(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    static boolean control(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    static boolean alt(int modifiers) {
        return (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }

    // ---- 命中判定 ----

    /** 节点是否参与指针命中：visible、非 disabled，且绑定了指针事件。 */
    static boolean canReceivePointer(com.tkisor.nekojs.api.ui.UiHostNode node) {
        if (node.disposed() || !node.props().bool(UiProp.VISIBLE, true)
                || node.props().bool(UiProp.DISABLED, false)) {
            return false;
        }
        var events = node.boundEvents();
        return events.contains(UiEventType.CLICK)
                || events.contains(UiEventType.MOUSE_DOWN)
                || events.contains(UiEventType.MOUSE_UP)
                || events.contains(UiEventType.MOUSE_MOVE)
                || events.contains(UiEventType.SCROLL);
    }

    /** 从根开始在已提交树里做后向深度优先命中（最上层、最深的可交互节点优先）。 */
    static Nf262UiHostAdapter.Nf262UiNode hitTest(com.tkisor.nekojs.api.ui.UiHostNode root,
                                                  double x, double y) {
        if (root == null || root.disposed() || !root.layout().contains(x, y)) {
            return null;
        }
        var children = root.children();
        for (int i = children.size() - 1; i >= 0; i--) {
            var found = hitTest(children.get(i), x, y);
            if (found != null) {
                return found;
            }
        }
        if (root instanceof Nf262UiHostAdapter.Nf262UiNode nf
                && (canReceivePointer(nf)
                    || nf.primitive() == com.tkisor.nekojs.api.ui.UiPrimitive.INPUT)) {
            return nf;
        }
        return null;
    }

    // ---- 文本与可访问性 ----

    /** label/button 的文本，或 input 的 value/placeholder（narration 与绘制共用）。 */
    static String displayText(UiProps props, com.tkisor.nekojs.api.ui.UiPrimitive primitive) {
        if (primitive == com.tkisor.nekojs.api.ui.UiPrimitive.INPUT) {
            String value = props.string(UiProp.VALUE, "");
            if (!value.isEmpty()) {
                return value;
            }
            String placeholder = props.string(UiProp.PLACEHOLDER);
            return placeholder == null ? "" : placeholder;
        }
        return props.string(UiProp.TEXT, "");
    }

    /** 基础 narration 文本：优先 narration，其次 text，递归拼接子节点。 */
    static String narration(com.tkisor.nekojs.api.ui.UiHostNode node) {
        StringBuilder sb = new StringBuilder();
        collectNarration(node, sb);
        return sb.toString().trim();
    }

    private static void collectNarration(com.tkisor.nekojs.api.ui.UiHostNode node, StringBuilder out) {
        if (node.disposed() || !node.props().bool(UiProp.VISIBLE, true)) {
            return;
        }
        String narration = node.props().string(UiProp.NARRATION);
        if (narration == null) {
            narration = node.props().string(UiProp.TEXT);
        }
        if (narration != null && !narration.isBlank()) {
            out.append(narration).append(' ');
        }
        for (com.tkisor.nekojs.api.ui.UiHostNode child : node.children()) {
            collectNarration(child, out);
        }
    }
}
//?}
