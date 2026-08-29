package com.tkisor.nekojs.js;

import graal.graalvm.polyglot.Context;
import graal.graalvm.polyglot.Value;
import graal.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 全局绑定代理：成员访问时先查 {@code extensions}（由 {@code helper} 对象提供），
 * 命中则委托 helper；否则委托 {@code targetClass} 的静态成员。
 *
 * <p>用途：NeoForge 平台无法给 MC 类真注入 public static 方法（Mixin 硬拒，JS Coremod
 * 依赖 standalone Nashorn 在 GraalVM Java25 缺失），于是把全局 {@code Item} 绑定为这个代理：
 * {@code Item.of(...)} 走 helper（ItemJS），其它成员委托回真正的 MC {@code Item} 类。
 *
 * <p>关键实现点：GraalJS 的 {@code Value.asValue} 必须在 Polyglot Context 活跃时调用才有意义。
 * 而 {@code DelegatingBinding} 是在 mod 注册阶段（{@code registerBinding}，Context 尚未建立）构造的，
 * 所以 <b>不能</b>在构造时 {@code asValue}——必须延迟到 {@code getMember} 等方法被 GraalJS 调用时
 * （此时 Context 已活跃）再求值。否则 helper 拿到的是分离的 Value，{@code getMember} 全返回 null，
 * 导致 {@code invokeMember} 报 {@code Unknown identifier}。
 *
 * <p>限制：代理不是 Java Class 镜像，{@code Java.type('..Item').of()} 仍拿不到 of（非真注入）。
 */
public final class DelegatingBinding implements ProxyObject {
    private final Object helperObj;
    private final Class<?> targetClassObj;
    private final Set<String> extensions;

    private volatile Value helperValue;
    private volatile Value targetValue;
    private volatile Context boundContext;

    public DelegatingBinding(Object helper, Class<?> targetClass, Set<String> extensions) {
        this.helperObj = helper;
        this.targetClassObj = targetClass;
        this.extensions = Set.copyOf(extensions);
    }

    public Set<String> extensions() {
        return extensions;
    }

    /** 代理委托的目标 MC 类；probe 据此把该类的静态成员合并进全局绑定的 .d.ts。 */
    public Class<?> targetClass() {
        return targetClassObj;
    }

    /**
     * 在 GraalJS 访问代理时（Context 活跃）才把原始对象包装成 Value。
     *
     * <p>本代理是进程级对象（bootstrap 一次构造、跨 Context 复用），而事务式 reload 会
     * 关闭旧 Context 并切换到新候选——缓存的 {@code Value} 归属旧 Context，之后访问会抛
     * {@code The Context is already closed}。因此按<b>当前活跃 Context</b> 缓存：发现
     * Context 变化（reload 切换）即重建包装。getMember 等回调由 GraalJS 在访问者线程的
     * 活跃 Context 内调用，{@code Context.getCurrent()} 恒非 null。
     */
    private void ensure() {
        Context current = Context.getCurrent();
        if (helperValue == null || boundContext != current) {
            boundContext = current;
            helperValue = Value.asValue(helperObj);
            targetValue = Value.asValue(targetClassObj);
        }
    }

    @Override
    public Object getMember(String key) {
        ensure();
        if (extensions.contains(key)) {
            return helperValue.getMember(key);
        }
        return targetValue.getMember(key);
    }

    @Override
    public boolean hasMember(String key) {
        ensure();
        if (extensions.contains(key)) {
            return helperValue.hasMember(key);
        }
        return targetValue.hasMember(key);
    }

    @Override
    public Object getMemberKeys() {
        ensure();
        Set<String> keys = new LinkedHashSet<>(extensions);
        try {
            if (targetValue.hasMembers()) {
                targetValue.getMemberKeys().forEach(keys::add);
            }
        } catch (Throwable ignored) {
            // 某些 host 对象不支持枚举成员键，忽略——getMember 委托仍可用
        }
        return keys.toArray();
    }

    @Override
    public void putMember(String key, Value value) {
        // 只读绑定，禁止脚本覆盖
    }
}
