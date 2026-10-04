//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.ui.FontAdapter;
import com.tkisor.nekojs.api.ui.InspectorScreenshot;
import com.tkisor.nekojs.api.ui.InspectorSnapshot;
import com.tkisor.nekojs.api.ui.InspectorSnapshots;
import com.tkisor.nekojs.api.ui.TextLayout;
import com.tkisor.nekojs.api.ui.TextLayouter;
import com.tkisor.nekojs.api.ui.UiDiagnostic;
import com.tkisor.nekojs.api.ui.UiInspector;
import com.tkisor.nekojs.api.ui.UiResourceResolver;
import com.tkisor.nekojs.api.ui.VisualSpec;
import com.tkisor.nekojs.api.ui.VisualStyleResolver;
import com.tkisor.nekojs.core.state.GenerationGlobals;
import com.tkisor.nekojs.platform.compat.McClientCompat;
import com.tkisor.nekojs.script.ScriptManager;
import com.tkisor.nekojs.wrapper.client.McFontAdapter;
import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Retained host transaction and owner-thread bridge for the common JSX runtime. One adapter
 * belongs to exactly one CLIENT generation (ticket 42): it captures the creating script
 * Context, registers itself into that generation's {@link GenerationGlobals}, and validates
 * the generation epoch before every state-changing or guest-calling operation, so handles
 * from superseded generations fail explicitly instead of silently succeeding.
 */
public final class JsxHostAdapter implements GenerationGlobals.UiRoot, UiInspector {
    private static final Set<String> PRIMITIVES = Set.of(
            "screen", "panel", "row", "column", "stack", "scroll", "label", "button", "input", "image", "spacer");

    private final Minecraft minecraft;
    private final Thread ownerThread;
    private final JsxScreen screen;
    private final JsxHostTree tree = new JsxHostTree();
    private final ScriptManager manager;
    private final GenerationGlobals globals;
    private final UiRootLifecycle lifecycle;
    private final FontAdapter fontAdapter;
    private final MinecraftUiResourceResolver resources;
    private final List<InspectorSnapshot.PhaseError> retainedErrors = new ArrayList<>();
    private String rootId;
    private Value root;
    private InspectorSnapshot lastSnapshot;
    private InspectorSnapshot layoutBasis;
    private InspectorSnapshot pendingSnapshot;
    private boolean tearingDown;
    private int viewportWidth;
    private int viewportHeight;

    public JsxHostAdapter(String title, boolean pausesGame) {
        minecraft = Minecraft.getInstance();
        ownerThread = Thread.currentThread();
        screen = new JsxScreen(title, pausesGame, this);
        this.fontAdapter = new McFontAdapter(minecraft.font);
        this.resources = new MinecraftUiResourceResolver(minecraft.getResourceManager(), minecraft.getTextureManager());
        Context context = Context.getCurrent();
        ScriptManager owner = null;
        if (context != null) {
            try {
                owner = ScriptManager.from(context);
            } catch (IllegalStateException unregistered) {
                owner = null;
            }
        }
        if (owner == null) {
            throw new IllegalStateException("[NEKO-7003] JSX host adapter rejected: it must be "
                    + "created from a managed CLIENT script context, not from host code");
        }
        ScriptManager.UiRootBinding binding = ScriptManager.registerUiRoot(context, this);
        if (binding == null) {
            throw new IllegalStateException("[NEKO-7001] JSX host adapter rejected: creating "
                    + "context is neither the active nor the in-flight candidate generation");
        }
        this.manager = owner;
        this.globals = binding.globals();
        this.lifecycle = new UiRootLifecycle(binding.candidate(), binding.generation(), binding.globals());
    }

    JsxScreen screen() {
        return screen;
    }

    public void bindRoot(Object root) {
        requireUsable("bind the common root");
        this.root = root == null ? null : Value.asValue(root);
        // The root id lives on the guest handle; capture it so host-side diagnostics can
        // name the root without reaching back into guest memory.
        if (this.root != null) {
            try {
                rootId = this.root.hasMember("id") ? this.root.getMember("id").asString() : null;
            } catch (RuntimeException unnamed) {
                rootId = null;
            }
        }
    }

    /**
     * Real glyph measurement for the common runtime (ticket 44 hand-off): wraps the shared
     * {@link TextLayouter} over this client's font, so the runtime never guesses widths.
     * Wrap-enabled and truncation-off match the runtime's intrinsic-size contract.
     */
    public Map<String, Object> measureText(String text, double fontSize, int maxWidth) {
        requireUsable("measure text");
        TextLayout layout = TextLayouter.layoutScaled(
                fontAdapter, text, fontSize, maxWidth, true, TextLayouter.Truncation.OFF);
        return Map.of("width", layout.width(), "height", layout.height());
    }

    public boolean isOwnerThread() {
        return Thread.currentThread() == ownerThread;
    }

