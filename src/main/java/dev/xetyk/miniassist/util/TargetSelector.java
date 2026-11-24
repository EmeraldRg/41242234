package dev.xetyk.miniassist.util;

import dev.xetyk.miniassist.modules.api.MultiBooleanSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class TargetSelector {
    private TargetSelector() {}
    public static boolean isValidType(LivingEntity entity, MultiBooleanSetting targets) {
        if (entity instanceof PlayerEntity) return targets.isSelected("Игроков");
        if (entity instanceof Monster) return targets.isSelected("Враждебных");
        return targets.isSelected("Мирных");
    }

    /** Distance in blocks from the viewer's eye to the nearest point of the entity's hitbox. */
    public static double distanceToHitbox(Vec3d eye, Entity entity) {
        Box box = entity.getBoundingBox();
        double x = MathHelper.clamp(eye.x, box.minX, box.maxX);
        double y = MathHelper.clamp(eye.y, box.minY, box.maxY);
        double z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
        return eye.distanceTo(new Vec3d(x, y, z));
    }
}
