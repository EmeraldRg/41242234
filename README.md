# MiniAssist 1.9.1 — Expensive-style HUD port

Client-side Fabric mod for Minecraft **1.21.4** (Java 21).

## HUD port

The HUD reproduces the supplied Expensive 3.1 reference's *widget structure and layout concepts*: split watermark, left-side animated module list, coordinates/BPS, draggable KeyBinds, Potions and StaffList cards, target information with player head, and armor icons. It uses MiniAssist branding, Minecraft's bundled font and original drawing code; private Expensive logos/fonts/assets are not bundled.

Press **T** to open chat and move HUD widgets. Open the ClickGUI with **Right Shift**. HUD widget positions and module settings are saved in `config/miniassist.properties`.

`StaffList` is heuristic: it examines scoreboard team prefixes and player-list presence; servers can hide or spoof this information, so it is not a reliable moderator detector.

## Included modules

- Aim Assist, TriggerBot and FreeCam (from the existing MiniAssist codebase)
- AutoSprint — ordinary vanilla sprint input
- InventoryMove — movement keys while a handled inventory/container screen is open
- AutoTool — chooses the hotbar item with the best vanilla mining-speed multiplier for the block under the crosshair
- Zoom — smooth FOV zoom, restores the previous FOV when disabled
- HUD — individual panels can be switched on/off; Keystrokes is optional and off by default

These modules are client-side helpers. They do not bypass server anti-cheat or guarantee behavior on every server.

## Build on GitHub

The workflow is at `.github/workflows/build.yml` and uses Temurin Java 21 + Gradle 8.12. Push the extracted project contents to the repository root. On a successful run, download artifact **MiniAssist-1.9.1**. The JAR is `zenith-client-1.9.1.jar`.

A local Gradle build was not available in the preparation environment; the GitHub Actions run is the authoritative compilation check.
