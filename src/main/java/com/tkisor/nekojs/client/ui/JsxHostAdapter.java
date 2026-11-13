//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.api.annotation.ScriptOutput;
import com.tkisor.nekojs.api.event.ScriptErrorReporter;
import com.tkisor.nekojs.api.ui.InspectorScreenshot;
import com.tkisor.nekojs.api.ui.InspectorSnapshot;
import com.tkisor.nekojs.api.ui.InspectorSnapshots;
import com.tkisor.nekojs.api.ui.TextLayout;
import com.tkisor.nekojs.api.ui.TextLayouter;
import com.tkisor.nekojs.api.ui.UiDiagnostic;
import com.tkisor.nekojs.api.ui.UiColor;
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
 * belongs to exactly one CLIENT generation: it captures the creating script Context, registers
 * itself into that generation's {@link GenerationGlobals}, and checks the generation epoch
 * before every state-changing or guest-calling operation, so handles from superseded
 * generations fail explicitly instead of silently succeeding.
 */
public final class JsxHostAdapter implements GenerationGlobals.UiRoot, UiInspector {
    private static final Set<String> PRIMITIVES = Set.of(
            "screen", "panel", "row", "column", "stack", "scroll", "label", "button", "input", "slider", "image", "spacer", "dialog", "item", "workspace", "window");

    private final Minecraft minecraft;
    private final Thread ownerThread;
    private final JsxScreen screen;
    private final boolean regionMount;
    private int regionX;
    private int regionY;
    private boolean mountFocused;
    private int tooltipOffsetX;
    private int tooltipOffsetY;
    private double windowDragOffsetX;
    private double windowDragOffsetY;
    private JsxWindowChrome.ResizeCapture windowResize;
    private JsxScrollBar.Capture scrollDrag;
    private final JsxHostTree tree = new JsxHostTree();
    private final ScriptManager manager;
    private final GenerationGlobals globals;
    private final UiRootLifecycle lifecycle;
    private final com.tkisor.nekojs.core.ui.BuiltinUiRuntime.Session builtinSession;
    private java.util.function.Consumer<RuntimeException> builtinFailure;
    private final McFontAdapter fontAdapter;
    private final UiTextLayoutCache textLayouts = new UiTextLayoutCache();
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
    private long layoutCount;
    private long successfulTransactionCount;
    private long initialBuildCount;
    private long reconcileCount;
    private long resizeCount;
    private long diagnosticCount;
    private long cleanupCount;
    private Clipboard clipboard;
    private JsxModalScope modalScope;
    private JsxItemVisual.Resolver itemResolver;

    void itemResolver(JsxItemVisual.Resolver resolver) {
        requireUsable("configure the item visual resolver");
        itemResolver = java.util.Objects.requireNonNull(resolver);
    }

    private JsxItemVisual.Resolved prepareItem(String type, Map<String, Object> props) {
        if (!"item".equals(type)) return null;
        JsxItemVisual.Descriptor descriptor = JsxItemVisual.parse(props);
        if (itemResolver == null) itemResolver = JsxItemVisual.minecraft();
        return java.util.Objects.requireNonNull(itemResolver.resolve(descriptor), "Item visual resolver returned null");
    }

    private List<JsxHostTree.Node> inputRoots() {
        if (modalScope == null) modalScope = new JsxModalScope();
        modalScope.synchronize(tree);
        JsxWorkspaceScope.synchronize(tree);
        JsxHostTree.Node captured = tree.captured(0);
        if (captured == null) {
            windowResize = null;
            scrollDrag = null;
        } else if ("window".equals(captured.type)) {
            boolean resizing = windowResize != null && windowResize.identity() == captured.identity;
            if (JsxWindowChrome.maximized(captured) || (resizing ? !JsxWindowChrome.resizable(captured)
                    : Boolean.FALSE.equals(captured.props.get("draggable")) || JsxWindowChrome.titlebarHeight(captured) == 0)) {
                // Publication must end invalid chrome gestures even if no input arrives before another commit.
                tree.cancelCapture();
                windowResize = null;
            }
        }
        if (scrollDrag != null) {
            JsxScrollBar.Geometry bar = captured == null ? null : JsxScrollBar.geometry(captured);
            if (captured == null || captured.identity != scrollDrag.identity() || bar == null
                    || bar.horizontal() != scrollDrag.horizontal()) {
                if (captured != null && captured.identity == scrollDrag.identity()) tree.cancelCapture();
                scrollDrag = null;
            }
        }
        JsxHostTree.Node modal = JsxModalScope.active(tree.roots());
        if (modal != null) return List.of(modal);
        if (JsxWorkspaceScope.workspace(tree.roots()) == null) return tree.roots();
        return JsxWorkspaceScope.inputRoots(tree.roots());
    }

    private boolean modalActive() { return JsxModalScope.active(tree.roots()) != null; }

    interface Clipboard {
        String read();
        void write(String value);
    }

    void clipboard(Clipboard clipboard) {
        requireUsable("configure the clipboard adapter");
        this.clipboard = java.util.Objects.requireNonNull(clipboard);
    }

    private Clipboard clipboard() {
        if (clipboard == null) {
            clipboard = new Clipboard() {
                @Override
                public String read() { return minecraft.keyboardHandler.getClipboard(); }

                @Override
                public void write(String value) {
                    minecraft.keyboardHandler.setClipboard(value);
                    if (!value.equals(minecraft.keyboardHandler.getClipboard())) {
                        throw new IllegalStateException("Clipboard contents did not match the copied selection");
                    }
                }
            };
        }
        return clipboard;
    }

    public JsxHostAdapter(String title, boolean pausesGame) {
        this(title, pausesGame, false, 0, 0, 0, 0, null);
    }

    public static JsxHostAdapter region(int x, int y, int width, int height) {
        validateRegion(x, y, width, height);
        return new JsxHostAdapter("", false, true, x, y, width, height, null);
    }

    static JsxHostAdapter builtin(com.tkisor.nekojs.core.ui.BuiltinUiRuntime.Session session,
            int width, int height, java.util.function.Consumer<RuntimeException> failure) {
        session.requireOwner();
        JsxHostAdapter adapter = new JsxHostAdapter("", false, true, 0, 0, Math.max(1, width), Math.max(1, height), session);
        adapter.builtinFailure = failure;
        return adapter;
    }

