package com.tkisor.nekojs.wrapper.event.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.tkisor.nekojs.wrapper.command.CommandContextJS;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.function.Consumer;

public class CommandRegisterEventJS {
    private final RegisterCommandsEvent rawEvent;
    private final CommandDispatcher<CommandSourceStack> dispatcher;

    public CommandRegisterEventJS(RegisterCommandsEvent rawEvent) {
        this.rawEvent = rawEvent;
        this.dispatcher = rawEvent.getDispatcher();
    }

    public void register(String commandName, Consumer<CommandContextJS> executor) {
        dispatcher.register(
                Commands.literal(commandName)
                        .executes(context -> {
                            executor.accept(new CommandContextJS(context));
                            return 1;
                        })
        );
    }

    public void registerBuilder(String commandName, Consumer<LiteralArgumentBuilder<CommandSourceStack>> builderCallback) {
        LiteralArgumentBuilder<CommandSourceStack> rootNode = Commands.literal(commandName);
        builderCallback.accept(rootNode);
        dispatcher.register(rootNode);
    }

    public LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return Commands.literal(name);
    }

    public <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(String name, ArgumentType<T> type) {
        return Commands.argument(name, type);
    }

    public CommandContextJS wrap(CommandContext<CommandSourceStack> context) {
        return new CommandContextJS(context);
    }

    public ArgumentType<Integer> intType() {
        return IntegerArgumentType.integer();
    }

    public ArgumentType<Integer> intType(int min) {
        return IntegerArgumentType.integer(min);
    }

    public ArgumentType<Integer> intType(int min, int max) {
        return IntegerArgumentType.integer(min, max);
    }

    public ArgumentType<Double> doubleType() {
        return DoubleArgumentType.doubleArg();
    }

    public ArgumentType<Double> doubleType(double min, double max) {
        return DoubleArgumentType.doubleArg(min, max);
    }

    public ArgumentType<Float> floatType() {
        return FloatArgumentType.floatArg();
    }

    public ArgumentType<Float> floatType(float min, float max) {
        return FloatArgumentType.floatArg(min, max);
    }

    public ArgumentType<Boolean> boolType() {
        return BoolArgumentType.bool();
    }

    public ArgumentType<String> wordType() {
        return StringArgumentType.word();
    }

    public ArgumentType<String> stringType() {
        return StringArgumentType.string();
    }

    public ArgumentType<String> greedyStringType() {
        return StringArgumentType.greedyString();
    }
}