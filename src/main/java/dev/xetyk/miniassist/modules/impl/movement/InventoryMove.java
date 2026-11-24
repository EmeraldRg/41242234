package dev.xetyk.miniassist.modules.impl.movement;

import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import net.minecraft.client.util.InputUtil;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.lwjgl.glfw.GLFW;

/** Mirrors physical movement keys to vanilla KeyBindings while an inventory screen is open. */
public final class InventoryMove extends Module {
    private boolean controllingInventoryInput;
    public InventoryMove() {
        super("InventoryMove", Category.MOVEMENT, "Позволяет двигаться с открытым инвентарём/контейнером");
        setKeyCode(GLFW.GLFW_KEY_F11);
    }

    @Override public void onPreTick() {
        if (mc.player == null || mc.currentScreen == null || !(mc.currentScreen instanceof HandledScreen<?>)) {
            if (controllingInventoryInput) releaseKeys();
            controllingInventoryInput = false;
            return;
        }
        controllingInventoryInput = true;
        long window = mc.getWindow().getHandle();
        mc.options.forwardKey.setPressed(isDown(mc.options.forwardKey, window));
        mc.options.backKey.setPressed(isDown(mc.options.backKey, window));
        mc.options.leftKey.setPressed(isDown(mc.options.leftKey, window));
        mc.options.rightKey.setPressed(isDown(mc.options.rightKey, window));
        mc.options.jumpKey.setPressed(isDown(mc.options.jumpKey, window));
        mc.options.sprintKey.setPressed(isDown(mc.options.sprintKey, window));
    }

    private boolean isDown(net.minecraft.client.option.KeyBinding binding, long window) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        return key.getCategory() == InputUtil.Type.KEYSYM && key.getCode() >= 0
                && GLFW.glfwGetKey(window, key.getCode()) == GLFW.GLFW_PRESS;
    }

    private void releaseKeys() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }

    @Override protected void onDisable() {
        if (controllingInventoryInput) releaseKeys();
        controllingInventoryInput = false;
    }
}
