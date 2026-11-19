package com.tkisor.nekojs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.nekojs.fabric.event.FabricEntityEventBindingsV2;
import net.minecraft.server.commands.RaidCommand;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.animal.camel.CamelHusk;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.equine.SkeletonTrapGoal;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.nautilus.ZombieNautilus;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.Parched;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric hook for {@code EntityEvents.finalizeSpawn}: the TAIL of {@code Mob#finalizeSpawn}.
 *
 * <p>Checked with javap (minecraft-merged-deobf-26.1.2.jar):
 * <ul>
 *   <li>{@code public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
 *       net.minecraft.world.level.ServerLevelAccessor, net.minecraft.world.DifficultyInstance,
 *       net.minecraft.world.entity.EntitySpawnReason, net.minecraft.world.entity.SpawnGroupData)}
 *       has the 26.x signature with <b>no</b> 1.21.x {@code CompoundTag} parameter
 *       ({@code SpawnGroupData} is passed in and returned unchanged). It is a public concrete
 *       method on the abstract Mob class, so TAIL is valid.</li>
 *   <li>Coverage: both {@code NaturalSpawner} call sites (bytecode 441 for natural spawning
 *       NATURAL, 502 for CHUNK_GENERATION), plus spawners and event spawning, use the same entry.
 *       A subclass that overrides {@code finalizeSpawn} without calling {@code super} does not
 *       trigger this hook.</li>
 *   <li><b>Why it cannot cancel or change the result</b>: neither {@code NaturalSpawner} call site
 *       <b>checks the return value</b> (441 goes straight on to
 *       {@code addFreshEntityWithPassengers}, and 502 does the same), so even a RETURN injection
 *       that returned null would not prevent the spawn. Fabric has no mechanism like the NeoForge
 *       patch of the spawn chain, so this bus only notifies.</li>
 * </ul>
 */
public final class MixinMobFinalizeSpawn {

    private static final String FINALIZE_SPAWN = "finalizeSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;"
            + "Lnet/minecraft/world/DifficultyInstance;Lnet/minecraft/world/entity/EntitySpawnReason;"
            + "Lnet/minecraft/world/entity/SpawnGroupData;)Lnet/minecraft/world/entity/SpawnGroupData;";

    private MixinMobFinalizeSpawn() {}

    @Mixin(Mob.class)
    public static abstract class MobFallback {
        @Inject(method = FINALIZE_SPAWN, at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
        private void nekojs$onFinalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                            EntitySpawnReason reason, SpawnGroupData data,
                                            CallbackInfoReturnable<SpawnGroupData> ci) {
            if (FabricEntityEventBindingsV2.postFallbackFinalizeSpawn((Mob) (Object) this, level, difficulty, reason, data)) {
                ci.setReturnValue(null);
            }
        }
    }

