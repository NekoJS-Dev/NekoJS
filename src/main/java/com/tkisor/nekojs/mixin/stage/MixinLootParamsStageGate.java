package com.tkisor.nekojs.mixin.stage;

import com.tkisor.nekojs.api.data.NekoId;
import com.tkisor.nekojs.stage.StageLootContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LootParams.class)
public abstract class MixinLootParamsStageGate implements StageLootContext {

    @Unique
    private @Nullable NekoId nekojs$lootTableId;

    @Unique
    private @Nullable Player nekojs$lootActor;

    @Override
    public void nekojs$setLootOrigin(NekoId lootTableId, @Nullable Player actor) {
        this.nekojs$lootTableId = lootTableId;
        this.nekojs$lootActor = actor;
    }

    @Override
    public @Nullable NekoId nekojs$lootTableId() {
        return nekojs$lootTableId;
    }

    @Override
    public @Nullable Player nekojs$lootActor() {
        return nekojs$lootActor;
    }
}
