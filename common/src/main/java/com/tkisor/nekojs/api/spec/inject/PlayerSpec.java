package com.tkisor.nekojs.api.spec.inject;

import com.tkisor.nekojs.api.annotation.RemapByPrefix;
import com.tkisor.nekojs.api.spec.PlatformAvailability;

/**
 * Player 跨平台统一扩展规范。
 *
 * <p>各平台的 {@code PlayerExtension} 必须 {@code extends PlayerSpec}，为每个 {@code neko$}
 * 方法提供平台特定的 {@code default} 实现。本接口的 {@code default} 方法体抛
 * {@link UnsupportedOperationException}——若平台 Extension 未覆盖某方法，运行时会明确报错，
 * SpecCoverageProcessor 编译期也会报错。
 *
 * <p><b>语义约定（不可违反）：</b>
 * <ul>
 *   <li>{@link #neko$addXpLevels(int)} 增加【等级】，不是经验点。
 *       NF 实现 {@code giveExperienceLevels}，CR 实现 {@code addExperienceLevel}。
 *   <li>{@link #neko$addXpPoints(int)} 增加【经验点】，不是等级。
 *       NF 实现 {@code giveExperiencePoints}，CR 实现 {@code addExperience}。
 * </ul>
 * 旧的 {@code neko$addXp(int)} 因 NF=等级 / CR=点数 的语义歧义从规范移除。
 *
 * <p><b>碰撞排除（调研驱动）：</b>以下方法因与原生 {@code Player} 零参同名碰撞（Graal
 * 无法分派，抛 "Multiple applicable overloads"）而<b>不</b>在本 spec 中：
 * <ul>
 *   <li>{@code isCreative()} —— 两平台原生都有零参 {@code isCreative()}，行为一致，JS 直接用原生
 *   <li>{@code sendMessage(Object)} —— CR 原生有 {@code sendMessage(ITextComponent)} 零参碰撞
 * </ul>
 *
 * <p><b>参数类型约定：</b>{@code give(Object)} 的参数是平台原生的 ItemStack——JS 侧 Graal
 * 自动转换，spec 不声明具体 MC 类型以遵守 common-api 边界约束。
 */
@RemapByPrefix("neko$")
@PlatformAvailability(PlatformAvailability.Scope.ALL)
public interface PlayerSpec {

    /** 该玩家是否为服务端 OP。 */
    default boolean neko$isOp() {
        throw new UnsupportedOperationException("PlayerSpec.neko$isOp not implemented");
    }

    /** 发放物品到背包（满则掉落）。参数是平台原生 ItemStack。 */
    default void neko$give(Object stack) {
        throw new UnsupportedOperationException("PlayerSpec.neko$give not implemented");
    }

    /** 设置游戏模式：survival / creative / adventure / spectator。 */
    default void neko$setGamemode(String gamemode) {
        throw new UnsupportedOperationException("PlayerSpec.neko$setGamemode not implemented");
    }

    /** 取当前游戏模式名。 */
    default String neko$getGamemode() {
        throw new UnsupportedOperationException("PlayerSpec.neko$getGamemode not implemented");
    }

    /** 增加经验【等级】（非点数）。各平台必须实现为加等级。 */
    default void neko$addXpLevels(int levels) {
        throw new UnsupportedOperationException("PlayerSpec.neko$addXpLevels not implemented");
    }

    /** 增加经验【点数】（非等级）。各平台必须实现为加经验点。 */
    default void neko$addXpPoints(int points) {
        throw new UnsupportedOperationException("PlayerSpec.neko$addXpPoints not implemented");
    }

    /** 取当前经验等级。 */
    default int neko$getXpLevel() {
        throw new UnsupportedOperationException("PlayerSpec.neko$getXpLevel not implemented");
    }

    /** 设置经验等级。 */
    default void neko$setXpLevel(int level) {
        throw new UnsupportedOperationException("PlayerSpec.neko$setXpLevel not implemented");
    }

    /** 踢出玩家。reason 接受 String 或平台原生 Component。 */
    default void neko$kick(Object reason) {
        throw new UnsupportedOperationException("PlayerSpec.neko$kick not implemented");
    }
}
