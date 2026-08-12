package com.tkisor.nekojs.probe;

/**
 * Probe 生成结果类型（由各 {@link ProbeBackend#generate} 返回）。
 *
 * <p>历史上面向单生成器模型（{@code generate(snapshot, outputDir)}）；Phase 1 多 backend 化后，
 * 原 {@code ProbeOrchestrator}（唯一实现者）已删除、逻辑迁入 {@link TypeScriptProbeBackend}，
 * 本接口仅保留 {@link GenerateResult} 作为 backend 返回值类型。
 */
public interface ProbeGenerator {

    /** 生成结果。 */
    record GenerateResult(
            boolean success,
            int filesGenerated,
            long durationMs,
            String message
    ) {
        public static GenerateResult success(int filesGenerated, long durationMs) {
            return new GenerateResult(true, filesGenerated, durationMs, "OK");
        }

        public static GenerateResult failure(String message) {
            return new GenerateResult(false, 0, 0, message);
        }
    }
}