    public boolean enqueue(Object action) {
        if (action == null) return false;
        // Explicit cross-thread queueing point (the common runtime calls this only from
        // non-owner threads). The queued guest closure may run after a reload committed or
        // discarded this root's generation; re-check the epoch at run time so a stale
        // generation never executes guest code.
        minecraft.execute(() -> {
            if (!lifecycle.isUsable(manager.generationId())) {
                reportStaleDrop("queued UI action");
                return;
            }
            Value.asValue(action).executeVoid();
        });
        return true;
    }

    public boolean supportsPrimitive(String type) {
        return PRIMITIVES.contains(type);
    }

    /** Returns the current logical viewport used when the common root is created. */
    public Map<String, Object> viewport() {
        int width = viewportWidth > 0 ? viewportWidth : minecraft.getWindow().getGuiScaledWidth();
        int height = viewportHeight > 0 ? viewportHeight : minecraft.getWindow().getGuiScaledHeight();
        return Map.of("width", Math.max(1, width), "height", Math.max(1, height));
    }

    /**
     * Ticket 45 inspector output: the retained runtime layout snapshot decorated with the
     * host-only facts (focus, resource statuses, retained phase errors, capture metadata)
     * through the same shared collection path the fake host tests use. Pixel capture
     * itself is auxiliary and not wired here; the metadata names the viewport-derived
     * origin it was synthesized from.
     */
    @Override
    public InspectorSnapshot inspect() {
        requireUsable("inspect the UI root");
        if (lastSnapshot == null) return null;
        if (resources.refresh()) {
            try {
                relayout();
            } catch (RuntimeException failure) {
                reportHostFailure("resource-refresh", failure);
            }
        }
        // The capture metadata describes the retained frame, not the live viewport: a
        // resize that has not been laid out yet must not relabel the measured frame.
        return InspectorSnapshots.decorate(lastSnapshot, focusedIds(tree.roots()),
                resources::resolveTexture, List.copyOf(retainedErrors),
                new InspectorScreenshot("neoforge-viewport-meta",
                        lastSnapshot.viewport().width(), lastSnapshot.viewport().height()));
    }

    /** Receives the frozen common layout candidate and its snapshot without mutating guest data. */
    public void layout(Object tree, Object viewport, Object snapshot) {
        layout(tree, viewport, snapshot, false);
    }

    public void layout(Object tree, Object viewport, Object snapshot, boolean publish) {
        requireUsable("apply the layout candidate");
        if (tree != null) readArray(tree);
        Value envelope = Value.asValue(snapshot);
        if (envelope.hasMember("rootId") && envelope.getMember("rootId").isString()) {
            rootId = envelope.getMember("rootId").asString();
        }
        InspectorSnapshot candidate = InspectorSnapshots.read(rootId == null ? "unknown" : rootId,
                "neoforge-host", Value.asValue(snapshot));
        if (publish) {
            JsxHostTree.Transaction transaction = this.tree.begin();
            try {
                transaction.commit(this.tree.roots(), nodes -> {
                    pendingSnapshot = projected(candidate, nodes);
                });
                lastSnapshot = pendingSnapshot;
                layoutBasis = candidate;
                pendingSnapshot = null;
            } catch (RuntimeException failure) {
                transaction.rollback();
                pendingSnapshot = null;
                throw failure;
            }
            finishPreparedResources();
        } else {
            pendingSnapshot = candidate;
        }
    }

    private InspectorSnapshot projected(InspectorSnapshot candidate, List<JsxHostTree.Node> nodes) {
        List<com.tkisor.nekojs.api.ui.InspectorNode> measured = JsxHostLayout.project(nodes, candidate.nodes(),
                candidate.viewport().designScale());
        prepareVisuals(nodes, candidate.viewport().designScale());
        return new InspectorSnapshot(candidate.rootId(), candidate.source(), candidate.viewport(),
                measured, candidate.diagnostics(), candidate.errors(), candidate.screenshot());
    }

    private void prepareVisuals(List<JsxHostTree.Node> nodes, double designScale) {
        for (JsxHostTree.Node node : nodes) {
            if (!"#text".equals(node.type)) {
                node.visual = resolveVisual(node.props, node.type,
                        node.key == null ? text(node.props.get("id")) : node.key,
                        rootId == null ? "unknown" : rootId, lifecycle.generation(), resources);
                node.texturePlan = null;
                if ("image".equals(node.type)) {
                    com.tkisor.nekojs.api.ui.UiResourceId resource = node.visual.image() == null
                            ? node.visual.icon() : node.visual.image();
                    if (resource != null) {
                        UiTextureBlitPlan.Result result = UiTextureBlitPlan.prepare(resources.texture(resource.toString()),
                                node.visual.crop(), node.visual.fit(), node.visual.opacity() == null ? 1 : node.visual.opacity(),
                                node.x, node.y, node.width, node.height,
                                new UiDiagnostic.Location(rootId == null ? "unknown" : rootId, node.type,
                                        node.key == null ? text(node.props.get("id")) : node.key, lifecycle.generation()));
                        node.texturePlan = result.plan();
                        if (result.diagnostic() != null && node.visual.diagnostics().stream()
                                .noneMatch(diagnostic -> diagnostic.code().equals(result.diagnostic().code()))) {
                            ScriptErrorReporter.recordCallbackError(ScriptType.CLIENT, "ui-visual",
                                    new IllegalStateException(result.diagnostic().logLine()));
                        }
                    }
                }
            }
            if ("label".equals(node.type) || "#text".equals(node.type)) {
                double fontSize = node.visual == null || node.visual.fontSize() == null ? 0 : node.visual.fontSize();
                if ("design".equals(node.props.get("coordinateSpace"))) {
                    fontSize = (fontSize <= 0 ? fontAdapter.lineHeight() : fontSize) * designScale;
                }
                boolean truncate = node.visual != null && Boolean.TRUE.equals(node.visual.truncate());
                node.textLayout = TextLayouter.layoutScaled(fontAdapter, text(node.props.get("text")), fontSize,
                        Math.max(1, node.width), !truncate, truncationFor(node.visual));
                node.textScale = fontSize <= 0 ? 1 : fontSize / fontAdapter.lineHeight();
            }
            prepareVisuals(node.children, designScale);
        }
    }

