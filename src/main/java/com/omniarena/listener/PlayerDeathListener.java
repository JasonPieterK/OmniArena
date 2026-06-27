package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.match.MatchState;
import com.omniarena.module.KillstreakModule;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Optional;

public class PlayerDeathListener implements Listener {

    private final OmniArena plugin;

    public PlayerDeathListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim == null) return;
        
        MatchManager mm = MatchManager.getInstance();
        if (mm == null) return;

        if (!mm.isInArena(victim)) return;

        Optional<String> arenaName = mm.getArenaName(victim);
        if (arenaName.isEmpty()) return;

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(arenaName.get());
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        Optional<Match> matchOpt = mm.getMatch(arena);
        if (matchOpt.isEmpty() || matchOpt.get().getState() != MatchState.RUNNING) return;

        Match match = matchOpt.get();

        // Suppress default death messages and item drops
        event.setDeathMessage(null);
        if (!arena.getRules().isItemDrop()) event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);

        Player killer = victim.getKiller();

        // Record kill
        if (killer != null && mm.isInArena(killer)) {
            match.recordKill(killer.getUniqueId(), victim.getUniqueId());

            // First blood
            if (!match.isFirstBloodDealt()) {
                match.setFirstBloodDealt(true);
                String fbMsg = ConfigManager.getInstance().getMessage("first-blood")
                        .replace("{killer}", killer.getName())
                        .replace("{victim}", victim.getName());
                mm.broadcastArena(match, fbMsg);
                plugin.getVisual().getTitleManager().sendFirstBloodTitle(match, killer.getName());
                plugin.getVisual().getSoundManager().playFirstBlood(match);
            }

            // Kill announcement
            String weapon = killer.getInventory().getItemInMainHand().getType().name()
                    .replace("_", " ").toLowerCase();
            String killMsg = ConfigManager.getInstance().getMessage("kill-announcement")
                    .replace("{killer}", killer.getName())
                    .replace("{killer_kills}", String.valueOf(match.getKills(killer.getUniqueId())))
                    .replace("{victim}", victim.getName())
                    .replace("{weapon}", weapon);
            mm.broadcastArena(match, killMsg);

            // Revenge
            if (match.isRevenge(killer.getUniqueId(), victim.getUniqueId())) {
                MessageUtil.send(killer, ConfigManager.getInstance().getMessage("revenge-kill")
                        .replace("{player}", victim.getName()));
                plugin.getVisual().getTitleManager().sendRevengeTitle(killer, victim.getName());
                plugin.getVisual().getSoundManager().playRevenge(killer);
            }

            // Killstreak
            KillstreakModule.getInstance().handleKill(killer, match);

            // Team kill
            if (arena.getTeamConfig().isEnabled()) {
                var team = match.getTeam(killer.getUniqueId());
                if (team != null) team.addKill();
            }
        } else {
            // Environmental death — reset stats
            match.resetStreak(victim.getUniqueId());
            match.resetCombo(victim.getUniqueId());
        }

        // Death effect
        plugin.getVisual().getEffectManager().playDeathEffect(victim.getLocation());

        // Send death message to victim
        String deathMsg = ConfigManager.getInstance().getMessage("death-message")
                .replace("{killer}", killer != null ? killer.getName() : "the environment");
        MessageUtil.send(victim, deathMsg);

        // Remove from alive list
        match.getAlivePlayers().remove(victim.getUniqueId());

        // Check end conditions
        mm.checkEndConditions(match);

        // Respawn, or eliminate in one-life modes (last-one-standing, sumo, duels…)
        if (arena.getGameMode().respawns()) {
            scheduleRespawn(victim, match, arena);
        } else {
            eliminate(victim, match);
        }
    }

    /** One-life modes: drop the player into spectator instead of respawning. */
    private void eliminate(Player victim, Match match) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!victim.isOnline()) return;
            victim.spigot().respawn();
            victim.setGameMode(org.bukkit.GameMode.SPECTATOR);
            int left = match.getAlivePlayers().size();
            MessageUtil.send(victim, ConfigManager.getInstance().getMessage("eliminated"));
            MatchManager.getInstance().broadcastArena(match,
                    "<gray>" + victim.getName() + " was eliminated. <yellow>" + left + "</yellow> left.");
        }, 2L);
    }

    private void scheduleRespawn(Player victim, Match match, Arena arena) {
        int delay = switch (arena.getRespawnType()) {
            case INSTANT  -> 1;
            case DELAYED  -> arena.getRespawnDelay() * 20;
            case WAVE     -> 20; // wave handled elsewhere
        };

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!victim.isOnline() || !MatchManager.getInstance().isInArena(victim)) return;
            if (match.getState() != MatchState.RUNNING) return;

            victim.spigot().respawn();
            match.getAlivePlayers().add(victim.getUniqueId());

            // Teleport to spawn + re-equip this game type's loadout
            var spawn = arena.getRandomBattleSpawn();
            if (spawn != null) victim.teleport(spawn);
            arena.getGameMode().applyLoadout(victim);

            // Spawn protection
            if (arena.getSpawnProtection() > 0) {
                match.setSpawnProtection(victim.getUniqueId(), arena.getSpawnProtection());
                plugin.getVisual().getEffectManager().playSpawnProtectionEffect(victim);
                plugin.getVisual().getSoundManager().playSpawnProtection(victim);

                // Remove protection after duration
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    match.getSpawnProtection().remove(victim.getUniqueId());
                    plugin.getVisual().getEffectManager().removeSpawnProtectionEffect(victim);
                    plugin.getVisual().getActionBarManager().sendSpawnProtectionExpired(victim);
                }, (long) arena.getSpawnProtection() * 20);
            }

            plugin.getVisual().getEffectManager().playSpawnEffect(victim);

        }, delay);
    }
}
