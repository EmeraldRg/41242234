package dev.xetyk.miniassist.modules.impl.player;

import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.glfw.GLFW;

/** Selects the hotbar tool with the best vanilla block mining speed while attack is held. */
public final class AutoTool extends Module {
    private int originalSlot = -1;
    private int lastSelectedSlot = -1;

    public AutoTool() {
        super("AutoTool", Category.PLAYER, "Выбирает лучший предмет хотбара для блока под прицелом");
        setKeyCode(GLFW.GLFW_KEY_F12);
    }

    @Override public void onPreTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null
                || !mc.options.attackKey.isPressed() || !(mc.crosshairTarget instanceof BlockHitResult hit)) {
            restoreIfNeeded();
            return;
        }
        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        if (state.isAir()) { restoreIfNeeded(); return; }

        int selected = mc.player.getInventory().selectedSlot;
        if (originalSlot < 0) originalSlot = selected;
        else if (lastSelectedSlot >= 0 && selected != lastSelectedSlot) originalSlot = selected;

        int bestSlot = selected;
        float bestSpeed = mc.player.getInventory().getStack(selected).getMiningSpeedMultiplier(state);
        for (int slot = 0; slot < 9; slot++) {
            float speed = mc.player.getInventory().getStack(slot).getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) { bestSpeed = speed; bestSlot = slot; }
        }
        if (bestSlot != selected) mc.player.getInventory().selectedSlot = bestSlot;
        lastSelectedSlot = bestSlot;
    }

    private void restoreIfNeeded() {
        if (mc.player != null && originalSlot >= 0 && lastSelectedSlot >= 0
                && mc.player.getInventory().selectedSlot == lastSelectedSlot) {
            mc.player.getInventory().selectedSlot = originalSlot;
        }
        originalSlot = -1;
        lastSelectedSlot = -1;
    }

    @Override protected void onDisable() { restoreIfNeeded(); }
}