    @Mixin(BaseSpawner.class)
    public static abstract class BaseSpawnerCalls {
        @WrapOperation(method = "serverTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(CatSpawner.class)
    public static abstract class CatSpawnerCalls {
        @WrapOperation(method = "spawnCat(Lnet/minecraft/core/BlockPos;Lnet/minecraft/server/level/ServerLevel;Z)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/feline/Cat;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Cat mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(Drowned.class)
    public static abstract class DrownedCalls {
        @WrapOperation(method = FINALIZE_SPAWN,
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/nautilus/ZombieNautilus;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                ZombieNautilus mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(EntityType.class)
    public static abstract class EntityTypeCalls {
        @WrapOperation(method = {
                "create(Lnet/minecraft/server/level/ServerLevel;Ljava/util/function/Consumer;"
                        + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/EntitySpawnReason;"
                        + "ZZ)Lnet/minecraft/world/entity/Entity;",
                "create(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/PostSpawnProcessor;"
                        + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/EntitySpawnReason;"
                        + "ZZ)Lnet/minecraft/world/entity/Entity;"
        },
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(targets = "net.minecraft.world.entity.monster.illager.Evoker$EvokerSummonSpellGoal")
    public static abstract class EvokerSummonSpellGoalCalls {
        @WrapOperation(method = "performSpellCasting()V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Vex;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Vex mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(Husk.class)
    public static abstract class HuskCalls {
        @WrapOperation(method = FINALIZE_SPAWN,
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/camel/CamelHusk;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeCamelHusk(
                CamelHusk mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }

        @WrapOperation(method = FINALIZE_SPAWN,
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/skeleton/Parched;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeParched(
                Parched mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(NaturalSpawner.class)
    public static abstract class NaturalSpawnerCalls {
        @WrapOperation(method = "spawnCategoryForPosition(Lnet/minecraft/world/entity/MobCategory;"
                        + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;"
                        + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/NaturalSpawner$SpawnPredicate;"
                        + "Lnet/minecraft/world/level/NaturalSpawner$AfterSpawnCallback;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private static SpawnGroupData nekojs$spawnCategoryForPosition(
                Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }

        @WrapOperation(method = "spawnMobsForChunkGeneration(Lnet/minecraft/world/level/ServerLevelAccessor;"
                        + "Lnet/minecraft/core/Holder;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/util/RandomSource;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private static SpawnGroupData nekojs$spawnMobsForChunkGeneration(
                Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces$OceanMonumentPiece")
    public static abstract class OceanMonumentPieceCalls {
        @WrapOperation(method = "spawnElder(Lnet/minecraft/world/level/WorldGenLevel;"
                        + "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;III)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/ElderGuardian;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                ElderGuardian mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.OceanRuinPieces$OceanRuinPiece")
    public static abstract class OceanRuinPieceCalls {
        @WrapOperation(method = "handleDataMarker(Ljava/lang/String;Lnet/minecraft/core/BlockPos;"
                        + "Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/util/RandomSource;"
                        + "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/zombie/Drowned;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Drowned mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(PatrolSpawner.class)
    public static abstract class PatrolSpawnerCalls {
        @WrapOperation(method = "spawnPatrolMember(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;"
                        + "Lnet/minecraft/util/RandomSource;Z)Z",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/PatrollingMonster;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                PatrollingMonster mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(PhantomSpawner.class)
    public static abstract class PhantomSpawnerCalls {
        @WrapOperation(method = "tick(Lnet/minecraft/server/level/ServerLevel;Z)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Phantom;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Phantom mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(Raid.class)
    public static abstract class RaidCalls {
        @WrapOperation(method = "joinRaid(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/entity/raid/Raider;"
                        + "Lnet/minecraft/core/BlockPos;Z)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raider;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Raider mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(RaidCommand.class)
    public static abstract class RaidCommandCalls {
        @WrapOperation(method = "spawnLeader(Lnet/minecraft/commands/CommandSourceStack;)I",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raider;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private static SpawnGroupData nekojs$finalizeSpawn(
                Raider mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(SkeletonTrapGoal.class)
    public static abstract class SkeletonTrapGoalCalls {
        @WrapOperation(method = "createHorse(Lnet/minecraft/world/DifficultyInstance;"
                        + ")Lnet/minecraft/world/entity/animal/equine/AbstractHorse;",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/equine/SkeletonHorse;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$createHorse(
                SkeletonHorse mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }

        @WrapOperation(method = "createSkeleton(Lnet/minecraft/world/DifficultyInstance;"
                        + "Lnet/minecraft/world/entity/animal/equine/AbstractHorse;"
                        + ")Lnet/minecraft/world/entity/monster/skeleton/Skeleton;",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/skeleton/Skeleton;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$createSkeleton(
                Skeleton mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(Spider.class)
    public static abstract class SpiderCalls {
        @WrapOperation(method = FINALIZE_SPAWN,
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/skeleton/Skeleton;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Skeleton mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(Strider.class)
    public static abstract class StriderCalls {
        @WrapOperation(method = "spawnJockey(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/DifficultyInstance;"
                        + "Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/SpawnGroupData;"
                        + ")Lnet/minecraft/world/entity/SpawnGroupData;",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(StructureTemplate.class)
    public static abstract class StructureTemplateCalls {
        @WrapOperation(method = "lambda$placeEntities$0(Lnet/minecraft/world/level/block/Rotation;"
                        + "Lnet/minecraft/world/level/block/Mirror;Lnet/minecraft/world/phys/Vec3;"
                        + "ZLnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/world/entity/Entity;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private static SpawnGroupData nekojs$finalizeSpawn(
                Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(SummonCommand.class)
    public static abstract class SummonCommandCalls {
        @WrapOperation(method = "createEntity(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/core/Holder$Reference;"
                        + "Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/nbt/CompoundTag;Z)Lnet/minecraft/world/entity/Entity;",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private static SpawnGroupData nekojs$finalizeSpawn(
                Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(SwampHutPiece.class)
    public static abstract class SwampHutPieceCalls {
        @WrapOperation(method = "postProcess(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/StructureManager;"
                        + "Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;"
                        + "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/ChunkPos;"
                        + "Lnet/minecraft/core/BlockPos;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Witch;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$postProcess(
                Witch mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }

        @WrapOperation(method = "spawnCat(Lnet/minecraft/world/level/ServerLevelAccessor;"
                        + "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/feline/Cat;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$spawnCat(
                Cat mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(Tadpole.class)
    public static abstract class TadpoleCalls {
        @WrapOperation(method = "lambda$ageUp$0(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/frog/Frog;"
                        + ")V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/frog/Frog;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Frog mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(TrialSpawner.class)
    public static abstract class TrialSpawnerCalls {
        @WrapOperation(method = "spawnMob(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Ljava/util/Optional;",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(Villager.class)
    public static abstract class VillagerCalls {
        @WrapOperation(method = "lambda$thunderHit$0(Lnet/minecraft/server/level/ServerLevel;"
                        + "Lnet/minecraft/world/entity/monster/Witch;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/Witch;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Witch mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(VillageSiege.class)
    public static abstract class VillageSiegeCalls {
        @WrapOperation(method = "trySpawn(Lnet/minecraft/server/level/ServerLevel;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/zombie/Zombie;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Zombie mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces$WoodlandMansionPiece")
    public static abstract class WoodlandMansionPieceCalls {
        @WrapOperation(method = "handleDataMarker(Ljava/lang/String;Lnet/minecraft/core/BlockPos;"
                        + "Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/util/RandomSource;"
                        + "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Mob;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Mob mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(Zombie.class)
    public static abstract class ZombieCalls {
        @WrapOperation(method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;"
                        + "F)Z",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/zombie/Zombie;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$hurtServer(
                Zombie mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }

        @WrapOperation(method = FINALIZE_SPAWN,
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/chicken/Chicken;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeChicken(
                Chicken mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }

        @WrapOperation(method = {
                "lambda$convertVillagerToZombieVillager$0(Lnet/minecraft/server/level/ServerLevel;"
                        + "Lnet/minecraft/world/entity/npc/villager/Villager;"
                        + "Lnet/minecraft/world/entity/monster/zombie/ZombieVillager;)V",
                "lambda$convertVillagerToZombieVillager$0(Lnet/minecraft/world/entity/npc/villager/Villager;"
                        + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/zombie/ZombieVillager;)V"
        },
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/zombie/ZombieVillager;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeZombieVillager(
                ZombieVillager mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(ZombieHorse.class)
    public static abstract class ZombieHorseCalls {
        @WrapOperation(method = FINALIZE_SPAWN,
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/monster/zombie/Zombie;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Zombie mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }

    @Mixin(ZombieVillager.class)
    public static abstract class ZombieVillagerCalls {
        @WrapOperation(method = "lambda$finishConversion$0(Lnet/minecraft/server/level/ServerLevel;"
                        + "Lnet/minecraft/world/entity/npc/villager/Villager;)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/villager/Villager;" + FINALIZE_SPAWN),
                require = 1, allow = 1)
        private SpawnGroupData nekojs$finalizeSpawn(
                Villager mob, ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
                SpawnGroupData data, Operation<SpawnGroupData> original) {
            return FabricEntityEventBindingsV2.wrapFinalizeSpawn(mob, level, difficulty, reason, data, original);
        }
    }
}