    public JsxHostTransaction begin() {
        requireUsable("open a host transaction");
        return new JsxHostTransaction(tree.begin());
    }

    public void reportDiagnostic(Object diagnostic) {
        Value envelope = Value.asValue(diagnostic);
        String rootId = text(memberOrNull(envelope, "rootId"));
        String phase = text(memberOrNull(envelope, "phase"));
        String message = "";
        Value error = memberOrNull(envelope, "error");
        if (error != null) {
            Value detail = memberOrNull(error, "message");
            message = detail == null ? error.toString() : detail.asString();
        }
        ScriptErrorReporter.recordCallbackError(ScriptType.CLIENT, "ui-" + phase,
                new IllegalStateException("[NEKO-7007] JSX UI root " + rootId + " failed in phase "
                        + phase + " (generation " + lifecycle.generation() + "): " + message));
        retainedErrors.add(new InspectorSnapshot.PhaseError(phase == null ? "unknown" : phase,
                rootId == null ? "unknown" : rootId, message));
        // ponytail: fixed 8-entry ring; query on demand if long error histories ever matter.
        if (retainedErrors.size() > 8) retainedErrors.removeFirst();
    }

    private static Value memberOrNull(Value value, String name) {
        try {
            return value.hasMember(name) ? value.getMember(name) : null;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private void reportStaleDrop(String what) {
        ScriptErrorReporter.recordCallbackError(ScriptType.CLIENT, "ui-stale",
                new IllegalStateException("[NEKO-7006] " + what + " was dropped: its UI root belongs "
                        + "to generation " + lifecycle.generation() + ", which is no longer active"));
    }

    /**
     * Shared entry validation for every state-changing or guest-calling entry point: the
     * call must arrive on the client owner thread — cross-thread guests go through the
     * explicit {@link #enqueue(Object)} channel instead of mutating state directly — and
     * the owning generation must still be usable.
     */
    private void requireUsable(String operation) {
        if (!isOwnerThread()) {
            throw new IllegalStateException("[NEKO-7004] Cannot " + operation + " off the client "
                    + "owner thread; cross-thread state updates must go through the explicit "
                    + "enqueue channel (UI root generation " + lifecycle.generation() + ")");
        }
        if (!lifecycle.isUsable(manager.generationId())) {
            throw new IllegalStateException("[NEKO-7001] Cannot " + operation + ": this UI root belongs "
                    + "to generation " + lifecycle.generation() + ", which is superseded or closed "
                    + "(active generation " + manager.generationId() + ", lifecycle state "
                    + lifecycle.state() + ")");
        }
    }

    public void resize(int width, int height) {
        requireUsable("resize");
        viewportWidth = Math.max(0, width);
        viewportHeight = Math.max(0, height);
        if (root != null) {
            try {
                root.invokeMember("resize", viewport());
            } catch (RuntimeException failure) {
                reportHostFailure("resize", failure);
            }
        }
    }

    private void reportHostFailure(String phase, RuntimeException failure) {
        ScriptErrorReporter.recordCallbackError(ScriptType.CLIENT, "ui-" + phase,
                new IllegalStateException("[NEKO-7007] JSX UI host operation '" + phase
                        + "' failed (generation " + lifecycle.generation() + ")", failure));
    }

    boolean closeOnEscape() {
        for (JsxHostTree.Node node : tree.roots()) if ("screen".equals(node.type)) return !node.props.containsKey("closeOnEscape") || bool(node.props.get("closeOnEscape"));
        return true;
    }

    void paint(Object graphics, int mouseX, int mouseY) {
        // Paint is implemented by JsxScreen so normal frames never execute a guest render function.
    }

    List<JsxHostTree.Node> roots() {
        return tree.roots();
    }

    String narration() {
        return narrationText(tree.roots());
    }

    boolean narrationDisabled() {
        return narrationDisabled(tree.roots());
    }

    /**
     * Narration text for the focused host node: explicit {@code narration} prop wins, then
     * {@code text}, then the input's own value or placeholder, else the primitive name. The
     * localized disabled marker is composed by the screen (see
     * {@code JsxScreen#getNarrationMessage}). Static and Minecraft-free so it is
     * observable from a retained tree without a live client.
     */
    static String narrationText(List<JsxHostTree.Node> roots) {
        JsxHostTree.Node node = focused(roots);
        if (node == null) return "";
        String label = text(node.props.get("narration"));
        if (label.isEmpty()) label = text(node.props.get("text"));
        if (label.isEmpty() && node.inputValue != null) {
            label = node.inputValue.isEmpty() ? text(node.props.get("placeholder")) : node.inputValue;
        }
        if (label.isEmpty()) label = node.type;
        return label;
    }

    /** Whether the focused host node carries the {@code disabled} prop, so its narration gets the localized marker. */
    static boolean narrationDisabled(List<JsxHostTree.Node> roots) {
        JsxHostTree.Node node = focused(roots);
        return node != null && bool(node.props.get("disabled"));
    }

    void paintNode(net.minecraft.client.gui.GuiGraphicsExtractor graphics, JsxHostTree.Node node, int mouseX, int mouseY) {
        if (node.removed || !visible(node)) return;
        if (node.hasClip && (node.clipWidth <= 0 || node.clipHeight <= 0)) return;
        if (node.hasClip) {
            graphics.enableScissor(node.clipX, node.clipY, node.clipX + node.clipWidth, node.clipY + node.clipHeight);
        }
        try {
            paintNodeContents(graphics, node, mouseX, mouseY);
        } finally {
            if (node.hasClip) graphics.disableScissor();
        }
    }

    private void paintNodeContents(net.minecraft.client.gui.GuiGraphicsExtractor graphics, JsxHostTree.Node node, int mouseX, int mouseY) {
        int x = node.x;
        int y = node.y;
        int width = node.width;
        int height = node.height;
        String type = node.type;
        if ("#text".equals(type)) {
            paintTextLines(graphics, node, x, y, 0xFFFFFFFF);
            return;
        }
        if ("panel".equals(type) || "screen".equals(type) || "scroll".equals(type)) {
            VisualSpec spec = resolveVisual(node);
            graphics.fill(x, y, x + width, y + height,
                    applyOpacity(spec, argb(spec.background(), 0xB0101010)));
            int borderWidth = spec.borderWidth() == null ? 0 : spec.borderWidth();
            if (borderWidth > 0) graphics.outline(x, y, x + width, y + height,
                    applyOpacity(spec, argb(spec.borderColor(), 0xFFFFFFFF)));
        } else if ("image".equals(type)) {
            VisualSpec spec = resolveVisual(node);
            if (node.texturePlan != null) {
                node.texturePlan.paint(graphics);
            } else {
                graphics.fill(x, y, x + width, y + height, applyOpacity(spec, argb(spec.background(), 0xFF1A1D22)));
                graphics.outline(x, y, x + width, y + height, applyOpacity(spec, argb(spec.borderColor(), 0xFF707780)));
            }
        } else if ("button".equals(type)) {
            boolean disabled = bool(node.props.get("disabled"));
            boolean hovered = contains(node, mouseX, mouseY);
            int fill = disabled ? 0xFF404040 : node.pressed ? 0xFF29415C : hovered ? 0xFF5A7FA8 : 0xFF3A536F;
            graphics.fill(x, y, x + width, y + height, fill);
            graphics.outline(x, y, x + width, y + height, node.focused ? 0xFFFFFFFF : 0xFF9AA7B5);
            graphics.centeredText(Minecraft.getInstance().font, text(node.props.get("text")), x + width / 2, y + 6,
                    disabled ? 0xFF888888 : 0xFFFFFFFF);
        } else if ("input".equals(type)) {
            graphics.fill(x, y, x + width, y + height, 0xFF20252B);
            graphics.outline(x, y, x + width, y + height, node.focused ? 0xFFFFFFFF : 0xFF707780);
            String input = node.inputValue == null ? "" : node.inputValue;
            boolean empty = input.isEmpty();
            String display = empty ? text(node.props.get("placeholder")) : input;
            if (node.focused && !empty && node.hasSelection()) {
                int start = Math.max(0, Math.min(node.selectionStart(), input.length()));
                int end = Math.max(start, Math.min(node.selectionEnd(), input.length()));
                int selectionX = x + 4 + Minecraft.getInstance().font.width(input.substring(0, start));
                int selectionEndX = x + 4 + Minecraft.getInstance().font.width(input.substring(0, end));
                graphics.fill(selectionX, y + 4, selectionEndX, y + height - 4, 0xFF4A6A95);
            }
            graphics.text(Minecraft.getInstance().font, display, x + 4, y + 6,
                    empty ? 0xFF888888 : 0xFFFFFFFF, false);
            if (node.focused) {
                int cursor = Math.max(0, Math.min(node.cursor, input.length()));
                String prefix = input.substring(0, cursor);
                int cursorX = x + 4 + Minecraft.getInstance().font.width(prefix);
                graphics.fill(cursorX, y + 4, cursorX + 1, y + height - 4, 0xFFFFFFFF);
            }
        } else if ("label".equals(type)) {
            VisualSpec spec = resolveVisual(node);
            paintTextLines(graphics, node, x, y, applyOpacity(spec, argb(spec.color(), 0xFFFFFFFF)));
        }
        if (node.focused && ("input".equals(type) || "button".equals(type))) {
            graphics.outline(x, y, x + width, y + height, 0xFFFFFFFF);
        }
        if ("scroll".equals(type)) {
            graphics.enableScissor(x, y, x + width, y + height);
            for (JsxHostTree.Node child : node.children) paintNode(graphics, child, mouseX, mouseY);
            graphics.disableScissor();
            return;
        }
        for (JsxHostTree.Node child : node.children) paintNode(graphics, child, mouseX, mouseY);
        if (contains(node, mouseX, mouseY) && node.props.get("tooltip") != null) {
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font,
                    net.minecraft.network.chat.Component.literal(text(node.props.get("tooltip"))), mouseX, mouseY);
        }
    }

    /**
     * Resolves one node's visual props through the shared {@link VisualStyleResolver} and
     * routes every produced {@link UiDiagnostic} into the ticket-30 reporting seam. Static and
     * Minecraft-free so the resolution contract is testable without a live client.
     */
    static VisualSpec resolveVisual(Map<String, Object> props, String nodeType, String nodeKey,
            String rootId, long generation, UiResourceResolver resources) {
        VisualSpec spec = VisualStyleResolver.resolve(props,
                new UiDiagnostic.Location(rootId, nodeType, nodeKey, generation), resources);
        for (UiDiagnostic diagnostic : spec.diagnostics()) {
            Throwable cause = resources instanceof MinecraftUiResourceResolver minecraftResources
                    ? minecraftResources.failureCause(diagnostic.resourceId()) : null;
            ScriptErrorReporter.recordCallbackError(ScriptType.CLIENT, "ui-visual",
                    new IllegalStateException(diagnostic.logLine(), cause));
        }
        return spec;
    }

    private VisualSpec resolveVisual(JsxHostTree.Node node) {
        return node.visual;
    }

    private void paintTextLines(net.minecraft.client.gui.GuiGraphicsExtractor graphics, JsxHostTree.Node node,
            int x, int y, int argbColor) {
        if (node.textLayout == null) return;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate((float) x, (float) y);
            graphics.pose().scale((float) node.textScale, (float) node.textScale);
            for (int lineIndex = 0; lineIndex < node.textLayout.lines().size(); lineIndex++) {
                int baseline = (int) Math.round(lineIndex * node.textLayout.lineHeight() / node.textScale);
                graphics.text(Minecraft.getInstance().font, node.textLayout.lines().get(lineIndex),
                        0, baseline, argbColor, false);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static int argb(com.tkisor.nekojs.api.ui.UiColor color, int fallback) {
        return color == null ? fallback : color.argb();
    }

    /** Truncation choice for painted text: the node's resolved {@code truncate} prop turns the ellipsis on. */
    static TextLayouter.Truncation truncationFor(VisualSpec spec) {
        return spec != null && Boolean.TRUE.equals(spec.truncate())
                ? TextLayouter.Truncation.ELLIPSIS : TextLayouter.Truncation.OFF;
    }

    /**
     * Applies the spec's opacity to the winning color's alpha. Opacity composes with an
     * explicit color too — a `#AARRGGBB` alpha is scaled, not kept — matching CSS opacity
     * semantics where the prop affects the whole element regardless of the color source.
     * Package-visible for the hand-off smoke.
     */
    static int applyOpacity(VisualSpec spec, int argb) {
        if (spec == null || spec.opacity() == null) return argb;
        int alpha = (int) Math.round((argb >>> 24) * Math.max(0, Math.min(1, spec.opacity())));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    boolean dispatchAt(double mouseX, double mouseY, String eventName, int button) {
        requireUsable("route a mouse event");
        JsxHostTree.Node node = hit(tree.roots(), mouseX, mouseY);
        if (node == null || bool(node.props.get("disabled"))) return false;
        if ("click".equals(eventName)) {
            tree.capture(node, button);
            if (isFocusable(node)) focus(node);
        }
        boolean callbackHandled = dispatch(node, eventName, Map.of("x", mouseX, "y", mouseY, "button", button));
        return callbackHandled || isFocusable(node);
    }

    boolean dispatchRelease(double mouseX, double mouseY, int button) {
        requireUsable("route a mouse release");
        JsxHostTree.Node node = tree.releaseCapture(button);
        if (node == null || node.removed) return false;
        dispatch(node, "release", Map.of("x", mouseX, "y", mouseY, "button", button));
        return true;
    }

    boolean dispatchScroll(double mouseX, double mouseY, double delta) {
        requireUsable("route a scroll event");
        JsxHostTree.Node node = scrollHit(tree.roots(), mouseX, mouseY);
        if (node == null) return false;
        double current = number(node.props.get("scrollOffset"), 0);
        double next = clamp(current - delta * 12, 0, node.scrollRange);
        node.props.put("scrollOffset", next);
        relayout();
        boolean callbackHandled = dispatch(node, "scroll", Map.of("x", mouseX, "y", mouseY, "delta", delta));
        return callbackHandled || node.scrollRange > 0;
    }

    boolean key(int key, int modifiers) {
        requireUsable("route a key event");
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && screen.closeOnEscape()) {
            screen.onClose();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            focusNext((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        JsxHostTree.Node focused = focused(tree.roots());
        if (focused == null) return false;
        String keyName = keyName(key);
        if (bool(focused.props.get("disabled"))) return false;
        if ("input".equals(focused.type) && editInput(focused, key, modifiers)) {
            dispatch(focused, "key", Map.of("key", keyName));
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            dispatch(focused, "key", Map.of("key", keyName));
            if ("input".equals(focused.type)) dispatch(focused, "submit", Map.of("key", keyName));
            else dispatch(focused, "click", Map.of("key", keyName));
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE && "button".equals(focused.type)) {
            dispatch(focused, "key", Map.of("key", keyName));
            dispatch(focused, "click", Map.of("key", keyName));
            return true;
        }
        return dispatch(focused, "key", Map.of("key", keyName));
    }

    private boolean editInput(JsxHostTree.Node node, int key, int modifiers) {
        boolean shift = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
        boolean control = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && key == org.lwjgl.glfw.GLFW.GLFW_KEY_A) {
            node.selectAll();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (!node.deleteBackward()) return true;
        } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
            if (!node.deleteForward()) return true;
        } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            int position = node.hasSelection() && !shift ? node.selectionStart()
                    : node.cursor == 0 ? 0 : node.inputValue.offsetByCodePoints(node.cursor, -1);
            node.moveCursor(position, shift);
            return true;
        } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            int position = node.hasSelection() && !shift ? node.selectionEnd()
                    : node.cursor >= node.inputValue.length() ? node.inputValue.length()
                    : node.inputValue.offsetByCodePoints(node.cursor, 1);
            node.moveCursor(position, shift);
            return true;
        } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) {
            node.moveCursor(0, shift);
            return true;
        } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_END) {
            node.moveCursor(node.inputValue.length(), shift);
            return true;
        } else {
            return false;
        }
        publishInput(node);
        return true;
    }