    private JsxHostAdapter(String title, boolean pausesGame, boolean regionMount, int x, int y, int width, int height,
            com.tkisor.nekojs.core.ui.BuiltinUiRuntime.Session builtinSession) {
        minecraft = Minecraft.getInstance();
        ownerThread = Thread.currentThread();
        this.regionMount = regionMount;
        regionX = x;
        regionY = y;
        viewportWidth = width;
        viewportHeight = height;
        screen = regionMount ? null : new JsxScreen(title, pausesGame, this);
        this.fontAdapter = new McFontAdapter(minecraft.font);
        this.resources = new MinecraftUiResourceResolver(minecraft.getResourceManager(), minecraft.getTextureManager());
        this.builtinSession = builtinSession;
        if (builtinSession != null) {
            if (builtinSession.isClosed()) throw new IllegalStateException("[NEKO-7015] Built-in UI session is closed");
            this.manager = null;
            this.globals = builtinSession.globals();
            this.lifecycle = new UiRootLifecycle(false, 0, globals);
            globals.registerUiRoot(this);
            return;
        }
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
        Value candidate = root == null ? null : Value.asValue(root);
        if (this.root != null) {
            if (this.root.equals(candidate)) return;
            throw new IllegalStateException("[NEKO-7014] JSX host adapter rejected: another root is already bound");
        }
        this.root = candidate;
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
     * Real glyph measurement for the common runtime: wraps the shared {@link TextLayouter} over
     * this client's font, so the runtime never guesses widths. Wrapping is enabled and
     * truncation is off, which matches the intrinsic-size rule the runtime expects.
     */
    @ScriptOutput("import('java:java/util').$Map<string, unknown> & { readonly width: number; readonly height: number }")
    public Map<String, Object> measureText(String text, double fontSize, int maxWidth) {
        return measureText(text, fontSize, maxWidth, null);
    }

    @ScriptOutput("import('java:java/util').$Map<string, unknown> & { readonly width: number; readonly height: number }")
    public Map<String, Object> measureText(String text, double fontSize, int maxWidth, String font) {
        requireUsable("measure text");
        TextLayout layout = textLayouts.layout(selectedFont(font), font, text, fontSize, maxWidth,
                true, TextLayouter.Truncation.OFF, resources.textLayoutRevision());
        return Map.of("width", layout.width(), "height", layout.height());
    }

    private McFontAdapter selectedFont(String rawFont) {
        if (rawFont == null) return fontAdapter;
        VisualSpec spec = VisualStyleResolver.resolve(Map.of("font", rawFont),
                new UiDiagnostic.Location(rootId == null ? "unknown" : rootId, "label", null, lifecycle.generation()));
        if (spec.font() == null || resources.resolveFont(rawFont).state() != com.tkisor.nekojs.api.ui.ResourceStatus.State.RESOLVED) {
            return fontAdapter;
        }
        net.minecraft.network.chat.Style style = net.minecraft.network.chat.Style.EMPTY.withFont(
                new net.minecraft.network.chat.FontDescription.Resource(
                        net.minecraft.resources.Identifier.parse(spec.font().toString())));
        return new McFontAdapter(minecraft.font, style);
    }

    public boolean isOwnerThread() {
        return Thread.currentThread() == ownerThread;
    }

    private long activeGeneration() { return builtinSession == null ? manager.generationId() : 0; }

    boolean isDisposed() { return lifecycle.isClosed(); }

    public boolean enqueue(Object action) {
        if (action == null) return false;
        // Explicit cross-thread queueing point (the common runtime calls this only from
        // non-owner threads). The queued guest closure may run after a reload committed or
        // discarded this root's generation; re-check the epoch at run time so a stale
        // generation never executes guest code.
        minecraft.execute(() -> {
            if (builtinSession != null && builtinSession.isClosed() || !lifecycle.isUsable(activeGeneration())) {
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
    @ScriptOutput("import('java:java/util').$Map<string, unknown> & import('nekojs/jsx-runtime').NekoUiViewportInput")
    public Map<String, Object> viewport() {
        int width = viewportWidth > 0 ? viewportWidth : minecraft.getWindow().getGuiScaledWidth();
        int height = viewportHeight > 0 ? viewportHeight : minecraft.getWindow().getGuiScaledHeight();
        return Map.of("width", Math.max(1, width), "height", Math.max(1, height));
    }

    /**
     * Inspector output: the retained runtime layout snapshot decorated with the host-only facts
     * (focus, resource statuses, retained phase errors, capture metadata) through the same
     * shared collection path the fake host tests use. Pixel capture itself is auxiliary and not
     * wired here; the metadata names the viewport-derived origin it was synthesized from.
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
                resources::resolveTexture, resources::resolveFont, List.copyOf(retainedErrors),
                new InspectorScreenshot("neoforge-viewport-meta",
                        lastSnapshot.viewport().width(), lastSnapshot.viewport().height()));
    }

    /** Receives the frozen common layout candidate and its snapshot without mutating guest data. */
    public void layout(Object tree, Object viewport, Object snapshot) {
        layout(tree, viewport, snapshot, false);
    }

    public void layout(Object tree, Object viewport, Object snapshot, boolean publish) {
        requireUsable("apply the layout candidate");
        Value envelope = Value.asValue(snapshot);
        String candidateRootId = envelope.hasMember("rootId") && envelope.getMember("rootId").isString()
                ? envelope.getMember("rootId").asString() : rootId;
        if (root != null && !java.util.Objects.equals(rootId, candidateRootId)) {
            throw new IllegalStateException("[NEKO-7014] JSX host adapter rejected: layout belongs to another root");
        }
        layoutCount++;
        if (tree != null) readArray(tree);
        rootId = candidateRootId;
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
        Map<String, Object> liveViewport = viewport();
        prepareVisuals(nodes, candidate.viewport().designScale(),
                Math.min(candidate.viewport().width(), integer(liveViewport.get("width"), candidate.viewport().width())),
                Math.min(candidate.viewport().height(), integer(liveViewport.get("height"), candidate.viewport().height())));
        return new InspectorSnapshot(candidate.rootId(), candidate.source(), candidate.viewport(),
                measured, candidate.diagnostics(), candidate.errors(), candidate.screenshot());
    }

    private void prepareVisuals(List<JsxHostTree.Node> nodes, double designScale, int paintWidth, int paintHeight) {
        for (JsxHostTree.Node node : nodes) {
            node.coordinateScale = "design".equals(node.props.get("coordinateSpace")) ? designScale : 1;
            if (!"#text".equals(node.type)) {
                node.visual = resolveVisual(node.props, node.type,
                        node.key == null ? text(node.props.get("id")) : node.key,
                        rootId == null ? "unknown" : rootId, lifecycle.generation(), resources);
                node.texturePlan = null;
                node.boxPlan = null;
                if ("panel".equals(node.type) || "screen".equals(node.type) || "scroll".equals(node.type)) {
                    int clipX = node.hasClip ? Math.max(0, node.clipX) : 0;
                    int clipY = node.hasClip ? Math.max(0, node.clipY) : 0;
                    long clipRight = node.hasClip ? Math.min((long) paintWidth, (long) node.clipX + node.clipWidth) : paintWidth;
                    long clipBottom = node.hasClip ? Math.min((long) paintHeight, (long) node.clipY + node.clipHeight) : paintHeight;
                    node.boxPlan = UiBoxPaintPlan.prepare(node.x, node.y, node.width, node.height,
                            node.visual.radius() == null ? 0 : node.visual.radius(),
                            node.visual.borderWidth() == null ? 0 : node.visual.borderWidth(),
                            clipX, clipY, (int) Math.max(0, clipRight - clipX), (int) Math.max(0, clipBottom - clipY));
                }
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
                String font = node.props.get("font") instanceof String name ? name : null;
                node.textFont = selectedFont(font);
                boolean truncate = node.visual != null && Boolean.TRUE.equals(node.visual.truncate());
                node.textLayout = textLayouts.layout(node.textFont, font, text(node.props.get("text")), fontSize,
                        Math.max(1, node.width), !truncate, truncationFor(node.visual), resources.textLayoutRevision());
                node.textScale = fontSize <= 0 ? 1 : fontSize / fontAdapter.lineHeight();
            }
            prepareVisuals(node.children, designScale, paintWidth, paintHeight);
        }
    }

    public JsxHostTransaction begin() {
        requireUsable("open a host transaction");
        return new JsxHostTransaction(tree.begin());
    }

    private void recordSuccessfulTransaction() {
        if (tearingDown) return;
        successfulTransactionCount++;
        if (successfulTransactionCount == 1) initialBuildCount++;
        else reconcileCount++;
    }

    /**
     * Returns counters for a retained UI proof fixture. Values are snapshots and have no
     * scheduling or diagnostic side effects.
     */
    public Map<String, Object> performanceCounters() {
        return Map.of(
                "initialBuild", initialBuildCount,
                "layout", layoutCount,
                "reconcile", reconcileCount,
                "resize", resizeCount,
                "paint", (long) screen.paintCount(),
                "diagnostics", diagnosticCount,
                "cleanup", cleanupCount);
    }

    public void reportDiagnostic(Object diagnostic) {
        diagnosticCount++;
        Value envelope = Value.asValue(diagnostic);
        if (builtinFailure != null) {
            builtinFailure.accept(new IllegalStateException("[NEKO-7015] Built-in UI reported a render or host diagnostic: "
                    + text(memberOrNull(envelope, "message"))));
            return;
        }
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
        if (builtinSession != null) return;
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
        if (builtinSession != null && builtinSession.isClosed() && !tearingDown) {
            throw new IllegalStateException("[NEKO-7015] Built-in UI session is closed");
        }
        if (!lifecycle.isUsable(activeGeneration())) {
            throw new IllegalStateException("[NEKO-7001] Cannot " + operation + ": this UI root belongs "
                    + "to generation " + lifecycle.generation() + ", which is superseded or closed "
                    + "(active generation " + activeGeneration() + ", lifecycle state "
                    + lifecycle.state() + ")");
        }
    }

    private static void validateRegion(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0 || (long) x + width > Integer.MAX_VALUE || (long) y + height > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("[NEKO-7014] JSX region requires positive, representable GUI bounds");
        }
    }

    public void setRegion(int x, int y, int width, int height) {
        requireUsable("set region bounds");
        if (!regionMount) throw new IllegalStateException("[NEKO-7014] Screen mounts do not have caller-owned region bounds");
        validateRegion(x, y, width, height);
        regionX = x;
        regionY = y;
        if (viewportWidth != width || viewportHeight != height) resize(width, height);
    }

    private boolean productionRouting() {
        if (!isOwnerThread()) throw new IllegalStateException("[NEKO-7004] JSX mount forwarding requires the client owner thread");
        if (builtinSession != null && builtinSession.isClosed()) return false;
        if (lifecycle.isClosed() || lifecycle.state() == UiRootLifecycle.State.CLOSING) return false;
        long active = activeGeneration();
        lifecycle.observeCommit(active);
        return lifecycle.isProduction(active);
    }

    public boolean isFocused() {
        return productionRouting() && (!regionMount || mountFocused);
    }

    public void setFocused(boolean focused) {
        if (!productionRouting()) return;
        mountFocused = focused;
        if (!focused) clearKeyboardFocus();
    }

    private void clearKeyboardFocus() {
        JsxHostTree.Node current = focused(tree.roots());
        if (current != null) {
            current.focused = false;
            dispatch(current, "blur", Map.of());
        }
    }

    private boolean insideRegion(double x, double y) {
        return !regionMount || x >= regionX && y >= regionY
                && x < (double) regionX + viewportWidth && y < (double) regionY + viewportHeight;
    }

    /** Paints the committed tree; region hosts restore the caller's pose and scissor stack. */
    public void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!productionRouting() || tree.roots().isEmpty()) return;
        if (!regionMount) {
            paintNodes(graphics, mouseX, mouseY);
            return;
        }
        graphics.pose().pushMatrix();
        int previousX = tooltipOffsetX;
        int previousY = tooltipOffsetY;
        try {
            graphics.pose().translate((float) regionX, (float) regionY);
            graphics.enableScissor(0, 0, viewportWidth, viewportHeight);
            try {
                tooltipOffsetX = regionX;
                tooltipOffsetY = regionY;
                paintNodes(graphics, mouseX - regionX, mouseY - regionY);
            } finally {
                graphics.disableScissor();
            }
        } finally {
            tooltipOffsetX = previousX;
            tooltipOffsetY = previousY;
            graphics.pose().popMatrix();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int modifiers) {
        if (!productionRouting()) return false;
        if (!insideRegion(mouseX, mouseY)) {
            setFocused(false);
            return false;
        }
        mountFocused = true;
        return dispatchAt(mouseX - regionX, mouseY - regionY, "click", button, modifiers);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (!productionRouting()) return false;
        if (!insideRegion(mouseX, mouseY)) {
            inputRoots();
            if (tree.captured(button) == null) return false;
        }
        return dispatchDrag(mouseX - regionX, mouseY - regionY, button);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!productionRouting()) return false;
        if (!insideRegion(mouseX, mouseY)) {
            inputRoots();
            if (tree.captured(button) == null) return false;
        }
        return dispatchRelease(mouseX - regionX, mouseY - regionY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return productionRouting() && insideRegion(mouseX, mouseY)
                && dispatchScroll(mouseX - regionX, mouseY - regionY, delta);
    }

    public boolean keyPressed(int key, int modifiers) {
        if (!isFocused()) return false;
        if (regionMount && key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && !modalActive()) {
            List<JsxHostTree.Node> values = new ArrayList<>();
            collectFocusable(inputRoots(), values);
            JsxHostTree.Node current = focused(inputRoots());
            boolean reverse = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
            int index = values.indexOf(current);
            if (values.isEmpty() || current != null && (reverse ? index <= 0 : index >= values.size() - 1)) {
                setFocused(false);
                return false;
            }
        }
        return key(key, modifiers);
    }

    public boolean charTyped(int codepoint) {
        return isFocused() && textInput(codepoint);
    }

    public void resize(int width, int height) {
        requireUsable("resize");
        if (regionMount) validateRegion(regionX, regionY, width, height);
        resizeCount++;
        viewportWidth = Math.max(0, width);
        viewportHeight = Math.max(0, height);
        if (root != null) {
            try {
                if (builtinSession == null) root.invokeMember("resize", viewport());
                else root.invokeMember("resize", graal.graalvm.polyglot.proxy.ProxyObject.fromMap(viewport()));
            } catch (RuntimeException failure) {
                reportHostFailure("resize", failure);
            }
        }
    }

    private void reportHostFailure(String phase, RuntimeException failure) {
        diagnosticCount++;
        if (builtinFailure != null) { builtinFailure.accept(failure); return; }
        ScriptErrorReporter.recordCallbackError(ScriptType.CLIENT, "ui-" + phase,
                new IllegalStateException("[NEKO-7007] JSX UI host operation '" + phase
                        + "' failed (generation " + lifecycle.generation() + ")", failure));
    }

    String screenTitle(String fallback) {
        for (JsxHostTree.Node node : tree.roots()) {
            if ("screen".equals(node.type) && node.props.get("title") instanceof String title) return title;
        }
        return fallback;
    }

    boolean pausesGame(boolean fallback) {
        for (JsxHostTree.Node node : tree.roots()) {
            if ("screen".equals(node.type) && node.props.get("pausesGame") instanceof Boolean pauses) return pauses;
        }
        return fallback;
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
        return narrationText(inputRoots());
    }

    boolean narrationDisabled() {
        return narrationDisabled(inputRoots());
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
        if (label.isEmpty() && "dialog".equals(node.type)) label = text(node.props.get("title"));
        if (label.isEmpty()) label = node.type;
        return label;
    }

    /** Whether the focused host node carries the {@code disabled} prop, so its narration gets the localized marker. */
    static boolean narrationDisabled(List<JsxHostTree.Node> roots) {
        JsxHostTree.Node node = focused(roots);
        return node != null && bool(node.props.get("disabled"));
    }

    void paintNodes(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        inputRoots();
        JsxHostTree.Node hoveredWindow = JsxWorkspaceScope.chromeAt(tree.roots(), mouseX, mouseY);
        String resizeEdge = null;
        if (windowResize != null) resizeEdge = windowResize.edge();
        else if (tree.captured(0) == null && hoveredWindow != null) {
            resizeEdge = JsxWindowChrome.resizeEdge(hoveredWindow, mouseX, mouseY);
        }
        if (resizeEdge != null) {
            // Minecraft owns these cursor handles and resets the request each frame, including after close.
            graphics.requestCursor(switch (resizeEdge) {
                case "e", "w" -> CursorTypes.RESIZE_EW;
                case "n", "s" -> CursorTypes.RESIZE_NS;
                default -> CursorTypes.RESIZE_ALL;
            });
        }
        List<JsxHostTree.Node> dialogs = JsxModalScope.dialogs(tree.roots());
        int backgroundMouse = Integer.MIN_VALUE;
        for (JsxHostTree.Node node : tree.roots()) {
            paintNode(graphics, node, dialogs.isEmpty() ? mouseX : backgroundMouse,
                    dialogs.isEmpty() ? mouseY : backgroundMouse);
        }
        for (int index = 0; index < dialogs.size(); index++) {
            JsxHostTree.Node dialog = dialogs.get(index);
            boolean top = index == dialogs.size() - 1;
            if (dialog.hasClip) graphics.enableScissor(dialog.clipX, dialog.clipY,
                    dialog.clipX + dialog.clipWidth, dialog.clipY + dialog.clipHeight);
            try {
                graphics.fill(dialog.x, dialog.y, dialog.x + dialog.width, dialog.y + dialog.height, 0xA0000000);
                for (JsxHostTree.Node child : dialog.children) {
                    paintNode(graphics, child, top ? mouseX : backgroundMouse, top ? mouseY : backgroundMouse);
                }
            } finally {
                if (dialog.hasClip) graphics.disableScissor();
            }
        }
    }

    void paintNode(net.minecraft.client.gui.GuiGraphicsExtractor graphics, JsxHostTree.Node node, int mouseX, int mouseY) {
        if (node.removed || !visible(node) || "dialog".equals(node.type)) return;
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
        if ("window".equals(type)) {
            JsxHostTree.Node hoveredWindow = JsxWorkspaceScope.chromeAt(tree.roots(), mouseX, mouseY);
            if (hoveredWindow == null || hoveredWindow.identity != node.identity) {
                mouseX = Integer.MIN_VALUE;
                mouseY = Integer.MIN_VALUE;
            }
            graphics.fill(x, y, x + width, y + height, 0xFF20252B);
            int titlebarHeight = JsxWindowChrome.titlebarHeight(node);
            if (titlebarHeight > 0) {
                graphics.fill(x, y, x + width, y + titlebarHeight,
                        bool(node.props.get("active")) ? 0xFF3A536F : 0xFF30363D);
                List<JsxWindowChrome.Control> controls = JsxWindowChrome.controls(node);
                JsxWindowChrome.Control hoveredControl = tree.captured(0) == null
                        ? JsxWindowChrome.controlAt(node, mouseX, mouseY) : null;
                int titleRight = controls.isEmpty() ? x + width : controls.getLast().x();
                int textY = y + Math.max(0, (titlebarHeight - minecraft.font.lineHeight) / 2 + 1);
                graphics.enableScissor(x, y, titleRight, y + titlebarHeight);
                try {
                    if (!Boolean.FALSE.equals(node.props.get("titlebarTitle"))) {
                        graphics.text(minecraft.font, text(node.props.get("title")), x + 4, textY, 0xFFFFFFFF, false);
                    }
                } finally {
                    graphics.disableScissor();
                }
                for (JsxWindowChrome.Control control : controls) {
                    graphics.enableScissor(control.x(), y, control.x() + control.width(), y + titlebarHeight);
                    try {
                        if (control.equals(hoveredControl)) {
                            graphics.fill(control.x(), y, control.x() + control.width(), y + titlebarHeight,
                                    "dismiss".equals(control.event()) ? 0xFFE81123 : 0xFF5A6D82);
                        }
                        int textX = control.x() + Math.max(0, (control.width() - minecraft.font.width(control.glyph())) / 2);
                        graphics.text(minecraft.font, control.glyph(), textX, textY, 0xFFFFFFFF, false);
                    } finally {
                        graphics.disableScissor();
                    }
                }
            }
        } else if ("panel".equals(type) || "screen".equals(type) || "scroll".equals(type)) {
            VisualSpec spec = resolveVisual(node);
            if (node.boxPlan != null) node.boxPlan.paint(graphics::fill,
                    argb(spec.background(), 0xB0101010), argb(spec.borderColor(), 0xFFFFFFFF),
                    spec.opacity() == null ? 1 : spec.opacity());
        } else if ("image".equals(type)) {
            VisualSpec spec = resolveVisual(node);
            if (node.texturePlan != null) {
                node.texturePlan.paint(graphics);
            } else {
                graphics.fill(x, y, x + width, y + height, applyOpacity(spec, argb(spec.background(), 0xFF1A1D22)));
                graphics.outline(x, y, width, height, applyOpacity(spec, argb(spec.borderColor(), 0xFF707780)));
            }
        } else if ("item".equals(type)) {
            if (node.itemVisual != null && width > 0 && height > 0
                    && (!node.hasClip || node.clipWidth > 0 && node.clipHeight > 0)) {
                node.itemVisual.paint(graphics, minecraft.font,
                        x + (width - 16) / 2, y + (height - 16) / 2);
            }
        } else if ("button".equals(type) || "input".equals(type) || "slider".equals(type)) {
            paintControl(graphics, node, mouseX, mouseY);
        } else if ("label".equals(type)) {
            VisualSpec spec = resolveVisual(node);
            paintTextLines(graphics, node, x, y, applyOpacity(spec, argb(spec.color(), 0xFFFFFFFF)));
        }
        if ("scroll".equals(type)) {
            JsxScrollBar.Geometry bar = JsxScrollBar.geometry(node);
            boolean overBar = bar != null && contains(node, mouseX, mouseY) && bar.contains(mouseX, mouseY);
            graphics.enableScissor(x, y, x + width, y + height);
            try {
                for (JsxHostTree.Node child : node.children) {
                    paintNode(graphics, child, overBar ? Integer.MIN_VALUE : mouseX, overBar ? Integer.MIN_VALUE : mouseY);
                }
                if (bar != null) {
                    graphics.fill(bar.x(), bar.y(), bar.x() + bar.width(), bar.y() + bar.height(), 0xFF252D38);
                    boolean dragging = scrollDrag != null && scrollDrag.identity() == node.identity && scrollDrag.thumb();
                    graphics.fill(bar.thumbX(), bar.thumbY(), bar.thumbX() + bar.thumbWidth(), bar.thumbY() + bar.thumbHeight(),
                            dragging ? 0xFFB4C7DD : overBar ? 0xFF9BAEC4 : 0xFF7D8A9A);
                }
            } finally {
                graphics.disableScissor();
            }
            return;
        }
        for (JsxHostTree.Node child : node.children) paintNode(graphics, child, mouseX, mouseY);
        if ("window".equals(type)) {
            paintWindowOutline(graphics, node);
            String edge = null;
            if (windowResize != null && windowResize.identity() == node.identity) edge = windowResize.edge();
            else if (tree.captured(0) == null) edge = JsxWindowChrome.resizeEdge(node, mouseX, mouseY);
            if (edge != null) {
                if (edge.contains("n")) graphics.fill(x, y, x + width, y + Math.min(2, height), 0xFF80BFFF);
                if (edge.contains("s")) graphics.fill(x, y + Math.max(0, height - 2), x + width, y + height, 0xFF80BFFF);
                if (edge.contains("w")) graphics.fill(x, y, x + Math.min(2, width), y + height, 0xFF80BFFF);
                if (edge.contains("e")) graphics.fill(x + Math.max(0, width - 2), y, x + width, y + height, 0xFF80BFFF);
            }
        }
        if (contains(node, mouseX, mouseY) && node.props.get("tooltip") != null) {
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font,
                    net.minecraft.network.chat.Component.literal(text(node.props.get("tooltip"))),
                    mouseX + tooltipOffsetX, mouseY + tooltipOffsetY);
        } else if ("item".equals(type) && node.itemVisual != null
                && !Boolean.FALSE.equals(node.props.get("itemTooltip")) && contains(node, mouseX, mouseY)) {
            node.itemVisual.tooltip(graphics, minecraft.font, mouseX + tooltipOffsetX, mouseY + tooltipOffsetY);
        }
    }

    private void paintControl(net.minecraft.client.gui.GuiGraphicsExtractor graphics, JsxHostTree.Node node,
                              int mouseX, int mouseY) {
        int x = node.x;
        int y = node.y;
        int width = node.width;
        int height = node.height;
        boolean scaled = node.coordinateScale != 1;
        if (scaled) {
            // Use design units for every control detail; hit testing keeps the committed logical rectangle.
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) x, (float) y);
            graphics.pose().scale((float) node.coordinateScale, (float) node.coordinateScale);
            x = 0;
            y = 0;
            width = node.controlWidth();
            height = (int) Math.round(height / node.coordinateScale);
        }
        try {
            paintControlContents(graphics, node, mouseX, mouseY, x, y, width, height);
        } finally {
            if (scaled) graphics.pose().popMatrix();
        }
    }

