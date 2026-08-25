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
 * ItemStack 注入接口的跨平台契约：本测试层被 1.21.1 / 26.1 / 26.2 三个平台共同挂载，
 * 各自 classpath 上的 {@code ItemStackExtension}（独立源码树维护）必须满足同一套形状——
 *
 * <ul>
 *   <li>与原版 {@code ItemStack} 撞名的方法改成显式唯一 JS 名（{@code @Remap}）：
 *       {@code setCountAndReturn / enchantById / hasGlint / setGlint}；</li>
 *   <li>原样可用的原版成员（copy/getItem/setCount/enchant/isEnchanted...）不得再包
 *       {@code neko$} facade——前缀剥掉后落回原版名会让 GraalJS 重载解析在
 *       「String 直传 vs Holder 适配」之间踩空，真机上表现为 "Unsupported target type"；</li>
 *   <li>残留的 holder 型 {@code neko$enchant} 重载必须 {@code @HideFromJS}，脚本侧
 *       只走 {@code enchantById}。</li>
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
        // String 直传的附魔入口已被 enchantById 取代；Holder 重载单独处理（见下）
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
