package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaRegion;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.match.MatchState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Optional;

/**
 * Enforces the per-game-type combat rules that don't belong in the match engine:
 *  - UHC and friends: suppress natural (food/passive) health regen.
 *  - Sumo / Spleef: ring-out — drop below the arena floor and you die.
 */
public class GameModeRulesListener implements Listener {

    private final OmniArena plugin;

    public GameModeRulesListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        if (plugin.isStopped()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        Arena arena = arenaOf(player);
        if (arena == null || arena.getGameMode().naturalRegen()) return;
        // Block passive/food regen; allow magic (golden apples, potions, totems).
        EntityRegainHealthEvent.RegainReason r = event.getRegainReason();
        if (r == EntityRegainHealthEvent.RegainReason.SATIATED
                || r == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (plugin.isStopped()) return;
        if (event.getTo() == null || event.getFrom().getBlockY() == event.getTo().getBlockY()) return;
        Player player = event.getPlayer();
        Arena arena = arenaOf(player);
        if (arena == null || !arena.getGameMode().ringOut()) return;

        ArenaRegion region = arena.getRegion();
        double floor = region.isDefined() ? region.getMinY() - 2 : player.getWorld().getMinHeight() + 3;
        if (event.getTo().getY() < floor) {
            // Treat falling below the platform as a kill (counts toward last-alive).
            player.setHealth(0.0);
        }
    }

    private Arena arenaOf(Player player) {
        MatchManager mm = MatchManager.getInstance();
        if (mm == null || !mm.isInArena(player)) return null;
        Optional<String> name = mm.getArenaName(player);
        if (name.isEmpty()) return null;
        Optional<Arena> arena = ArenaManager.getInstance().get(name.get());
        if (arena.isEmpty()) return null;
        Optional<Match> match = mm.getMatch(arena.get());
        if (match.isEmpty() || match.get().getState() != MatchState.RUNNING) return null;
        return arena.get();
    }
}
