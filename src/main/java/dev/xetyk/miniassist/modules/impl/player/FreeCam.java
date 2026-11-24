package dev.xetyk.miniassist.modules.impl.player;

import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Replay-Mod-style free camera.
 *
 * The real player stays frozen where it was (and is rendered like any other player), while a
 * detached camera entity flies freely: no collisions, so you can look through the ground, and
 * all other players/entities keep rendering. The module key is configured in ClickGUI.
 *
 * Camera rotation and movement are updated every rendered frame (not only every tick), which
 * keeps the camera smooth instead of stepping at 20 Hz.
 */
public final class FreeCam extends Module {
    private final NumberSetting speed = add(new NumberSetting(
            "Скорость", "Скорость свободной камеры (блоков за игровой тик)", 0.35, 0.05, 2.0, 0.05));
    private final NumberSetting sprintMultiplier = add(new NumberSetting(
            "Множитель Sprint", "Во сколько раз Sprint ускоряет камеру", 3.0, 1.0, 6.0, 0.5));

    private Entity cameraEntity;
    private Entity previousCameraEntity;
    private Object attachedWorld;
    private Vec3d playerStartPos;
    private Vec3d playerStartVelocity = Vec3d.ZERO;
    private float playerStartFallDistance;
    private float lockedYaw;
    private float lockedPitch;
    private boolean previousChunkCulling = true;
    private long lastFrameNanos;

    private boolean forwardHeld;
    private boolean backHeld;
    private boolean leftHeld;
    private boolean rightHeld;
    private boolean jumpHeld;
    private boolean sneakHeld;
    private boolean sprintHeld;
    private boolean attackHeld;
    private boolean useHeld;

    private static boolean frameHookRegistered;

    public FreeCam() {
        super("FreeCam", Category.PLAYER,
                "Летающая камера как в Replay Mod: игрок стоит на месте, камера пролетает сквозь блоки");
        setKeyCode(GLFW.GLFW_KEY_F8);
        registerFrameHook();
    }

    private void registerFrameHook() {
        if (frameHookRegistered) return;
        frameHookRegistered = true;
        WorldRenderEvents.START.register(context -> {
            if (isEnabled()) onFrame();
        });
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
        playerStartPos = mc.player.getPos();
        playerStartVelocity = mc.player.getVelocity();
        playerStartFallDistance = mc.player.fallDistance;
        lockedYaw = mc.player.getYaw();
        lockedPitch = mc.player.getPitch();

        created.refreshPositionAndAngles(
                mc.player.getX(), mc.player.getEyeY() - created.getStandingEyeHeight(), mc.player.getZ(),
                lockedYaw, lockedPitch);
        created.setNoGravity(true);
        created.setInvisible(true);
        created.noClip = true;
        cameraEntity = created;
        mc.setCameraEntity(cameraEntity);

        // Without this the terrain renderer culls everything it cannot reach through air from the
        // camera, so a camera inside rock would see nothing. Disabling it shows caves/underground.
        previousChunkCulling = mc.chunkCullingEnabled;
        mc.chunkCullingEnabled = false;
        mc.gameRenderer.setRenderHand(false);
        lastFrameNanos = 0L;
    }

