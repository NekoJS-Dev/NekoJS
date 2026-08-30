package com.tkisor.nekojs.api.facade;

import java.util.List;

/**
 * 平台信息门面，暴露为脚本侧全局对象 {@code Platform}。
 *
 * <p>提供运行环境（客户端/服务端、开发环境）、加载器、Minecraft 版本以及已加载 mod
 * 的只读信息。所有方法只返回基础类型或 {@link ModInfoValue}，不暴露 Minecraft 原生对象。
 */
public interface PlatformFacade {
    /** 当前进程是否为逻辑客户端。 */
    boolean isClient();

    /** 是否运行在开发/调试环境。 */
    boolean isDevelopment();

    /** 当前 Minecraft 版本字符串（如 {@code "1.21.1"}）。 */
    String getMcVersion();

    /** 当前 mod 加载器标识（如 {@code "neoforge"}、{@code "forge"}）。 */
    String getLoaderId();

    /** 当前 mod 加载器版本字符串。 */
    String getLoaderVersion();

    /** 指定 modId 是否已加载；modId 为 {@code null} 或空白时抛异常。 */
    boolean isLoaded(String modId);

    /** 返回指定 modId 的元信息；mod 未加载时返回 {@code null}。 */
    ModInfoValue getInfo(String modId);

    /** 所有已加载 mod 的 id 列表（按字典序排序）。 */
    List<String> getList();

    /** 当前平台支持的平台能力标识列表（小写、下划线转连字符，按字典序排序）。 */
    List<String> capabilities();
}
