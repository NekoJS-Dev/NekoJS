// fabric has no matching implementation yet; remove this whole-file guard once the port lands
//? if neoforge {
package com.tkisor.nekojs.platform;

import com.tkisor.nekojs.api.annotation.HideFromJS;
import com.tkisor.nekojs.api.annotation.Remap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-platform contract for the ItemStack injection interface: this test layer is mounted by the
 * 1.21.1, 26.1 and 26.2 platforms, and each classpath's {@code ItemStackExtension} (maintained in
 * its own source tree) must have the same shape.
 *
 * <ul>
 *   <li>Methods whose name collides with vanilla {@code ItemStack} get an explicit unique JS name
 *       via {@code @Remap}: {@code setCountAndReturn / enchantById / hasGlint / setGlint};</li>
 *   <li>Vanilla members that already work as-is (copy/getItem/setCount/enchant/isEnchanted...)
 *       must not be wrapped in a {@code neko$} facade again — stripping the prefix falls back to
 *       the vanilla name, and GraalJS overload resolution then fails between "pass a String
 *       directly" and "adapt to Holder", which shows up on a real client as
 *       "Unsupported target type";</li>
 *   <li>Any remaining holder-typed {@code neko$enchant} overload must be {@code @HideFromJS} so
 *       scripts only use {@code enchantById}.</li>
 * </ul>
 */
class ItemStackExtensionContractTest {

    private static final String EXTENSION = "com.tkisor.nekojs.api.inject.ItemStackExtension";

    @Test
    void ambiguousMethodsExposeUniqueRemappedJsNames() throws Exception {
        Class<?> ext = Class.forName(EXTENSION);
        assertEquals("setCountAndReturn", remapOf(ext, "neko$setCountAndReturn", int.class));
        assertEquals("enchantById", remapOf(ext, "neko$enchantById", String.class, int.class));
        assertEquals("hasGlint", remapOf(ext, "neko$hasGlint"));
        assertEquals("setGlint", remapOf(ext, "neko$setGlint", boolean.class));
    }

    @Test
    void vanillaReservedNamesCarryNoNekoFacade() throws Exception {
        Class<?> ext = Class.forName(EXTENSION);
        assertNull(declared(ext, "neko$copy"));
        assertNull(declared(ext, "neko$getItem"));
        assertNull(declared(ext, "neko$setCount", int.class));
        assertNull(declared(ext, "neko$isEnchanted"));
        assertNull(declared(ext, "neko$setEnchanted", boolean.class));
        // The String overload of enchant is replaced by enchantById; the Holder overload is covered separately below
        assertNull(declared(ext, "neko$enchant", String.class, int.class));
    }

    @Test
    void remainingEnchantOverloadsStayHiddenFromScripts() throws Exception {
        Class<?> ext = Class.forName(EXTENSION);
        List<String> exposed = new ArrayList<>();
        for (Method method : ext.getDeclaredMethods()) {
            if (!"neko$enchant".equals(method.getName())) continue;
            if (method.getAnnotation(HideFromJS.class) == null) {
                exposed.add(method.toString());
            }
        }
        assertTrue(exposed.isEmpty(),
                "holder-typed neko$enchant overloads must be @HideFromJS (scripts use enchantById): " + exposed);
    }

    private static String remapOf(Class<?> type, String name, Class<?>... params) throws Exception {
        Method method = declared(type, name, params);
        assertNotNull(method, name + " missing on " + type.getName());
        Remap remap = method.getAnnotation(Remap.class);
        assertNotNull(remap, method + " must carry @Remap");
        return remap.value();
    }

    private static Method declared(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getDeclaredMethod(name, params);
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }
}
//?}
