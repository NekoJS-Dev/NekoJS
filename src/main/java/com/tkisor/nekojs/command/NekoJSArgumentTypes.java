//? if neoforge {
package com.tkisor.nekojs.command;

import com.tkisor.nekojs.NekoJS;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Serializable registration for NekoJS custom brigadier argument types, shared by all NeoForge
 * nodes. A custom argument used in a server command tree must satisfy two lookups before the
 * tree can reach a real client: {@code ClientboundCommandsPacket} resolves the info via
 * {@code ArgumentTypeInfos.byClass} (an unmapped class throws "Unrecognized argument type" and
 * blocks world entry during the configuration phase) and then writes the info's numeric id from
 * {@code BuiltInRegistries.COMMAND_ARGUMENT_TYPE}, which the client resolves against its own
 * registry. Both sides therefore need the same registered info, and the class map must hold that
 * same instance: the DeferredRegister supplier routes through
 * {@link ArgumentTypeInfos#registerByClass} so one instance lands in both places. Console/RCON
 * dispatch never serializes the tree, which is why the miss only surfaces on client sync.
 */
public final class NekoJSArgumentTypes {

    private NekoJSArgumentTypes() {}

    /**
     * Maps {@link AddressArgument} to its wire info. Also the register supplier of
     * {@code nekojs:address}: running both through one call keeps the registry entry and the
     * class-map entry identical. Package-visible for the serialization JVM test.
     */
    static SingletonArgumentInfo<AddressArgument> addressArgumentInfo() {
        return ArgumentTypeInfos.registerByClass(AddressArgument.class,
                SingletonArgumentInfo.contextFree(AddressArgument::address));
    }

    /** Mod-construction wiring: registers {@code nekojs:address} in {@code minecraft:command_argument_type}. */
    public static void register(IEventBus modEventBus) {
        DeferredRegister<ArgumentTypeInfo<?, ?>> commandArgumentTypes =
                DeferredRegister.create(BuiltInRegistries.COMMAND_ARGUMENT_TYPE, NekoJS.MODID);
        commandArgumentTypes.register("address", NekoJSArgumentTypes::addressArgumentInfo);
        commandArgumentTypes.register(modEventBus);
    }
}
//?}
