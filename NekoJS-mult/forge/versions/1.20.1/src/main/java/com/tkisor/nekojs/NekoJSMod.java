package com.tkisor.nekojs;

import net.minecraftforge.fml.common.Mod;

/**
 * 1.20.1 Forge 骨架入口（B4 spike）：仅验证 legacyforge 工具链与产物形态。
 * 引擎层（:common）刻意未接入——Java 21 字节码与 1.20.1 的 Java 17 运行时冲突
 * 待主仓计划 D7（--release 17 / graal 工件矩阵）拍板后再接。
 */
@Mod(NekoJSMod.MOD_ID)
public final class NekoJSMod {

    public static final String MOD_ID = "nekojs";

    public NekoJSMod() {
    }
}
