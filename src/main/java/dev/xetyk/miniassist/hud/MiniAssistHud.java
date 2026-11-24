package dev.xetyk.miniassist.hud;

import dev.xetyk.miniassist.ClientKeybinds;
import dev.xetyk.miniassist.modules.api.Category;
import dev.xetyk.miniassist.modules.api.Module;
import dev.xetyk.miniassist.modules.api.ModuleManager;
import dev.xetyk.miniassist.modules.impl.combat.AimAssist;
import dev.xetyk.miniassist.modules.impl.render.ClientHudModule;
import dev.xetyk.miniassist.util.Draw;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Expensive-style multi-widget HUD ported to Fabric/Yarn 1.21.4.
 * Layout and widget concepts follow the supplied reference; MiniAssist uses its own brand,
 * colors and vanilla font rather than bundling the reference client's private assets.
 */
public final class MiniAssistHud {
    private static final int PANEL = 0xF0151515;
    private static final int OUTLINE = 0xFF050505;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFFD2D2D2;
    private static final int FAINT = 0xFF8E8E8E;
    private static final int ACCENT_A = 0xFF9A83FF;
    private static final int ACCENT_B = 0xFF62D5FF;
    private static final int GREEN = 0xFF9EFFB9;
    private static final int TRACK = 0xFF202020;
    private static final Pattern PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern STAFF_PREFIX = Pattern.compile(
            ".*(mod|moder|admin|helper|support|staff|owner|curat|developer|dev|yt|сотруд|модер|адм|хелп|поддерж|кура).*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static float targetHealthAnim = -1f;
    private static LivingEntity heldTarget;
    private static long heldTargetSeenMs;

    public record HudWidget(String id, String title, int x, int y, int width, int height) {
        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private record StaffRow(String prefix, String name, boolean vanished) {}

    private MiniAssistHud() {}

    public static void init() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
        HudEditor.init();
    }

    private static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ClientKeybinds.isHidden() || client.options.hudHidden || client.player == null || client.world == null) return;
        if (client.getDebugHud().shouldShowDebugHud()) return;
        Module module = ModuleManager.get("HUD");
        if (!(module instanceof ClientHudModule hud) || !hud.isEnabled()) return;

        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        if (sw < 150 || sh < 90) return;
        for (HudWidget widget : getWidgets(client)) {
            switch (widget.id()) {
                case "watermark" -> drawWatermark(context, client, widget);
                case "active_modules" -> drawActiveModules(context, client, widget);
                case "keybinds" -> drawKeybinds(context, client, widget);
                case "effects" -> drawEffects(context, client, widget);
                case "staff_list" -> drawStaffList(context, client, widget);
                case "coordinates" -> drawCoordinates(context, client, widget);
                case "armor" -> drawArmor(context, client, widget);
                case "keystrokes" -> drawKeystrokes(context, client, widget);
                case "target" -> drawTarget(context, client, widget);
                default -> { }
            }
        }
    }

    /** Exact visible bounds, shared with the chat HUD editor for drag hit-testing. */
    public static List<HudWidget> getWidgets(MinecraftClient client) {
        List<HudWidget> out = new ArrayList<>();
        if (client == null || client.player == null || client.world == null || client.getWindow() == null
                || ClientKeybinds.isHidden() || client.options.hudHidden) return out;
        Module module = ModuleManager.get("HUD");
        if (!(module instanceof ClientHudModule hud) || !hud.isEnabled()) return out;
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        var tr = client.textRenderer;

        // Expensive-style watermark: compact logo tile + independent user/status tile.
        if (hud.showWatermark()) {
            String status = client.getSession().getUsername() + "  |  " + client.getCurrentFps() + " FPS  |  " + getLocalPing(client);
            int statusWidth = Math.min(Math.max(88, tr.getWidth(status) + 30), Math.max(88, sw - 38));
            add(out, "watermark", "WATERMARK", 4, 4, 22 + 3 + statusWidth, 22, sw, sh);
        }

        List<Module> active = activeModules();
        if (hud.showActiveModules() && !active.isEmpty()) {
            int max = active.stream().limit(14).mapToInt(m -> tr.getWidth(m.getName())).max().orElse(30);
            add(out, "active_modules", "MODULES", 4, 32, Math.min(sw - 8, max + 12), Math.min(14, active.size()) * 13, sw, sh);
        }

        List<Module> binds = activeBoundModules();
        if (hud.showKeybinds() && !binds.isEmpty()) {
            int max = binds.stream().limit(9).mapToInt(m -> tr.getWidth(m.getName()) + tr.getWidth("[" + m.getBindName() + "]")).max().orElse(70);
            int w = Math.max(80, Math.min(sw - 8, max + 20));
            add(out, "keybinds", "KEYBINDS", 185, 5, w, 19 + Math.min(9, binds.size()) * 11, sw, sh);
        }

        List<StatusEffectInstance> effects = effectList(client);
        if (hud.showEffects() && !effects.isEmpty()) {
            int max = effects.stream().mapToInt(e -> tr.getWidth(effectName(e)) + tr.getWidth(effectDuration(e))).max().orElse(72);
            int w = Math.max(80, Math.min(sw - 8, max + 20));
            add(out, "effects", "POTIONS", 278, 5, w, 19 + effects.size() * 11, sw, sh);
        }

        List<StaffRow> staff = staffRows(client);
        if (hud.showStaffList() && !staff.isEmpty()) {
            int max = staff.stream().mapToInt(row -> tr.getWidth(row.prefix() + row.name()) + (row.vanished() ? tr.getWidth("V") + 5 : 0)).max().orElse(55);
            int w = Math.max(80, Math.min(sw - 8, max + 20));
            add(out, "staff_list", "STAFFLIST", 96, 5, w, 19 + Math.min(8, staff.size()) * 11, sw, sh);
        }

        if (hud.showCoordinates()) {
            int w = Math.max(tr.getWidth(coordinateLine(client, true)), tr.getWidth(coordinateLine(client, false))) + 4;
            add(out, "coordinates", "COORDS", 3, sh - 29, w, 25, sw, sh);
        }

        if (hud.showArmor()) {
            add(out, "armor", "ARMOR", sw / 2 + 95, sh - 20, 4 * 18 + 4, 18, sw, sh);
        }
        if (hud.showKeystrokes() && sw >= 280 && sh >= 170) {
            add(out, "keystrokes", "KEYSTROKES", sw - 111, sh - 86, 104, 70, sw, sh);
        }
        if (hud.showTarget() && sw >= 300 && findHudTarget(client) != null) {
            add(out, "target", "TARGET HUD", 74, 128, 116, 40, sw, sh);
        }
        return out;
    }

    private static void add(List<HudWidget> list, String id, String title, int x, int y,
                            int width, int height, int screenWidth, int screenHeight) {
        HudLayout.Position pos = HudLayout.resolve(id, x, y, width, height, screenWidth, screenHeight);
        list.add(new HudWidget(id, title, pos.x(), pos.y(), width, height));
    }

    private static List<Module> activeModules() {
        List<Module> result = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            if (!module.isEnabled() || module.getName().equalsIgnoreCase("HUD")) continue;
            if (module.getCategory() == Category.RENDER || module.getCategory() == Category.MISCELLANEOUS) continue;
            result.add(module);
        }
        result.sort(Comparator.comparingInt((Module m) -> MinecraftClient.getInstance().textRenderer.getWidth(m.getName())).reversed());
        return result;
    }

    /** Expensive's KeyBinds list displays modules that are both enabled and bound. */
    private static List<Module> activeBoundModules() {
        List<Module> result = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            if (module.isEnabled() && !module.getName().equalsIgnoreCase("HUD")
                    && !"NONE".equalsIgnoreCase(module.getBindName())) result.add(module);
        }
        result.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static List<StatusEffectInstance> effectList(MinecraftClient client) {
        return client.player.getStatusEffects().stream()
                .sorted(Comparator.comparing(e -> e.getEffectType().value().getName().getString()))
                .limit(8).toList();
    }

    private static String effectName(StatusEffectInstance effect) {
        String level = effect.getAmplifier() > 0 && effect.getAmplifier() < 10 ? " " + (effect.getAmplifier() + 1) : "";
        return effect.getEffectType().value().getName().getString() + level;
    }

    private static String effectDuration(StatusEffectInstance effect) {
        return StatusEffectUtil.getDurationText(effect, 1.0F, 20.0F).getString();
    }

    private static List<StaffRow> staffRows(MinecraftClient client) {
        List<StaffRow> rows = new ArrayList<>();
        if (client.world == null) return rows;
        Set<String> online = new HashSet<>();
        if (client.getNetworkHandler() != null) {
            for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
                online.add(entry.getProfile().getName());
            }
        }
        Collection<Team> teams = client.world.getScoreboard().getTeams();
        for (Team team : teams.stream().sorted(Comparator.comparing(Team::getName)).toList()) {
            String prefix = team.getPrefix().getString().trim();
            String lower = prefix.toLowerCase(Locale.ROOT);
            boolean looksLikeStaff = STAFF_PREFIX.matcher(lower).matches();
            for (String name : team.getPlayerList()) {
                if (!PLAYER_NAME.matcher(name).matches() || name.equals(client.player.getGameProfile().getName())) continue;
                boolean vanished = !online.contains(name);
                // Vanish is only a scoreboard heuristic, not a reliable staff detector.
                if (looksLikeStaff || (vanished && !prefix.isBlank())) {
                    rows.add(new StaffRow(prefix, name, vanished));
                }
            }
        }
        rows.sort(Comparator.comparing(StaffRow::name, String.CASE_INSENSITIVE_ORDER));
        return rows.stream().limit(8).toList();
    }

    private static String coordinateLine(MinecraftClient client, boolean xyz) {
        var player = client.player;
        if (xyz) return "XYZ: " + (int) player.getX() + ", " + (int) player.getY() + ", " + (int) player.getZ();
        double speed = Math.hypot(player.getVelocity().x, player.getVelocity().z) * 20.0;
        return String.format(Locale.ROOT, "BPS: %.2f", speed);
    }

    private static void drawWatermark(DrawContext ctx, MinecraftClient client, HudWidget box) {
        int x = box.x(), y = box.y();
        styledPanel(ctx, x, y, 22, 22);
        // Original MiniAssist monogram, replacing the source client's private logo asset.
        Draw.rr(ctx, x + 5, y + 5, 12, 12, 4, ACCENT_A);
        ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal("M"), x + 11, y + 7, TEXT);
        int secondX = x + 25;
        int secondW = box.width() - 25;
        styledPanel(ctx, secondX, y, secondW, 22);
        Draw.rr(ctx, secondX + 6, y + 7, 7, 7, 4, ACCENT_B);
        String status = client.getSession().getUsername() + "  " + client.getCurrentFps() + " FPS  " + getLocalPing(client);
        ctx.drawTextWithShadow(client.textRenderer, trim(client, status, secondW - 25), secondX + 18, y + 7, TEXT);
    }

    private static void drawActiveModules(DrawContext ctx, MinecraftClient client, HudWidget box) {
        List<Module> active = activeModules();
        int y = box.y();
        for (int i = 0; i < active.size() && i < 14; i++) {
            Module module = active.get(i);
            int w = Math.min(box.width(), client.textRenderer.getWidth(module.getName()) + 12);
            int color = Draw.mix(ACCENT_A, ACCENT_B, active.size() <= 1 ? 0f : (float) i / (active.size() - 1));
            Draw.rr(ctx, box.x() - 1, y, w + 2, 12, 3, OUTLINE);
            Draw.rr(ctx, box.x(), y, w, 11, 3, PANEL);
            ctx.fill(box.x(), y + 2, box.x() + 1, y + 9, color);
            ctx.drawTextWithShadow(client.textRenderer, module.getName(), box.x() + 5, y + 2, color);
            y += 13;
        }
    }

    private static void drawKeybinds(DrawContext ctx, MinecraftClient client, HudWidget box) {
        var tr = client.textRenderer;
        styledPanel(ctx, box.x(), box.y(), box.width(), box.height());
        drawPanelHeader(ctx, client, box, "KeyBinds");
        int y = box.y() + 18;
        List<Module> binds = activeBoundModules();
        for (int i = 0; i < binds.size() && i < 9; i++) {
            Module module = binds.get(i);
            String key = "[" + module.getBindName() + "]";
            ctx.drawTextWithShadow(tr, trim(client, module.getName(), box.width() - tr.getWidth(key) - 20), box.x() + 5, y, MUTED);
            ctx.drawTextWithShadow(tr, key, box.x() + box.width() - tr.getWidth(key) - 5, y, ACCENT_A);
            y += 11;
        }
    }

    private static void drawEffects(DrawContext ctx, MinecraftClient client, HudWidget box) {
        var tr = client.textRenderer;
        List<StatusEffectInstance> effects = effectList(client);
        styledPanel(ctx, box.x(), box.y(), box.width(), box.height());
        drawPanelHeader(ctx, client, box, "Potions");
        int y = box.y() + 18;
        for (StatusEffectInstance effect : effects) {
            String duration = effectDuration(effect);
            ctx.drawTextWithShadow(tr, trim(client, effectName(effect), box.width() - tr.getWidth(duration) - 18), box.x() + 5, y, MUTED);
            ctx.drawTextWithShadow(tr, duration, box.x() + box.width() - tr.getWidth(duration) - 5, y, MUTED);
            y += 11;
        }
    }

    private static void drawStaffList(DrawContext ctx, MinecraftClient client, HudWidget box) {
        var tr = client.textRenderer;
        List<StaffRow> rows = staffRows(client);
        styledPanel(ctx, box.x(), box.y(), box.width(), box.height());
        drawPanelHeader(ctx, client, box, "StaffList");
        int y = box.y() + 18;
        for (StaffRow row : rows) {
            int prefixW = tr.getWidth(row.prefix());
            ctx.drawTextWithShadow(tr, trim(client, row.prefix(), box.width() / 2), box.x() + 5, y, ACCENT_A);
            int nameX = box.x() + 5 + Math.min(prefixW, box.width() / 2);
            ctx.drawTextWithShadow(tr, trim(client, row.name(), box.width() - 24 - Math.min(prefixW, box.width() / 2)), nameX, y, TEXT);
            if (row.vanished()) {
                ctx.drawTextWithShadow(tr, "V", box.x() + box.width() - 11, y, 0xFFFF5656);
            }
            y += 11;
        }
    }

    private static void drawCoordinates(DrawContext ctx, MinecraftClient client, HudWidget box) {
        var tr = client.textRenderer;
        int y = box.y() + 1;
        // Match the reference layout: BPS above, XYZ on the bottom line.
        ctx.drawTextWithShadow(tr, "BPS: ", box.x(), y, TEXT);
        ctx.drawTextWithShadow(tr, coordinateLine(client, false).substring(5), box.x() + tr.getWidth("BPS: "), y, GREEN);
        y += 11;
        ctx.drawTextWithShadow(tr, "XYZ: ", box.x(), y, TEXT);
        ctx.drawTextWithShadow(tr, coordinateLine(client, true).substring(5), box.x() + tr.getWidth("XYZ: "), y, GREEN);
    }

    private static void drawArmor(DrawContext ctx, MinecraftClient client, HudWidget box) {
        List<ItemStack> armor = new ArrayList<>();
        for (ItemStack stack : client.player.getArmorItems()) armor.add(stack);
        java.util.Collections.reverse(armor);
        for (int i = 0; i < armor.size(); i++) {
            ItemStack stack = armor.get(i);
            if (stack.isEmpty()) continue;
            int x = box.x() + i * 18;
            ctx.drawItem(stack, x, box.y());
            ctx.drawStackOverlay(client.textRenderer, stack, x, box.y());
        }
    }

    private static void drawKeystrokes(DrawContext ctx, MinecraftClient client, HudWidget box) {
        styledPanel(ctx, box.x(), box.y(), box.width(), box.height());
        drawPanelHeader(ctx, client, box, "Keystrokes");
        drawKey(ctx, client, "W", box.x() + 41, box.y() + 18, client.options.forwardKey.isPressed());
        drawKey(ctx, client, "A", box.x() + 17, box.y() + 34, client.options.leftKey.isPressed());
        drawKey(ctx, client, "S", box.x() + 41, box.y() + 34, client.options.backKey.isPressed());
        drawKey(ctx, client, "D", box.x() + 65, box.y() + 34, client.options.rightKey.isPressed());
        drawKey(ctx, client, "J", box.x() + 17, box.y() + 52, client.options.jumpKey.isPressed());
        drawKey(ctx, client, "L", box.x() + 45, box.y() + 52, client.options.attackKey.isPressed());
        drawKey(ctx, client, "R", box.x() + 73, box.y() + 52, client.options.useKey.isPressed());
    }

    private static void drawKey(DrawContext ctx, MinecraftClient client, String label, int x, int y, boolean down) {
        Draw.rr(ctx, x, y, 22, 13, 3, down ? 0xFF5B4A9D : 0xFF242424);
        ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal(label), x + 11, y + 3, down ? TEXT : MUTED);
    }

    private static void drawTarget(DrawContext ctx, MinecraftClient client, HudWidget box) {
        LivingEntity target = findHudTarget(client);
        if (target == null || !target.isAlive()) { targetHealthAnim = -1f; return; }
        styledPanel(ctx, box.x(), box.y(), box.width(), box.height());
        float ratio = target.getMaxHealth() <= 0 ? 0 : MathHelper.clamp(target.getHealth() / target.getMaxHealth(), 0, 1);
        targetHealthAnim = targetHealthAnim < 0 ? ratio : targetHealthAnim + (ratio - targetHealthAnim) * 0.18f;
        int headX = box.x() + 5, headY = box.y() + 5;
        Draw.rr(ctx, headX - 1, headY - 1, 26, 26, 4, 0xFF303030);
        if (target instanceof PlayerEntity player && client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(player.getUuid());
            if (entry != null) PlayerSkinDrawer.draw(ctx, entry.getSkinTextures(), headX, headY, 24);
            else drawFallbackHead(ctx, client, target, headX, headY);
        } else {
            drawFallbackHead(ctx, client, target, headX, headY);
        }
        int textX = box.x() + 36;
        String name = trim(client, target.getName().getString(), box.width() - 43);
        ctx.drawTextWithShadow(client.textRenderer, name, textX, box.y() + 5, TEXT);
        String hp = String.format(Locale.ROOT, "HP: %.1f", target.getHealth());
        ctx.drawTextWithShadow(client.textRenderer, hp, textX, box.y() + 16, MUTED);
        int barX = textX, barY = box.y() + 29, barW = box.width() - 42;
        Draw.rr(ctx, barX, barY, barW, 5, 2, TRACK);
        int fill = Math.round(barW * targetHealthAnim);
        if (fill > 0) Draw.rr(ctx, barX, barY, Math.max(2, fill), 5, 2, Draw.mix(0xFFFF5656, GREEN, targetHealthAnim));
    }

    private static void drawFallbackHead(DrawContext ctx, MinecraftClient client, LivingEntity target, int x, int y) {
        Draw.rr(ctx, x, y, 24, 24, 3, 0xFF41465A);
        String initial = target.getName().getString().isEmpty() ? "?" : target.getName().getString().substring(0, 1).toUpperCase(Locale.ROOT);
        ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal(initial), x + 12, y + 8, TEXT);
    }

    private static LivingEntity findHudTarget(MinecraftClient client) {
        long now = System.currentTimeMillis();
        LivingEntity live = findLiveTarget(client);
        if (live != null) {
            heldTarget = live;
            heldTargetSeenMs = now;
            return live;
        }
        if (heldTarget == null) return null;
        Module hudModule = ModuleManager.get("HUD");
        double seconds = hudModule instanceof ClientHudModule hud ? hud.getTargetHoldSeconds() : 3.0;
        boolean expired = now - heldTargetSeenMs > (long) (seconds * 1000.0);
        if (expired || heldTarget.isRemoved() || !heldTarget.isAlive() || heldTarget.getWorld() != client.world) {
            heldTarget = null;
            return null;
        }
        return heldTarget;
    }

    private static LivingEntity findLiveTarget(MinecraftClient client) {
        Module module = ModuleManager.get("Aim Assist");
        if (module instanceof AimAssist aim && aim.isEnabled()) {
            LivingEntity target = aim.getLockedTarget();
            if (target != null && target.isAlive()) return target;
        }
        if (client.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity living
                && living != client.player) return living;
        return null;
    }

    private static String getLocalPing(MinecraftClient client) {
        if (client.getNetworkHandler() == null || client.player == null) return "singleplayer";
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
        return entry == null ? "-- ms" : entry.getLatency() + " ms";
    }

    private static void drawPanelHeader(DrawContext ctx, MinecraftClient client, HudWidget box, String title) {
        ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal(title), box.x() + box.width() / 2, box.y() + 5, ACCENT_A);
        ctx.fill(box.x() + 1, box.y() + 15, box.x() + box.width() - 1, box.y() + 16, 0x55333333);
    }

    private static void styledPanel(DrawContext ctx, int x, int y, int width, int height) {
        Draw.rr(ctx, x - 1, y - 1, width + 2, height + 2, 4, OUTLINE);
        Draw.rr(ctx, x, y, width, height, 4, PANEL);
    }

    private static String trim(MinecraftClient client, String value, int maxWidth) {
        if (maxWidth <= 0) return "";
        if (client.textRenderer.getWidth(value) <= maxWidth) return value;
        String out = value;
        while (!out.isEmpty() && client.textRenderer.getWidth(out + "…") > maxWidth) out = out.substring(0, out.length() - 1);
        return out + "…";
    }
}
