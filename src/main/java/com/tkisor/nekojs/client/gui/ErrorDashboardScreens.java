//? if neoforge && >=26 {
package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.client.ui.BuiltinErrorDashboard;
import com.tkisor.nekojs.core.ui.BuiltinUiRuntime;
import com.tkisor.nekojs.network.ErrorSummaryDTO;
import com.tkisor.nekojs.platform.compat.McClientCompat;

import java.util.List;
import java.util.function.Supplier;

/** Client composition entry for the bundled dashboard and its native failure fallback. */
public final class ErrorDashboardScreens {
    private static Supplier<BuiltinUiRuntime.Session> sessions;
    private static McClientCompat.Impl screenHost;
    private ErrorDashboardScreens() { }

    public static void install(Supplier<BuiltinUiRuntime.Session> factory, McClientCompat.Impl screens) {
        sessions = java.util.Objects.requireNonNull(factory);
        screenHost = java.util.Objects.requireNonNull(screens);
    }

    public static void showOrUpdate(List<ErrorSummaryDTO> errors, boolean openIfMissing) {
        McClientCompat.Impl client = screenHost == null ? McClientCompat.get() : screenHost;
        var current = client.currentScreen();
        if (current instanceof BuiltinErrorDashboard screen) { screen.updateErrors(errors); return; }
        if (current instanceof NekoErrorDashboardScreen fallback) { fallback.updateErrors(errors); return; }
        if (!openIfMissing) return;
        BuiltinUiRuntime.Session session = null;
        try {
            if (sessions == null) throw new IllegalStateException("[NEKO-7015] Built-in UI is not installed");
            session = sessions.get();
            client.showScreen(new BuiltinErrorDashboard(session, errors, client));
        } catch (RuntimeException | LinkageError failure) {
            if (session != null) session.close();
            NekoJS.LOGGER.warn("[NEKO-7015] Built-in error dashboard could not open; showing native fallback", failure);
            client.showScreen(NekoErrorDashboardScreen.create(errors));
        }
    }
}
//?}
