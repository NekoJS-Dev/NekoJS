package com.tkisor.nekojs.core.state;

/**
 * 候选域计划的联合预检/联合成败边界（票 10 AC5）。
 *
 * <p>本接口刻意<b>不包含领域语义</b>：global/shared 写集是第一个内建计划，测试计划与后续
 * 领域计划（如 Item/Block modification 的 candidate plan）可以挂入同一联合边界——同一
 * candidate 的所有计划在 commit 前统一 {@link #preflight()}，全部通过后在 commit 点统一
 * {@link #publish()}，任一预检失败则 candidate 失败、一切计划都不发布（联合成败）。
 * 本票不实现任何领域计划，也不把领域 Adapter 拉进 global owner。
 *
 * <p>契约（实现者必须遵守）：
 * <ul>
 *   <li>{@link #preflight()} 在候选期（STATE_PLAN 阶段）调用，可以自由抛出——抛出即让
 *       整个 candidate 失败（结构化 reload 失败，domain = {@link #domain()}）；</li>
 *   <li>{@link #publish()} 在 commit 点、global/shared 写集发布<b>之前</b>调用；
 *       preflight 通过后 publish <b>不得抛出</b>——若违反契约抛出，reload 在 commit 点
 *       中止（global/shared 写集不发布、active 保留），但该计划自身的部分副作用不被回滚
 *       （spec 10：不承诺对计划内部副作用做深回滚）；</li>
 *   <li>publish 不得再进入 global/shared 视图写入（写集已冻结，联合预检已覆盖）。</li>
 * </ul>
 */
public interface CandidateStatePlan {

    /** 计划标识（进入失败结果的 domain，如 {@code "test-plan"}、{@code "item-modification"}）。 */
    String domain();

    /**
     * 联合预检：验证计划自身与已提交状态的相容性。可以抛出（任意异常都会以
     * {@code state-plan-preflight:<domain>} 归因为 candidate 失败），不得产生对外可见副作用。
     */
    void preflight();

    /**
     * 联合发布：preflight 全部通过后在 commit 点执行。通过预检后不得抛出（见接口契约）。
     */
    void publish();
}
