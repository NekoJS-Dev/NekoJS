package com.tkisor.nekojs.wrapper.event.entity;

import com.tkisor.nekojs.api.annotation.Doc;
import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 生物死亡掉落事件（{@code EntityEvents.drops}）的**加载器中立**载荷。
 *
 * <p>语义对齐 NeoForge {@code LivingDropsEvent}（26.1.2 中 getDrops 为
 * {@code Collection<ItemEntity>}，本载荷以 {@link #drops}
 * {@code List<ItemStack>} 形态提供——脚本加成物品/drops.clear() 等操作对
 * {@code List<ItemStack>} 更常见）。fabric 侧由 {@code FabricEntityEventBindingsV2} +
 * {@code MixinLivingEntityDeathDrops} 从 {@code LivingEntity#dropAllDeathLoot} 入口转换
 * （fabric-api 的 {@code ServerLivingEntityEvents} 无掉落回调，javap 实证）。
 *
 * <p><b>已知差异（fabric）</b>：原生掉落逻辑（装备 + 死亡战利品表 + 经验球）在
 * {@code dropAllDeathLoot} 内部逐件生成，无法作为集合交给脚本——监听器收到时
 * {@code drops} 恒为空列表（可 push 自定义物品）。取消语义：监听器
 * {@code return true} = 跳过原版掉落（对应 NeoForge setCanceled = 清空掉落），
 * 此时脚本 push 进 {@code drops} 的物品由桥接经
 * {@code LivingEntity#spawnAtLocation} 放出。
 *
 * <p>数据层面差异：NeoForge 侧脚本可读取原版生成的掉落（已生成 ItemEntity 集合）；
 * fabric 侧无此能力，只有实体/来源/被取消后的自定义掉落。
 */
@Doc("Fired when a living entity's death drops are generated (EntityEvents.drops). Dispatched by entity type.")
@Doc("Cancellable on fabric: return true to skip vanilla death drops (script-provided event.drops are then spawned).")
@Getter
public class LivingDropsEventJS {

    @Doc("The dying entity.")
    private final LivingEntity entity;

    @Doc("The damage source of the death.")
    private final DamageSource source;

    /**
     * 死亡掉落物品（fabric 侧触发瞬间为空列表；监听器可 push，取消时会被放出生效）。
     */
    @Doc("The drops. Empty when fired on fabric (vanilla generates them internally); push items here and cancel the event to spawn them instead.")
    private final List<ItemStack> drops;

    public LivingDropsEventJS(LivingEntity entity, DamageSource source, List<ItemStack> drops) {
        this.entity = entity;
        this.source = source;
        this.drops = new ArrayList<>(drops);
    }
}