    private void paintControlContents(net.minecraft.client.gui.GuiGraphicsExtractor graphics, JsxHostTree.Node node,
                                      int mouseX, int mouseY, int x, int y, int width, int height) {
        if ("button".equals(node.type)) {
            boolean disabled = bool(node.props.get("disabled"));
            boolean hovered = contains(node, mouseX, mouseY);
            Map<String, UiColor> colors = node.appearance.resolve(hovered, node.focused, node.pressed, disabled);
            int fill = disabled ? 0xFF404040 : node.pressed ? 0xFF29415C : hovered ? 0xFF5A7FA8 : 0xFF3A536F;
            graphics.fill(x, y, x + width, y + height, controlColor(colors, "background", fill));
            graphics.outline(x, y, width, height,
                    controlColor(colors, "borderColor", node.focused ? 0xFFFFFFFF : 0xFF9AA7B5));
            graphics.centeredText(minecraft.font, text(node.props.get("text")), x + width / 2,
                    y + Math.max(0, (height - minecraft.font.lineHeight) / 2),
                    controlColor(colors, "color", disabled ? 0xFF888888 : 0xFFFFFFFF));
        } else if ("input".equals(node.type)) {
            paintInput(graphics, node, mouseX, mouseY, x, y, width, height);
        } else if ("slider".equals(node.type)) {
            Map<String, UiColor> colors = node.appearance.resolve(contains(node, mouseX, mouseY),
                    node.focused, node.pressed, bool(node.props.get("disabled")));
            graphics.fill(x, y, x + width, y + height, controlColor(colors, "background", 0xFF20252B));
            graphics.outline(x, y, width, height,
                    controlColor(colors, "borderColor", node.focused ? 0xFFFFFFFF : 0xFF707780));
            int centerY = y + height / 2;
            graphics.fill(x + 4, centerY - 1, Math.max(x + 4, x + width - 4), centerY + 1,
                    controlColor(colors, "trackColor", 0xFF707780));
            double ratio = (node.sliderValue - node.sliderRange.min()) / (node.sliderRange.max() - node.sliderRange.min());
            int thumbX = x + 4 + (int) Math.round(ratio * Math.max(0, width - 8));
            graphics.fill(thumbX - 2, y + 3, thumbX + 2, Math.max(y + 3, y + height - 3),
                    controlColor(colors, "thumbColor", bool(node.props.get("disabled")) ? 0xFF888888 : 0xFFFFFFFF));
        }
    }

