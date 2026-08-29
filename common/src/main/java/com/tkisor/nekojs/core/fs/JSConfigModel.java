package com.tkisor.nekojs.core.fs;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JSConfigModel {
    public CompilerOptions compilerOptions = new CompilerOptions();

    /**
     * 关闭 Automatic Type Acquisition：脚本工程自带 probe 全量声明，ATA 只会按 JS 依赖名
     * 猜测并联网拉取 @types 包（离线/代理环境下卡住语言服务、混入无关全局），对补全有害无益。
     */
    public TypeAcquisition typeAcquisition = new TypeAcquisition();

    public List<String> include = Arrays.asList(
            "./**/*.js",
            "./**/*.mjs",
            "./**/*.cjs",
            "./**/*.ts",
            "./**/*.jsx",
            "./**/*.tsx"
    );

    /**
     * Configures TypeScript's automatic JSX runtime for this model.
     * Generated workspace configs remain classic by default; callers opt in explicitly.
     */
    public void useAutomaticJsxRuntime() {
        compilerOptions.jsx = "react-jsx";
        compilerOptions.jsxImportSource = "nekojs";
        compilerOptions.jsxFactory = null;
        compilerOptions.jsxFragmentFactory = null;
        compilerOptions.experimentalDecorators = false;
    }

    public static class CompilerOptions {
        public String target = "ESNext";
        // ESM-first：脚本源码统一用 import/export（NekoEsmToUnifiedIrLowering 在编译期处理），
        // probe 生成的 .d.ts 也全用 ESM import，ScriptExecutor 走 native ESM evaluation。
        // moduleResolution 用 "bundler"（TS 5.0+）：支持 paths 通配与 ESM 互操作 specifier，
        // 且不触发 TS 6/7 对 "node"（node10）的弃用警告。
        public String module = "ESNext";

        public String moduleDetection = "force";

        public String moduleResolution = "bundler";
        public String jsx = "react";
        public String jsxFactory = "__nekoJsxFactory";
        public String jsxFragmentFactory = "__nekoJsxFragment";
        public String jsxImportSource;

        public List<String> lib = List.of("ESNext");
        public boolean allowJs = true;
        public boolean checkJs = false;

        public boolean skipLibCheck = true;

        // baseUrl 已弃用（TS 6/7）：paths 相对 jsconfig 位置解析，无需 baseUrl

        public List<String> typeRoots;

        public boolean esModuleInterop = true;
        public boolean allowSyntheticDefaultImports = true;
        // 装饰器：NekoJS 运行时不支持（脚本引擎非 TS 框架）。保持 false 避免误导 IDE/用户。
        // 遇到 @Decorator 会在擦除阶段清晰报错。如需装饰器语义，请用普通函数包装。
        public boolean experimentalDecorators = false;
        public boolean strict = true;
        public Map<String, List<String>> paths = new LinkedHashMap<>();

    }

    public static class TypeAcquisition {
        public boolean enable = false;
    }
}