package com.tkisor.nekojs.wrapper.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 注册 builder（{@code .tag(...)}）的待写 tag 集合。
 *
 * <p>注册事件在 STARTUP 阶段触发，而 tag 要到数据包加载阶段才构建绑定——两者隔着整个
 * 启动流程。{@code .tag(...)} 因此只在 builder 上记下 (注册表 key, tag id, 目标 id)
 * 三元组（{@link #record}），存入本类的静态待写集合；等
 * {@link com.tkisor.nekojs.wrapper.event.server.TagEventJS} 构造时
 * （{@code ServerEvents.tags} 机制，{@code TagLoader} build 阶段）由
 * {@link #flushInto} 按注册表灌入事件，随事件 {@code apply()} 写回 tag 源表。
 *
 * <p>待写条目是注册对象的稳定事实：{@link #flushInto} 不消费集合，每次 tag（重）加载
 * 都会重新灌入（{@code /reload} 后依然生效）。注册事件每个 JVM 生命周期只触发一次，
 * 运行期不存在「builder 重注册、旧条目失效」的路径——{@link #clear()} 供 STARTUP
 * 整体重载与测试做全量清理（builder 重注册前调用，旧条目随之丢弃）。
 */
public final class BuilderTags {

    private BuilderTags() {
    }

    /** 一条待写 tag：把 {@code targetId} 加进 {@code registryKey} 注册表的 {@code tagId}。 */
    public record PendingTag(ResourceKey<? extends Registry<?>> registryKey, Identifier tagId, Identifier targetId) {
    }

    private static final List<PendingTag> PENDING = new ArrayList<>();

    /**
     * 记一条待写 tag。重复三元组只记一次（builder 重复调用 {@code .tag(...)} 无副作用）；
     * 任一参数为 {@code null} 时静默忽略（拼错的脚本参数不应炸掉注册流程）。
     */
    public static void record(ResourceKey<? extends Registry<?>> registryKey, Identifier tagId, Identifier targetId) {
        if (registryKey == null || tagId == null || targetId == null) {
            return;
        }
        PendingTag pending = new PendingTag(registryKey, tagId, targetId);
        synchronized (BuilderTags.PENDING) {
            if (!PENDING.contains(pending)) {
                PENDING.add(pending);
            }
        }
    }

    /**
     * 把注册表 id 匹配（{@code registryKey.identifier().equals(registryId)}）的待写条目
     * 灌给 {@code adder}——即 {@link com.tkisor.nekojs.wrapper.event.server.TagEventJS}
     * 暴露的 {@code add(tagId, targetId)} 语义。不消费集合：每次 tag（重）加载都重灌，
     * 让待写条目跨 reload 存活。
     */
    public static void flushInto(Identifier registryId, BiConsumer<Identifier, Identifier> adder) {
        List<PendingTag> snapshot;
        synchronized (BuilderTags.PENDING) {
            snapshot = List.copyOf(PENDING);
        }
        for (PendingTag pending : snapshot) {
            if (pending.registryKey().identifier().equals(registryId)) {
                adder.accept(pending.tagId(), pending.targetId());
            }
        }
    }

    /** 清空全部待写条目（STARTUP 整体重载 / 测试用）。 */
    public static void clear() {
        synchronized (BuilderTags.PENDING) {
            PENDING.clear();
        }
    }

    /** 当前待写条目数（诊断 / 测试用）。 */
    public static int size() {
        synchronized (BuilderTags.PENDING) {
            return PENDING.size();
        }
    }
}
