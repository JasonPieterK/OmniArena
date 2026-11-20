package com.omniarena.gui;

import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaSettingsService;
import com.omniarena.arena.regen.RegenAnimationType;
import com.omniarena.config.ConfigManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Edits one arena: enum values cycle on click, numbers use left +1 / right -1 / shift 10, booleans are
 * lime (on) / red (off) concrete. Every change goes through {@link ArenaSettingsService} (validated and saved).
 */
public final class ArenaEditMenu {

    private ArenaEditMenu() {}

    private record Toggle(String key, String label, String hint) {}

    private static final List<Toggle> RULES = List.of(
            new Toggle("fall-damage", "Fall damage", "Players take fall damage."),
            new Toggle("fire-damage", "Fire damage", "Players take fire and lava damage."),
            new Toggle("drowning", "Drowning", "Players drown underwater."),
            new Toggle("suffocation", "Suffocation", "Players suffocate inside blocks."),
            new Toggle("friendly-fire", "Friendly fire", "Teammates can hurt each other."),
            new Toggle("block-break", "Block breaking", "Players can break blocks."),
            new Toggle("block-place", "Block placing", "Players can place blocks."),
            new Toggle("item-drop", "Item dropping", "Players can drop items."),
            new Toggle("hunger-drain", "Hunger drain", "Food level drops over time."));

    private static final List<Toggle> VISUALS = List.of(
            new Toggle("visual-titles", "Titles", "Show titles."),
            new Toggle("visual-sounds", "Sounds", "Play sounds."),
            new Toggle("visual-particles", "Particles", "Show particles."),
            new Toggle("visual-scoreboard", "Scoreboard", "Show the sidebar."),
            new Toggle("visual-boss-bar", "Boss bar", "Show the boss bar."),
            new Toggle("visual-action-bar", "Action bar", "Show the action bar."));

    private static final List<Toggle> MODULES = List.of(
            new Toggle("module-killstreak", "Killstreaks", "Streak announcements and rewards."),
            new Toggle("module-combo", "Combos", "Combo counter on rapid hits."),
            new Toggle("module-power-up", "Power-ups", "Power-ups spawn during matches."));

    public static void open(Player player, Arena arena) {
        if (!AdminHubMenu.allowed(player, true)) return;
        MenuBuilder b = new MenuBuilder("<dark_gray>Edit: <green>" + MenuBuilder.esc(arena.getName()), 6);
        String s = arena.getName();

        b.set(4, MenuBuilder.item(Material.NAME_TAG, "<green><bold>" + MenuBuilder.esc(s),
                "<gray>State: <white>" + arena.getState().name().toLowerCase(Locale.ROOT),
                "<gray>Numbers: <yellow>left +1<gray>, <yellow>right -1<gray>,",
                "<gray>shift = 10. Enums: click to cycle."), null);

        b.row(1, List.of(
                enumButton(arena, "game-mode", Material.DIAMOND_SWORD, "Game mode", "How the match is played."),
                enumButton(arena, "goal-type", Material.TARGET, "Goal type", "How the match is won."),
                numButton(arena, "goal-value", Material.GOLD_NUGGET, "Goal value", "Target (kills, ...). 0 = mode default."),
                numButton(arena, "lives", Material.GOLDEN_APPLE, "Lives", "0 = off, max 100."),
                numButton(arena, "min-players", Material.PLAYER_HEAD, "Min players", "Needed to start."),
                numButton(arena, "max-players", Material.ZOMBIE_HEAD, "Max players", "Arena capacity.")));

        b.row(2, List.of(
                numButton(arena, "match-duration", Material.CLOCK, "Match duration", "Seconds."),
                numButton(arena, "countdown", Material.FIREWORK_ROCKET, "Countdown", "Seconds before the start."),
                enumButton(arena, "respawn-type", Material.RESPAWN_ANCHOR, "Respawn type", "INSTANT, DELAYED or WAVE."),
                numButton(arena, "respawn-delay", Material.BELL, "Respawn delay", "Seconds (DELAYED only)."),
                numButton(arena, "spawn-protection", Material.SHIELD, "Spawn protection", "Seconds of invulnerability.")));

        b.row(3, List.of(
                toggleButton(arena, "enabled", "Enabled", "Open for players."),
                toggleButton(arena, "announcements", "Announcements", "Broadcast match events."),
                toggleButton(arena, "mid-match-join", "Join mid-match", "Allow joining a running match."),
                toggleButton(arena, "spectator-allowed", "Spectators", "Allow spectating."),
                enumButton(arena, "chat-mode", Material.OAK_SIGN, "Chat mode", "Who sees arena chat.")));

        b.row(4, List.of(
                toggleButton(arena, "team-enabled", "Teams", "Split players into teams."),
                numButton(arena, "team-count", Material.WHITE_BANNER, "Team count", "Number of teams."),
                toggleButton(arena, "whitelist-enabled", "Whitelist", "Only whitelisted players may join."),
                regenAutoButton(arena),
                regenStyleButton(arena),
                MenuBuilder.button(MenuBuilder.item(Material.IRON_BARS, "<gold><bold>Rules",
                        "<gray>Damage, building, drops, hunger.", "", "<yellow>Click <gray>to open"),
                        (p, t) -> openToggles(p, arena, "Rules", RULES)),
                MenuBuilder.button(MenuBuilder.item(Material.GLOWSTONE_DUST, "<light_purple><bold>Visuals",
                        "<gray>Titles, sounds, sidebar, ...", "", "<yellow>Click <gray>to open"),
                        (p, t) -> openToggles(p, arena, "Visuals", VISUALS)),
                MenuBuilder.button(MenuBuilder.item(Material.COMMAND_BLOCK, "<aqua><bold>Modules",
                        "<gray>Killstreak, combo, power-ups.", "", "<yellow>Click <gray>to open"),
                        (p, t) -> openToggles(p, arena, "Modules", MODULES))));

        b.footer((p, t) -> AdminHubMenu.openArenas(p, 0),
                "<gray>Edit " + MenuBuilder.esc(s) + ".",
                "<gray>Changes save immediately.",
                "<gray>Run the setup wizard with shift-click in the arena list.");
        b.open(player);
    }