    private void publishInput(JsxHostTree.Node node) {
        node.props.put("value", node.inputValue);
        dispatch(node, "textInput", Map.of("value", node.inputValue));
        dispatch(node, "change", Map.of("value", node.inputValue));
    }

    boolean textInput(int codepoint) {
        requireUsable("route text input");
        JsxHostTree.Node focused = focused(tree.roots());
        if (focused == null || !"input".equals(focused.type) || bool(focused.props.get("disabled"))) return false;
        String next = new String(Character.toChars(codepoint));
        int maxLength = integer(focused.props.get("maxLength"), Integer.MAX_VALUE);
        focused.insertText(next, maxLength);
        publishInput(focused);
        return true;
    }

    public void open() {
        if (!isOwnerThread()) throw new IllegalStateException("[NEKO-7004] JSX Screen must open on the client owner thread");
        long active = manager.generationId();
        lifecycle.observeCommit(active);
        if (lifecycle.isProduction(active)) {
            showScreen();
            return;
        }
        if (lifecycle.isUsable(active)) {
            // In-flight candidate: the Screen must stay invisible before commit. CLIENT reloads
            // run as a client-thread task, so re-queueing here defers the decision to after that
            // task — the epoch check in runDeferredOpen then sees either the committed
            // generation (open) or the discarded candidate (explicit drop with a diagnostic).
            minecraft.execute(this::runDeferredOpen);
            return;
        }
        throw new IllegalStateException("[NEKO-7001] Cannot open the JSX Screen: this UI root belongs "
                + "to generation " + lifecycle.generation() + ", which is superseded or closed "
                + "(active generation " + active + ")");
    }

