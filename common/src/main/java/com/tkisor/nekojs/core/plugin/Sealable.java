package com.tkisor.nekojs.core.plugin;

/**
 * 累积器可选实现的密封接口（ADR-0001："finish 后不再收集"从约定变机制）。
 *
 * <p>bootstrap 在某扩展点的 finisher 运行结束后，若累积器实现了本接口则调用
 * {@link #seal()}——此后累积器对任何再收集（迟到的 register 调用、被偷走的引用）
 * 抛 {@link IllegalStateException}。产物本身是 finisher 产出的不可变快照，
 * 密封保护的是"finish 后继续写入被静默丢弃"的困惑源。
 */
public interface Sealable {

    /** 密封累积器：此后任何收集调用必须抛 {@link IllegalStateException}。 */
    void seal();
}
