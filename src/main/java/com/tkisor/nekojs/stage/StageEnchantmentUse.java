package com.tkisor.nekojs.stage;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class StageEnchantmentUse {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<SlotVisit> SLOT_VISIT = new ThreadLocal<>();

    private StageEnchantmentUse() {
    }

    public static <T> T withActor(@Nullable Entity entity, ItemStack stack, Supplier<T> effect) {
        return withActor(entity, stack, false, effect);
    }

    public static <T> T withMiningActor(@Nullable Entity entity, Object tool, Supplier<T> effect) {
        return withActor(entity, tool, true, effect);
    }

    private static <T> T withActor(@Nullable Entity entity, Object stack, boolean mining, Supplier<T> effect) {
        Player actor = StageDamageActors.fromEntity(entity);
        if (actor == null || actor.level().isClientSide()) {
            return effect.get();
        }
        Context previous = CURRENT.get();
        CURRENT.set(new Context(stack, actor, mining));
        try {
            return effect.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static @Nullable Player actorFor(ItemStack stack) {
        Context context = CURRENT.get();
        return context != null && context.stack() == stack ? context.actor() : null;
    }

    public static @Nullable Player miningActorFor(Object tool) {
        Context context = CURRENT.get();
        return context != null && context.mining() && context.stack() == tool ? context.actor() : null;
    }

    public static ItemEnchantments miningEnchantments(ItemEnchantments enchantments) {
        Context context = CURRENT.get();
        if (context == null || !context.mining()) {
            return enchantments;
        }
        ItemEnchantments.Mutable filtered = null;
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            int level = enchantments.getLevel(enchantment);
            if (!StageGateEnforcer.allowUseEnchantment(context.actor(), enchantment, level)) {
                if (filtered == null) {
                    filtered = new ItemEnchantments.Mutable(enchantments);
                }
                filtered.set(enchantment, 0);
            }
        }
        return filtered != null ? filtered.toImmutable() : enchantments;
    }

    public static void withSlotVisitor(Object visitor, boolean stopBlockedEffects, Runnable effect) {
        SlotVisit previous = SLOT_VISIT.get();
        SLOT_VISIT.set(new SlotVisit(visitor, stopBlockedEffects));
        try {
            effect.run();
        } finally {
            if (previous == null) {
                SLOT_VISIT.remove();
            } else {
                SLOT_VISIT.set(previous);
            }
        }
    }

    public static @Nullable Boolean stopBlockedEffectsFor(Object visitor) {
        SlotVisit current = SLOT_VISIT.get();
        return current != null && current.visitor() == visitor ? current.stopBlockedEffects() : null;
    }

    private record SlotVisit(Object visitor, boolean stopBlockedEffects) {
    }

    private record Context(Object stack, Player actor, boolean mining) {
    }
}
