package com.tkisor.nekojs.core.modification;

import java.util.List;

/**
 * 修改计划的平台/版本 Adapter 接缝（票 39 AC11）：MC 应用只存在 Adapter——
 * 26.x 与 1.21.1 的 item default components、block/state 属性、注册时机与同步差异
 * 由各节点 Adapter 各自实现并记录，common 不感知任何 MC 类型。
 *
 * <p>契约（与 {@link ModificationCandidatePlan} 的联合预检/联合发布边界对齐）：
 * <ul>
 *   <li>{@link #preflight(List)} 在候选 STATE_PLAN 阶段调用：解析每个目标、用
 *       <b>基线</b>（NekoJS 持有的原始值，见 AC7）校验属性不变量、确认 Adapter 可应用
 *       整批。可以自由抛出——抛出即整批失败（保留旧 active）。不得产生对外可见副作用
 *       （只读解析/校验）。</li>
 *   <li>{@link #apply(List)} 在 commit 点调用：<b>先恢复全部 NekoJS 持有基线，再按声明
 *       顺序应用新完整计划</b>（AC7）。preflight 通过后 apply 不得抛（违反
 *       {@code CandidateStatePlan#publish} 契约时 global/shared 写集不发布、计划自身
 *       部分副作用不回滚）；平台无法证明某字段可恢复的字段必须在 preflight 拒绝整批，
 *       不得把静默 stale 当成功。</li>
 * </ul>
 *
 * <p>恢复/apply 只承诺 NekoJS 拥有且 Adapter 已证明可恢复的字段；不承诺回滚任意 Java
 * 对象内部状态、其他 mod、世界、网络或文件副作用（spec 09 外部副作用边界）。
 */
public interface ModificationApplier {

    /** Adapter 节点标识（诊断/capability 记录用，如 {@code "26.x-shared"}）。 */
    String adapterId();

    /**
     * 联合预检（候选期）：目标解析 + 基线不变量校验 + 可恢复性确认。抛出即整批失败。
     */
    void preflight(List<ModificationDeclaration> declarations);

    /**
     * 应用（commit 点）：恢复 NekoJS 持有基线 → 按声明顺序应用完整新计划。
     * preflight 通过后不得抛。
     */
    void apply(List<ModificationDeclaration> declarations);
}
