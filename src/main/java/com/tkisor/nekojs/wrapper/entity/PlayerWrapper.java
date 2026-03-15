package com.tkisor.nekojs.wrapper.entity;

import com.tkisor.nekojs.wrapper.item.ItemStackWrapper;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class PlayerWrapper {
    private final Player rawPlayer;

    public PlayerWrapper(Player rawPlayer) {
        this.rawPlayer = rawPlayer;
    }

    public String getName() {
        return rawPlayer.getName().getString();
    }

    public String getUuid() {
        return rawPlayer.getUUID().toString();
    }

    public double getX() { return rawPlayer.getX(); }
    public double getY() { return rawPlayer.getY(); }
    public double getZ() { return rawPlayer.getZ(); }

    public Vec3 getPos() {
        return rawPlayer.position();
    }

    public String getDimension() {
        return rawPlayer.level().dimension().registry().toString();
    }

    public void teleport(double x, double y, double z) {
        if (rawPlayer instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(x, y, z);
        } else {
            rawPlayer.setPos(x, y, z);
        }
    }

    public void teleport(Vec3 pos) {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;
        if (rawPlayer instanceof ServerPlayer serverPlayer) {
            serverPlayer.teleportTo(x, y, z);
        } else {
            rawPlayer.setPos(x, y, z);
        }
    }

    public boolean isFlying() {
        return rawPlayer.getAbilities().flying;
    }

    public void setFlying(boolean flying) {
        rawPlayer.getAbilities().flying = flying;
        rawPlayer.onUpdateAbilities(); // 通知客户端同步
    }

    public void setOnFire(int seconds) {
        rawPlayer.igniteForSeconds(seconds);
    }

    public void tell(String message) {
        rawPlayer.sendSystemMessage(Component.literal(message));
    }

    public void tell(Component message) {
        rawPlayer.sendSystemMessage(message);
    }

    public void give(ItemStackWrapper itemStackWrapper) {
        if (itemStackWrapper == null || itemStackWrapper.isEmpty()) return;

        ItemStack stack = itemStackWrapper.getRaw();

        boolean success = rawPlayer.getInventory().add(stack);

//        if (!stack.isEmpty()) {
//            rawPlayer.drop(stack, false);
//        }
    }

    public float getHealth() {
        return rawPlayer.getHealth();
    }

    public void setHealth(float health) {
        rawPlayer.setHealth(health);
    }

    public float getMaxHealth() { return rawPlayer.getMaxHealth(); }

    public int getFoodLevel() {
        return rawPlayer.getFoodData().getFoodLevel();
    }

    public void setFoodLevel(int foodLevel) {
        rawPlayer.getFoodData().setFoodLevel(foodLevel);
    }

    public float getSaturation() { return rawPlayer.getFoodData().getSaturationLevel(); }

    public void setSaturation(float saturation) {
        rawPlayer.getFoodData().setSaturation(saturation);
    }

    public int getXpLevel() { return rawPlayer.experienceLevel; }

    public void setXpLevel(int level) { rawPlayer.experienceLevel = level; }

    public void addXpLevel(int level) { rawPlayer.giveExperienceLevels(level); }

    public void addXp(int points) { rawPlayer.giveExperiencePoints(points); }

    public float getXpProgress() { return rawPlayer.experienceProgress; }

    public void setAbsorption(float amount) { rawPlayer.setAbsorptionAmount(amount); }

    public float getAbsorption() { return rawPlayer.getAbsorptionAmount(); }

    public boolean isOp() {
        return Commands.LEVEL_GAMEMASTERS.check(rawPlayer.permissions());
    }

    public boolean isCreative() {
        return rawPlayer.isCreative();
    }

    public boolean isSpectator() { return rawPlayer.isSpectator(); }

    public boolean isSurvival() { return rawPlayer.gameMode().isSurvival(); }

    public ItemStackWrapper getMainHandItem() {
        return new ItemStackWrapper(rawPlayer.getMainHandItem());
    }

    public ItemStackWrapper getOffHandItem() {
        return new ItemStackWrapper(rawPlayer.getOffhandItem());
    }

    public Player getRaw() {
        return rawPlayer;
    }
}