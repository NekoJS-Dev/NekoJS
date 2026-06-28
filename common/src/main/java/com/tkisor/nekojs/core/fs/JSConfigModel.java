package com.tkisor.nekojs.core.fs;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JSConfigModel {
    public CompilerOptions compilerOptions = new CompilerOptions();

    public List<String> include = Arrays.asList(
            "./**/*.js",
            "./**/*.mjs",
            "./**/*.cjs",
            "./**/*.ts",
            "./**/*.jsx",
            "./**/*.tsx"
    );

    public static class CompilerOptions {
        public String target = "ESNext";
        public String module = "CommonJS";

        public String moduleDetection = "force";

        public String moduleResolution = null;
        public String jsx = "react";
        public String jsxFactory = "__nekoJsxFactory";
        public String jsxFragmentFactory = "__nekoJsxFragment";

        public List<String> lib = List.of("ESNext");
        public boolean allowJs = true;
        public boolean checkJs = false;

        public boolean skipLibCheck = true;

        public String baseUrl = ".";

        public List<String> typeRoots;

        public boolean esModuleInterop = true;
        public boolean allowSyntheticDefaultImports = true;
        public boolean experimentalDecorators = true;
        public boolean strict = true;
        public Map<String, List<String>> paths = new LinkedHashMap<>();

    }
}