    private static void openToggles(Player player, Arena arena, String title, List<Toggle> toggles) {
        int rows = toggles.size() > 5 ? 4 : 3;
        MenuBuilder b = new MenuBuilder("<dark_gray>" + title + ": <green>" + MenuBuilder.esc(arena.getName()), rows);
        List<MenuBuilder.Button> buttons = new ArrayList<>();
        for (Toggle t : toggles) buttons.add(toggleButton(arena, t.key(), t.label(), t.hint(), () -> openToggles(player, arena, title, toggles)));
        // two rows when more than five: split evenly
        if (toggles.size() > 5) {
            int first = (buttons.size() + 1) / 2;
            b.row(1, buttons.subList(0, first));
            b.row(2, buttons.subList(first, buttons.size()));
        } else {
            b.row(1, buttons);
        }
        b.footer((p, c) -> open(p, arena), "<gray>Click to switch on or off.", "<gray>Green = on, red = off.");
        b.open(player);
    }

    // ── button factories ────────────────────────────────────────────────────

    private static MenuBuilder.Button toggleButton(Arena arena, String key, String label, String hint) {
        return toggleButton(arena, key, label, hint, null);
    }

    private static MenuBuilder.Button toggleButton(Arena arena, String key, String label, String hint, Runnable reopen) {
        boolean on = Boolean.parseBoolean(ArenaSettingsService.get(arena, key));
        return MenuBuilder.button(MenuBuilder.toggle(on, (on ? "<green><bold>" : "<red><bold>") + label, "<gray>" + hint),
                (p, t) -> {
                    apply(p, arena, key, String.valueOf(!on));
                    if (reopen != null) reopen.run(); else open(p, arena);
                });
    }

    private static MenuBuilder.Button enumButton(Arena arena, String key, Material icon, String label, String hint) {
        String cur = ArenaSettingsService.get(arena, key);
        List<String> values = ArenaSettingsService.valuesFor(key);
        return MenuBuilder.button(MenuBuilder.item(icon, "<yellow><bold>" + label,
                "<gray>" + hint, "", "<gray>Value: <white>" + cur,
                "<yellow>Click <gray>next, <yellow>right-click <gray>previous"),
                (p, t) -> {
                    int idx = Math.max(0, values.indexOf(cur));
                    idx = (idx + (t.isRightClick() ? values.size() - 1 : 1)) % values.size();
                    apply(p, arena, key, values.get(idx));
                    open(p, arena);
                });
    }

    private static MenuBuilder.Button numButton(Arena arena, String key, Material icon, String label, String hint) {
        String cur = ArenaSettingsService.get(arena, key);
        return MenuBuilder.button(MenuBuilder.item(icon, "<yellow><bold>" + label,
                "<gray>" + hint, "", "<gray>Value: <white>" + cur,
                "<yellow>Left <gray>+1  <yellow>Right <gray>-1",
                "<yellow>Shift <gray>for 10"),
                (p, t) -> {
                    int step = stepFor(t);
                    int now;
                    try { now = Integer.parseInt(cur); } catch (NumberFormatException e) { now = 0; }
                    apply(p, arena, key, String.valueOf(Math.max(0, now + step)));
                    open(p, arena);
                });
    }

    private static int stepFor(ClickType t) {
        int base = t.isShiftClick() ? 10 : 1;
        return t.isRightClick() ? -base : base;
    }

    private static MenuBuilder.Button regenAutoButton(Arena arena) {
        String override = ArenaSettingsService.regenOverride(arena);
        boolean on = ArenaSettingsService.regenEffective(arena);
        return MenuBuilder.button(MenuBuilder.toggle(on, (on ? "<green><bold>" : "<red><bold>") + "Auto regen",
                "<gray>Rebuild the arena after each match.",
                "<gray>Override: <white>" + override + " <dark_gray>(default = global setting)",
                "<gray>Cycles default, on, off."),
                (p, t) -> {
                    String next = switch (override) {
                        case "default" -> "true";
                        case "true" -> "false";
                        default -> "default";
                    };
                    apply(p, arena, "regen-auto", next);
                    open(p, arena);
                });
    }

    private static MenuBuilder.Button regenStyleButton(Arena arena) {
        String cur = RegenAnimationType.fromConfig(ConfigManager.getInstance().getConfig().getString("regen.style", "SPHERE")).name();
        RegenAnimationType[] all = RegenAnimationType.values();
        return MenuBuilder.button(MenuBuilder.item(Material.BRICKS, "<gold><bold>Regen style",
                "<gray>Animation used to rebuild arenas.", "<dark_gray>Global setting, same for every arena.", "",
                "<gray>Value: <white>" + cur, "<yellow>Click <gray>next, <yellow>right-click <gray>previous"),
                (p, t) -> {
                    int idx = RegenAnimationType.fromConfig(cur).ordinal();
                    idx = (idx + (t.isRightClick() ? all.length - 1 : 1)) % all.length;
                    ConfigManager.getInstance().setConfigValue("regen.style", all[idx].name());
                    open(p, arena);
                });
    }

    private static void apply(Player p, Arena arena, String key, String value) {
        List<String> errors = new ArrayList<>();
        if (!ArenaSettingsService.set(arena, key, value, errors)) {
            for (String e : errors) MessageUtil.send(p, "<red>" + e);
        }
    }
}
