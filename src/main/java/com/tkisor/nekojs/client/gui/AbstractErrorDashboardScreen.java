//? if neoforge {
package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.client.gui.dashboard.DashboardText;
import com.tkisor.nekojs.client.gui.dashboard.DashboardView;
import com.tkisor.nekojs.core.error.ErrorDashboardModel;
import com.tkisor.nekojs.core.error.ErrorOpenService;
import com.tkisor.nekojs.core.error.LocalErrorSource;
import com.tkisor.nekojs.core.error.VsCodeProcessOpener;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import com.tkisor.nekojs.platform.compat.McClientCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/** Shared dashboard lifecycle and platform actions; no render/input-version branches. */
public abstract class AbstractErrorDashboardScreen extends Screen {
    protected static final String PREFIX = "nekojs.gui.dashboard.";
    private static final Executor BACKGROUND = ForkJoinPool.commonPool();
    protected final ErrorDashboardModel model = new ErrorDashboardModel();
    protected final DashboardView view = new DashboardView(model, (key, args) -> I18n.get(PREFIX + key, args));
    protected EditBox searchBox;
    private LocalErrorSource source;
    private ErrorOpenService opener;
    private ErrorSummaryDTO checkedError;
    private long locationRequest, openRequest;
    private boolean disposed, opening;
    protected boolean draggingSearch;

    protected AbstractErrorDashboardScreen() { super(Component.translatable(PREFIX + "title")); }

    public void updateErrors(List<ErrorSummaryDTO> errors) {
        // Never setScreen here: a late live refresh must not resurrect a closed screen.
        if (disposed) return;
        model.update(errors); view.refresh();
        if (searchBox != null) refreshLocation(false);
    }

    @Override
    protected void init() {
        disposed = false;
        draggingSearch = false; view.release();
        view.configure(width, height, font.lineHeight + 1, font::width);
        // Screen rebuilds its widget list on resize. Reuse the editor to retain cursor,
        // selection and horizontal scroll, not just the query string.
        if (searchBox == null) {
            searchBox = new EditBox(font, 0, 0, 1, 10, Component.translatable(PREFIX + "search"));
            searchBox.setBordered(false);
            searchBox.setTextColor(DashboardView.TEXT);
            searchBox.setMaxLength(4096);
            searchBox.setHint(Component.translatable(PREFIX + "search"));
            searchBox.setValue(model.search());
            searchBox.setResponder(value -> { model.setSearch(value); view.refresh(); refreshLocation(false); });
        }
        addWidget(searchBox);
        syncSearch();
        refreshLocation(true);
    }

    @Override
    protected void setInitialFocus() {
        // Vanilla only sees the EditBox, not the custom controls. Its default initial
        // focus would steal keyboard focus from the list/buttons after every rebuild.
        syncSearch();
    }

    protected final void syncSearch() {
        var rect = view.layout().search();
        searchBox.setX(rect.x() + 13); searchBox.setY(rect.y() + Math.max(3, (rect.height() - 8) / 2));
        searchBox.setWidth(Math.max(1, rect.width() - 17));
        searchBox.visible = !view.collapsed() && !view.modal();
        searchBox.active = searchBox.visible;
        boolean focused = searchBox.visible && view.focus() == DashboardView.Action.SEARCH;
        setFocused(focused ? searchBox : null);
        searchBox.setFocused(focused);
    }

