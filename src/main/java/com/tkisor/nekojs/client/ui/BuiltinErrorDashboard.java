//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import com.google.gson.Gson;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.client.gui.NekoErrorDashboardScreen;
import com.tkisor.nekojs.core.error.ErrorDashboardModel;
import com.tkisor.nekojs.core.error.ErrorOpenService;
import com.tkisor.nekojs.core.error.LocalErrorSource;
import com.tkisor.nekojs.core.error.VsCodeProcessOpener;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.core.ui.BuiltinUiRuntime;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import com.tkisor.nekojs.platform.compat.McClientCompat;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyExecutable;
import graal.graalvm.polyglot.proxy.ProxyObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BooleanSupplier;

/** Native Screen and action wiring only; all presentation is in the bundled JSX factory. */
public final class BuiltinErrorDashboard extends Screen {
    private static final Gson JSON = new Gson();
    private final BuiltinUiRuntime.Session session;
    private final McClientCompat.Impl screens;
    private final ErrorDashboardModel data = new ErrorDashboardModel();
    private JsxHostAdapter host;
    private Value view;
    private Value parseJson;
    private boolean disposed;
    private boolean broken;
    private boolean opening;
    private long locationRequest;
    private long openRequest;
    private String selectedId;
    private ErrorSummaryDTO verifiedError;
    private LocalErrorSource source;
    private ErrorOpenService opener;
    private JsxHostAdapter.Clipboard clipboard;

    public BuiltinErrorDashboard(BuiltinUiRuntime.Session session, List<ErrorSummaryDTO> errors, McClientCompat.Impl screens) {
        super(Component.translatable("nekojs.gui.dashboard.title"));
        this.session = Objects.requireNonNull(session);
        this.screens = Objects.requireNonNull(screens);
        data.update(errors);
    }

    @Override
    protected void init() {
        if (disposed || session.isClosed()) return;
        try {
            if (host == null) {
                host = JsxHostAdapter.builtin(session, width, height, this::fail);
                session.beforeRelease(host::dispose);
                var factory = session.errorDashboardFactory(new com.tkisor.nekojs.bindings.static_access.I18nJS());
                parseJson = session.context().eval("js", "JSON.parse");
                view = factory.execute(BuiltinUiHostBridge.host(host), bridge(), snapshot());
                host.bindRoot(view.getMember("root"));
                if (broken) throw new IllegalStateException("[NEKO-7015] Built-in error dashboard initialization failed");
                view.invokeMember("refreshLocation");
            } else {
                host.setRegion(0, 0, Math.max(1, width), Math.max(1, height));
            }
        } catch (RuntimeException failure) { fail(failure); }
    }

    public void updateErrors(List<ErrorSummaryDTO> errors) {
        if (disposed || session.isClosed()) return;
        session.requireOwner();
        data.update(errors);
        if (broken) return;
        if (view != null) {
            try { view.invokeMember("update", snapshot()); }
            catch (RuntimeException failure) { fail(failure); }
        }
    }

    private Value snapshot() { return parseJson.execute(JSON.toJson(data.visibleErrors())); }

    private ProxyObject bridge() {
        return ProxyObject.fromMap(Map.of(
                "focus", (ProxyExecutable) args -> host.focusControl(args[0].asString()),
                "copy", (ProxyExecutable) args -> copy(args[0].asString(), args[1].asBoolean()),
                "checkLocation", (ProxyExecutable) args -> { checkLocation(BuiltinUiHostBridge.nullableText(args[0])); return null; },
                "openSource", (ProxyExecutable) args -> { openSource(args[0].asString()); return null; }));
    }

    private ErrorSummaryDTO error(String id) {
        return data.visibleErrors().stream().filter(row -> row.id().equals(id)).findFirst().orElse(null);
    }

    private boolean current(ErrorSummaryDTO error) {
        return !disposed && !broken && !session.isClosed() && Objects.equals(selectedId, error.id())
                && Objects.equals(error, error(error.id()));
    }