    private void runDeferredOpen() {
        long active = manager.generationId();
        // A discarded candidate has already closed this root; observing would throw, and the
        // drop below is the observable outcome the ticket requires.
        if (lifecycle.state() == UiRootLifecycle.State.CANDIDATE) lifecycle.observeCommit(active);
        if (lifecycle.isProduction(active)) {
            showScreen();
            return;
        }
        reportStaleDrop("deferred JSX Screen open");
    }

    private void showScreen() {
        if (McClientCompat.get().currentScreen() != screen) McClientCompat.get().showScreen(screen);
    }

    /** Script- or Screen-initiated close: notifies the guest root, then releases host state. */
    void close() {
        teardown("script-close", true);
    }

    /**
     * Generation-initiated close (ticket 42): invoked exactly once per root from
     * {@link GenerationGlobals#close()} on the owner thread, whichever teardown path ends the
     * generation — commit supersede, candidate discard, reset or manager close. The guest
     * context is about to be destroyed, so no guest callback is attempted.
     */
    @Override
    public void closeForGeneration(String reason) {
        teardown(reason, false);
    }

    private void teardown(String reason, boolean notifyGuest) {
        // Re-entrant calls are no-ops: dismissing the Screen during teardown fires
        // Screen.removed() back into close(), and every cleanup path (reload success/failure,
        // setScreen replace, client exit, close preemption) may run more than once. The first
        // call to reach CANDIDATE/ACTIVE claims the teardown through this flag, which stays
        // set for the whole body so those re-entrant calls return instead of recursing.
        UiRootLifecycle.State state = lifecycle.state();
        if (state != UiRootLifecycle.State.CANDIDATE && state != UiRootLifecycle.State.ACTIVE) {
            return;
        }
        if (tearingDown) return;
        tearingDown = true;
        try {
            // The guest release runs while the root is still usable: the common root closes
            // through the same host transaction channel as any other update, so it must not
            // observe CLOSING yet — entering CLOSING first made every Screen close report a
            // NEKO-7001 host-transaction rejection.
            if (notifyGuest && root != null) {
                try {
                    root.invokeMember("close");
                } catch (RuntimeException failure) {
                    reportHostFailure("close", failure);
                }
            }
            tree.cancelCapture();
            lifecycle.beginClose();
            try {
                if (McClientCompat.get().currentScreen() == screen) McClientCompat.get().showScreen(null);
                tree.close(() -> { });
                root = null;
            } finally {
                try {
                    resources.close();
                } finally {
                    lifecycle.finishClose();
                    globals.unregisterUiRoot(this);
                }
            }
        } finally {
            tearingDown = false;
        }
    }

