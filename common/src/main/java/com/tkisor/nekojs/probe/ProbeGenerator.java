package com.tkisor.nekojs.probe;

import java.util.ArrayList;
import java.util.List;

/**
 * Probe 生成结果类型（由各 {@link ProbeBackend#generate} 返回）。
 *
 * <p>历史上面向单生成器模型（{@code generate(snapshot, outputDir)}）；Phase 1 多 backend 化后，
 * 原 {@code ProbeOrchestrator}（唯一实现者）已删除、逻辑迁入 {@link TypeScriptProbeBackend}，
 * 本接口仅保留 {@link GenerateResult} 作为 backend 返回值类型。
 */
public interface ProbeGenerator {

    /**
     * 生成结果。{@code warnings} 携带「生成成功但存在部分降级」的信息（事件监听器抛异常、
     * editor-config 贡献失败等）——此前这些只进日志，调用方（命令层）无从感知。
     */
    record GenerateResult(
            boolean success,
            int filesGenerated,
            long durationMs,
            String message,
            List<String> warnings
    ) {
        public GenerateResult {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        /** 兼容旧 4 参调用点（warnings = 空）。 */
        public GenerateResult(boolean success, int filesGenerated, long durationMs, String message) {
            this(success, filesGenerated, durationMs, message, List.of());
        }

        public static GenerateResult success(int filesGenerated, long durationMs) {
            return new GenerateResult(true, filesGenerated, durationMs, "OK");
        }

        public static GenerateResult failure(String message) {
            return new GenerateResult(false, 0, 0, message);
        }

        /** 在既有结果上追加 warnings（无新增时原样返回）。 */
        public static GenerateResult withWarnings(GenerateResult base, List<String> extra) {
            if (extra == null || extra.isEmpty()) return base;
            List<String> merged = new ArrayList<>(base.warnings());
            merged.addAll(extra);
            return new GenerateResult(base.success(), base.filesGenerated(), base.durationMs(), base.message(), merged);
        }
    }
}
