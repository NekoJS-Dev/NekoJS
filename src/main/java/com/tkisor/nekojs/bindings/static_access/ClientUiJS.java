//? if neoforge && >=26 {
package com.tkisor.nekojs.bindings.static_access;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.client.ui.JsxHostAdapter;

/** CLIENT-only entrypoint for creating a JSX host adapter and Screen. */
@Doc("Creates a NeoForge JSX Screen host adapter for UI.createRoot.")
public final class ClientUiJS {
    @Doc("Creates a retained JSX Screen host adapter. Pass it to UI.createRoot, then bind the returned root.")
    @Param(name = "title", value = "screen title")
    @Param(name = "pausesGame", value = "whether the game pauses while this Screen is open")
    public JsxHostAdapter screen(String title, boolean pausesGame) {
        return new JsxHostAdapter(title, pausesGame);
    }
}
//?}
