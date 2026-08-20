package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.api.surface.ApiTypeRef;
import com.tkisor.nekojs.probe.events.GlobalDecl;
import com.tkisor.nekojs.probe.events.ProbeAddGlobalEventJS;
import com.tkisor.nekojs.probe.events.ProbeAssignTypeEventJS;
import com.tkisor.nekojs.probe.events.ProbeEvents;
import com.tkisor.nekojs.probe.events.ProbeModifyTypeEventJS;
import com.tkisor.nekojs.probe.events.ProbeOverrides;
import com.tkisor.nekojs.probe.events.ProbeSnippetEventJS;
import com.tkisor.nekojs.probe.events.Snippet;
import com.tkisor.nekojs.probe.ir.TypeDecl;
import com.tkisor.nekojs.probe.ir.TypeReflector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 共享 IR 构建与 probe.* 覆盖事件（包内组件，从 {@link ProbeCoordinator} 抽出）。无状态，全静态。
 *
 * <p>固定顺序契约：先应用 {@code probe.assign_type}（全局类型重定向，标记受影响类 mutated），
 * 再触发 {@code probe.modify_type}（参数级编辑）——{@code modify_type} 显式设置的槽不被
 * {@code assign_type} 二次覆盖（有 {@code ProbeCoordinatorHardeningTest#assignTypeAppliedBeforeModifyTypeExplicitEdit} 回归）。
 */
final class ProbeIrBuilder {

    private ProbeIrBuilder() {
    }

    /**
     * 构建共享 IR（TypeReflector 反射每个收集到的类），依次：应用 {@code probe.assign_type}（全局类型重定向，
     * 标记受影响类 mutated）→ 触发 {@code probe.modify_type}（参数级编辑）。反射失败的类被跳过。
     * 返回不可变 IR 列表（已含编辑）。{@code assignMap} 为空时跳过应用步骤。
     * 事件监听器抛异常时继续（best-effort），但把「部分应用」风险写入 {@code warnings} 供调用方感知。
     *
     * <p>反射阶段并行执行（传入共享线程池，每类一个任务），随后按**原始收集顺序**（BFS 序）
     * 汇总进 LinkedHashMap，保证 {@code List.copyOf(ir.values())} 与串行版本顺序一致；
     * assign_type / modify_type 仍在 map 建完后串行执行（事件触发顺序不变）。
     */
    static List<TypeDecl> buildAndMutateIr(Collection<Class<?>> collected, Map<String, ApiTypeRef> assignMap,
                                            ExecutorService pool, List<String> warnings) {
        List<Class<?>> ordered = new ArrayList<>(collected);
        if (ordered.isEmpty()) return List.of();

        Map<String, TypeDecl> ir = new LinkedHashMap<>();
        {
            // 每类一个反射任务；TypeReflector 无实例状态，各任务新建实例（线程内独立）
            List<Future<TypeDecl>> futures = new ArrayList<>(ordered.size());
            for (Class<?> c : ordered) {
                futures.add(pool.submit(() -> new TypeReflector().reflect(c)));
            }
            // 按原始收集顺序汇总：LinkedHashMap 插入序即 collectClasses 的 BFS 序，后续 List.copyOf 稳定
            for (int i = 0; i < futures.size(); i++) {
                String fqn = ordered.get(i).getName();
                try {
                    ir.put(fqn, futures.get(i).get());
                } catch (InterruptedException e) {
                    // 中断不能当「反射失败」吞掉：恢复中断标志并向上传播，probe 立刻终止
                    // （携带半截 IR 继续渲染会把残缺产物提交到磁盘）
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while building shared probe IR", e);
                } catch (Throwable t) {
                    // 无法反射的类跳过：各 backend 自行按需补反射；Python 不产出其 stub。
                    // 逐类 debug 日志（含类名）便于排查缺失类型
                    NekoJS.LOGGER.debug("Probe: failed to reflect class {} into shared IR (skipped)", fqn, t);
                }
            }
        }
        // 1. assign_type：先重定向反射产出的类型（标记受影响类 mutated，TS 会重渲染）
        if (assignMap != null && !assignMap.isEmpty()) {
            for (TypeDecl d : ir.values()) ProbeAssignTypeEventJS.applyTo(d, assignMap);
        }
        // 2. modify_type：参数级编辑（在 assign 之后；modify_type 显式设置的类型不被 assign 二次覆盖）
        if (!ir.isEmpty() && ProbeEvents.MODIFY_TYPE.hasListeners()) {
            try {
                ProbeEvents.MODIFY_TYPE.post(new ProbeModifyTypeEventJS(ir));
            } catch (Throwable t) {
                NekoJS.LOGGER.error("probe.modify_type event threw; edits may be incomplete", t);
                warnings.add("probe.modify_type event threw; edits may be incomplete (" + t + ")");
            }
        }
        return List.copyOf(ir.values());
    }

    /** 触发 {@code probe.assign_type} 一次，返回收集到的「Java FQN → 自定义类型」映射。 */
    static Map<String, ApiTypeRef> fireAssignType(List<String> warnings) {
        ProbeAssignTypeEventJS ev = new ProbeAssignTypeEventJS();
        try {
            ProbeEvents.ASSIGN_TYPE.post(ev);
        } catch (Throwable t) {
            NekoJS.LOGGER.error("probe.assign_type event threw; assignments may be incomplete", t);
            warnings.add("probe.assign_type event threw; assignments may be incomplete (" + t + ")");
        }
        return ev.assignments();
    }

    /** 触发 {@code probe.add_global} 与 {@code probe.snippets}（各有监听器才触发），返回捆绑结果。 */
    static ProbeOverrides fireOverrides(List<String> warnings) {
        List<GlobalDecl> globals = List.of();
        List<Snippet> snippets = List.of();
        if (ProbeEvents.ADD_GLOBAL.hasListeners()) {
            ProbeAddGlobalEventJS ev = new ProbeAddGlobalEventJS();
            try {
                ProbeEvents.ADD_GLOBAL.post(ev);
            } catch (Throwable t) {
                NekoJS.LOGGER.error("probe.add_global event threw", t);
                warnings.add("probe.add_global event threw (" + t + ")");
            }
            globals = ev.globals();
        }
        if (ProbeEvents.SNIPPETS.hasListeners()) {
            ProbeSnippetEventJS ev = new ProbeSnippetEventJS();
            try {
                ProbeEvents.SNIPPETS.post(ev);
            } catch (Throwable t) {
                NekoJS.LOGGER.error("probe.snippets event threw", t);
                warnings.add("probe.snippets event threw (" + t + ")");
            }
            snippets = ev.snippets();
        }
        return new ProbeOverrides(globals, snippets);
    }
}
