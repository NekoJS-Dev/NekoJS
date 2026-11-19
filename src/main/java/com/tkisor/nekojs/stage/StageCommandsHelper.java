package com.tkisor.nekojs.stage;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.api.inject.PlayerExtension;
import com.tkisor.nekojs.api.stage.StageHandle;
import com.tkisor.nekojs.api.stage.StageNotifyOptions;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.BlockGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.ContainerGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.DimensionGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.EffectGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.EnchantGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.EntityGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.ItemGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.LootGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.RecipeGate;
import com.tkisor.nekojs.api.stage.gate.StageGateDeclaration.RegionGate;
import com.tkisor.nekojs.api.stage.gate.StageGateKind;
import com.tkisor.nekojs.core.stage.StageStore;
import com.tkisor.nekojs.core.stage.gate.CompiledStageGateSnapshot;
import com.tkisor.nekojs.core.stage.gate.InlineRecipeStageRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Shared Brigadier command builder for the {@code /nekojs stages} subcommand tree across
 * all supported versions and loaders.
 */
public final class StageCommandsHelper {

    private StageCommandsHelper() {
    }

    /** Builds the {@code stages} Brigadier command subtree for attachment under {@code /nekojs}. */
    public static LiteralArgumentBuilder<CommandSourceStack> buildStagesLiteral() {
        return literal("stages")
                .then(buildAddNode())
                .then(buildRemoveNode())
                .then(buildClearNode())
                .then(buildListNode())
                .then(buildTimerNode())
                .then(buildServerNode())
                .then(buildHandNode())
                .then(buildInspectNode());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAddNode() {
        return literal("add")
                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("targets", EntityArgument.players())
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("stage", StringArgumentType.string())
                                .suggests(StageCommandsHelper::suggestKnownStages)
                                .executes(ctx -> executePlayerAdd(
                                        ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "targets"),
                                        StringArgumentType.getString(ctx, "stage"),
                                        false
                                ))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("silent", BoolArgumentType.bool())
                                        .executes(ctx -> executePlayerAdd(
                                                ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                StringArgumentType.getString(ctx, "stage"),
                                                BoolArgumentType.getBool(ctx, "silent")
                                        )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemoveNode() {
        return literal("remove")
                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("targets", EntityArgument.players())
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("stage", StringArgumentType.string())
                                .suggests(StageCommandsHelper::suggestKnownStages)
                                .executes(ctx -> executePlayerRemove(
                                        ctx.getSource(),
                                        EntityArgument.getPlayers(ctx, "targets"),
                                        StringArgumentType.getString(ctx, "stage"),
                                        false
                                ))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("silent", BoolArgumentType.bool())
                                        .executes(ctx -> executePlayerRemove(
                                                ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                StringArgumentType.getString(ctx, "stage"),
                                                BoolArgumentType.getBool(ctx, "silent")
                                        )))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildClearNode() {
        return literal("clear")
                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("targets", EntityArgument.players())
                        .executes(ctx -> executePlayerClear(
                                ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "targets")
                        )));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildListNode() {
        return literal("list")
                .executes(ctx -> executePlayerList(ctx.getSource(), List.of(ctx.getSource().getPlayerOrException())))
                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("targets", EntityArgument.players())
                        .executes(ctx -> executePlayerList(
                                ctx.getSource(),
                                EntityArgument.getPlayers(ctx, "targets")
                        )));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTimerNode() {
        return literal("timer")
                .then(buildTimerOperation("set"))
                .then(buildTimerOperation("add"))
                .then(buildTimerOperation("reset"))
                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("targets", EntityArgument.players())
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("stage", StringArgumentType.string())
                                .suggests(StageCommandsHelper::suggestKnownStages)
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Long>argument("ticks", LongArgumentType.longArg(1L))
                                        .executes(ctx -> executePlayerTimer(
                                                ctx.getSource(),
                                                EntityArgument.getPlayers(ctx, "targets"),
                                                StringArgumentType.getString(ctx, "stage"),
                                                "set",
                                                LongArgumentType.getLong(ctx, "ticks"),
                                                false
                                        ))
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("silent", BoolArgumentType.bool())
                                                .executes(ctx -> executePlayerTimer(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayers(ctx, "targets"),
                                                        StringArgumentType.getString(ctx, "stage"),
                                                        "set",
                                                        LongArgumentType.getLong(ctx, "ticks"),
                                                        BoolArgumentType.getBool(ctx, "silent")
                                                ))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTimerOperation(String operation) {
        LiteralArgumentBuilder<CommandSourceStack> command = literal(operation);
        if (operation.equals("reset")) {
            command.then(literal("--server")
                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("serverStage", StringArgumentType.string())
                            .suggests(StageCommandsHelper::suggestKnownStages)
                            .executes(ctx -> executeServerTimer(
                                    ctx.getSource(), StringArgumentType.getString(ctx, "serverStage"), operation, 0L, false
                            ))));
            command.then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("targets", EntityArgument.players())
                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("stage", StringArgumentType.string())
                            .suggests(StageCommandsHelper::suggestKnownStages)
                            .executes(ctx -> executePlayerTimer(
                                    ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                    StringArgumentType.getString(ctx, "stage"), operation, 0L, false
                            ))));
            return command;
        }
        command.then(literal("--server")
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("serverStage", StringArgumentType.string())
                        .suggests(StageCommandsHelper::suggestKnownStages)
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Long>argument("ticks", LongArgumentType.longArg(1L))
                                .executes(ctx -> executeServerTimer(
                                        ctx.getSource(), StringArgumentType.getString(ctx, "serverStage"), operation,
                                        LongArgumentType.getLong(ctx, "ticks"), false
                                ))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("silent", BoolArgumentType.bool())
                                        .executes(ctx -> executeServerTimer(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "serverStage"), operation,
                                                LongArgumentType.getLong(ctx, "ticks"), BoolArgumentType.getBool(ctx, "silent")
                                        ))))));
        command.then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("targets", EntityArgument.players())
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("stage", StringArgumentType.string())
                        .suggests(StageCommandsHelper::suggestKnownStages)
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Long>argument("ticks", LongArgumentType.longArg(1L))
                                .executes(ctx -> executePlayerTimer(
                                        ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                        StringArgumentType.getString(ctx, "stage"), operation,
                                        LongArgumentType.getLong(ctx, "ticks"), false
                                ))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("silent", BoolArgumentType.bool())
                                        .executes(ctx -> executePlayerTimer(
                                                ctx.getSource(), EntityArgument.getPlayers(ctx, "targets"),
                                                StringArgumentType.getString(ctx, "stage"), operation,
                                                LongArgumentType.getLong(ctx, "ticks"), BoolArgumentType.getBool(ctx, "silent")
                                        ))))));
        return command;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildServerNode() {
        return literal("server")
                .then(literal("add")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("stage", StringArgumentType.string())
                                .suggests(StageCommandsHelper::suggestKnownStages)
                                .executes(ctx -> executeServerAdd(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "stage"),
                                        false
                                ))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("silent", BoolArgumentType.bool())
                                        .executes(ctx -> executeServerAdd(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "stage"),
                                                BoolArgumentType.getBool(ctx, "silent")
                                        )))))
                .then(literal("remove")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("stage", StringArgumentType.string())
                                .suggests(StageCommandsHelper::suggestKnownStages)
                                .executes(ctx -> executeServerRemove(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "stage"),
                                        false
                                ))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("silent", BoolArgumentType.bool())
                                        .executes(ctx -> executeServerRemove(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "stage"),
                                                BoolArgumentType.getBool(ctx, "silent")
                                        )))))
                .then(literal("clear")
                        .executes(ctx -> executeServerClear(ctx.getSource())))
                .then(literal("list")
                        .executes(ctx -> executeServerList(ctx.getSource())))
                .then(literal("timer")
                        .then(literal("set")
                                .then(buildServerTimerSetNode()))
                        .then(literal("add")
                                .then(buildServerTimerSetNode("add")))
                        .then(literal("reset")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("stage", StringArgumentType.string())
                                        .suggests(StageCommandsHelper::suggestKnownStages)
                                        .executes(ctx -> executeServerTimer(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "stage"), "reset", 0L, false
                                        ))))
                        .then(buildServerTimerSetNode()));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> buildServerTimerSetNode() {
        return buildServerTimerSetNode("set");
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> buildServerTimerSetNode(String operation) {
        return RequiredArgumentBuilder.<CommandSourceStack, String>argument("stage", StringArgumentType.string())
                .suggests(StageCommandsHelper::suggestKnownStages)
                .then(RequiredArgumentBuilder.<CommandSourceStack, Long>argument("ticks", LongArgumentType.longArg(1L))
                        .executes(ctx -> executeServerTimer(
                                ctx.getSource(), StringArgumentType.getString(ctx, "stage"), operation,
                                LongArgumentType.getLong(ctx, "ticks"), false
                        ))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("silent", BoolArgumentType.bool())
                                .executes(ctx -> executeServerTimer(
                                        ctx.getSource(), StringArgumentType.getString(ctx, "stage"), operation,
                                        LongArgumentType.getLong(ctx, "ticks"), BoolArgumentType.getBool(ctx, "silent")
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildHandNode() {
        return literal("hand")
                .executes(ctx -> executeHand(ctx.getSource()));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildInspectNode() {
        return literal("inspect")
                .executes(ctx -> executeInspectSummary(ctx.getSource()))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("ruleId", StringArgumentType.greedyString())
                        .suggests(StageCommandsHelper::suggestRuleIds)
                        .executes(ctx -> executeInspectRule(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "ruleId")
                        )));
    }

    // ---- Player stage executors ----

    private static int executePlayerAdd(
            CommandSourceStack source,
            Collection<ServerPlayer> targets,
            String rawStage,
            boolean silent
    ) {
        String stage;
        try {
            stage = StageStore.validateStageName(rawStage);
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
        StageNotifyOptions options = StageNotifyOptions.silent(silent);
        int changed = 0;
        for (ServerPlayer target : targets) {
            StageHandle handle = ((PlayerExtension) target).neko$getStages();
            if (handle.add(stage, options)) {
                changed++;
            }
        }
        final int updated = changed;
        source.sendSuccess(
                () -> Component.literal("Granted stage '" + stage + "' to " + updated + "/" + targets.size() + " player(s).")
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        return Math.max(1, updated);
    }

    private static int executePlayerRemove(
            CommandSourceStack source,
            Collection<ServerPlayer> targets,
            String rawStage,
            boolean silent
    ) {
        String stage;
        try {
            stage = StageStore.validateStageName(rawStage);
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
        StageNotifyOptions options = StageNotifyOptions.silent(silent);
        int changed = 0;
        for (ServerPlayer target : targets) {
            StageHandle handle = ((PlayerExtension) target).neko$getStages();
            if (handle.remove(stage, options)) {
                changed++;
            }
        }
        final int updated = changed;
        source.sendSuccess(
                () -> Component.literal("Removed stage '" + stage + "' from " + updated + "/" + targets.size() + " player(s).")
                        .withStyle(ChatFormatting.YELLOW),
                true
        );
        return Math.max(1, updated);
    }

    private static int executePlayerClear(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int changed = 0;
        for (ServerPlayer target : targets) {
            StageHandle handle = ((PlayerExtension) target).neko$getStages();
            if (handle.clear()) {
                changed++;
            }
        }
        final int updated = changed;
        source.sendSuccess(
                () -> Component.literal("Cleared stages for " + updated + "/" + targets.size() + " player(s).")
                        .withStyle(ChatFormatting.YELLOW),
                true
        );
        return Math.max(1, updated);
    }

    private static int executePlayerList(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int count = 0;
        for (ServerPlayer target : targets) {
            StageHandle handle = ((PlayerExtension) target).neko$getStages();
            List<String> stages = handle.getAll();
            if (stages.isEmpty()) {
                source.sendSystemMessage(
                        Component.literal("Player " + target.getScoreboardName() + " has no active stages.")
                                .withStyle(ChatFormatting.GRAY)
                );
                continue;
            }
            source.sendSystemMessage(
                    Component.literal("Stages for " + target.getScoreboardName() + " (" + stages.size() + "):")
                            .withStyle(ChatFormatting.GOLD)
            );
            for (String stage : stages) {
                source.sendSystemMessage(formatStageEntry(stage, handle));
            }
            count += stages.size();
        }
        return count;
    }

    private static int executePlayerTimer(
            CommandSourceStack source,
            Collection<ServerPlayer> targets,
            String rawStage,
            String operation,
            long ticks,
            boolean silent
    ) {
        String stage;
        try {
            stage = StageStore.validateStageName(rawStage);
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
        StageNotifyOptions options = StageNotifyOptions.silent(silent);
        int changed = 0;
        for (ServerPlayer target : targets) {
            StageHandle handle = ((PlayerExtension) target).neko$getStages();
            if (applyTimerOperation(handle, stage, operation, ticks, options)) {
                changed++;
            }
        }
        final int updated = changed;
        source.sendSuccess(
                () -> Component.literal("Applied timer " + operation + " for stage '" + stage + "' to "
                        + updated + "/" + targets.size() + " player(s).")
                        .withStyle(updated > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                true
        );
        return Math.max(1, updated);
    }

    // ---- Server stage executors ----

    private static int executeServerAdd(CommandSourceStack source, String rawStage, boolean silent) {
        String stage;
        try {
            stage = StageStore.validateStageName(rawStage);
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
        boolean changed = StageDomainOwner.activeStore().serverHandle().add(stage, StageNotifyOptions.silent(silent));
        source.sendSuccess(
                () -> Component.literal(changed
                        ? "Granted server stage '" + stage + "'."
                        : "Server already has stage '" + stage + "'.")
                        .withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                true
        );
        return 1;
    }

    private static int executeServerRemove(CommandSourceStack source, String rawStage, boolean silent) {
        String stage;
        try {
            stage = StageStore.validateStageName(rawStage);
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
        boolean changed = StageDomainOwner.activeStore().serverHandle().remove(stage, StageNotifyOptions.silent(silent));
        source.sendSuccess(
                () -> Component.literal(changed
                        ? "Removed server stage '" + stage + "'."
                        : "Server did not have stage '" + stage + "'.")
                        .withStyle(changed ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                true
        );
        return 1;
    }

    private static int executeServerClear(CommandSourceStack source) {
        boolean changed = StageDomainOwner.activeStore().serverHandle().clear();
        source.sendSuccess(
                () -> Component.literal(changed
                        ? "Cleared all server stages."
                        : "Server had no active stages to clear.")
                        .withStyle(changed ? ChatFormatting.YELLOW : ChatFormatting.GRAY),
                true
        );
        return 1;
    }

    private static int executeServerList(CommandSourceStack source) {
        StageHandle serverHandle = StageDomainOwner.activeStore().serverHandle();
        List<String> stages = serverHandle.getAll();
        if (stages.isEmpty()) {
            source.sendSystemMessage(Component.literal("Server has no active stages.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        source.sendSystemMessage(
                Component.literal("Active server stages (" + stages.size() + "):").withStyle(ChatFormatting.GOLD)
        );
        for (String stage : stages) {
            source.sendSystemMessage(formatStageEntry(stage, serverHandle));
        }
        return stages.size();
    }

    private static int executeServerTimer(
            CommandSourceStack source,
            String rawStage,
            String operation,
            long ticks,
            boolean silent
    ) {
        String stage;
        try {
            stage = StageStore.validateStageName(rawStage);
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }
        boolean changed = applyTimerOperation(
                StageDomainOwner.activeStore().serverHandle(), stage, operation, ticks,
                StageNotifyOptions.silent(silent)
        );
        source.sendSuccess(
                () -> Component.literal(changed
                        ? "Applied timer " + operation + " to server stage '" + stage + "'."
                        : "Server stage '" + stage + "' was unchanged.")
                        .withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.GRAY),
                true
        );
        return 1;
    }

    private static boolean applyTimerOperation(
            StageHandle handle,
            String stage,
            String operation,
            long ticks,
            StageNotifyOptions options
    ) {
        return switch (operation) {
            case "set" -> handle.grantTemporary(stage, ticks, options);
            case "add" -> {
                long remaining = handle.remainingTicks(stage);
                if (remaining < 0L || Long.MAX_VALUE - remaining < ticks) {
                    yield false;
                }
                yield handle.grantTemporary(stage, remaining + ticks, options);
            }
            case "reset" -> {
                com.tkisor.nekojs.api.stage.StageDefinition definition = StageDomainOwner.activeStore().definition(stage);
                if (definition == null || !definition.temporary() || definition.defaultTicks() <= 0L
                        || (handle.has(stage) && !handle.isTemporary(stage))) {
                    yield false;
                }
                yield handle.grantTemporary(stage, definition.defaultTicks(), options);
            }
            default -> false;
        };
    }

    // ---- Hand & gate inspection executors ----

    private static int executeHand(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("Main hand is empty."));
            return 0;
        }

        NekoId id = StageGateEnforcer.itemId(stack);
        if (id == null) {
            source.sendFailure(Component.literal("Unable to resolve item identifier for held stack."));
            return 0;
        }

        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageGateEnforcer.StageViews views = StageGateEnforcer.viewsFor(player);
        List<ItemGate> rules = snapshot.matchingItemRules(id);
        List<String> requiredStages = snapshot.requiredStagesForItem(id);
        ItemGate winningLocked = snapshot.lockedItemRule(id, views.player(), views.server());
        boolean unlocked = winningLocked == null;

        source.sendSystemMessage(
                Component.literal("Held item: ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(id.toString()).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(unlocked ? " [UNLOCKED]" : " [LOCKED]")
                                .withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.RED))
        );

        if (!requiredStages.isEmpty()) {
            source.sendSystemMessage(
                    Component.literal("  Required stage(s): ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(String.join(", ", requiredStages)).withStyle(ChatFormatting.WHITE))
            );
        }

        if (rules.isEmpty()) {
            source.sendSystemMessage(
                    Component.literal("  No matching ItemGate declarations.").withStyle(ChatFormatting.GRAY)
            );
            return 1;
        }

        source.sendSystemMessage(
                Component.literal("  Matching ItemGate rules (" + rules.size() + "):").withStyle(ChatFormatting.YELLOW)
        );
        for (ItemGate rule : rules) {
            boolean ruleUnlocked = rule.scope().evaluate(
                    views.player().has(rule.stage()),
                    views.server().has(rule.stage())
            );
            MutableComponent line = Component.literal("  - " + rule.id())
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" [priority=" + rule.priority()
                            + ", stage=" + rule.stage()
                            + ", scope=" + rule.scope().id() + "] ")
                            .withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(ruleUnlocked ? "UNLOCKED" : "LOCKED")
                            .withStyle(ruleUnlocked ? ChatFormatting.GREEN : ChatFormatting.RED));
            source.sendSystemMessage(line);
        }
        return rules.size();
    }

    private static int executeInspectSummary(CommandSourceStack source) {
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageStore store = StageDomainOwner.activeStore();
        List<StageGateDeclaration> all = snapshot.allDeclarations();

        Map<StageGateKind, Integer> counts = new EnumMap<>(StageGateKind.class);
        for (StageGateDeclaration decl : all) {
            counts.merge(decl.kind(), 1, Integer::sum);
        }

        source.sendSystemMessage(
                Component.literal("Active Stage Gate Snapshot Summary:")
                        .withStyle(ChatFormatting.GOLD)
        );
        source.sendSystemMessage(
                Component.literal("  Customized stages: " + store.definitions().size()
                        + " | Declarative gates: " + all.size()
                        + " | Inline recipes: " + snapshot.inlineRecipes().size()
                        + " | Whitelisted containers: " + snapshot.whitelistedContainers().size())
                        .withStyle(ChatFormatting.WHITE)
        );
        for (StageGateKind kind : StageGateKind.values()) {
            int count = counts.getOrDefault(kind, 0);
            if (count > 0) {
                source.sendSystemMessage(
                        Component.literal("  - " + kind.name() + ": " + count).withStyle(ChatFormatting.AQUA)
                );
            }
        }
        return all.size() + snapshot.inlineRecipes().size();
    }

    private static int executeInspectRule(CommandSourceStack source, String rawRuleId) {
        NekoId ruleId;
        try {
            ruleId = NekoId.of(rawRuleId.trim());
        } catch (IllegalArgumentException invalid) {
            source.sendFailure(Component.literal("Invalid rule identifier '" + rawRuleId + "': " + invalid.getMessage()));
            return 0;
        }

        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        StageGateDeclaration decl = snapshot.findDeclaration(ruleId);
        if (decl == null) {
            InlineRecipeStageRegistry.InlineEntry inline = snapshot.inlineRecipes().get(ruleId);
            if (inline != null) {
                source.sendSystemMessage(
                        Component.literal("Inline Recipe Gate: " + inline.recipeId())
                                .withStyle(ChatFormatting.GOLD)
                );
                source.sendSystemMessage(
                        Component.literal("  stage=" + inline.stage() + ", scope=" + inline.scope().id() + ", priority=0")
                                .withStyle(ChatFormatting.WHITE)
                );
                return 1;
            }
            source.sendFailure(Component.literal("No stage gate declaration found for id: " + ruleId));
            return 0;
        }

        source.sendSystemMessage(
                Component.literal("Stage Gate [" + decl.kind() + "] " + decl.id())
                        .withStyle(ChatFormatting.GOLD)
        );
        source.sendSystemMessage(
                Component.literal("  stage=" + decl.stage()
                        + ", scope=" + decl.scope().id()
                        + ", priority=" + decl.priority()
                        + (decl.lockedMessage().isEmpty() ? "" : ", lockedMessage=\"" + decl.lockedMessage() + "\""))
                        .withStyle(ChatFormatting.WHITE)
        );
        source.sendSystemMessage(
                Component.literal("  " + describeDeclarationTargets(decl)).withStyle(ChatFormatting.GRAY)
        );
        return 1;
    }

    private static String describeDeclarationTargets(StageGateDeclaration decl) {
        return switch (decl) {
            case ItemGate g -> "items=" + g.items()
                    + ", tags=" + g.tags()
                    + ", namespaces=" + g.namespaces()
                    + ", allowInventory=" + g.allowInventoryStorage()
                    + ", allowContainer=" + g.allowContainerStorage()
                    + ", allowEquip=" + g.allowEquip()
                    + ", allowUse=" + g.allowUse()
                    + ", allowAttack=" + g.allowAttack()
                    + ", showInViewer=" + g.showInViewer()
                    + (g.lockedName().isEmpty() ? "" : ", lockedName=\"" + g.lockedName() + "\"");
            case BlockGate g -> "blocks=" + g.blocks()
                    + ", tags=" + g.tags()
                    + ", namespaces=" + g.namespaces()
                    + ", allowBreak=" + g.allowBreak()
                    + ", allowPlace=" + g.allowPlace()
                    + ", allowInteract=" + g.allowInteract()
                    + ", allowToolModify=" + g.allowToolModify()
                    + (g.minAge() == null ? "" : ", minAge=" + g.minAge());
            case RecipeGate g -> "recipeIds=" + g.recipeIds()
                    + ", outputItems=" + g.outputItems()
                    + ", outputTags=" + g.outputTags()
                    + ", recipeTypes=" + g.recipeTypes()
                    + ", namespaces=" + g.namespaces()
                    + ", showInViewer=" + g.showInViewer();
            case ContainerGate g -> "menus=" + g.menus();
            case DimensionGate g -> "dimensions=" + g.dimensions();
            case RegionGate g -> "shapes=" + g.shapes().size()
                    + ", allowEnter=" + g.allowEnter()
                    + ", allowBreak=" + g.allowBreak()
                    + ", allowPlace=" + g.allowPlace()
                    + ", allowInteract=" + g.allowInteract();
            case EntityGate g -> "entityTypes=" + g.entityTypes()
                    + ", tags=" + g.tags()
                    + ", namespaces=" + g.namespaces()
                    + ", allowAttack=" + g.allowAttack()
                    + ", allowInteract=" + g.allowInteract()
                    + ", allowMount=" + g.allowMount();
            case EffectGate g -> "effects=" + g.effects();
            case EnchantGate g -> "enchants=" + g.enchants() + ", minLevel=" + g.minLevel();
            case LootGate g -> "items=" + g.items()
                    + ", tags=" + g.tags()
                    + ", fromLootTables=" + g.fromLootTables()
                    + ", fromEntities=" + g.fromEntities()
                    + (g.replacementItem() == null ? "" : ", replacementItem=" + g.replacementItem());
        };
    }

    private static Component formatStageEntry(String stage, StageHandle handle) {
        long remaining = handle.remainingTicks(stage);
        MutableComponent line = Component.literal("  - " + stage).withStyle(ChatFormatting.GREEN);
        if (remaining > 0L) {
            line.append(Component.literal(" (temporary: " + remaining + " ticks)").withStyle(ChatFormatting.AQUA));
        }
        return line;
    }

    private static CompletableFuture<Suggestions> suggestKnownStages(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        Set<String> known = new LinkedHashSet<>();
        StageStore store = StageDomainOwner.activeStore();
        known.addAll(store.definitions().keySet());
        known.addAll(store.serverHandle().getAll());
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        for (StageGateDeclaration decl : snapshot.allDeclarations()) {
            known.add(decl.stage());
        }
        for (InlineRecipeStageRegistry.InlineEntry inline : snapshot.inlineRecipes().values()) {
            known.add(inline.stage());
        }
        return SharedSuggestionProvider.suggest(known, builder);
    }

    private static CompletableFuture<Suggestions> suggestRuleIds(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        CompiledStageGateSnapshot snapshot = StageDomainOwner.activeSnapshot();
        Set<String> ids = new LinkedHashSet<>();
        for (StageGateDeclaration decl : snapshot.allDeclarations()) {
            ids.add(decl.id().toString());
        }
        for (NekoId inlineId : snapshot.inlineRecipes().keySet()) {
            ids.add(inlineId.toString());
        }
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }
}
