package com.tkisor.nekojs.client.gui;

import com.tkisor.nekojs.network.ErrorSummaryDTO;
import com.tkisor.nekojs.platform.compat.McClientCompat;
import java.util.List;

/** Keeps the existing native dashboard on nodes without a JSX host. */
public final class ErrorDashboardScreens {
    private ErrorDashboardScreens() { }

    public static void showOrUpdate(List<ErrorSummaryDTO> errors, boolean openIfMissing) {
        if (McClientCompat.get().currentScreen() instanceof NekoErrorDashboardScreen screen) screen.updateErrors(errors);
        else if (openIfMissing) McClientCompat.get().showScreen(NekoErrorDashboardScreen.create(errors));
    }
}
