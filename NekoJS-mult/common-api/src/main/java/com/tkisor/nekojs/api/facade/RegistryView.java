package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.annotation.ContractReceiver;
import java.util.List;

/**
 * 单个注册表的只读视图，暴露给脚本的 {@code Registry.get(...)} 结果。
 *
 * <p>所有方法只返回基础类型，不暴露 Minecraft 原生对象。
 */
@ContractReceiver
public interface RegistryView {
    /** 注册表本身是否存在。 */
    boolean exists();

    /** 注册表内所有条目 id（含命名空间）。 */
    List<String> all();

    /** 注册表内是否存在指定 id。 */
    boolean has(String id);

    /** 指定 tag 下的所有条目 id。 */
    List<String> tag(String tagId);

    /** 该注册表已注册的所有 data map 类型 id（如 {@code neoforge:furnace_fuels}）。 */
    List<String> dataMapIds();

    /** 读取指定条目的 data map 值（JSON 字符串）；条目/类型不存在返回 {@code null}。 */
    String dataMapValue(String dataMapTypeId, String id);
}
