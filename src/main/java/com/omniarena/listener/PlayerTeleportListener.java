package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.match.MatchManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Removes a player from their arena when something other than this plugin moves them out of
 * the arena region (command/portal/pearl/chorus/world change). Plugin-initiated teleports
 * (cause PLUGIN or flagged via {@link MatchManager#markInternalTeleport}) are ignored.
 * Combat-tag teleport blocking (combat/CombatListener) cancels first, so cancelled events are skipped.
 */
public class PlayerTeleportListener implements Listener {

    private final OmniArena plugin;

    public PlayerTeleportListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.isStopped()) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        Player player = event.getPlayer();
        MatchManager mm = MatchManager.getInstance();
        if (mm == null || mm.isInternalTeleport(player.getUniqueId())) return;
        if (!mm.isInArena(player) || mm.isSpectating(player)) return;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return; // eliminated players using spectator teleport

        Location to = event.getTo();
        if (to == null) return;
        Optional<Arena> arena = arenaOf(mm, player);
        if (arena.isEmpty() || !arena.get().getRegion().isDefined()) return;
        if (arena.get().getRegion().contains(to)) return;

        scheduleLeave(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (plugin.isStopped()) return;
        Player player = event.getPlayer();
        MatchManager mm = MatchManager.getInstance();
        if (mm == null || mm.isInternalTeleport(player.getUniqueId())) return;
        if (!mm.isInArena(player) || mm.isSpectating(player)) return;
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        Optional<Arena> arena = arenaOf(mm, player);
        if (arena.isEmpty() || !arena.get().getRegion().isDefined()) return;
        if (arena.get().getRegion().contains(player.getLocation())) return;

        scheduleLeave(player.getUniqueId());
    }

    private Optional<Arena> arenaOf(MatchManager mm, Player player) {
        return mm.getArenaName(player).flatMap(n -> ArenaManager.getInstance().get(n));
    }

    private void scheduleLeave(UUID uuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            MatchManager mm = MatchManager.getInstance();
            if (p == null || mm == null || plugin.isStopped()) return;
            if (mm.isInArena(p) && !mm.isSpectating(p)) mm.leaveArena(p);
        });
    }
}