    private boolean dispatch(JsxHostTree.Node node, String eventName, Map<String, Object> input) {
        if (root == null || node == null || node.removed || node.props.get("id") == null) return false;
        try {
            Value result = root.invokeMember("dispatch", text(node.props.get("id")), eventName, input);
            return result.isBoolean() && result.asBoolean() || result.isString() && "queued".equals(result.asString());
        } catch (RuntimeException failure) {
            reportHostFailure("event", failure);
            return false;
        }
    }

    private void focus(JsxHostTree.Node next) {
        JsxHostTree.Node current = focused(tree.roots());
        if (current == next) return;
        if (current != null) { current.focused = false; dispatch(current, "blur", Map.of()); }
        if (next != null) { next.focused = true; dispatch(next, "focus", Map.of()); }
    }

    private void focusNext(boolean reverse) {
        List<JsxHostTree.Node> focusable = new ArrayList<>();
        collectFocusable(tree.roots(), focusable);
        if (focusable.isEmpty()) return;
        JsxHostTree.Node current = focused(tree.roots());
        int index = current == null ? (reverse ? 0 : -1) : focusable.indexOf(current);
        int next = (index + (reverse ? -1 : 1) + focusable.size()) % focusable.size();
        focus(focusable.get(next));
    }

    private void collectFocusable(List<JsxHostTree.Node> values, List<JsxHostTree.Node> output) {
        for (JsxHostTree.Node node : values) {
            if (isFocusable(node) && !bool(node.props.get("disabled"))) output.add(node);
            collectFocusable(node.children, output);
        }
    }

