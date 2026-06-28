package com.tkisor.nekojs.api.probe;

import com.tkisor.nekojs.api.catalog.NekoScriptCatalogSnapshot;

/**
 * 探针生成器接口：负责生成类型声明文件（.d.ts）和 AI skill 文件。
 *
 * <p>NekoJS 内置简易实现，第三方插件可通过 {@link ProbeRegistry#setGenerator(ProbeGenerator, String)} 替换为更完善的实现。
 * 替换后，内置的 {@code /nekojs probe} 指令自动使用新实现。
 *
 * <p>实现要求：
 * <ul>
 *   <li>线程安全 — 可能在服务器线程调用</li>
 *   <li>幂等 — 相同输入多次调用结果一致</li>
 *   <li>不阻塞主线程过久 — 大型 modpack 应在后台线程执行</li>
 * </ul>
 */
public interface ProbeGenerator {

    /**
     * 生成探针文件。
     *
     * @param snapshot 当前 catalog 快照（包含事件、绑定、类型文档等）
     * @param outputDir 输出目录（通常为 {@code .neko_probe/}）
     * @return 生成结果
     */
    GenerateResult generate(NekoScriptCatalogSnapshot snapshot, java.nio.file.Path outputDir);

    /**
     * 返回此生成器的名称，用于日志和诊断。
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * 生成结果。
     */
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
