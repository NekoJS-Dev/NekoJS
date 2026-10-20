package neko.acceptance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Reads the native screen on Minecraft 26.2 for the isolated acceptance mod. */
final class ClientScreens {
    private ClientScreens() {}

    static Screen current(Minecraft client) {
        return client.gui.screen();
    }

    static boolean hasOverlay(Minecraft client) {
        return client.gui.overlay() != null;
    }
}
