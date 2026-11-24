package dev.xetyk.miniassist.modules.impl.combat;

import dev.xetyk.miniassist.modules.api.BooleanSetting;
import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.ModeSetting;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.MultiBooleanSetting;
import dev.xetyk.miniassist.modules.api.NumberSetting;
import dev.xetyk.miniassist.util.TargetSelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Client-side aim assistance: every tick the view is turned towards the best visible target
 * inside the configured radius. Never sends attack or rotation packets by itself.
 */
public final class AimAssist extends Module {
    private final NumberSetting range = add(new NumberSetting(
            "Дистанция (блоки)", "Наводиться на цели не дальше этого числа блоков", 30.0, 3.0, 100.0, 1.0));
    private final NumberSetting fov = add(new NumberSetting(
            "Угол обзора (FOV)", "Ширина конуса поиска цели; 360 — цели вокруг тебя", 180.0, 30.0, 360.0, 5.0));
    private final NumberSetting speed = add(new NumberSetting(
            "Скорость (1-10)", "Насколько быстро камера догоняет цель", 5.0, 1.0, 10.0, 0.5));
    private final ModeSetting aimPoint = add(new ModeSetting(
            "Точка прицела", "Куда наводиться на цели", List.of("Голова", "Тело", "Ноги"), 1));
    private final BooleanSetting throughWalls = add(new BooleanSetting(
            "Через стены", "Наводиться и на цели, закрытые блоками", false));
    private final BooleanSetting onlyWhileAttacking = add(new BooleanSetting(
            "Только при атаке", "Наводить только при удержании ЛКМ", false));
    private final BooleanSetting ignoreInvisible = add(new BooleanSetting(
            "Игнорировать невидимых", "Не выбирать невидимые цели", true));
    private final BooleanSetting keepTarget = add(new BooleanSetting(
            "Удерживать цель", "Не переключаться на другую цель, пока текущая подходит", true));
    private final MultiBooleanSetting targets = add(MultiBooleanSetting.create(
            "Цели", "Какие сущности можно выбирать", List.of("Игроков", "Враждебных", "Мирных")));

    private int lockedTargetId = -1;
    private UUID lockedTargetUuid;
    private Object lockedWorld;
    private Object lockedPlayer;

    public AimAssist() {
        super("Aim Assist", Category.COMBAT, "Плавно наводит камеру на ближайшую к прицелу цель в заданном радиусе");
        setKeyCode(GLFW.GLFW_KEY_F6);
        targets.set("Мирных", false);
    }

    @Override
    protected void onDisable() {
        clearLockedTarget();
    }

    /** Current target used by Target HUD; null when no valid target is held. */
    public LivingEntity getLockedTarget() {
        if (!isEnabled() || mc.world == null || lockedTargetId < 0
                || lockedWorld != mc.world || lockedPlayer != mc.player) return null;
        Entity entity = mc.world.getEntityById(lockedTargetId);
        return entity instanceof LivingEntity living && lockedTargetUuid != null
                && lockedTargetUuid.equals(living.getUuid()) && isValidTarget(living) ? living : null;
    }

    private void clearLockedTarget() {
        lockedTargetId = -1;
        lockedTargetUuid = null;
        lockedWorld = null;
        lockedPlayer = null;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null
                || mc.getCameraEntity() != mc.player || mc.player.isSpectator() || !mc.player.isAlive()) {
            clearLockedTarget();
            return;
        }
        if (lockedWorld != mc.world || lockedPlayer != mc.player) clearLockedTarget();
        if (onlyWhileAttacking.isEnabled() && !mc.options.attackKey.isPressed()) {
            clearLockedTarget();
            return;
        }

        LivingEntity target = findTarget();
        if (target == null) {
            clearLockedTarget();
            return;
        }
        lockedTargetId = target.getId();
        lockedTargetUuid = target.getUuid();
        lockedWorld = mc.world;
        lockedPlayer = mc.player;

        float[] wanted = anglesTo(getAimPoint(target));
        float yawDelta = MathHelper.wrapDegrees(wanted[0] - mc.player.getYaw());
        float pitchDelta = wanted[1] - mc.player.getPitch();

        // Fraction of the remaining error closed each tick, limited by a per-tick maximum so
        // large corrections are still a fast but continuous turn instead of an instant snap.
        double s = speed.getValue();
        float alpha = (float) MathHelper.clamp(0.10 + 0.07 * s, 0.1, 0.85);
        float maxStep = (float) (3.0 + 4.0 * s);

        float yawStep = MathHelper.clamp(yawDelta * alpha, -maxStep, maxStep);
        float pitchStep = MathHelper.clamp(pitchDelta * alpha, -maxStep * 0.7f, maxStep * 0.7f);
        if (Math.abs(yawDelta) < 0.05f) yawStep = 0.0f;
        if (Math.abs(pitchDelta) < 0.05f) pitchStep = 0.0f;

        mc.player.setYaw(mc.player.getYaw() + yawStep);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + pitchStep, -90.0f, 90.0f));
    }

    private LivingEntity findTarget() {
        double halfFov = fov.getValue() * 0.5;

        if (keepTarget.isEnabled() && lockedTargetId >= 0) {
            Entity existing = mc.world.getEntityById(lockedTargetId);
            if (existing instanceof LivingEntity living && lockedTargetUuid != null
                    && lockedTargetUuid.equals(living.getUuid()) && isValidTarget(living)
                    && inFov(living, halfFov + 10.0)) {
                return living;
            }
        }

        double r = range.getValue();
        List<Entity> nearby = mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(r + 1.0));
        return nearby.stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(this::isValidTarget)
                .filter(entity -> inFov(entity, halfFov))
                .min(Comparator.comparingDouble(this::angularDistance))
                .orElse(null);
    }

    private boolean inFov(LivingEntity target, double halfFov) {
        return fov.getValue() >= 360.0 || angularDistance(target) <= halfFov;
    }

    private Vec3d getAimPoint(Entity entity) {
        Box box = entity.getBoundingBox();
        double height = box.maxY - box.minY;
        double fraction = switch (aimPoint.getIndex()) {
            case 0 -> 0.90;   // head
            case 2 -> 0.20;   // legs
            default -> 0.55;  // body
        };
        return new Vec3d((box.minX + box.maxX) * 0.5, box.minY + height * fraction, (box.minZ + box.maxZ) * 0.5);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player || entity.isRemoved() || !entity.isAlive() || entity.isSpectator()) return false;
        if (entity.getWorld() != mc.world || entity instanceof ArmorStandEntity) return false;
        if (ignoreInvisible.isEnabled() && entity.isInvisible()) return false;
        if (mc.player.isTeammate(entity)) return false;
        if (!throughWalls.isEnabled() && !mc.player.canSee(entity)) return false;
        if (TargetSelector.distanceToHitbox(mc.player.getEyePos(), entity) > range.getValue()) return false;
        return TargetSelector.isValidType(entity, targets);
    }

    /** {yaw, pitch} needed to look at the point from the player's eyes. */
    private float[] anglesTo(Vec3d point) {
        Vec3d delta = point.subtract(mc.player.getEyePos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return new float[]{yaw, pitch};
    }

    private float angularDistance(LivingEntity target) {
        float[] wanted = anglesTo(getAimPoint(target));
        float yawDiff = MathHelper.wrapDegrees(wanted[0] - mc.player.getYaw());
        float pitchDiff = wanted[1] - mc.player.getPitch();
        return (float) Math.hypot(yawDiff, pitchDiff);
    }
}
