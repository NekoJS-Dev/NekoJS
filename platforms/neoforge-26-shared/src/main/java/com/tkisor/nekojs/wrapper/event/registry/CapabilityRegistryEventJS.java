package com.tkisor.nekojs.wrapper.event.registry;

import com.tkisor.nekojs.js.type_adapter.ParseIds;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * {@code CapabilityEvents.register} 事件对象：为方块实体注册标准 capability provider。
 *
 * <p>MVP 仅支持 block entity + 标准 item / energy / fluid 三类能力。provider 为无上下文
 * 回调（返回 {@code Capabilities.itemHandler(...)} 等 transfer API 实例）；挂到
 * NeoForge {@code RegisterCapabilitiesEvent}（mod bus）时生效。
 */
public class CapabilityRegistryEventJS {
    private final List<PendingRegistration> pending = new ArrayList<>();

    /**
     * 为方块实体类型注册 capability。
     *
     * @param blockEntityTypeId 方块实体类型 id（如 {@code 'mymod:storage'}）
     * @param capability        {@code 'item'} / {@code 'energy'} / {@code 'fluid'}
     * @param provider          返回能力实例的回调（如 {@code () => Capabilities.itemHandler(6)}）
     */
    public void registerBlockEntity(String blockEntityTypeId, String capability, Supplier<Object> provider) {
        Identifier location = ParseIds.parseItemOrBlockId(blockEntityTypeId);
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(location)
                .orElseThrow(() -> new IllegalArgumentException("未知方块实体类型: " + blockEntityTypeId));
        pending.add(new PendingRegistration(type, capability.toLowerCase(Locale.ROOT), provider));
    }

    /** NeoForge 回调：把 pending 注册应用到 {@code RegisterCapabilitiesEvent}。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void apply(RegisterCapabilitiesEvent event) {
        for (PendingRegistration registration : pending) {
            switch (registration.capability) {
                case "item" -> event.registerBlockEntity(
                        (BlockCapability) Capabilities.Item.BLOCK,
                        (BlockEntityType) registration.type,
                        (blockEntity, direction) -> (ResourceHandler<ItemResource>) registration.provider.get());
                case "energy" -> event.registerBlockEntity(
                        (BlockCapability) Capabilities.Energy.BLOCK,
                        (BlockEntityType) registration.type,
                        (blockEntity, direction) -> (EnergyHandler) registration.provider.get());
                case "fluid" -> event.registerBlockEntity(
                        (BlockCapability) Capabilities.Fluid.BLOCK,
                        (BlockEntityType) registration.type,
                        (blockEntity, direction) -> (ResourceHandler<FluidResource>) registration.provider.get());
                default -> throw new IllegalArgumentException("未知 capability（支持 item/energy/fluid）: " + registration.capability);
            }
        }
    }

    private record PendingRegistration(BlockEntityType<?> type, String capability, Supplier<Object> provider) {
    }
}
