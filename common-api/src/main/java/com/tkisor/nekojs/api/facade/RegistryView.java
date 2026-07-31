package com.tkisor.nekojs.api.facade;

import java.util.List;

/**
 * 单个注册表的只读视图，暴露给脚本的 {@code Registry.get(...)} 结果。
 *
 * <p>所有方法只返回基础类型，不暴露 Minecraft 原生对象。
 */
public interface RegistryView {
    /** 注册表本身是否存在。 */
    boolean exists();

    /** 注册表内所有条目 id（含命名空间）。 */
    List<String> all();

    /** 注册表内是否存在指定 id。 */
    boolean has(String id);

    /** 指定 tag 下的所有条目 id。 */
    List<String> tag(String tagId);
}
