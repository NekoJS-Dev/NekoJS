package com.tkisor.nekojs.fabric;

/**
 * fabric 客户端「离开旧世界才清」守卫：NeoForge 侧挂在 client level unload（断线与切维度
 * 都触发），fabric 无对应事件——盯客户端世界实例变化等价（切维度换 {@code ClientLevel}
 * 实例、断线变 null）。
 *
 * <p>关键语义：只在「离开一个已有世界」（上次见过非 null 世界）时报告需要清空；
 * 首次进服那次 null→世界 的变化不清——否则会把刚随进服推下来的数据
 * （pdata mirror / clientData 键值）一起抹掉。实例按引用（identity）比较，
 * 每次世界实例变化都视为切换。
 *
 * <p>进程级单实例（与两端挂钩点同生命周期）：
 * {@code FabricPDataSync}（清 pdata mirror）与 {@code FabricPlayNetwork}
 * （清 clientData store）各持一个，互不串扰。
 */
public final class ClientLevelWatch {

    private Object lastLevel;

    /**
     * 每客户端 tick 喂当前世界实例（可为 null）；返回是否「离开了旧世界」——
     * true 时调用方应清空其对应的客户端 mirror/store，false 时不动。
     */
    public boolean leftPreviousLevel(Object currentLevel) {
        if (currentLevel == lastLevel) return false;
        boolean leftPrevious = lastLevel != null;
        lastLevel = currentLevel;
        return leftPrevious;
    }
}
