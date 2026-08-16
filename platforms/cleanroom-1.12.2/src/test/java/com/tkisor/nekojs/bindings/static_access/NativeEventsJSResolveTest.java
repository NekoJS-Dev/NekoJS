package com.tkisor.nekojs.bindings.static_access;

import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 1.12.2 NativeEventsJS 的类/优先级解析逻辑单测（与 NeoForge 版同语义的纯逻辑部分）。
 * 事件注册本体（bindNative → 优先级槽）依赖运行中的 Forge 总线，由游戏内验证覆盖。
 */
class NativeEventsJSResolveTest {

    private final NativeEventsJS nativeEvents = new NativeEventsJS();

    @Test
    void resolveClassFromStringAcceptsFqnAndInnerClassDollarFallback() {
        // 普通 FQN 直接命中
        assertEquals(Map.class, nativeEvents.resolveClassFromString("java.util.Map"));
        // 点号嵌套类：Class.forName 失败后逐级回退为 $ 分隔
        assertEquals(Map.Entry.class, nativeEvents.resolveClassFromString("java.util.Map.Entry"));
    }

    @Test
    void resolveClassFromStringCachesMisses() {
        assertNull(nativeEvents.resolveClassFromString("not.a.RealClassName"));
        // 第二次命中负缓存，仍是 null 且不再抛异常
        assertNull(nativeEvents.resolveClassFromString("not.a.RealClassName"));
    }

    @Test
    void resolveClassHandlesNullClassAndString() {
        assertNull(nativeEvents.resolveClass(null));
        assertEquals(String.class, nativeEvents.resolveClass(String.class));
        assertEquals(Integer.class, nativeEvents.resolveClass("java.lang.Integer"));
    }

    @Test
    void resolveEventClassRejectsNonEventTypes() {
        // 非 Event 子类：解析成功但被拒绝
        assertNull(nativeEvents.resolveEventClass("java.lang.String"));
        // Event 基类本身可接受
        assertSame(Event.class, nativeEvents.resolveEventClass(Event.class));
        // 无法解析的输入
        assertNull(nativeEvents.resolveEventClass("not.a.RealClassName"));
    }

    @Test
    void resolvePriorityAcceptsEnumStringAndFallsBackToNormal() {
        assertEquals(EventPriority.HIGH, nativeEvents.resolvePriority(EventPriority.HIGH));
        assertEquals(EventPriority.HIGHEST, nativeEvents.resolvePriority("highest"));
        assertEquals(EventPriority.NORMAL, nativeEvents.resolvePriority("bogus"));
        assertEquals(EventPriority.NORMAL, nativeEvents.resolvePriority(null));
    }
}
