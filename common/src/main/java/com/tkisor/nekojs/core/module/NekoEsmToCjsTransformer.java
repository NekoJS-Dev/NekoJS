package com.tkisor.nekojs.core.module;

import java.nio.file.Path;

public interface NekoEsmToCjsTransformer {
    NekoModuleTransformResult transform(Path file, String code, String inputSourceMap) throws Exception;
}
