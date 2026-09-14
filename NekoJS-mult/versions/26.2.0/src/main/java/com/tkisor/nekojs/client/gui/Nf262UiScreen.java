//? if neoforge {
package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.api.ui.UiEvent;
import com.tkisor.nekojs.api.ui.UiEventType;
import com.tkisor.nekojs.api.ui.UiHostNode;
import com.tkisor.nekojs.api.ui.UiPrimitive;
import com.tkisor.nekojs.api.ui.UiProp;
import com.tkisor.nekojs.api.ui.UiRect;
import com.tkisor.nekojs.api.ui.UiRootHandle;
import com.tkisor.nekojs.api.ui.UiRootStatus;
import com.tkisor.nekojs.api.ui.UiScreenOptions;
import com.tkisor.nekojs.api.ui.UiSize;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * JSX UI root 在 NeoForge 26.2 的原生 Screen 投影（jsx-ui-spec「Screen 行为」「暂停与替换」）。
 *
 * <p><b>AC5 的核心约束</b>：普通绘制帧只 paint 已提交 host tree，不重新执行 GraalJS render。
 * {@link #extractRenderState} 只读 {@code root.inspection()} 的已提交节点与它们最后一次
 * arrange 的矩形；render function 只在首次 open、resize、signal 失效与事件回调后的
 * reconcile 中执行一次（jsx-ui-spec「布局」「性能边界」）。
 *
 * <p>本类不与 HUD / world render 合并（jsx-ui-spec「Screen 行为」）：它是独立
 * {@code Screen}，不订阅 {@code ClientEvents.hud} 或世界渲染路径。
 *
 * <p>原生类型只在本类内出现：{@code MouseButtonEvent} / {@code KeyEvent} /
 * {@code CharacterEvent} / {@code GuiGraphicsExtractor} 全部在此拆包为基本量后交给
 * {@link Nf262UiInputMapper}；脚本只看到 {@link UiEvent}（AC2）。
 *
 * <p>生命周期：玩家关闭、外部 {@code setScreen} 替换、客户端退出与 reload 释放都进入
 * {@link #closeFromScreen()} 这一条幂等路径。
 */
public final class Nf262UiScreen extends Screen {

    private final UiRootHandle root;
    private final Nf262UiHostAdapter adapter;
    private final UiScreenOptions options;

    /** 共享的原生文本输入框：光标/选择/最大长度复用版本原生编辑能力（规格「输入与焦点」）。 */
    private EditBox inputBox;
    private Nf262UiHostAdapter.Nf262UiNode focusedInput;
    private Nf262UiHostAdapter.Nf262UiNode hovered;
    private Nf262UiHostAdapter.Nf262UiNode pressed;
    private boolean closing;

    public Nf262UiScreen(UiRootHandle root, Nf262UiHostAdapter adapter, UiScreenOptions options) {
        super(Component.literal(options == null || options.title() == null || options.title().isEmpty()
                ? "NekoJS UI" : options.title()));
        this.root = root;
        this.adapter = adapter;
        this.options = options == null ? UiScreenOptions.defaults() : options;
    }

    /** Screen 选项（title / pauseGame / closeOnEscape / closeOnExternal）。 */
    public UiScreenOptions uiOptions() {
        return options;
    }

    @Override
    public boolean isPauseScreen() {
        return options.pauseGame();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return options.closeOnEscape();
    }

    // ---- 生命周期 ----

    @Override
    protected void init() {
        rebuildInputBox();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        // resize 只让布局失效并重算（jsx-ui-spec「布局」）：普通绘制帧不重新执行 render。
        if (root instanceof com.tkisor.nekojs.core.ui.UiRoot impl) {
            impl.resizeViewport(new UiSize(width, height));
        }
        rebuildInputBox();
    }

    @Override
    public void onClose() {
        closeFromScreen();
    }

    @Override
    public void removed() {
        closeFromScreen();
        super.removed();
    }

    /**
     * 关闭 root 与其原生投影；幂等。
     *
     * <p>玩家主动关闭（Escape / F6 类关闭）、外部 {@code setScreen} 替换与 reload 释放
     * 都调用本方法：同一条路径释放 host node、事件闭包与 Screen handle（jsx-ui-spec
     * 「暂停与替换」/ AC7）。
     */
    public void closeFromScreen() {
        if (closing) {
            return;
        }
        closing = true;
        try {
            if (root.isActive()) {
                root.dispatchEvent(UiEvent.plain(UiEventType.CLOSE));
                root.dispose();
            }
        } finally {
            releaseNativeState();
        }
    }

    private void releaseNativeState() {
        inputBox = null;
        focusedInput = null;
        pressed = null;
        hovered = null;
        adapter.clear();
    }

    // ---- 绘制：只 paint 已提交 host tree（不跑脚本） ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (root.status() == UiRootStatus.DISPOSED) {
            return;
        }
        // AC5：paint() 只遍历已提交 host tree 并记录绘制顺序/裁剪，不执行 render function。
        var inspection = root.paint();
        if (!inspection.hasCommittedTree()) {
            return;
        }
        List<String> order = inspection.paintOperations();
        for (String path : order) {
            UiHostNode node = findByPath(inspection.committedRoot(), path);
            if (node == null || node.disposed()) {
                continue;
            }
            paintNode(graphics, node);
        }
        if (focusedInput != null && !focusedInput.disposed() && inputBox != null) {
            UiRect rect = focusedInput.layout();
            graphics.enableScissor((int) rect.x(), (int) rect.y(),
                    (int) (rect.x() + rect.width()), (int) (rect.y() + rect.height()));
            inputBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
            graphics.disableScissor();
        }
    }

    private void paintNode(GuiGraphicsExtractor graphics, UiHostNode node) {
        UiRect rect = node.layout();
        int x = (int) Math.round(rect.x());
        int y = (int) Math.round(rect.y());
        int w = (int) Math.round(rect.width());
        int h = (int) Math.round(rect.height());
        if (w <= 0 || h <= 0) {
            return;
        }
        int background = color(node.props().string(UiProp.BACKGROUND), 0x00000000);
        switch (node.primitive()) {
            case PANEL, SCROLL, STACK, ROW, COLUMN, SPACER, SCREEN -> fillIfVisible(graphics, x, y, w, h, background);
            case LABEL -> {
                String text = node.props().string(UiProp.TEXT, "");
                if (!text.isEmpty()) {
                    graphics.text(font, text, x, y, color(node.props().string(UiProp.COLOR), 0xFFE0E0E0), false);
                }
            }
            case BUTTON -> paintButton(graphics, node, x, y, w, h);
            case INPUT -> paintInput(graphics, node, x, y, w, h);
            default -> {
                // IMAGE 未接线：不会出现在提交树里（adapter capability 校验先行）。
            }
        }
    }

    private void paintButton(GuiGraphicsExtractor graphics, UiHostNode node, int x, int y, int w, int h) {
        boolean disabled = node.props().bool(UiProp.DISABLED, false);
        boolean focused = node == focusedInput || node.path().equals(root.focusedPath());
        int fill = disabled ? 0xFF3A3A3A : node == hovered ? 0xFF5A6B8C : 0xFF3F4A63;
        fillIfVisible(graphics, x, y, w, h, fill);
        if (focused) {
            graphics.outline(x, y, w, h, 0xFFFFFFFF);
        }
        String text = node.props().string(UiProp.TEXT, "");
        if (!text.isEmpty()) {
            graphics.centeredText(font, text, x + w / 2, y + (h - font.lineHeight) / 2,
                    disabled ? 0xFF8A8A8A : 0xFFFFFFFF);
        }
    }

    private void paintInput(GuiGraphicsExtractor graphics, UiHostNode node, int x, int y, int w, int h) {
        boolean disabled = node.props().bool(UiProp.DISABLED, false);
        fillIfVisible(graphics, x, y, w, h, disabled ? 0xFF202020 : 0xFF101010);
        if (node.path().equals(root.focusedPath())) {
            graphics.outline(x, y, w, h, 0xFFFFFFFF);
        }
        String shown = Nf262UiInputMapper.displayText(node.props(), node.primitive());
        if (!shown.isEmpty()) {
            String value = node.props().string(UiProp.VALUE, "");
            int textColor = value.isEmpty() ? 0xFF808080 : color(node.props().string(UiProp.COLOR), 0xFFFFFFFF);
            graphics.text(font, shown, x + 4, y + (h - font.lineHeight) / 2, textColor, false);
        }
    }

    private static void fillIfVisible(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        if ((color >>> 24) != 0) {
            graphics.fill(x, y, x + w, y + h, color);
        }
    }

    /** {@code #RRGGBB} / {@code #AARRGGBB} / 16 进制整数；非法值回退。 */
    static int color(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String text = raw.trim();
        try {
            if (text.startsWith("#")) {
                String hex = text.substring(1);
                long value = Long.parseLong(hex, 16);
                if (hex.length() == 6) {
                    return (int) (0xFF000000L | value);
                }
                if (hex.length() == 8) {
                    return (int) value;
                }
                return fallback;
            }
            return (int) Long.parseLong(text, 16);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    // ---- 输入：原生 → 稳定脚本事件 ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Nf262UiHostAdapter.Nf262UiNode target = hitTest(event.x(), event.y());
        if (target == null) {
            return super.mouseClicked(event, doubleClick);
        }
        pressed = target;
        focusNode(target);
        dispatch(target, Nf262UiInputMapper.mouse(UiEventType.MOUSE_DOWN,
                event.x(), event.y(), event.button(), event.modifiers()));
        if (target.boundEvents().contains(UiEventType.CLICK)) {
            dispatch(target, Nf262UiInputMapper.mouse(UiEventType.CLICK,
                    event.x(), event.y(), event.button(), event.modifiers()));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        Nf262UiHostAdapter.Nf262UiNode target = pressed;
        pressed = null;
        if (target != null && !target.disposed()) {
            dispatch(target, Nf262UiInputMapper.mouse(UiEventType.MOUSE_UP,
                    event.x(), event.y(), event.button(), event.modifiers()));
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double x, double y) {
        Nf262UiHostAdapter.Nf262UiNode target = hitTest(x, y);
        hovered = target;
        if (target != null) {
            dispatch(target, Nf262UiInputMapper.mouse(UiEventType.MOUSE_MOVE, x, y, -1, 0));
        }
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Nf262UiHostAdapter.Nf262UiNode target = hitTest(mouseX, mouseY);
        if (target == null) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        dispatch(target, Nf262UiInputMapper.scroll(mouseX, mouseY, scrollY));
        // 已消费：不再把滚轮交给背后的世界/HUD（jsx-ui-spec「scroll 正确管理…滚轮消费」）。
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean shift = event.hasShiftDown();
        boolean control = event.hasControlDown();
        boolean alt = event.hasAltDown();
        Nf262UiHostAdapter.Nf262UiNode focused = focusedNode();

        if (Nf262UiInputMapper.escapes(key)) {
            Nf262UiHostAdapter.Nf262UiNode target = focused != null ? focused : screenNode();
            if (target != null) {
                dispatch(target, Nf262UiInputMapper.key(UiEventType.KEY_DOWN, key, shift, control, alt));
            }
            if (options.closeOnEscape()) {
                onClose();
            }
            return true;
        }

        if (Nf262UiInputMapper.tabs(key)) {
            moveFocus(shift);
            return true;
        }

        if (focusedInput != null && focused == focusedInput) {
            // 文本输入复用原生编辑能力：光标/选择/最大长度都在 EditBox 内维护。
            inputBox.keyPressed(event);
            syncInputValue(false);
            return true;
        }

        if (Nf262UiInputMapper.activates(key)) {
            if (focused != null) {
                dispatch(focused, Nf262UiInputMapper.key(Nf262UiInputMapper.activationType(key),
                        key, shift, control, alt));
                if (focused.boundEvents().contains(UiEventType.SUBMIT)) {
                    dispatch(focused, Nf262UiInputMapper.key(UiEventType.SUBMIT, key, shift, control, alt));
                }
                return true;
            }
        }

        if (focused != null) {
            dispatch(focused, Nf262UiInputMapper.key(UiEventType.KEY_DOWN, key, shift, control, alt));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        Nf262UiHostAdapter.Nf262UiNode focused = focusedNode();
        if (focusedInput != null && focused == focusedInput) {
            inputBox.keyReleased(event);
            return true;
        }
        if (focused != null) {
            dispatch(focused, Nf262UiInputMapper.key(UiEventType.KEY_UP, event.key(),
                    event.hasShiftDown(), event.hasControlDown(), event.hasAltDown()));
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focusedInput != null) {
            inputBox.charTyped(event);
            syncInputValue(false);
            return true;
        }
        Nf262UiHostAdapter.Nf262UiNode focused = focusedNode();
        if (focused != null) {
            dispatch(focused, Nf262UiInputMapper.character(event.codepointAsString()));
            return true;
        }
        return super.charTyped(event);
    }

    // ---- 焦点 ----

    private void moveFocus(boolean backwards) {
        List<Nf262UiHostAdapter.Nf262UiNode> order = Nf262UiInputMapper.collectFocusOrder(committedRoot());
        int next = Nf262UiInputMapper.nextFocusIndex(indexOf(order, focusedNode()), order.size(), backwards);
        if (next >= 0 && next < order.size()) {
            focusNode(order.get(next));
        }
    }

    private void focusNode(Nf262UiHostAdapter.Nf262UiNode target) {
        Nf262UiHostAdapter.Nf262UiNode previous = focusedNode();
        if (previous == target) {
            return;
        }
        if (previous != null && !previous.disposed()) {
            previous.props = withString(previous.props, UiProp.FOCUSED, false);
            dispatch(previous, UiEvent.plain(UiEventType.BLUR));
        }
        setFocusedInput(target != null && target.primitive() == UiPrimitive.INPUT ? target : null);
        if (target != null && !target.disposed()) {
            target.props = withString(target.props, UiProp.FOCUSED, true);
            dispatch(target, UiEvent.plain(UiEventType.FOCUS));
        }
    }

    private static int indexOf(List<Nf262UiHostAdapter.Nf262UiNode> order,
                               Nf262UiHostAdapter.Nf262UiNode node) {
        return node == null ? -1 : order.indexOf(node);
    }

    private Nf262UiHostAdapter.Nf262UiNode focusedNode() {
        String path = root.focusedPath();
        UiHostNode node = path == null ? null : findByPath(committedRoot(), path);
        return node instanceof Nf262UiHostAdapter.Nf262UiNode nf ? nf : null;
    }

    private Nf262UiHostAdapter.Nf262UiNode screenNode() {
        return committedRoot() instanceof Nf262UiHostAdapter.Nf262UiNode nf ? nf : null;
    }

    private UiHostNode committedRoot() {
        var inspection = root.inspection();
        return inspection.hasCommittedTree() ? inspection.committedRoot() : null;
    }

    private Nf262UiHostAdapter.Nf262UiNode hitTest(double x, double y) {
        return Nf262UiInputMapper.hitTest(committedRoot(), x, y);
    }

    // ---- 输入框接线 ----

    private void setFocusedInput(Nf262UiHostAdapter.Nf262UiNode target) {
        if (focusedInput == target) {
            return;
        }
        if (inputBox != null && focusedInput != null && !focusedInput.disposed()) {
            syncInputValue(false);
        }
        focusedInput = target;
        if (target == null) {
            if (inputBox != null) {
                inputBox.setVisible(false);
            }
            return;
        }
        rebuildInputBox();
        if (inputBox != null) {
            applyInputRect(target);
            inputBox.setVisible(true);
            inputBox.setEditable(!target.props().bool(UiProp.DISABLED, false));
            setFocused(inputBox);
        }
    }

    /** 每次事件分发与 reconcile 后同步 input 的原生文本/尺寸。 */
    void refreshAfterReconcile() {
        if (focusedInput != null && focusedInput.disposed()) {
            focusedInput = null;
        }
        if (focusedInput != null) {
            applyInputRect(focusedInput);
        }
        if (hovered != null && hovered.disposed()) {
            hovered = null;
        }
    }

    private void rebuildInputBox() {
        if (focusedInput == null) {
            return;
        }
        // 复用一个 EditBox 以保留光标、选择与水平滚动（与 error dashboard 同一手法）。
        if (inputBox == null) {
            inputBox = new EditBox(font, 0, 0, 1, font.lineHeight, Component.literal("NekoJS UI input"));
            inputBox.setBordered(false);
            inputBox.setMaxLength(4096);
            inputBox.setResponder(value -> syncInputValue(true));
        }
        applyInputRect(focusedInput);
    }

    private void applyInputRect(Nf262UiHostAdapter.Nf262UiNode node) {
        if (inputBox == null || node == null) {
            return;
        }
        UiRect rect = node.layout();
        int maxLength = (int) node.props().number(UiProp.MAX_LENGTH, 4096);
        if (maxLength > 0) {
            inputBox.setMaxLength(maxLength);
        }
        inputBox.setX((int) Math.round(rect.x()) + 4);
        inputBox.setY((int) Math.round(rect.y()) + Math.max(0, ((int) rect.height() - font.lineHeight) / 2));
        inputBox.setWidth(Math.max(1, (int) rect.width() - 8));
        String value = node.props().string(UiProp.VALUE, "");
        if (!inputBox.getValue().equals(value)) {
            inputBox.setValue(value);
        }
    }

    /** 把原生编辑结果写回脚本可观察的 value（不触发额外 render 循环）。 */
    private void syncInputValue(boolean fromNativeEdit) {
        if (focusedInput == null || inputBox == null) {
            return;
        }
        String value = inputBox.getValue();
        if (value.equals(focusedInput.props().string(UiProp.VALUE, ""))) {
            return;
        }
        focusedInput.props = withString(focusedInput.props, UiProp.VALUE, value);
        if (fromNativeEdit) {
            dispatch(focusedInput, Nf262UiInputMapper.character(value));
        }
    }

    private void dispatch(Nf262UiHostAdapter.Nf262UiNode target, UiEvent event) {
        if (target == null || target.disposed()) {
            return;
        }
        try {
            root.dispatchEvent(event);
        } finally {
            refreshAfterReconcile();
        }
    }

    // ---- narration ----

    @Override
    public Component getNarrationMessage() {
        UiHostNode committed = committedRoot();
        if (committed == null) {
            return title;
        }
        String narration = Nf262UiInputMapper.narration(committed);
        return narration.isEmpty() ? title : Component.literal(narration);
    }

    @Override
    protected void updateNarrationState(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, getNarrationMessage());
    }

    // ---- 助手 ----

    private static com.tkisor.nekojs.api.ui.UiProps withString(
            com.tkisor.nekojs.api.ui.UiProps props, UiProp prop, Object value) {
        var map = new java.util.LinkedHashMap<>(props.asMap());
        map.put(prop.jsName(), value);
        return com.tkisor.nekojs.api.ui.UiProps.of(map);
    }

    private static UiHostNode findByPath(UiHostNode node, String path) {
        if (node == null || path == null) {
            return null;
        }
        if (path.equals(node.path())) {
            return node;
        }
        for (UiHostNode child : node.children()) {
            UiHostNode found = findByPath(child, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
//?}
