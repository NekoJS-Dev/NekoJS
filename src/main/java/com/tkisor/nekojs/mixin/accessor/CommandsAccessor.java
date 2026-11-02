package com.tkisor.nekojs.mixin.accessor;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.ExecutionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Detects nested dispatch without appending authoring work to an existing command queue. */
@Mixin(Commands.class)
public interface CommandsAccessor {
    @Accessor("CURRENT_EXECUTION_CONTEXT")
    static ThreadLocal<ExecutionContext<CommandSourceStack>> nekojs$currentExecutionContext() {
        throw new AssertionError("NEKO-AUTHORING-MIXIN: Command context accessor was not transformed.");
    }
}
