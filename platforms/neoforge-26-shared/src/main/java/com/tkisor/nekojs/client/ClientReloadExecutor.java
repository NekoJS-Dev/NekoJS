package com.tkisor.nekojs.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * CLIENT 脚本 reload 的线程归属（W7/A2）：客户端 Context 的所有权线程是客户端主线程
 * （Render 线程）——事件分发、timers flush、F3+T 资源 reload 全在那里。单人游戏中
 * {@code /nekojs reload client} 在集成服务器线程执行，原地 reload 会让同一 Context
 * 被两个线程先后触碰（外部 synchronized 保住了顺序，但每次新增入口都要重新推理）；
 * 这里统一转投客户端主线程，让「CLIENT 的 Context 只有一个所有者线程」结构成立。
 *
 * <p>dedicated server 上 {@link #isClientDist()} 为 false，调用方不会走 {@link #execute}；
 * {@code Minecraft} 只在 execute 内被触碰，本类在服务器侧类初始化安全。
 */
public final class ClientReloadExecutor {

    private ClientReloadExecutor() {}

    public static boolean isClientDist() {
        return FMLEnvironment.getDist() == Dist.CLIENT;
    }

    /** 转投客户端主线程执行；命令侧立即返回，结果反馈由 reload 流程自身的广播/日志承担。 */
    public static void execute(Runnable task) {
        Minecraft.getInstance().execute(task);
    }
}