    private void paintWindowOutline(net.minecraft.client.gui.GuiGraphicsExtractor graphics, JsxHostTree.Node node) {
        if (node.width <= 0 || node.height <= 0) return;
        int scale = minecraft.getWindow() == null ? 1 : Math.max(1, minecraft.getWindow().getGuiScale());
        // Cancel GUI scaling locally so the passive outline stays one framebuffer pixel wide.
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate((float) node.x, (float) node.y);
            graphics.pose().scale(1.0f / scale);
            graphics.outline(0, 0, node.width * scale, node.height * scale,
                    bool(node.props.get("active")) ? 0xFF697582 : 0xFF454D57);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static int controlColor(Map<String, UiColor> colors, String key, int fallback) {
        UiColor color = colors.get(key);
        return color == null ? fallback : color.argb();
    }

    private void paintInput(net.minecraft.client.gui.GuiGraphicsExtractor graphics, JsxHostTree.Node node,
            int mouseX, int mouseY, int x, int y, int width, int height) {
        Map<String, UiColor> colors = node.appearance.resolve(contains(node, mouseX, mouseY),
                node.focused, node.pressed, bool(node.props.get("disabled")));
        graphics.fill(x, y, x + width, y + height, controlColor(colors, "background", 0xFF20252B));
        graphics.outline(x, y, width, height,
                controlColor(colors, "borderColor", node.focused ? 0xFFFFFFFF : 0xFF707780));
        node.followInputCursor(fontAdapter::stringWidth);
        int left = x + 4;
        int right = Math.max(left, x + width - 4);
        int top = y + 4;
        int bottom = Math.max(top, y + height - 4);
        graphics.enableScissor(left, top, right, bottom);
        try {
            String input = node.inputValue;
            boolean empty = input.isEmpty();
            if (node.focused && !empty && node.hasSelection()) {
                int selectionX = left + fontAdapter.stringWidth(input.substring(0, node.selectionStart())) - node.inputScroll;
                int selectionEndX = left + fontAdapter.stringWidth(input.substring(0, node.selectionEnd())) - node.inputScroll;
                graphics.fill(Math.max(left, Math.min(right, selectionX)), top,
                        Math.max(left, Math.min(right, selectionEndX)), bottom,
                        controlColor(colors, "selectionColor", 0xFF4A6A95));
            }
            graphics.text(minecraft.font, empty ? text(node.props.get("placeholder")) : input,
                    left - node.inputScroll, y + 6, controlColor(colors, empty ? "placeholderColor" : "color",
                            empty ? 0xFF888888 : 0xFFFFFFFF), false);
            if (node.focused) {
                int cursorX = left + fontAdapter.stringWidth(input.substring(0, node.cursor)) - node.inputScroll;
                graphics.fill(cursorX, top, cursorX + 1, bottom, controlColor(colors, "cursorColor", 0xFFFFFFFF));
            }
        } finally {
            graphics.disableScissor();
        }
    }

    /**
     * Resolves one node's visual props through the shared {@link VisualStyleResolver} and reports
     * every produced {@link UiDiagnostic} through {@link ScriptErrorReporter}. Static and free of
     * Minecraft types, so the resolution rules are testable without a live client.
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
        if (node.textLayout == null || node.textLayout.lineHeight() <= 0 || node.textScale <= 0) return;
        int firstLine = 0;
        int endLine = node.textLayout.lines().size();
        double lineHeight = node.textFont.lineHeight() * node.textScale;
        if (node.hasClip) {
            firstLine = Math.max(0, (int) Math.floor(((double) node.clipY - y - lineHeight) / lineHeight));
            endLine = (int) Math.min(endLine, Math.ceil(((double) node.clipY + node.clipHeight - y) / lineHeight) + 1);
        }
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate((float) x, (float) y);
            graphics.pose().scale((float) node.textScale, (float) node.textScale);
            // Bound submissions to the visible line window; the native scissor alone still pays for every glyph.
            for (int lineIndex = firstLine; lineIndex < endLine; lineIndex++) {
                // Advance in base-font units and apply the pose scale once; rounded logical gaps accumulate drift.
                int baseline = lineIndex * node.textFont.lineHeight();
                double top = y + baseline * node.textScale;
                if (node.hasClip && (top >= (double) node.clipY + node.clipHeight
                        || top + lineHeight <= node.clipY)) continue;
                graphics.text(Minecraft.getInstance().font, node.textFont.text(node.textLayout.lines().get(lineIndex)),
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

    boolean dispatchAt(double mouseX, double mouseY, String eventName, int button, int modifiers) {
        requireUsable("route a mouse event");
        List<JsxHostTree.Node> scope = inputRoots();
        JsxHostTree.Node modal = JsxModalScope.active(tree.roots());
        JsxHostTree.Node pointerWindow = null;
        JsxHostTree.Node presentation = modal == null
                ? JsxWorkspaceScope.presentationAt(tree.roots(), mouseX, mouseY) : null;
        if (presentation != null) {
            if (bool(presentation.props.get("disabled"))) return true;
            scope = List.of(presentation);
        } else if (modal == null && JsxWorkspaceScope.workspace(tree.roots()) != null) {
            pointerWindow = JsxWorkspaceScope.windowAt(tree.roots(), mouseX, mouseY);
            if (pointerWindow == null) return false;
            if (bool(pointerWindow.props.get("disabled"))) return true;
            if (!bool(pointerWindow.props.get("active"))) {
                dispatch(pointerWindow, "activate", Map.of());
                JsxHostTree.Node retained = tree.retained(pointerWindow);
                JsxHostTree.Node top = JsxWorkspaceScope.windowAt(tree.roots(), mouseX, mouseY);
                if (retained == null || top == null || top.identity != retained.identity
                        || !bool(retained.props.get("active"))) return true;
                pointerWindow = retained;
                inputRoots();
                if (JsxModalScope.active(tree.roots()) != null
                        || JsxWorkspaceScope.presentationAt(tree.roots(), mouseX, mouseY) != null) return true;
            }
            scope = List.of(pointerWindow);
            JsxWindowChrome.Control control = JsxWindowChrome.controlAt(pointerWindow, mouseX, mouseY);
            String resizeEdge = JsxWindowChrome.resizeEdge(pointerWindow, mouseX, mouseY);
            if (resizeEdge != null && control == null && button == 0 && "click".equals(eventName)) {
                JsxHostTree.Node workspace = JsxWorkspaceScope.workspace(tree.roots());
                windowResize = new JsxWindowChrome.ResizeCapture(pointerWindow.identity, resizeEdge, mouseX, mouseY,
                        (double) pointerWindow.x - workspace.x, (double) pointerWindow.y - workspace.y,
                        pointerWindow.width, pointerWindow.height);
                tree.capture(pointerWindow, button);
                return true;
            }
            if (mouseY < pointerWindow.y + JsxWindowChrome.titlebarHeight(pointerWindow)) {
                if (button == 0 && "click".equals(eventName)) {
                    if (control != null) {
                        dispatch(pointerWindow, control.event(), "dismiss".equals(control.event())
                                ? Map.of("reason", "close") : Map.of());
                    } else if (!Boolean.FALSE.equals(pointerWindow.props.get("draggable"))
                            && !JsxWindowChrome.maximized(pointerWindow)) {
                        windowResize = null;
                        windowDragOffsetX = mouseX - pointerWindow.x;
                        windowDragOffsetY = mouseY - pointerWindow.y;
                        tree.capture(pointerWindow, button);
                    }
                }
                return true;
            }
        }
        JsxHostTree.Node node = hit(scope, mouseX, mouseY);
        if (pointerWindow != null && (node == null || node.identity == pointerWindow.identity)) return true;
        if (modal != null && (node == null || node.identity == modal.identity)) {
            if (button == 0 && "click".equals(eventName)
                    && !Boolean.FALSE.equals(modal.props.get("closeOnBackdrop"))) dismiss(modal, "backdrop");
            return true;
        }
        if (node == null || bool(node.props.get("disabled"))) return modal != null || pointerWindow != null || presentation != null;
        JsxScrollBar.Geometry bar = JsxScrollBar.geometry(node);
        if (bar != null && bar.contains(mouseX, mouseY) && button == 0 && "click".equals(eventName)) {
            tree.capture(node, button);
            windowResize = null;
            double position = bar.position(mouseX, mouseY);
            if (bar.onThumb(position)) {
                scrollDrag = new JsxScrollBar.Capture(node.identity, bar.horizontal(), true,
                        (position - bar.thumbStart()) / bar.thumbLength());
            } else {
                scrollDrag = new JsxScrollBar.Capture(node.identity, bar.horizontal(), false, 0);
                double current = number(node.props.get("scrollOffset"), 0);
                double page = (bar.horizontal() ? node.width : node.height) / node.coordinateScale;
                setScrollOffset(node, current + (position < bar.thumbStart() ? -page : page), mouseX, mouseY);
            }
            return true;
        }
        if ("click".equals(eventName)) {
            if (("input".equals(node.type) || "slider".equals(node.type)) && button != 0) return modal != null || presentation != null;
            windowResize = null;
            tree.capture(node, button);
            if ("input".equals(node.type)) {
                node.moveCursor(inputPosition(node, mouseX),
                        (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0);
            }
            if (isFocusable(node)) focus(node);
            node = tree.captured(button);
            if (node == null) return true;
            if ("slider".equals(node.type)) {
                updateSlider(node, node.sliderAt(mouseX), true);
                node = tree.captured(button);
                if (node == null) return true;
            }
        }
        boolean callbackHandled = dispatch(node, eventName, Map.of("x", mouseX, "y", mouseY, "button", button));
        return callbackHandled || isFocusable(node) || modal != null || pointerWindow != null || presentation != null;
    }

    private int inputPosition(JsxHostTree.Node node, double mouseX) {
        node.followInputCursor(fontAdapter::stringWidth);
        double relativeX = (mouseX - node.x) / node.coordinateScale - 4 + node.inputScroll;
        int position = 0;
        int previousWidth = 0;
        while (position < node.inputValue.length()) {
            int next = node.inputValue.offsetByCodePoints(position, 1);
            int nextWidth = fontAdapter.stringWidth(node.inputValue.substring(0, next));
            if (relativeX < (previousWidth + (double) nextWidth) / 2) return position;
            position = next;
            previousWidth = nextWidth;
        }
        return position;
    }

    boolean dispatchDrag(double mouseX, double mouseY, int button) {
        requireUsable("route a mouse drag");
        inputRoots();
        JsxHostTree.Node node = tree.captured(button);
        if (node == null || button != 0) return modalActive();
        if (scrollDrag != null && scrollDrag.identity() == node.identity) {
            JsxScrollBar.Geometry bar = JsxScrollBar.geometry(node);
            if (scrollDrag.thumb() && bar != null) {
                setScrollOffset(node, bar.offsetAt(bar.position(mouseX, mouseY), scrollDrag.grabFraction(), node.scrollRange),
                        mouseX, mouseY);
            }
            return true;
        }
        if ("window".equals(node.type)) {
            JsxHostTree.Node workspace = JsxWorkspaceScope.workspace(tree.roots());
            if (workspace == null || JsxWindowChrome.maximized(node)) {
                tree.cancelCapture();
                windowResize = null;
                return true;
            }
            if (windowResize != null && windowResize.identity() == node.identity) {
                if (!JsxWindowChrome.resizable(node)) {
                    tree.cancelCapture();
                    windowResize = null;
                    return true;
                }
                dispatch(node, "resize", windowResize.request(mouseX, mouseY));
            } else if (!Boolean.FALSE.equals(node.props.get("draggable")) && JsxWindowChrome.titlebarHeight(node) > 0) {
                dispatch(node, "move", Map.of("x", mouseX - windowDragOffsetX - workspace.x,
                        "y", mouseY - windowDragOffsetY - workspace.y));
            } else {
                tree.cancelCapture();
            }
            return true;
        }
        if ("slider".equals(node.type)) {
            updateSlider(node, node.sliderAt(mouseX), true);
            return true;
        }
        if (!"input".equals(node.type)) return false;
        if (!node.focused) {
            tree.cancelCapture();
            return false;
        }
        node.moveCursor(inputPosition(node, mouseX), true);
        return true;
    }

    boolean dispatchRelease(double mouseX, double mouseY, int button) {
        requireUsable("route a mouse release");
        inputRoots();
        JsxHostTree.Node node = tree.releaseCapture(button);
        if (node != null && scrollDrag != null && scrollDrag.identity() == node.identity && button == 0) {
            JsxScrollBar.Geometry bar = JsxScrollBar.geometry(node);
            JsxScrollBar.Capture drag = scrollDrag;
            scrollDrag = null;
            if (bar != null && drag.thumb()) {
                setScrollOffset(node, bar.offsetAt(bar.position(mouseX, mouseY), drag.grabFraction(), node.scrollRange),
                        mouseX, mouseY);
            }
            return true;
        }
        if (node != null && windowResize != null && windowResize.identity() == node.identity) windowResize = null;
        if (node == null || node.removed) return modalActive();
        if (button == 0 && "input".equals(node.type) && node.focused) {
            node.moveCursor(inputPosition(node, mouseX), true);
        }
        if (button == 0 && "slider".equals(node.type)) {
            updateSlider(node, node.sliderAt(mouseX), true);
            node = tree.retained(node);
            if (node == null) return true;
        }
        dispatch(node, "release", Map.of("x", mouseX, "y", mouseY, "button", button));
        return true;
    }

    boolean dispatchScroll(double mouseX, double mouseY, double delta) {
        requireUsable("route a scroll event");
        List<JsxHostTree.Node> scope = inputRoots();
        JsxHostTree.Node window = null;
        JsxHostTree.Node presentation = modalActive() ? null
                : JsxWorkspaceScope.presentationAt(tree.roots(), mouseX, mouseY);
        if (presentation != null) {
            if (bool(presentation.props.get("disabled"))) return true;
            scope = List.of(presentation);
        } else if (!modalActive() && JsxWorkspaceScope.workspace(tree.roots()) != null) {
            window = JsxWorkspaceScope.windowAt(tree.roots(), mouseX, mouseY);
            if (window == null) return false;
            if (!bool(window.props.get("active")) || bool(window.props.get("disabled"))) return true;
            scope = List.of(window);
        }
        JsxHostTree.Node node = scrollHit(scope, mouseX, mouseY);
        if (node == null) return modalActive() || window != null || presentation != null;
        double current = number(node.props.get("scrollOffset"), 0);
        double next = clamp(current - delta * 12, 0, node.scrollRange);
        node.props.put("scrollOffset", next);
        relayout();
        boolean callbackHandled = dispatch(node, "scroll", Map.of("x", mouseX, "y", mouseY, "delta", delta));
        return callbackHandled || node.scrollRange > 0 || modalActive() || presentation != null;
    }

    private void setScrollOffset(JsxHostTree.Node node, double offset, double mouseX, double mouseY) {
        double previous = number(node.props.get("scrollOffset"), 0);
        double next = clamp(offset, 0, node.scrollRange);
        if (next == previous) return;
        node.props.put("scrollOffset", next);
        relayout();
        dispatch(tree.retained(node), "scroll", Map.of("x", mouseX, "y", mouseY, "delta", (previous - next) / 12));
    }

    boolean key(int key, int modifiers) {
        requireUsable("route a key event");
        List<JsxHostTree.Node> scope = inputRoots();
        JsxHostTree.Node modal = JsxModalScope.active(tree.roots());
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && modal != null) {
            if (!Boolean.FALSE.equals(modal.props.get("closeOnEscape"))) dismiss(modal, "escape");
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && closeOnEscape()) {
            if (screen == null) close();
            else screen.onClose();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            focusNext((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        JsxHostTree.Node focused = focused(scope);
        if (focused == null) return modal != null;
        String keyName = keyName(key);
        if (bool(focused.props.get("disabled"))) return false;
        if ("slider".equals(focused.type) && editSlider(focused, key)) {
            focused = tree.retained(focused);
            if (focused != null) dispatch(focused, "key", Map.of("key", keyName));
            return true;
        }
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
        return dispatch(focused, "key", Map.of("key", keyName)) || modal != null;
    }

    private void dismiss(JsxHostTree.Node modal, String reason) {
        tree.cancelCapture();
        dispatch(modal, "dismiss", Map.of("reason", reason));
    }

    private void updateSlider(JsxHostTree.Node node, double value, boolean snap) {
        if (node.updateSlider(value, snap)) {
            dispatch(node, "change", Map.of("value", Double.toString(node.sliderValue)));
        }
    }

    private boolean editSlider(JsxHostTree.Node node, int key) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) updateSlider(node, node.sliderRange.min(), false);
        else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_END) updateSlider(node, node.sliderRange.max(), false);
        else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            updateSlider(node, node.sliderValue - node.sliderRange.step(), true);
        } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT || key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
            updateSlider(node, node.sliderValue + node.sliderRange.step(), true);
        } else return false;
        return true;
    }

    private boolean editInput(JsxHostTree.Node node, int key, int modifiers) {
        boolean shift = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
        boolean control = (modifiers & (org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL
                | org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER)) != 0;
        if (control && (key == org.lwjgl.glfw.GLFW.GLFW_KEY_C || key == org.lwjgl.glfw.GLFW.GLFW_KEY_X)) {
            if (!node.hasSelection()) return true;
            try {
                clipboard().write(node.inputValue.substring(node.selectionStart(), node.selectionEnd()));
            } catch (RuntimeException failure) {
                reportHostFailure("clipboard-write", failure);
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_X) {
                node.replaceSelection("");
                publishInput(node);
            }
            return true;
        }
        if (control && key == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
            String pasted;
            try {
                pasted = clipboard().read();
            } catch (RuntimeException failure) {
                reportHostFailure("clipboard-read", failure);
                return true;
            }
            String previous = node.inputValue;
            if (pasted != null) node.insertText(pasted, integer(node.props.get("maxLength"), Integer.MAX_VALUE));
            if (!previous.equals(node.inputValue)) publishInput(node);
            return true;
        }
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
        JsxHostTree.Node focused = focused(inputRoots());
        if (focused == null || !"input".equals(focused.type) || bool(focused.props.get("disabled"))) return modalActive();
        String next = new String(Character.toChars(codepoint));
        int maxLength = integer(focused.props.get("maxLength"), Integer.MAX_VALUE);
        focused.insertText(next, maxLength);
        publishInput(focused);
        return true;
    }

    public void open() {
        if (!isOwnerThread()) throw new IllegalStateException("[NEKO-7004] JSX Screen must open on the client owner thread");
        if (regionMount) {
            requireUsable("open the region mount");
            return;
        }
        long active = activeGeneration();
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
            minecraft.schedule(this::runDeferredOpen);
            return;
        }
        throw new IllegalStateException("[NEKO-7001] Cannot open the JSX Screen: this UI root belongs "
                + "to generation " + lifecycle.generation() + ", which is superseded or closed "
                + "(active generation " + active + ")");
    }

    private void runDeferredOpen() {
        long active = activeGeneration();
        // A discarded candidate has already closed this root, so observing it would throw; dropping the
        // deferred open below is the intended outcome.
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

    /** Closes the mount only after the guest accepts its clear transaction; false permits retry. */
    public boolean close() {
        if (!isOwnerThread()) throw new IllegalStateException("[NEKO-7004] JSX mount must close on the client owner thread");
        if (lifecycle.isClosed()) return true;
        if (tearingDown) return false;
        tearingDown = true;
        try {
            if (root != null) {
                Value result = root.invokeMember("close");
                if (!result.isBoolean() || !result.asBoolean()) return false;
            }
        } catch (RuntimeException failure) {
            reportHostFailure("close", failure);
            return false;
        } finally {
            tearingDown = false;
        }
        teardown("script-close", false, true);
        return true;
    }

    /**
     * Closes a mount when its generation ends. {@link GenerationGlobals#close()} calls this
     * exactly once per root on the owner thread, whichever path ends the generation: commit
     * supersede, candidate discard, reset or manager close. Guest dependencies are released
     * while its context is still available; native cleanup always runs.
     */
    @Override
    public void closeForGeneration(String reason) {
        teardown(reason, true, true);
    }

    /** Irreversibly disposes this mount, even if the guest cannot clear its host transaction. */
    public void dispose() {
        if (!isOwnerThread()) throw new IllegalStateException("[NEKO-7004] JSX mount must dispose on the client owner thread");
        teardown("dispose", true, true);
    }

    void removedFromHost() {
        teardown("host-removed", true, false);
    }

    private void teardown(String reason, boolean notifyGuest, boolean dismissScreen) {
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
                    if (root.canInvokeMember("forceClose")) root.invokeMember("forceClose");
                } catch (RuntimeException failure) {
                    reportHostFailure("close", failure);
                }
            }
            tree.cancelCapture();
            windowResize = null;
            lifecycle.beginClose();
            try {
                try {
                    if (dismissScreen && screen != null && McClientCompat.get().currentScreen() == screen) {
                        McClientCompat.get().showScreen(null);
                    }
                } catch (RuntimeException failure) {
                    reportHostFailure("dismiss", failure);
                } finally {
                    tree.close(() -> { });
                    modalScope = null;
                    root = null;
                    layoutBasis = null;
                    lastSnapshot = null;
                    pendingSnapshot = null;
                }
            } finally {
                try {
                    textLayouts.clear();
                    resources.close();
                } finally {
                    lifecycle.finishClose();
                    cleanupCount++;
                    globals.unregisterUiRoot(this);
                }
            }
        } finally {
            tearingDown = false;
        }
    }

    private boolean dispatch(JsxHostTree.Node node, String eventName, Map<String, Object> input) {
        if (root == null || node == null || node.removed || node.props.get("id") == null) return false;
        JsxHostTree.Node modal = JsxModalScope.active(tree.roots());
        if (modal != null && !JsxModalScope.contains(modal, node)) return false;
        try {
            Object eventInput = builtinSession == null ? input
                    : graal.graalvm.polyglot.proxy.ProxyObject.fromMap(new LinkedHashMap<>(input));
            Value result = root.invokeMember("dispatch", text(node.props.get("id")), eventName, eventInput);
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
        JsxHostTree.Node retained = tree.retained(next);
        if (retained != null && isFocusable(retained)) {
            retained.focused = true;
            dispatch(retained, "focus", Map.of());
        }
    }

    private void focusNext(boolean reverse) {
        List<JsxHostTree.Node> focusable = new ArrayList<>();
        collectFocusable(inputRoots(), focusable);
        if (focusable.isEmpty()) return;
        JsxHostTree.Node current = focused(tree.roots());
        int index = current == null ? (reverse ? 0 : -1) : focusable.indexOf(current);
        int next = (index + (reverse ? -1 : 1) + focusable.size()) % focusable.size();
        focus(focusable.get(next));
    }

    private void collectFocusable(List<JsxHostTree.Node> values, List<JsxHostTree.Node> output) {
        for (JsxHostTree.Node node : values) {
            if (!visible(node)) continue;
            if (isFocusable(node) && !bool(node.props.get("disabled"))) output.add(node);
            collectFocusable(node.children, output);
        }
    }

    boolean inputFocused() {
        JsxHostTree.Node node = focused(inputRoots());
        return node != null && "input".equals(node.type);
    }

    boolean focusControl(String id) {
        requireUsable("focus an internal UI control");
        List<JsxHostTree.Node> candidates = new ArrayList<>();
        collectFocusable(inputRoots(), candidates);
        for (JsxHostTree.Node node : candidates) {
            if (id.equals(node.props.get("id"))) { focus(node); return true; }
        }
        return false;
    }

    private static JsxHostTree.Node focused(List<JsxHostTree.Node> values) {
        for (JsxHostTree.Node node : values) {
            if (!visible(node)) continue;
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
            if (!visible(node)) continue;
            if (node.focused && node.props.get("id") instanceof String id) ids.add(id);
            collectFocusedIds(node.children, ids);
        }
    }

    private static JsxHostTree.Node hit(List<JsxHostTree.Node> values, double x, double y) {
        for (int i = values.size() - 1; i >= 0; i--) {
            JsxHostTree.Node node = values.get(i);
            if (!visible(node) || !contains(node, x, y)) continue;
            JsxScrollBar.Geometry bar = JsxScrollBar.geometry(node);
            if (bar != null && bar.contains(x, y)) return node;
            JsxHostTree.Node child = hit(node.children, x, y);
            return child == null ? node : child;
        }
        return null;
    }

    static JsxHostTree.Node scrollHit(List<JsxHostTree.Node> values, double x, double y) {
        for (int i = values.size() - 1; i >= 0; i--) {
            JsxHostTree.Node node = values.get(i);
            if (!visible(node) || !contains(node, x, y)) continue;
            JsxScrollBar.Geometry bar = JsxScrollBar.geometry(node);
            if (bar != null && bar.contains(x, y)) return node;
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
        return "button".equals(node.type) || "input".equals(node.type) || "slider".equals(node.type);
    }

    private void relayout() {
        if (layoutBasis == null) return;
        JsxHostTree.Transaction transaction = tree.begin();
        final InspectorSnapshot[] next = new InspectorSnapshot[1];
        try {
            transaction.commit(tree.roots(), nodes -> next[0] = projected(layoutBasis, nodes));
            lastSnapshot = next[0];
            layoutCount++;
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
    private static String keyName(int key) {
        return switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> "left";
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> "right";
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> "up";
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> "down";
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> "home";
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> "end";
            default -> {
                String name = org.lwjgl.glfw.GLFW.glfwGetKeyName(key, 0);
                yield name == null ? Integer.toString(key) : name;
            }
        };
    }

    /** Converts one guest props object into plain Java values; package-visible so tests can call it. */
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
            requireUsable("create a host node");
            Map<String, Object> next = props(props);
            JsxItemVisual.Resolved visual = prepareItem(type, next);
            Object handle = transaction.create(type, key, next);
            transaction.itemVisual(handle, visual);
            return handle;
        }

        public void update(Object handle, String type, String key, Object props) {
            requireUsable("update a host node");
            Map<String, Object> next = props(props);
            JsxItemVisual.Resolved visual = prepareItem(type, next);
            transaction.update(interopValue(handle), type, key, next);
            transaction.itemVisual(interopValue(handle), visual);
        }

        public void order(Object parent, Object children) {
            requireUsable("order host nodes");
            transaction.order(interopValue(parent), readArray(children));
        }

        public void remove(Object handle) {
            requireUsable("remove a host node");
            transaction.remove(interopValue(handle));
        }

        public void commit(Object roots) {
            requireUsable("commit a host transaction");
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
            recordSuccessfulTransaction();
            lastSnapshot = committed[0];
            layoutBasis = committed[0] == null ? null : candidate;
            pendingSnapshot = null;
            finishPreparedResources();
            inputRoots();
        }

        public void rollback() {
            requireUsable("roll back a host transaction");
            transaction.rollback();
            pendingSnapshot = null;
        }
    }
}
//?}