    private static JsxHostTree.Node focused(List<JsxHostTree.Node> values) {
        for (JsxHostTree.Node node : values) {
            if (node.focused) return node;
            JsxHostTree.Node nested = focused(node.children);
            if (nested != null) return nested;
        }
        return null;
    }

    /** Ids of focused host nodes that carry a script id; the inspector locates nodes by id. */
    static Set<String> focusedIds(List<JsxHostTree.Node> values) {
        Set<String> ids = new java.util.LinkedHashSet<>();
        collectFocusedIds(values, ids);
        return ids;
    }

    private static void collectFocusedIds(List<JsxHostTree.Node> values, Set<String> ids) {
        for (JsxHostTree.Node node : values) {
            if (node.focused && node.props.get("id") instanceof String id) ids.add(id);
            collectFocusedIds(node.children, ids);
        }
    }

    private static JsxHostTree.Node hit(List<JsxHostTree.Node> values, double x, double y) {
        for (int i = values.size() - 1; i >= 0; i--) {
            JsxHostTree.Node node = values.get(i);
            if (!visible(node) || !contains(node, x, y)) continue;
            JsxHostTree.Node child = hit(node.children, x, y);
            return child == null ? node : child;
        }
        return null;
    }

    static JsxHostTree.Node scrollHit(List<JsxHostTree.Node> values, double x, double y) {
        for (int i = values.size() - 1; i >= 0; i--) {
            JsxHostTree.Node node = values.get(i);
            if (!visible(node) || !contains(node, x, y)) continue;
            JsxHostTree.Node child = scrollHit(node.children, x, y);
            if (child != null) return child;
            if ("scroll".equals(node.type)) return node;
        }
        return null;
    }

