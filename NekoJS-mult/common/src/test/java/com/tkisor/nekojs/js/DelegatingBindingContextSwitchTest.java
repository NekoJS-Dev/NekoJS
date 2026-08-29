package com.tkisor.nekojs.js;

import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.HostAccess;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 组合绑定跨 Context 复用回归：绑定是进程级对象（bootstrap 一次构造），事务式 reload 会
 * 关闭旧 Context 并切换到新候选——修复前惰性缓存的 {@code Value} 归属旧 Context，
 * reload 后首次访问抛 {@code The Context is already closed}；修复后按当前活跃 Context 重建包装。
 */
class DelegatingBindingContextSwitchTest {

    public static class Helper {
        public String help() {
            return "ok";
        }
    }

    @Test
    void valueCacheRebindsAcrossContextSwitch() {
        DelegatingBinding binding = new DelegatingBinding(new Helper(), Helper.class, Set.of("help"));

        try (Context first = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            first.getBindings("js").putMember("b", binding);
            assertEquals("ok", first.eval("js", "b.help()").asString());
        }

        // 旧 Context 已关闭：换一个新 Context 再访问同一绑定实例
        try (Context second = Context.newBuilder().allowHostAccess(HostAccess.ALL).build()) {
            second.getBindings("js").putMember("b", binding);
            assertEquals("ok", second.eval("js", "b.help()").asString());
        }
    }
}
