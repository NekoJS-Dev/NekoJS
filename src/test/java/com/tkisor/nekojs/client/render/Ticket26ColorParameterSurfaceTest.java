package com.tkisor.nekojs.client.render;

import com.tkisor.nekojs.wrapper.client.PainterJS;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Defect D6 surface guard (ticket 26): every script-facing ARGB color parameter on the
 * three painter surfaces must be declared as {@link Number}. A raw {@code int} color
 * parameter saturates unsigned script literals ({@code 0xFF......} ≥ 2³¹) to
 * {@code 0x7FFFFFFF} — translucent white — at the engine conversion boundary.
 *
 * <p>Behavioral proof lives in {@code PainterJSScriptColorBoundaryTest} (the production
 * {@code PainterJS} through a real GraalJS context) and the common
 * {@code ScriptColorBoundaryRealEngineTest} (engine conversion + normalization). The
 * hud/world contexts cannot be constructed headless (their constructors dereference a
 * live {@code Minecraft} client), so their seam types are pinned here instead: each
 * {@link Number}-typed overload must exist and its all-{@code int} color twin (the exact
 * pre-fix signature) must be gone.
 */
class Ticket26ColorParameterSurfaceTest {

    @Test
    void painterColorSeamsStayNumberTyped() {
        Class<?> painter = PainterJS.class;
        colorSeam(painter, "color", Number.class);
        colorSeam(painter, "text", String.class, int.class, int.class, Number.class);
        colorSeam(painter, "centerText", String.class, int.class, int.class, Number.class);
        colorSeam(painter, "rect", int.class, int.class, int.class, int.class, Number.class);
        colorSeam(painter, "outline", int.class, int.class, int.class, int.class, Number.class);
        colorSeam(painter, "gradient", int.class, int.class, int.class, int.class, Number.class, Number.class);
        colorSeam(painter, "gradientH", int.class, int.class, int.class, int.class, Number.class, Number.class);
    }

    @Test
    void hudRenderContextColorSeamsStayNumberTyped() {
        Class<?> hud = HudRenderContextJS.class;
        colorSeam(hud, "color", Number.class);
        colorSeam(hud, "text", String.class, int.class, int.class, Number.class);
        colorSeam(hud, "drawText", String.class, int.class, int.class, Number.class);
        colorSeam(hud, "centerText", String.class, int.class, int.class, Number.class);
        colorSeam(hud, "rect", int.class, int.class, int.class, int.class, Number.class);
        colorSeam(hud, "fillRect", int.class, int.class, int.class, int.class, Number.class);
        colorSeam(hud, "outline", int.class, int.class, int.class, int.class, Number.class);
        colorSeam(hud, "gradient", int.class, int.class, int.class, int.class, Number.class, Number.class);
    }

    @Test
    void worldRenderContextColorSeamsStayNumberTyped() {
        Class<?> world = WorldRenderContextJS.class;
        colorSeam(world, "line", double.class, double.class, double.class,
                double.class, double.class, double.class, Number.class);
        colorSeam(world, "line", double.class, double.class, double.class,
                double.class, double.class, double.class, Number.class, float.class);
        colorSeam(world, "box", double.class, double.class, double.class,
                double.class, double.class, double.class, Number.class);
    }

    /**
     * Asserts the seam resolves with {@link Number} at the color position(s) and that the
     * signature with every color position narrowed to {@code int} — the exact pre-fix D6
     * form — no longer exists.
     */
    private static void colorSeam(Class<?> owner, String method, Class<?>... shape) {
        Method seam = assertDoesNotThrow(() -> owner.getMethod(method, shape),
                () -> owner.getSimpleName() + "." + method + " must keep Number color parameters");
        assertTrue(seam.getDeclaringClass().equals(owner), "seam must be declared on the surface itself");

        Class<?>[] intTyped = new Class<?>[shape.length];
        boolean hasColorParameter = false;
        for (int i = 0; i < shape.length; i++) {
            if (shape[i] == Number.class) {
                intTyped[i] = int.class;
                hasColorParameter = true;
            } else {
                intTyped[i] = shape[i];
            }
        }
        assertTrue(hasColorParameter, "the shape must name at least one color parameter");
        assertThrows(NoSuchMethodException.class, () -> owner.getMethod(method, intTyped),
                () -> owner.getSimpleName() + "." + method
                        + " must not redeclare an int color parameter (D6 saturation seam)");
    }
}
