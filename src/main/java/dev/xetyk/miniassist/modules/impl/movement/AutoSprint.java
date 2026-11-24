package dev.xetyk.miniassist.modules.impl.movement;

import dev.xetyk.miniassist.modules.api.BooleanSetting;
import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import org.lwjgl.glfw.GLFW;

/** Vanilla-input auto sprint; does not alter packets or movement speed. */
public final class AutoSprint extends Module {
    private final BooleanSetting onlyForward = add(new BooleanSetting(
            "Только вперёд", "Включать спринт только при движении вперёд", true));
    private final BooleanSetting allowWhenHungry = add(new BooleanSetting(
            "Учитывать голод", "Не включать спринт при голоде, когда это запрещает ваниль", true));
    private boolean applied;

    public AutoSprint() {
        super("AutoSprint", Category.MOVEMENT, "Автоматически включает обычный ванильный спринт");
        setKeyCode(GLFW.GLFW_KEY_F10);
    }

    @Override public void onPreTick() {
        if (mc.player == null) return;
        if (mc.currentScreen != null) {
            if (applied) mc.player.setSprinting(false);
            applied = false;
            return;
        }
        boolean movingForward = mc.options.forwardKey.isPressed();
        boolean movingBack = mc.options.backKey.isPressed();
        boolean directionAllowed = onlyForward.isEnabled() ? movingForward && !movingBack : movingForward || movingBack;
        boolean foodAllowed = !allowWhenHungry.isEnabled() || mc.player.getHungerManager().getFoodLevel() > 6
                || mc.player.getAbilities().allowFlying;
        boolean allowed = directionAllowed && !mc.player.isSneaking() && !mc.player.isUsingItem()
                && !mc.player.horizontalCollision && foodAllowed;
        if (allowed) {
            mc.player.setSprinting(true);
            applied = true;
        } else if (applied) {
            mc.player.setSprinting(false);
            applied = false;
        }
    }

    @Override protected void onDisable() {
        if (applied && mc.player != null) mc.player.setSprinting(false);
        applied = false;
    }
}
