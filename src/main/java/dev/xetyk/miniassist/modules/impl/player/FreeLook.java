package dev.xetyk.miniassist.modules.impl.player;

import dev.xetyk.miniassist.modules.api.BooleanSetting;
import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/**
 * Free look: the view turns with the mouse, but the player keeps facing (and walking in) the
 * direction it had when the module was switched on. Implemented with a detached camera entity
 * that mirrors the player's position every frame and owns its own rotation.
 */
public final class FreeLook extends Module {
    private final BooleanSetting holdMode = add(new BooleanSetting(
            "Режим удержания", "Осматриваться, пока зажата клавиша модуля; отпустил — камера возвращается", true));

    private Entity cameraEntity;
    private Entity previousCameraEntity;
    private Object attachedWorld;
    private float lockedYaw;
    private float lockedPitch;

    private static boolean frameHookRegistered;

    public FreeLook() {
        super("FreeLook", Category.PLAYER,
                "Осмотр по сторонам мышью без поворота игрока (направление движения не меняется)");
        setKeyCode(GLFW.GLFW_KEY_LEFT_ALT);
        if (!frameHookRegistered) {
            frameHookRegistered = true;
            WorldRenderEvents.START.register(context -> {
                if (isEnabled()) onFrame();
            });
        }
    }

    @Override
    protected void onEnable() {
        createCamera();
    }

    private void createCamera() {
        if (cameraEntity != null || mc.player == null || mc.world == null) return;

        Entity created = EntityType.ARMOR_STAND.create(mc.world, SpawnReason.TRIGGERED);
        if (created == null) {
            setEnabled(false);
            return;
        }

        previousCameraEntity = mc.getCameraEntity();
        attachedWorld = mc.world;
        lockedYaw = mc.player.getYaw();
        lockedPitch = mc.player.getPitch();

        created.refreshPositionAndAngles(mc.player.getX(),
                mc.player.getY() + eyeOffset(created), mc.player.getZ(), lockedYaw, lockedPitch);
        created.setNoGravity(true);
        created.setInvisible(true);
        created.noClip = true;
        cameraEntity = created;
        mc.setCameraEntity(cameraEntity);
    }

    private double eyeOffset(Entity camera) {
        return mc.player.getStandingEyeHeight() - camera.getStandingEyeHeight();
    }

    @Override
    public void onPreTick() {
        if (!sameWorldOrDisable()) return;
        if (cameraEntity == null) createCamera();
        consumePlayerRotation();
    }

    @Override
    public void onTick() {
        if (!sameWorldOrDisable()) return;
        if (cameraEntity == null) createCamera();
        if (cameraEntity == null) return;
        if (mc.getCameraEntity() != cameraEntity) mc.setCameraEntity(cameraEntity);

        if (holdMode.isEnabled() && getKeyCode() != GLFW.GLFW_KEY_UNKNOWN && mc.getWindow() != null
                && GLFW.glfwGetKey(mc.getWindow().getHandle(), getKeyCode()) != GLFW.GLFW_PRESS) {
            setEnabled(false);
        }
    }

    private boolean sameWorldOrDisable() {
        if (mc.player == null || mc.world == null) return false;
        if (attachedWorld != null && attachedWorld != mc.world) {
            if (mc.getCameraEntity() == cameraEntity) mc.setCameraEntity(mc.player);
            cameraEntity = null;
            previousCameraEntity = null;
            attachedWorld = null;
            setEnabled(false);
            return false;
        }
        return true;
    }

    /** Every rendered frame: mirror the player's position and move mouse look onto the camera. */
    private void onFrame() {
        if (cameraEntity == null || mc.player == null || mc.world == null || attachedWorld != mc.world) return;
        consumePlayerRotation();

        // Copy previous+current position so the renderer's own tick interpolation stays identical
        // to what it would do for the player itself.
        double offset = eyeOffset(cameraEntity);
        cameraEntity.prevX = mc.player.prevX;
        cameraEntity.prevY = mc.player.prevY + offset;
        cameraEntity.prevZ = mc.player.prevZ;
        cameraEntity.setPosition(mc.player.getX(), mc.player.getY() + offset, mc.player.getZ());
    }

    private void consumePlayerRotation() {
        if (cameraEntity == null || mc.player == null) return;
        float yawDelta = MathHelper.wrapDegrees(mc.player.getYaw() - lockedYaw);
        float pitchDelta = mc.player.getPitch() - lockedPitch;
        if (yawDelta != 0.0f || pitchDelta != 0.0f) {
            cameraEntity.setYaw(cameraEntity.getYaw() + yawDelta);
            cameraEntity.setPitch(MathHelper.clamp(cameraEntity.getPitch() + pitchDelta, -90.0f, 90.0f));
        }
        cameraEntity.prevYaw = cameraEntity.getYaw();
        cameraEntity.prevPitch = cameraEntity.getPitch();

        mc.player.setYaw(lockedYaw);
        mc.player.setPitch(lockedPitch);
        mc.player.prevYaw = lockedYaw;
        mc.player.prevPitch = lockedPitch;
    }

    @Override
    protected void onDisable() {
        boolean sameWorld = mc.world != null && attachedWorld == mc.world;
        if (mc.player != null) {
            if (sameWorld && mc.player.isAlive()) {
                mc.player.setYaw(lockedYaw);
                mc.player.setPitch(lockedPitch);
            }
            Entity restore = sameWorld ? previousCameraEntity : null;
            if (restore == null || restore.isRemoved()) restore = mc.player;
            mc.setCameraEntity(restore);
        } else if (mc.getCameraEntity() == cameraEntity && previousCameraEntity != null
                && !previousCameraEntity.isRemoved()) {
            mc.setCameraEntity(previousCameraEntity);
        }
        cameraEntity = null;
        previousCameraEntity = null;
        attachedWorld = null;
    }
}
