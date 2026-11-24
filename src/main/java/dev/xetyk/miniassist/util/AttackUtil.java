package dev.xetyk.miniassist.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;

public final class AttackUtil {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private AttackUtil() {}

    /** Returns true only when an attack was actually issued against an entity in the current world. */
    public static boolean attackEntity(Entity target) {
        return attackEntity(target, false);
    }

    /**
     * @param releaseSprint vanilla never gives a critical hit while sprinting, so when a crit is wanted
     *                      the player stops sprinting (same STOP_SPRINTING packet a released key sends) first.
     */
    public static boolean attackEntity(Entity target, boolean releaseSprint) {
        ClientPlayerEntity player = MC.player;
        if (player == null || MC.world == null || MC.interactionManager == null || target == null) return false;
        if (target.isRemoved() || MC.world.getEntityById(target.getId()) != target) return false;
        if (releaseSprint && player.isSprinting()) {
            player.setSprinting(false);
            if (MC.getNetworkHandler() != null) {
                MC.getNetworkHandler().sendPacket(
                        new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }
        }
        MC.interactionManager.attackEntity(player, target);
        player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    /** Falling-state part of the vanilla critical-hit conditions, without the sprint check. */
    public static boolean isCritFallState() {
        ClientPlayerEntity player = MC.player;
        if (player == null) return false;
        return player.isAlive()
                && player.fallDistance > 0.0f
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.isSubmergedInWater()
                && !player.isInLava()
                && !player.isGliding()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !player.hasVehicle();
    }

    /** Mirrors the ordinary vanilla critical-hit conditions (including the sprint rule). */
    public static boolean isPlayerInCriticalState() {
        ClientPlayerEntity player = MC.player;
        if (player == null) return false;
        return player.isAlive()
                && player.fallDistance > 0.0f
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.isSubmergedInWater()
                && !player.isInLava()
                && !player.isGliding()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !player.hasVehicle()
                && !player.isSprinting();
    }
}