    private static boolean contains(JsxHostTree.Node node, double x, double y) {
        boolean bounds = x >= node.x && y >= node.y && x < node.x + node.width && y < node.y + node.height;
        return bounds && (!node.hasClip || (x >= node.clipX && y >= node.clipY
                && x < node.clipX + node.clipWidth && y < node.clipY + node.clipHeight));
    }

    private static boolean isFocusable(JsxHostTree.Node node) {
        return "button".equals(node.type) || "input".equals(node.type);
    }

    private void relayout() {
        if (layoutBasis == null) return;
        JsxHostTree.Transaction transaction = tree.begin();
        final InspectorSnapshot[] next = new InspectorSnapshot[1];
        try {
            transaction.commit(tree.roots(), nodes -> next[0] = projected(layoutBasis, nodes));
            lastSnapshot = next[0];
        } catch (RuntimeException failure) {
            transaction.rollback();
            throw failure;
        }
        finishPreparedResources();
    }

    private void finishPreparedResources() {
        try {
            resources.commitPrepared();
        } catch (RuntimeException failure) {
            reportHostFailure("resource-retire", failure);
        }
    }

    private static boolean visible(JsxHostTree.Node node) { return !node.removed && (!node.props.containsKey("visible") || bool(node.props.get("visible"))); }
    private static boolean bool(Object value) { return Boolean.TRUE.equals(value); }
    private static int integer(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private static double number(Object value, double fallback) { return value instanceof Number n ? n.doubleValue() : fallback; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String keyName(int key) { return org.lwjgl.glfw.GLFW.glfwGetKeyName(key, 0) == null ? Integer.toString(key) : org.lwjgl.glfw.GLFW.glfwGetKeyName(key, 0); }

    /** Converts one guest props object into plain Java values; package-visible for boundary tests. */
    static Map<String, Object> props(Object value) {
        if (value == null) return new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), scalar(item)));
            return result;
        }
        Value guest = Value.asValue(value);
        if (!guest.hasMembers()) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : guest.getMemberKeys()) result.put(key, scalar(guest.getMember(key)));
        return result;
    }

    private static List<Object> readArray(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return list.stream().map(JsxHostAdapter::interopValue).toList();
        if (value instanceof Object[] array) return List.of(array);
        Value guest = Value.asValue(value);
        if (!guest.hasArrayElements()) return List.of();
        List<Object> result = new ArrayList<>();
        for (long i = 0; i < guest.getArraySize(); i++) result.add(interopValue(guest.getArrayElement(i)));
        return result;
    }

    private static Object interopValue(Object value) {
        if (!(value instanceof Value guest)) return value;
        if (guest.isHostObject()) return guest.asHostObject();
        return scalar(guest);
    }

    private static Object scalar(Object value) {
        if (!(value instanceof Value guest)) return value;
        if (guest.isNull()) return null;
        if (guest.isBoolean()) return guest.asBoolean();
        if (guest.isString()) return guest.asString();
        if (guest.isNumber()) return guest.fitsInInt() ? guest.asInt() : guest.asDouble();
        // Callbacks and host objects keep their guest/host identity; plain data arrays and
        // objects materialize so nested props (e.g. image crop) reach Java as lists and maps.
        if (guest.canExecute() || guest.isHostObject()) return value;
        if (guest.hasArrayElements()) {
            List<Object> items = new ArrayList<>();
            for (long i = 0; i < guest.getArraySize(); i++) items.add(scalar(guest.getArrayElement(i)));
            return items;
        }
        if (guest.hasMembers()) {
            Map<String, Object> members = new LinkedHashMap<>();
            for (String key : guest.getMemberKeys()) members.put(key, scalar(guest.getMember(key)));
            return members;
        }
        return value;
    }

    public final class JsxHostTransaction {
        private final JsxHostTree.Transaction transaction;

        private JsxHostTransaction(JsxHostTree.Transaction transaction) {
            this.transaction = transaction;
        }

        public Object create(String type, String key, Object props) {
            return transaction.create(type, key, props(props));
        }

        public void update(Object handle, String type, String key, Object props) {
            transaction.update(interopValue(handle), type, key, props(props));
        }

        public void order(Object parent, Object children) {
            transaction.order(interopValue(parent), readArray(children));
        }

        public void remove(Object handle) {
            transaction.remove(interopValue(handle));
        }

        public void commit(Object roots) {
            InspectorSnapshot candidate = pendingSnapshot;
            List<Object> handles = readArray(roots);
            final InspectorSnapshot[] committed = new InspectorSnapshot[1];
            transaction.commit(handles, nodes -> {
                if (!nodes.isEmpty()) {
                    if (candidate == null) {
                        throw new IllegalStateException("[NEKO-8001] JSX host commit rejected: layout candidate is missing");
                    }
                    committed[0] = projected(candidate, nodes);
                }
            });
            lastSnapshot = committed[0];
            layoutBasis = committed[0] == null ? null : candidate;
            pendingSnapshot = null;
            finishPreparedResources();
        }

        public void rollback() {
            transaction.rollback();
            pendingSnapshot = null;
        }
    }
}
//?}
