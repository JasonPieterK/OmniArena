package com.omniarena.arena;

import com.omniarena.OmniArena;
import com.omniarena.match.MatchManager;
import com.omniarena.util.ItemUtil;
import com.omniarena.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reusable arena setup actions (wand, region, spawns, snapshot, enable/disable) shared by the setup menu and the
 * {@code /oa setup} text shortcuts. Every method gives the player feedback in chat and saves the arena itself.
 */
public final class ArenaSetupService {

    private ArenaSetupService() {}

    /** Gives the player the selection wand. */
    public static void giveWand(Player player) {
        ItemStack wand = ItemUtil.buildGuiItem(
                com.omniarena.listener.PlayerInteractListener.wandMaterial(),
                "&6&lOmniArena Selection Wand",
                "&7Left-click a block: &eSet Position 1",
                "&7Right-click a block: &eSet Position 2",
                "&7Then press &bRegion &7in the setup menu"
        );
        player.getInventory().addItem(wand);
        MessageUtil.send(player, "&aYou received the OmniArena Selection Wand!");
    }

    /** Whether the player has marked both wand corners. */
    public static boolean hasSelection(Player player) {
        return ArenaManager.getInstance().hasFullSelection(player.getUniqueId());
    }

    /** Sets the arena region from the player's wand selection and takes a regen snapshot. */
    public static boolean setRegionFromSelection(Player player, Arena arena) {
        ArenaManager am = ArenaManager.getInstance();
        UUID uid = player.getUniqueId();
        if (!am.hasFullSelection(uid)) {
            MessageUtil.send(player, "<red>You have not selected both corners yet. <gray>Take the wand, left-click corner 1 and right-click corner 2, then try again.");
            return false;
        }
        Location p1 = am.getPoint1(uid).get();
        Location p2 = am.getPoint2(uid).get();
        if (p1.getWorld() == null || p2.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            MessageUtil.send(player, "<red>Your two corners are in different worlds; a region must be in a single world. <gray>Select both corners again in the same world.");
            return false;
        }
        arena.setRegion(new ArenaRegion(p1, p2));
        am.save(arena);
        am.clearSelection(uid);
        MessageUtil.send(player, "<green>Region set for <yellow>" + arena.getName() + "</yellow>.");
        final String an = arena.getName();
        OmniArena.getInstance().getBlockRestoreModule().createSnapshot(arena,
                () -> MessageUtil.send(player, "<gray>Region snapshot saved for regeneration."),
                reason -> MessageUtil.send(player, "<red>Region saved, but the regen snapshot of <yellow>" + an
                        + "</yellow> failed: " + reason + " <gray>Fix the cause, then use <white>Take snapshot</white> again."));
        return true;
    }

    /** Sets the (single) lobby spawn to the player's position. */
    public static void setLobby(Player player, Arena arena) {
        arena.clearSpawns(SpawnType.LOBBY);
        arena.addSpawn(SpawnType.LOBBY, player.getLocation());
        ArenaManager.getInstance().save(arena);
        MessageUtil.send(player, "<green>Lobby spawn set for <yellow>" + arena.getName() + "</yellow>.");
    }

    /** Sets the (single) spectator spawn to the player's position. */
    public static void setSpectator(Player player, Arena arena) {
        arena.clearSpawns(SpawnType.SPECTATOR);
        arena.addSpawn(SpawnType.SPECTATOR, player.getLocation());
        ArenaManager.getInstance().save(arena);
        MessageUtil.send(player, "<green>Spectator spawn set for <yellow>" + arena.getName() + "</yellow>.");
    }

    /** Adds a battle spawn at the player's position; returns the new spawn count. */
    public static int addBattleSpawn(Player player, Arena arena) {
        arena.addSpawn(SpawnType.BATTLE, player.getLocation());
        ArenaManager.getInstance().save(arena);
        int n = arena.getBattleSpawns().size();
        MessageUtil.send(player, "<green>Added <yellow>battle spawn #" + n + "</yellow> for <yellow>" + arena.getName()
                + "</yellow>. <gray>(players fill spawns 1 to " + n + " in order)");
        return n;
    }

    public static void clearBattleSpawns(Player player, Arena arena) {
        arena.clearSpawns(SpawnType.BATTLE);
        ArenaManager.getInstance().save(arena);
        MessageUtil.send(player, "<yellow>Cleared all battle spawns for " + arena.getName() + ".");
    }

    /** Takes the regen snapshot of the arena region (async write). Returns false when the region is not set. */
    public static boolean snapshot(Player player, Arena arena) {
        if (!arena.getRegion().isDefined()) {
            MessageUtil.send(player, "<red>Set the region first; a snapshot needs a region.");
            return false;
        }
        MessageUtil.send(player, "<yellow>Creating snapshot for <white>" + arena.getName() + "</white>...");
        OmniArena.getInstance().getBlockRestoreModule().createSnapshot(arena,
                () -> MessageUtil.send(player, "<green>Snapshot saved for <yellow>" + arena.getName() + "</yellow>."),
                why -> MessageUtil.send(player, "<red>Snapshot failed: " + why));
        return true;
    }

    public static boolean hasSnapshot(Arena arena) {
        return OmniArena.getInstance().getBlockRestoreModule().hasSnapshot(arena.getName());
    }

    /** What is still missing before the arena can be enabled (empty = ready). Human readable, plain text. */
    public static List<String> missingRequirements(Arena arena) {
        List<String> missing = new ArrayList<>();
        if (!arena.getRegion().isDefined()) missing.add("Region (select two corners with the wand)");
        if (arena.getLobbySpawns().isEmpty()) missing.add("Lobby spawn");
        if (arena.getBattleSpawns().isEmpty()) missing.add("At least one battle spawn");
        return missing;
    }

    /** Enables the arena when {@link #missingRequirements} is empty; otherwise tells the player what is missing. */
    public static boolean enable(Player player, Arena arena) {
        if (arena.isEnabled()) {
            MessageUtil.send(player, "<yellow>Arena <white>" + arena.getName() + "</white> is already enabled.");
            return true;
        }
        List<String> missing = missingRequirements(arena);
        if (!missing.isEmpty()) {
            MessageUtil.send(player, "<red>Arena <yellow>" + arena.getName() + "</yellow> is not ready. Still missing: <white>"
                    + String.join("<gray>, </gray>", missing) + "</white>.");
            return false;
        }
        ArenaManager.getInstance().enable(arena);
        MessageUtil.send(player, "<green>Arena <yellow>" + arena.getName() + "</yellow> is now enabled.");
        return true;
    }

    /** Kicks everyone from the arena's match and disables it. */
    public static void disable(Arena arena) {
        kickAll(arena);
        ArenaManager.getInstance().disable(arena);
    }

    /** Removes every player and spectator from the arena's active match (if any). */
    public static void kickAll(Arena arena) {
        MatchManager mm = MatchManager.getInstance();
        mm.getMatch(arena).ifPresent(match -> {
            for (UUID id : new ArrayList<>(match.getSpectators())) {
                Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null) mm.leaveSpectator(p);
            }
            for (UUID id : new ArrayList<>(match.getPlayers())) {
                Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null) mm.leaveArena(p);
            }
        });
    }
}
