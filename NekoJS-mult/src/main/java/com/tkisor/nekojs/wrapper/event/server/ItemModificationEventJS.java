// 26.x 实现（Holder.Reference#bindComponents 为 26.x API）。1.21.1 的实现是
// versions/1.21.1/src 下的同名文件，改本文件行为时须同步它。
//? if >=26 {
package com.tkisor.nekojs.wrapper.event.server;

import com.tkisor.nekojs.bindings.event.ItemEvents;
import com.tkisor.nekojs.core.modification.ModificationCandidatePlan;
import com.tkisor.nekojs.core.modification.ModificationDeclaration;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import java.util.function.Consumer;

/**
 * Server-side item property modification event payload（{@code ItemEvents.modification}）。
 *
 * <h2>ticket 39 起的收集语义</h2>
 * 事件由 {@code ModificationDomainOwner} 在两个合法收集点派发：
 * <ul>
 *   <li><b>事务 reload 的 DOMAIN_PLAN 阶段</b>——派发给<b>候选</b>的挂起监听器
 *       （{@code CandidateDomainCollector.Handle.dispatch}），声明进入 inert 候选计划，
 *       与 global/shared 顶层写集联合预检（STATE_PLAN）、commit 点联合应用；</li>
 *   <li><b>初始 generation（服务器 about-to-start）</b>——派发给 active 总线监听器，
 *       收集后在同一 owner 内 preflight + 应用（无旧 active 时旧值 = vanilla 基线）。</li>
 * </ul>
 * {@link #modify} 只<b>收集与规范化</b>声明（同目标后声明整体替换前声明，与旧行为
 * characterization 一致），不触碰 live Item/默认组件——MC 应用只在
 * {@code ModificationDomainOwner}（平台 Adapter）。
 *
 * <h2>JS API（payload/side/priority/cancel/dispatch 时机不变，无第二 bus）</h2>
 * <pre>
 * ItemEvents.modification(event {@code ->} {
 *   event.modify('minecraft:diamond', item {@code ->} {
 *     item.maxStackSize = 16;   // property 写与 setMaxStackSize 经同一 setter（ModificationViewSurface）
 *   });
 * });
 * </pre>
 */
public class ItemModificationEventJS {

    private final ModificationCandidatePlan plan;
    private int declaredCount;

    /** @param plan 收集目标计划（由 {@code ModificationDomainOwner} 创建）。 */
    public ItemModificationEventJS(ModificationCandidatePlan plan) {
        this.plan = plan;
    }

    /**
     * Records a modification declaration for the item with the given id. The callback
     * receives an {@link ItemModificationJS} view（回调抛出的异常向上传播：收集期失败 →
     * 整批失败，不再有旧路径的部分应用）。
     *
     * <p>回调参数类型保持改造前的函数式接口签名（{@code Consumer<ItemModificationJS>}）：
     * Java 侧直接传 lambda；脚本侧的 Graal 函数由沙盒 HostAccess
     * （{@code allowAllImplementations}）实现该接口。视图本身是
     * {@link graal.graalvm.polyglot.proxy.ProxyObject}，所以脚本拿到的参数上
     * {@code item.maxStackSize = 16} 与 {@code item.setMaxStackSize(16)} 命中同一
     * setter / 校验 / 规范化 / 计划路径（AC8，见 {@link ModificationViewSurface}）。
     *
     * @param itemId item id, e.g. {@code 'minecraft:diamond'} (namespace optional)
     * @param modifier property callback（脚本函数或 Java {@code Consumer}）
     */
    public void modify(String itemId, Consumer<ItemModificationJS> modifier) {
        Identifier id = parseItemId(itemId);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            throw new IllegalArgumentException("Unknown item: " + itemId);
        }
        if (modifier == null) {
            throw new IllegalArgumentException("Modifier must not be null");
        }
        ItemModificationJS view = new ItemModificationJS();
        modifier.accept(view);
        plan.add(new ModificationDeclaration("item", id.toString(), view.normalizedProperties(), null));
        declaredCount++;
    }

    /** Number of declarations recorded so far during this event. */
    public int getModifiedCount() {
        return declaredCount;
    }

    static Identifier parseItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Item id must not be empty");
        }
        String id = itemId.trim();
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        Identifier location = Identifier.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("Invalid item id: " + itemId);
        }
        return location;
    }

    // builtInRegistryHolder() 无非废弃等价 API（components() 委托它），保守保留
    @SuppressWarnings("deprecation")
    static void applyComponents(Item item, DataComponentMap components) {
        item.builtInRegistryHolder().bindComponents(components);
    }
}
//?}
