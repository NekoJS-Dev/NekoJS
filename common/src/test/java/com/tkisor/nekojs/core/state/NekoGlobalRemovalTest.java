package com.tkisor.nekojs.core.state;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 工单 10 AC10 删除门：旧隐式跨类型回退（进程级 NekoGlobal 静态 Map）随本票删除，
 * 不保留旧新双写或隐式回退兼容层（1.2.0 clean cutover）。本 fixture 钉住删除本身——
 * 类若被复活（或删除被 revert），这里立刻变红提醒补一次显式决策。
 *
 * <p>迁移后的可观察替代语义（旧跨类型 global 不可见）由
 * {@code Ticket10GlobalStateTest.sameTypeUsageUnchangedAndLegacyCrossTypeFallbackIsGone}
 * 承载；本测试只守「实现只有一套」。
 */
class NekoGlobalRemovalTest {

    @Test
    void processWideNekoGlobalClassIsRemoved() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.tkisor.nekojs.bindings.static_access.NekoGlobal"),
                "the process-wide NekoGlobal static Map must stay deleted (ticket 10 deletion gate;"
                        + " single standard implementation lives in core.state)");
    }
}
