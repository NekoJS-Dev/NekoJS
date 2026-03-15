package com.tkisor.nekojs.core.fs;

import java.util.Arrays;
import java.util.List;

public class JSConfigModel {
    public CompilerOptions compilerOptions = new CompilerOptions();
    public List<String> include = Arrays.asList(
            "startup_scripts/**/*",
            "server_scripts/**/*",
            "client_scripts/**/*",
            "common_scripts/**/*",
            "probe/**/*"
    );

    public static class CompilerOptions {
        public String target = "ESNext";
        public String module = "CommonJS";

        public String moduleDetection = "force";

        public String moduleResolution = null;
        public String jsx = null;

        public List<String> lib = List.of("ESNext");
        public boolean allowJs = true;
        public boolean checkJs = false;
        public String baseUrl = ".";
        public List<String> typeRoots = List.of("./probe");
    }
}