    /** Start of client tick: sample the keys the player is holding, then hide them from the real player. */
    @Override
    public void onPreTick() {
        if (mc.player == null || mc.world == null) return;
        if (attachedWorld != null && attachedWorld != mc.world) {
            resetForWorldChange();
            setEnabled(false);
            return;
        }
        if (cameraEntity == null) createCamera();
        if (cameraEntity == null) return;

        // Rotation is normally consumed per frame; this is only a safety net.
        consumePlayerRotation();

        // Read Minecraft's configured bindings before suppressing them for the real player.
        boolean allowMovement = mc.currentScreen == null;
        forwardHeld = allowMovement && mc.options.forwardKey.isPressed();
        backHeld = allowMovement && mc.options.backKey.isPressed();
        leftHeld = allowMovement && mc.options.leftKey.isPressed();
        rightHeld = allowMovement && mc.options.rightKey.isPressed();
        jumpHeld = allowMovement && mc.options.jumpKey.isPressed();
        sneakHeld = allowMovement && mc.options.sneakKey.isPressed();
        sprintHeld = allowMovement && mc.options.sprintKey.isPressed();
        attackHeld = allowMovement && mc.options.attackKey.isPressed();
        useHeld = allowMovement && mc.options.useKey.isPressed();

        setPlayerKeys(false, false, false, false, false, false, false, false, false);
        // The camera sits somewhere else, so clicks must never hit/place anything for the frozen player.
        while (mc.options.attackKey.wasPressed()) { /* discard */ }
        while (mc.options.useKey.wasPressed()) { /* discard */ }
        while (mc.options.pickItemKey.wasPressed()) { /* discard */ }

        freezePlayer();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (attachedWorld != null && attachedWorld != mc.world) {
            resetForWorldChange();
            setEnabled(false);
            return;
        }
        if (cameraEntity == null) createCamera();
        if (cameraEntity == null) return;

        if (mc.getCameraEntity() != cameraEntity) mc.setCameraEntity(cameraEntity);
        mc.chunkCullingEnabled = false;
        freezePlayer();
        // Give the physical key state back so the next START_CLIENT_TICK can sample it again.
        restoreMovementBindings();
    }

    /** Runs once per rendered frame while FreeCam is enabled. */
    private void onFrame() {
        if (cameraEntity == null || mc.player == null || mc.world == null || attachedWorld != mc.world) {
            lastFrameNanos = 0L;
            return;
        }
        long now = System.nanoTime();
        double ticks = lastFrameNanos == 0L ? 0.0 : Math.min(0.1, (now - lastFrameNanos) / 1.0E9) * 20.0;
        lastFrameNanos = now;

        consumePlayerRotation();
        if (mc.currentScreen == null && ticks > 0.0) moveCamera(ticks);
    }

    /** Mouse look rotates the real player; move that rotation onto the camera and put the player back. */
    private void consumePlayerRotation() {
        if (cameraEntity == null || mc.player == null) return;
        float yawDelta = MathHelper.wrapDegrees(mc.player.getYaw() - lockedYaw);
        float pitchDelta = mc.player.getPitch() - lockedPitch;
        if (yawDelta != 0.0f || pitchDelta != 0.0f) {
            cameraEntity.setYaw(cameraEntity.getYaw() + yawDelta);
            cameraEntity.setPitch(MathHelper.clamp(cameraEntity.getPitch() + pitchDelta, -90.0f, 90.0f));
        }
        // No interpolation lag: previous == current.
        cameraEntity.prevYaw = cameraEntity.getYaw();
        cameraEntity.prevPitch = cameraEntity.getPitch();

        mc.player.setYaw(lockedYaw);
        mc.player.setPitch(lockedPitch);
        mc.player.prevYaw = lockedYaw;
        mc.player.prevPitch = lockedPitch;
    }

    private void freezePlayer() {
        if (mc.player == null || playerStartPos == null) return;
        mc.player.setPosition(playerStartPos);
        mc.player.setVelocity(Vec3d.ZERO);
        mc.player.fallDistance = playerStartFallDistance;
    }

