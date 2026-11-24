package dev.xetyk.miniassist.modules.impl.player;

import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.NumberSetting;
import org.lwjgl.glfw.GLFW;

/** Smooth vanilla FOV zoom which restores the user's original FOV when disabled. */
public final class Zoom extends Module {
    private final NumberSetting zoomFov = add(new NumberSetting(
            "FOV", "Целевое поле зрения при зуме", 30.0, 30.0, 70.0, 1.0));
    private final NumberSetting smooth = add(new NumberSetting(
            "Плавность", "Скорость приближения и возврата", 0.35, 0.10, 0.80, 0.05));
    private int originalFov = -1;

    public Zoom() {
        super("Zoom", Category.PLAYER, "Плавное приближение средствами обычного FOV Minecraft");
        setKeyCode(GLFW.GLFW_KEY_F9);
    }

    @Override protected void onEnable() {
        if (mc.options != null) originalFov = mc.options.getFov().getValue();
    }

    @Override public void onTick() {
        if (mc.options == null) return;
        int current = mc.options.getFov().getValue();
        int target = (int) Math.round(zoomFov.getValue());
        int next = (int) Math.round(current + (target - current) * smooth.getValue());
        if (next != current) mc.options.getFov().setValue(next);
    }

    @Override protected void onDisable() {
        if (mc.options != null && originalFov >= 0) mc.options.getFov().setValue(originalFov);
        originalFov = -1;
    }
}