    private boolean current(ErrorSummaryDTO error) {
        return !disposed && minecraft != null && McClientCompat.get().currentScreen() == this && Objects.equals(error, model.selectedError());
    }
    private String locationReason(LocalErrorSource.Status status) {
        return view.t("location." + status.name().toLowerCase(Locale.ROOT));
    }
    protected final void refreshLocation(boolean force) {
        var error = model.selectedError();
        if (!force && Objects.equals(error, checkedError)) return;
        checkedError = error; opening = false; openRequest++;
        long request = ++locationRequest;
        if (error == null) { view.location(false, locationReason(LocalErrorSource.Status.NO_ERROR)); return; }
        view.location(false, view.t("checking_location"));
        try {
            if (source == null) {
                source = LocalErrorSource.forPaths(NekoJSPaths.get());
                opener = new ErrorOpenService(source, new VsCodeProcessOpener());
            }
            // Only the actual integrated server permits mapping. File checks stay off the render thread.
            boolean local = minecraft.hasSingleplayerServer();
            LocalErrorSource resolver = source;
            Minecraft client = minecraft;
            CompletableFuture.supplyAsync(() -> resolver.resolve(error, local), BACKGROUND)
                    .whenComplete((result, failure) -> client.execute(() -> {
                        if (!current(error) || request != locationRequest) return;
                        if (failure != null || result == null) view.location(false, locationReason(LocalErrorSource.Status.IO_ERROR));
                        else view.location(result.available(), result.available()
                                ? view.t(result.target().gotoLine() ? "location_ready" : "file_only") : locationReason(result.status()));
                    }));
        } catch (RuntimeException e) {
            view.location(false, locationReason(LocalErrorSource.Status.IO_ERROR));
        }
    }

    private void openSource() {
        var error = model.selectedError();
        if (error == null || opening || !view.enabled(DashboardView.Action.OPEN_SOURCE)) return;
        if (opener == null) { view.status(locationReason(LocalErrorSource.Status.IO_ERROR)); return; }
        opening = true; locationRequest++;
        long request = ++openRequest;
        view.location(false, view.t("opening")); view.status(view.t("opening"));
        Minecraft client = minecraft;
        opener.openAsync(error, client.hasSingleplayerServer(), BACKGROUND).whenComplete((result, failure) -> client.execute(() -> {
            if (!current(error) || request != openRequest) return;
            opening = false;
            if (failure != null || result == null) {
                view.status(view.t("open_failed")); view.dialog(view.t("open_failed"), view.t("open_failed_note"));
            } else if (result.accepted()) {
                // Acceptance is not proof that an editor window opened or that the line was reached.
                view.status(view.t(result.lineRequested() ? "dispatch_accepted" : "dispatch_file_only"));
            } else {
                String reason = result.outcome() == ErrorOpenService.Outcome.LOCATION_UNAVAILABLE
                        ? locationReason(result.locationStatus()) : view.t("open_failed_note");
                view.status(reason); view.dialog(view.t("open_failed"), reason);
            }
            refreshLocation(true); syncSearch();
        }));
    }

    private void copy(boolean full) {
        var error = model.selectedError();
        if (error == null) return;
        String raw = full ? error.fullDetails() : error.path();
        try {
            minecraft.keyboardHandler.setClipboard(raw);
            if (!raw.equals(minecraft.keyboardHandler.getClipboard())) throw new IllegalStateException("Clipboard mismatch");
            view.status(view.t(full ? "copied_full" : "copied_path"));
        } catch (RuntimeException e) {
            view.status(view.t("copy_failed")); view.dialog(view.t("copy_failed"), view.t("copy_failed_note")); syncSearch();
        }
    }

    protected final void perform(DashboardView.Action action) {
        if (!view.enabled(action)) { if (action == DashboardView.Action.OPEN_SOURCE) view.status(view.sourceReason()); return; }
        switch (action) {
            case TOGGLE -> view.toggleSidebar();
            case SEARCH -> view.focusSearch();
            case CLEAR -> { searchBox.setValue(""); view.focusSearch(); }
            case COPY_PATH -> copy(false);
            case COPY_DETAILS -> copy(true);
            case OPEN_SOURCE -> openSource();
            case RAW, STACK -> view.toggleFold(action);
            default -> { }
        }
        syncSearch();
    }

    @Override
    public void onClose() {
        if (view.modal()) { view.dismissDialog(); syncSearch(); return; }
        super.onClose();
    }
    @Override
    public void removed() {
        disposed = true; locationRequest++; openRequest++; view.release(); draggingSearch = false;
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public Component getNarrationMessage() {
        return view == null ? title : Component.literal(DashboardText.display(view.narration()));
    }
}
//?}