    private void checkLocation(String id) {
        selectedId = id;
        verifiedError = null;
        opening = false;
        openRequest++;
        long request = ++locationRequest;
        ErrorSummaryDTO error = error(id);
        if (error == null) return;
        if (source == null) {
            source = LocalErrorSource.forPaths(NekoJSPaths.get());
            opener = new ErrorOpenService(source, new VsCodeProcessOpener());
        }
        Minecraft client = Minecraft.getInstance();
        boolean local = client.hasSingleplayerServer();
        LocalErrorSource resolver = source;
        CompletableFuture.supplyAsync(() -> resolver.resolve(error, local), ForkJoinPool.commonPool())
                .whenComplete((result, failure) -> client.execute(() -> {
                    if (!current(error) || request != locationRequest) return;
                    boolean available = failure == null && result != null && result.available();
                    verifiedError = available ? error : null;
                    String reason = failure != null || result == null ? "location.io_error"
                            : available ? (result.target().gotoLine() ? "location_ready" : "file_only")
                            : "location." + result.status().name().toLowerCase(Locale.ROOT);
                    invoke("locationResult", id, available, reason);
                }));
    }

    private void openSource(String id) {
        ErrorSummaryDTO error = error(id);
        if (error == null || opening || !current(error) || !Objects.equals(error, verifiedError)) return;
        opening = true;
        locationRequest++;
        long request = ++openRequest;
        Minecraft client = Minecraft.getInstance();
        opener.openAsync(error, client.hasSingleplayerServer(), ForkJoinPool.commonPool())
                .whenComplete((result, failure) -> client.execute(() -> {
                    if (!current(error) || request != openRequest) return;
                    opening = false;
                    boolean accepted = failure == null && result != null && result.accepted();
                    String reason = failure == null && result != null
                            && result.outcome() == ErrorOpenService.Outcome.LOCATION_UNAVAILABLE
                            ? "location." + result.locationStatus().name().toLowerCase(Locale.ROOT) : "open_failed_note";
                    invoke("openResult", id, accepted, accepted && result.lineRequested(), reason);
                    checkLocation(id);
                }));
    }

    private boolean copy(String id, boolean full) {
        ErrorSummaryDTO error = error(id);
        if (error == null || disposed || session.isClosed()) return false;
        String original = full ? error.fullDetails() : error.path();
        try {
            if (clipboard == null) {
                var keyboard = Minecraft.getInstance().keyboardHandler;
                keyboard.setClipboard(original);
                return original.equals(keyboard.getClipboard());
            }
            clipboard.write(original);
            return original.equals(clipboard.read());
        } catch (RuntimeException failure) { return false; }
    }

    void clipboard(JsxHostAdapter.Clipboard clipboard) { this.clipboard = Objects.requireNonNull(clipboard); }

    private void invoke(String method, Object... args) {
        if (disposed || broken || session.isClosed()) return;
        try { view.invokeMember(method, args); }
        catch (RuntimeException failure) { fail(failure); }
    }

    private void fail(RuntimeException failure) {
        if (disposed || broken || session.isClosed()) return;
        broken = true;
        NekoJS.LOGGER.warn("[NEKO-7015] Built-in error dashboard failed; native fallback requested", failure);
        Minecraft.getInstance().schedule(() -> {
            if (!disposed && !session.isClosed() && screens.currentScreen() == this) {
                screens.showScreen(NekoErrorDashboardScreen.create(data.visibleErrors()));
            }
        });
    }

    private boolean route(BooleanSupplier action) {
        if (disposed || host == null) return false;
        if (session.isClosed()) { onClose(); return true; }
        if (broken) return false;
        try {
            boolean handled = action.getAsBoolean();
            if (host.isDisposed()) onClose();
            return handled;
        } catch (RuntimeException failure) { fail(failure); return true; }
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        route(() -> { host.render(graphics, mouseX, mouseY); return true; });
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return route(() -> host.dispatchAt(event.x(), event.y(), "click", event.button(), event.modifiers()));
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return route(() -> host.dispatchDrag(event.x(), event.y(), event.button()));
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return route(() -> host.dispatchRelease(event.x(), event.y(), event.button()));
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return route(() -> host.dispatchScroll(mouseX, mouseY, scrollY));
    }
    @Override public boolean keyPressed(KeyEvent event) { return route(() -> host.key(event.key(), event.modifiers())); }
    @Override public boolean charTyped(CharacterEvent event) {
        return route(() -> view.invokeMember("textInput", event.codepoint(), host.inputFocused()).asBoolean()
                || host.textInput(event.codepoint()));
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public Component getNarrationMessage() {
        return host == null || disposed || session.isClosed() ? title : Component.literal(host.narration());
    }
    @Override public void onClose() {
        if (screens.currentScreen() == this) screens.showScreen(null);
    }
    @Override public void removed() {
        if (disposed) return;
        disposed = true;
        locationRequest++; openRequest++;
        try { session.close(); }
        finally { super.removed(); }
    }
}
//?}
