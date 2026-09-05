package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.core.RegistryAccess;

/**
 * 标签更新事件（{@code ServerEvents.tagsUpdated}）的**加载器中立**载荷。
 *
 * <p>NeoForge 侧直传原生 {@code TagsUpdatedEvent}，不经本类；fabric 侧由
 * {@code FabricServerEventBindings} 从 {@code CommonLifecycleEvents.TAGS_LOADED}
 * 转换（fabric impl 经其 {@code ReloadableServerResourcesMixin} 在服务端资源
 * （含标签）装载完成时触发，时机与 NF 对齐）。
 *
 * <p><b>语义差异（记录）</b>：NF 原生事件带 {@code UpdateCause}（服务端数据加载/
 * 客户端同步/静态数据三类），fabric 回调无成因参数——本载荷以
 * {@code shouldUpdateStaticData} 承载 fabric 的 {@code updated} 布尔值，成因信息
 * 不跨平台。不可取消。高频面（每次 reload），监听器请保持轻量。
 */
@Doc("Fired after tags have been (re)loaded on the server.")
@Getter
public class TagUpdatedEventJS {

    @Doc("The registry access holding the updated tags.")
    private final RegistryAccess registries;

    @Doc("Whether the tag contents actually changed (fabric semantics).")
    private final boolean shouldUpdateStaticData;

    public TagUpdatedEventJS(RegistryAccess registries, boolean shouldUpdateStaticData) {
        this.registries = registries;
        this.shouldUpdateStaticData = shouldUpdateStaticData;
    }
}
