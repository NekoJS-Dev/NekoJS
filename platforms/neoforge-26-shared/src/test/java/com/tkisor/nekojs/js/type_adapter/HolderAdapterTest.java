package com.tkisor.nekojs.js.type_adapter;

import com.tkisor.nekojs.api.data.ValueConversionException;
import graal.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HolderAdapter} 的纯逻辑路径（无 FML/注册表 bootstrap 也能跑）：
 * 非法 id 的带上下文报错与非 Holder 宿主对象的拒绝。
 *
 * <p>注意 {@code AbstractJSTypeAdapter.test()} 对字符串直接委托 {@code fromString}，
 * 所以合法 id 的形状判定与跨注册表解析（唯一命中/歧义/未命中）都依赖注册表内容——
 * 裸 JUnit 无 FML Loader 无法 bootstrap（ModDev unitTest 支持是后续工作），由
 * in-game 验证覆盖。
 */
class HolderAdapterTest {

    private final HolderAdapter adapter = new HolderAdapter();

    @Test
    void invalidIdThrowsWithContext() {
        String message = assertThrows(ValueConversionException.class,
                () -> adapter.apply(Value.asValue("not an id!"))).getMessage();
        assertTrue(message.contains("Holder"), message);
        assertTrue(message.contains("invalid id"), message);
        assertTrue(message.contains("registry entry id"), message);
    }

    @Test
    void nonHolderValuesRejectedByTest() {
        try (var context = graal.graalvm.polyglot.Context.newBuilder().build()) {
            assertEquals(false, adapter.test(context.asValue(new Object())));
            assertEquals(false, adapter.test(Value.asValue(42)));
            assertEquals(false, adapter.test(Value.asValue(true)));
        }
    }
}
