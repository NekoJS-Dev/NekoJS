// 26.x 面（BlockBehaviour.Properties 修改面为 26.x API，1.21.1 无 BlockEvents.modification 总线）。
//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.api.annotation.Doc;
import com.tkisor.nekojs.api.annotation.Param;
import com.tkisor.nekojs.api.annotation.Return;
import com.tkisor.nekojs.bindings.event.BlockEvents;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * Server-side block property modification event payload（{@code BlockEvents.modification}）。
 *
 * <h2>ticket 39 起的收集语义（与 {@link ItemModificationEventJS} 同）</h2>
 * 事件由 {@code ModificationDomainOwner} 在两个合法收集点派发（事务 reload 的
 * DOMAIN_PLAN 阶段派发给候选挂起监听器 / 初始 generation 派发给 active 总线）。
 * {@link #modify} 只<b>收集与规范化</b>声明，不触碰 live Block/BlockState——
 * 「恢复基线 → 应用完整新计划」只在 commit 点的平台 Adapter。
 *
 * <h2>JS API（不变）</h2>
 * <pre>
 * BlockEvents.modification(event {@code ->} {
 *   event.modify('minecraft:stone', block {@code ->} {
 *     block.hardness = 2;              // 与 block.setHardness(2) 经同一 setter
 *     block.resistance = 6;
 *     block.lightLevel = 15;
 *   });
 * });
 * </pre>
 *
 * <h2>Visibility note（不变）</h2>
 * Writes update the {@code BlockBehaviour.Properties} fields plus the copies held by the
 * {@link Block} and every {@code BlockState}（Adapter 应用期），so all existing states pick
 * up the change immediately on the server. Clients are NOT resynced: players need to relog
 * (or receive a chunk resync) to observe visual-only effects such as light emission——
 * 该不可同步边界由 ticket 39 capability 记录显式表达，不靠隐藏漂移。
 */
public class BlockModificationEventJS {

    private final ModificationCandidatePlan plan;
    private int declaredCount;

    /** @param plan 收集目标计划（由 {@code ModificationDomainOwner} 创建）。 */
    public BlockModificationEventJS(ModificationCandidatePlan plan) {
        this.plan = plan;
    }

    /**
     * Records a modification declaration for the block with the given id. The callback
     * receives a {@link BlockModificationJS} view（回调抛出的异常向上传播：收集期失败 →
     * 整批失败，不再有旧路径的部分应用）。
     *
     * <p>回调参数类型保持改造前的函数式接口签名（{@code Consumer<BlockModificationJS>}）：
     * Java 侧直接传 lambda；脚本侧的 Graal 函数由沙盒 HostAccess
     * （{@code allowAllImplementations}）实现该接口。视图本身是
     * {@link graal.graalvm.polyglot.proxy.ProxyObject}，所以脚本拿到的参数上
     * {@code block.hardness = 2} 与 {@code block.setHardness(2)} 命中同一 setter /
     * 校验 / 规范化 / 计划路径（AC8，见 {@link ModificationViewSurface}）。
     *
     * @param blockId block id, e.g. {@code 'minecraft:stone'} (namespace optional)
     * @param modifier property callback（脚本函数或 Java {@code Consumer}）
     */
    @Doc("Records a runtime property modification declaration for one block.")
    @Param(name = "blockId", value = "block id like 'minecraft:stone' (the 'minecraft:' prefix is optional)")
    @Param(name = "modifier", value = "callback receiving a block property view; assign block.hardness / block.resistance / block.lightLevel / block.requiresTool / block.friction / block.jumpFactor")
    public void modify(String blockId, Consumer<BlockModificationJS> modifier) {
        Identifier id = parseBlockId(blockId);
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            throw new IllegalArgumentException("Unknown block: " + blockId);
        }
        if (modifier == null) {
            throw new IllegalArgumentException("Modifier must not be null");
        }
        BlockModificationJS view = new BlockModificationJS(block);
        modifier.accept(view);
        plan.add(new ModificationDeclaration("block", id.toString(), view.normalizedProperties(), null));
        declaredCount++;
    }

    /** Number of declarations recorded so far during this event. */
    @Doc("Number of declarations recorded so far during this event.")
    @Return("how many declarations this event has collected")
    public int getModifiedCount() {
        return declaredCount;
    }

    static Identifier parseBlockId(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("Block id must not be empty");
        }
        String id = blockId.trim();
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid block id: " + blockId);
        }
        return location;
    }
}
//?}
