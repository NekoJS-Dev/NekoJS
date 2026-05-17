package com.tkisor.nekojs.core.compiler;

import com.tkisor.nekojs.api.compiler.NekoSourceFile;
import com.tkisor.nekojs.api.compiler.NekoTokenStream;

public record NekoRawTokenStream(NekoSourceFile source) implements NekoTokenStream {
}
