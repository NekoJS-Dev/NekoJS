package com.tkisor.nekojs.platform.compat;

import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.util.ServiceLoader;

/**
 * Client-side facade for compile-time symbol differences between versions.
 *
 * <p>The facade lives in neoforge-shared, which both 1.21.1 and 26.x compile, and covers the
 * differences between them:
 * <ul>
 *   <li>Mechanical renames from 26.1 to 26.2: {@code Minecraft#screen / setScreen} →
 *       {@code Gui#screen() / setScreen()}, {@code RenderSystem#getApiDescription()} →
 *       {@code getBackendDescription()};</li>
 *   <li>Shape differences from 26.x to 1.21.1: the toast manager is {@code ToastManager} in 26.x
 *       and {@code ToastComponent} in 1.21.1 (different type names, so it cannot appear in a shared
 *       signature, and {@link #addOrUpdateSystemToast} moves the whole operation down instead); the
 *       show-text HoverEvent is {@code HoverEvent.ShowText} in 26.x and
 *       {@code new HoverEvent(Action, ...)} in 1.21.1.</li>
 * </ul>
 * The implementations live in the per-version modules ({@code Nf1211ClientCompat} /
 * {@code Nf261ClientCompat} / {@code Nf262ClientCompat}, deliberately with different class names
 * because the drift comparison forbids the same name with a different body) and are resolved by
 * ServiceLoader through {@code META-INF/services} on first use.
 *
 * <p><strong>Reference this class only on client code paths</strong>: its signatures contain
 * client-only types, so loading this class on a dedicated server throws NoClassDefFoundError.
 */
public final class McClientCompat {

    private static final Impl IMPL = ServiceLoader.load(Impl.class, McClientCompat.class.getClassLoader())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                    "No McClientCompat.Impl provider on classpath: expected a per-version impl "
                            + "in neoforge-1.21.1/neoforge-26.1/neoforge-26.2 (META-INF/services)"));

    private McClientCompat() {}

    public static Impl get() {
        return IMPL;
    }

    public interface Impl {
        Screen currentScreen();

        void showScreen(Screen screen);

        /** 26.x: {@code RenderSystem.getApiDescription()}; renamed to getBackendDescription as of 26.2. */
        String renderBackendDescription();

        /**
         * Version-independent form of SystemToast.addOrUpdate. The toast manager type differs between the
         * two platforms, so the whole operation moves down into the implementation.
         */
        void addOrUpdateSystemToast(SystemToast.SystemToastId id, Component title, Component description);

        /**
         * Version-independent construction of a show-text HoverEvent (26.x HoverEvent.ShowText vs the
         * 1.21.1 Action constructor).
         */
        HoverEvent hoverEventShowText(Component text);

    }
}