    private void moveCamera(double ticks) {
        double inputForward = (forwardHeld ? 1.0 : 0.0) - (backHeld ? 1.0 : 0.0);
        double inputSideways = (rightHeld ? 1.0 : 0.0) - (leftHeld ? 1.0 : 0.0);
        double inputVertical = (jumpHeld ? 1.0 : 0.0) - (sneakHeld ? 1.0 : 0.0);
        if (inputForward == 0.0 && inputSideways == 0.0 && inputVertical == 0.0) return;

        double length = Math.sqrt(inputForward * inputForward + inputSideways * inputSideways);
        if (length > 1.0) {
            inputForward /= length;
            inputSideways /= length;
        }

        // Move along the full view direction (including pitch) so W flies where you look.
        double yaw = Math.toRadians(cameraEntity.getYaw());
        double pitch = Math.toRadians(cameraEntity.getPitch());
        double cosPitch = Math.cos(pitch);
        double lookX = -Math.sin(yaw) * cosPitch;
        double lookY = -Math.sin(pitch);
        double lookZ = Math.cos(yaw) * cosPitch;
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        double moveSpeed = speed.getValue() * (sprintHeld ? sprintMultiplier.getValue() : 1.0) * ticks;
        double dx = (lookX * inputForward + rightX * inputSideways) * moveSpeed;
        double dy = (lookY * inputForward + inputVertical) * moveSpeed;
        double dz = (lookZ * inputForward + rightZ * inputSideways) * moveSpeed;

        double nx = cameraEntity.getX() + dx;
        double ny = cameraEntity.getY() + dy;
        double nz = cameraEntity.getZ() + dz;
        cameraEntity.setPosition(nx, ny, nz);
        // Camera uses lerp(prev, current); keeping them equal makes per-frame movement exact.
        cameraEntity.prevX = nx;
        cameraEntity.prevY = ny;
        cameraEntity.prevZ = nz;
    }

    private void setPlayerKeys(boolean forward, boolean back, boolean left, boolean right,
                               boolean jump, boolean sneak, boolean sprint, boolean attack, boolean use) {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(forward);
        mc.options.backKey.setPressed(back);
        mc.options.leftKey.setPressed(left);
        mc.options.rightKey.setPressed(right);
        mc.options.jumpKey.setPressed(jump);
        mc.options.sneakKey.setPressed(sneak);
        mc.options.sprintKey.setPressed(sprint);
        mc.options.attackKey.setPressed(attack);
        mc.options.useKey.setPressed(use);
    }

    private void restoreMovementBindings() {
        setPlayerKeys(forwardHeld, backHeld, leftHeld, rightHeld, jumpHeld, sneakHeld, sprintHeld,
                attackHeld, useHeld);
    }

    private void clearState() {
        cameraEntity = null;
        previousCameraEntity = null;
        attachedWorld = null;
        playerStartPos = null;
        playerStartVelocity = Vec3d.ZERO;
        lastFrameNanos = 0L;
        forwardHeld = backHeld = leftHeld = rightHeld = jumpHeld = sneakHeld = sprintHeld = false;
        attackHeld = useHeld = false;
    }

    private void resetForWorldChange() {
        mc.chunkCullingEnabled = previousChunkCulling;
        mc.gameRenderer.setRenderHand(true);
        if (mc.player != null && mc.getCameraEntity() == cameraEntity) mc.setCameraEntity(mc.player);
        clearState();
    }

    @Override
    protected void onDisable() {
        boolean sameWorld = mc.world != null && attachedWorld == mc.world;
        if (cameraEntity != null) {
            mc.chunkCullingEnabled = previousChunkCulling;
            mc.gameRenderer.setRenderHand(true);
        }
        if (mc.player != null) {
            if (sameWorld && playerStartPos != null && mc.player.isAlive()) {
                mc.player.setPosition(playerStartPos);
                // The player never moved or turned, so simply return to the original look direction.
                mc.player.setYaw(lockedYaw);
                mc.player.setPitch(lockedPitch);
                mc.player.prevYaw = lockedYaw;
                mc.player.prevPitch = lockedPitch;
                mc.player.setVelocity(playerStartVelocity);
                mc.player.fallDistance = playerStartFallDistance;
            }
            Entity restoreCamera = sameWorld ? previousCameraEntity : null;
            if (restoreCamera == null || restoreCamera.isRemoved()) restoreCamera = mc.player;
            mc.setCameraEntity(restoreCamera);
        } else if (mc.getCameraEntity() == cameraEntity && previousCameraEntity != null
                && !previousCameraEntity.isRemoved()) {
            mc.setCameraEntity(previousCameraEntity);
        }

        restoreMovementBindings();
        clearState();
    }
}
