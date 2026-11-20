package com.omniarena.gui;

import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaSetupService;
import com.omniarena.listener.PlayerInteractListener;
import com.omniarena.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Setup wizard: a checklist where each step has an action button (top) and a status block below it
 * (lime concrete = done, red concrete = missing).
 */
public final class ArenaSetupMenu {

    private ArenaSetupMenu() {}

    public static void open(Player player, Arena arena) {
        if (!AdminHubMenu.allowed(player, true)) return;
        MenuBuilder b = new MenuBuilder("<dark_gray>Setup: <green>" + MenuBuilder.esc(arena.getName()), 5);

        boolean region = arena.getRegion().isDefined();
        boolean lobby = !arena.getLobbySpawns().isEmpty();
        boolean spectator = !arena.getSpectatorSpawns().isEmpty();
        int battle = arena.getBattleSpawns().size();
        boolean snapshot = ArenaSetupService.hasSnapshot(arena);
        List<String> missing = ArenaSetupService.missingRequirements(arena);

        b.set(4, MenuBuilder.item(Material.NAME_TAG, "<green><bold>" + MenuBuilder.esc(arena.getName()),
                "<gray>State: <white>" + arena.getState().name().toLowerCase(),
                "<gray>Required: region, lobby spawn, battle spawn.",
                "<gray>Optional: spectator spawn, snapshot (regen)."), null);

        // Row 1 actions (5 spread), row 2 statuses directly below
        int[] cols = MenuLayout.centeredSlots(1, 5);

        b.set(cols[0], MenuBuilder.item(PlayerInteractListener.wandMaterial(), "<gold><bold>Region",
                "<gray>Mark two corners with the wand, then", "<gray>click here to save the region.", "",
                "<yellow>Click <gray>to save the selection",
                "<yellow>Right-click <gray>to get the wand"), (p, t) -> {
            if (t.isRightClick()) {
                ArenaSetupService.giveWand(p);
            } else if (!ArenaSetupService.hasSelection(p)) {
                ArenaSetupService.giveWand(p);
                MessageUtil.send(p, "<gray>Left-click corner 1, right-click corner 2 with the wand, then press <yellow>Region</yellow> again.");
                p.closeInventory();
                return;
            } else {
                ArenaSetupService.setRegionFromSelection(p, arena);
            }
            open(p, arena);
        });
        b.set(cols[0] + 9, status(region, "Region", region ? arena.getRegion().getDisplayString() : "Not set"), null);

        b.set(cols[1], MenuBuilder.item(Material.RED_BED, "<aqua><bold>Lobby spawn",
                "<gray>Players wait here before the match.", "", "<yellow>Click <gray>to set it at your feet"), (p, t) -> {
            ArenaSetupService.setLobby(p, arena);
            open(p, arena);
        });
        b.set(cols[1] + 9, status(lobby, "Lobby spawn", lobby ? "Set" : "Not set"), null);

        b.set(cols[2], MenuBuilder.item(Material.ENDER_EYE, "<light_purple><bold>Spectator spawn",
                "<gray>Where spectators watch from.", "<gray>Optional (falls back to the lobby).", "",
                "<yellow>Click <gray>to set it at your feet"), (p, t) -> {
            ArenaSetupService.setSpectator(p, arena);
            open(p, arena);
        });
        b.set(cols[2] + 9, status(spectator, "Spectator spawn", spectator ? "Set" : "Not set (optional)"), null);

        b.set(cols[3], MenuBuilder.item(Material.IRON_SWORD, "<red><bold>Add battle spawn",
                "<gray>Players fill battle spawns in order.", "<gray>Currently: <white>" + battle, "",
                "<yellow>Click <gray>to add one at your feet"), (p, t) -> {
            ArenaSetupService.addBattleSpawn(p, arena);
            open(p, arena);
        });
        b.set(cols[3] + 9, status(battle > 0, "Battle spawns", battle + " set"), null);

        b.set(cols[4], MenuBuilder.item(Material.PAINTING, "<yellow><bold>Take snapshot",
                "<gray>Saves the region so it can be rebuilt", "<gray>after every match (regeneration).", "",
                "<yellow>Click <gray>to take it now"), (p, t) -> {
            ArenaSetupService.snapshot(p, arena);
            open(p, arena);
        });
        b.set(cols[4] + 9, status(snapshot, "Snapshot", snapshot ? "Saved" : "Missing (needed for regen)"), null);

        // Row 3: clear spawns, finish
        List<MenuBuilder.Button> row3 = new java.util.ArrayList<>();
        row3.add(MenuBuilder.button(MenuBuilder.item(Material.LAVA_BUCKET, "<dark_red><bold>Clear battle spawns",
                "<gray>Removes all " + battle + " battle spawn(s).", "", "<yellow>Click <gray>to clear"), (p, t) -> {
            ArenaSetupService.clearBattleSpawns(p, arena);
            open(p, arena);
        }));
        if (arena.isEnabled()) {
            row3.add(MenuBuilder.button(MenuBuilder.item(Material.REDSTONE_BLOCK, "<red><bold>Disable arena",
                    "<gray>Kicks everyone out and closes it.", "", "<yellow>Click <gray>to disable"), (p, t) -> {
                ArenaSetupService.disable(arena);
                MessageUtil.send(p, "<yellow>Arena <white>" + arena.getName() + "</white> disabled.");
                open(p, arena);
            }));
        } else if (missing.isEmpty()) {
            row3.add(MenuBuilder.button(MenuBuilder.item(Material.EMERALD_BLOCK, "<green><bold>Finish: enable arena",
                    "<gray>Everything required is set.", "", "<yellow>Click <gray>to enable"), (p, t) -> {
                ArenaSetupService.enable(p, arena);
                open(p, arena);
            }));
        } else {
            String[] lore = new String[missing.size() + 3];
            lore[0] = "<red>Cannot enable yet. Missing:";
            for (int i = 0; i < missing.size(); i++) lore[i + 1] = "<gray> - <white>" + missing.get(i);
            lore[missing.size() + 1] = "";
            lore[missing.size() + 2] = "<yellow>Click <gray>to see this in chat";
            row3.add(MenuBuilder.button(MenuBuilder.item(Material.COAL_BLOCK, "<gray><bold>Finish (not ready)", lore), (p, t) -> {
                ArenaSetupService.enable(p, arena); // prints exactly what is missing
            }));
        }
        b.row(3, row3);

        b.footer((p, t) -> AdminHubMenu.openArenas(p, 0),
                "<gray>Work through the steps top to bottom.",
                "<gray>Green block = done, red block = missing.",
                "<gray>Finish enables the arena for players.");
        b.open(player);
    }

    private static ItemStack status(boolean done, String name, String detail) {
        return MenuBuilder.item(done ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                (done ? "<green><bold>" : "<red><bold>") + name, "<gray>" + MenuBuilder.esc(detail));
    }
}
