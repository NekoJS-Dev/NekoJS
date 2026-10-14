package com.tkisor.nekojs.core.pack;

import java.nio.file.Path;

/** Frozen author-tool outcome; output creation does not imply activation or publication. */
public record PackToolResult(boolean success, String selector, Path output, String detail) {}
