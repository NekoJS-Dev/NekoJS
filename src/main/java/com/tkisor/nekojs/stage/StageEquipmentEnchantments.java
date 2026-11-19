package com.tkisor.nekojs.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class StageEquipmentEnchantments {
    private static final ThreadLocal<Capture> CURRENT = new ThreadLocal<>();

    private final EnumMap<EquipmentSlot, List<EquippedModifier>> equipped = new EnumMap<>(EquipmentSlot.class);
    private final EnumSet<EquipmentSlot> collected = EnumSet.noneOf(EquipmentSlot.class);

    public void collect(LivingEntity wearer, Player actor, EquipmentSlot slot,
            BiConsumer<Holder<Attribute>, AttributeModifier> consumer,
            Consumer<BiConsumer<Holder<Attribute>, AttributeModifier>> apply) {
        Capture previous = CURRENT.get();
        Capture capture = new Capture(actor, consumer);
        CURRENT.set(capture);
        try {
            apply.accept(capture);
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
        collected.add(slot);
        discard(wearer, slot);
        if (capture.modifiers != null) {
            equipped.put(slot, capture.modifiers);
        }
    }

    public static void withModifierVisitor(BiConsumer<Holder<Attribute>, AttributeModifier> consumer, Object visitor,
            Runnable visit) {
        Capture capture = CURRENT.get();
        if (capture == null || consumer != capture) {
            visit.run();
            return;
        }
        Object previous = capture.visitor;
        capture.visitor = visitor;
        try {
            visit.run();
        } finally {
            capture.visitor = previous;
        }
    }

    public static boolean visitModifier(Object visitor, Holder<Enchantment> enchantment, int level, Runnable visit) {
        Capture capture = CURRENT.get();
        if (capture == null || visitor != capture.visitor) {
            return false;
        }
        Source previous = capture.source;
        boolean eligible = StageGateEnforcer.allowUseEnchantment(capture.actor, enchantment, level);
        NekoId id = enchantment.unwrapKey().map(MinecraftStageRegistryAccess::fromResourceKey).orElse(null);
        capture.source = id == null ? null : new Source(id, level, eligible);
        try {
            visit.run();
        } finally {
            capture.source = previous;
        }
        return true;
    }

    private void discard(LivingEntity wearer, EquipmentSlot slot) {
        List<EquippedModifier> previous = equipped.remove(slot);
        if (previous != null) {
            for (EquippedModifier modifier : previous) {
                modifier.remove(wearer);
            }
        }
    }

    public void refresh(LivingEntity wearer, Iterable<EquipmentSlot> changedSlots) {
        for (EquipmentSlot slot : changedSlots) {
            if (!collected.contains(slot)) {
                discard(wearer, slot);
            }
        }
        collected.clear();
        if (equipped.isEmpty()) {
            return;
        }
        Player actor = StageDamageActors.fromEntity(wearer);
        StageGateEnforcer.StageViews views = StageGateEnforcer.viewsFor(actor);
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        List<EquippedModifier> newlyEligible = null;
        for (List<EquippedModifier> modifiers : equipped.values()) {
            for (EquippedModifier modifier : modifiers) {
                boolean eligible = actor == null || snapshot.canUseEnchantment(
                        modifier.enchantment, modifier.level, views.player(), views.server());
                if (modifier.eligible == eligible) {
                    continue;
                }
                modifier.eligible = eligible;
                if (!eligible) {
                    modifier.remove(wearer);
                } else {
                    if (newlyEligible == null) {
                        newlyEligible = new ArrayList<>();
                    }
                    newlyEligible.add(modifier);
                }
            }
        }
        if (newlyEligible != null) {
            for (EquippedModifier modifier : newlyEligible) {
                modifier.apply(wearer);
            }
        }
    }

    private static final class Capture implements BiConsumer<Holder<Attribute>, AttributeModifier> {
        private final Player actor;
        private final BiConsumer<Holder<Attribute>, AttributeModifier> consumer;
        private @Nullable Object visitor;
        private @Nullable Source source;
        private @Nullable List<EquippedModifier> modifiers;

        private Capture(Player actor, BiConsumer<Holder<Attribute>, AttributeModifier> consumer) {
            this.actor = actor;
            this.consumer = consumer;
        }

        @Override
        public void accept(Holder<Attribute> attribute, AttributeModifier modifier) {
            Source current = source;
            if (current == null || current.eligible()) {
                consumer.accept(attribute, modifier);
            }
            if (current != null) {
                if (modifiers == null) {
                    modifiers = new ArrayList<>();
                }
                modifiers.add(new EquippedModifier(current, attribute, modifier));
            }
        }
    }

    private record Source(NekoId enchantment, int level, boolean eligible) {
    }

    private static final class EquippedModifier {
        private final NekoId enchantment;
        private final int level;
        private final Holder<Attribute> attribute;
        private final AttributeModifier modifier;
        private boolean eligible;

        private EquippedModifier(Source source, Holder<Attribute> attribute, AttributeModifier modifier) {
            this.enchantment = source.enchantment();
            this.level = source.level();
            this.attribute = attribute;
            this.modifier = modifier;
            this.eligible = source.eligible();
        }

        private void apply(LivingEntity wearer) {
            AttributeInstance instance = wearer.getAttributes().getInstance(attribute);
            if (instance != null && instance.getModifier(modifier.id()) == null) {
                instance.addTransientModifier(modifier);
            }
        }

        private void remove(LivingEntity wearer) {
            AttributeInstance instance = wearer.getAttributes().getInstance(attribute);
            if (instance != null && instance.getModifier(modifier.id()) == modifier) {
                instance.removeModifier(modifier);
            }
        }
    }
}
