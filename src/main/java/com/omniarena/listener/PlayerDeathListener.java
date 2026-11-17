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
import java.util.UUID;

public class PlayerDeathListener implements Listener {

    private final OmniArena plugin;

    public PlayerDeathListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (plugin.isStopped()) return;
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
        if (matchOpt.isEmpty()) return;
        MatchState st = matchOpt.get().getState();

        // Death while the match is winding down: no drops/keep inventory, respawn at arena lobby
        if (st != MatchState.RUNNING) {
            event.setDeathMessage(null);
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            scheduleEndingRespawn(victim.getUniqueId(), arena);
            return;
        }

        Match match = matchOpt.get();

        // Suppress default death messages and item drops
        event.setDeathMessage(null);
        if (!arena.getRules().isItemDrop()) event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);

        Player killer = victim.getKiller();

        // Record kill
        if (killer != null && !killer.equals(victim) && match.hasPlayer(killer.getUniqueId())) {
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

            // Kill reward (suppressed for repeated kills on the same victim, see pvp.killabuse)
            if (!plugin.getCombatManager().registerKillAndCheckAbuse(killer.getUniqueId(), victim.getUniqueId())) {
                mm.getRewardHandler().onKill(killer, victim, match);
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
            match.getDeathsMap().merge(victim.getUniqueId(), 1, Integer::sum);
            match.resetStreak(victim.getUniqueId());
            match.resetCombo(victim.getUniqueId());
        }

        // Death effect
        plugin.getVisual().getEffectManager().playDeathEffect(victim.getLocation());

        // Send death message to victim
        String deathMsg = ConfigManager.getInstance().getMessage("death-message")
                .replace("{killer}", killer != null ? killer.getName() : "the environment");
        MessageUtil.send(victim, deathMsg);

        // Lives (per-arena `lives`): respawn while any remain, eliminate at 0.
        // 0 = not configured -> the game mode's own respawn semantics apply.
        boolean livesMode = arena.getLives() > 0;
        boolean outOfLives = false;
        if (livesMode) {
            int left = Math.max(0, match.getLivesMap().getOrDefault(victim.getUniqueId(), arena.getLives()) - 1);
            match.getLivesMap().put(victim.getUniqueId(), left);
            outOfLives = left <= 0;
            if (!outOfLives) {
                MessageUtil.send(victim, "<red>You died. <yellow>" + left + "</yellow> "
                        + (left == 1 ? "life" : "lives") + " remaining.");
            }
        }

        // Remove from alive list (with lives, only once the last one is spent)
        if (!livesMode || outOfLives) match.getAlivePlayers().remove(victim.getUniqueId());

        // Check end conditions
        mm.checkEndConditions(match);

        // Respawn, or eliminate in one-life modes (last-one-standing, sumo, duels…)
        if (livesMode ? !outOfLives : arena.getGameMode().respawns()) {
            scheduleRespawn(victim, match, arena);
        } else {
            eliminate(victim, match);
        }
    }

    /** Dead players in an arena respawn inside it: lobby when not fighting, spectator spot when eliminated. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        if (plugin.isStopped()) return;
        Player p = event.getPlayer();
        MatchManager mm = MatchManager.getInstance();
        if (mm == null || !mm.isInArena(p)) return;
        Optional<Arena> arenaOpt = mm.getArenaName(p).flatMap(n -> ArenaManager.getInstance().get(n));
        if (arenaOpt.isEmpty()) return;
        Arena arena = arenaOpt.get();
        Optional<Match> m = mm.getMatch(arena);
        org.bukkit.Location loc;
        if (mm.isSpectating(p)) loc = arena.getSpectatorSpawn();
        else if (m.isPresent() && m.get().getState() == MatchState.RUNNING)
            loc = (arena.getLives() > 0 ? m.get().getLives(p.getUniqueId()) > 0 : arena.getGameMode().respawns())
                    ? arena.getRandomBattleSpawn() : arena.getSpectatorSpawn();
        else loc = arena.getLobbySpawn();
        if (loc != null) event.setRespawnLocation(loc);
    }

    private void scheduleEndingRespawn(UUID uuid, Arena arena) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) return;
            if (p.isDead()) p.spigot().respawn();
        }, 1L);
    }

    /** One-life modes: drop the player into spectator instead of respawning. */
    private void eliminate(Player victimPlayer, Match match) {
        final UUID victimId = victimPlayer.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player victim = Bukkit.getPlayer(victimId);
            if (victim == null || !victim.isOnline()) return;
            victim.spigot().respawn();
            victim.setGameMode(org.bukkit.GameMode.SPECTATOR);
            int left = match.getAlivePlayers().size();
            MessageUtil.send(victim, ConfigManager.getInstance().getMessage("eliminated"));
            MatchManager.getInstance().broadcastArena(match,
                    "<gray>" + victim.getName() + " was eliminated. <yellow>" + left + "</yellow> left.");
        }, 2L);
    }

    private void scheduleRespawn(Player victimPlayer, Match match, Arena arena) {
        final UUID victimId = victimPlayer.getUniqueId();
        int delay = switch (arena.getRespawnType()) {
            case INSTANT  -> 1;
            case DELAYED  -> arena.getRespawnDelay() * 20;
            case WAVE     -> 20; // wave handled elsewhere
        };

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player victim = Bukkit.getPlayer(victimId);
            if (victim == null || !victim.isOnline() || !match.hasPlayer(victimId)
                    || !MatchManager.getInstance().isInArena(victim)) return;
            if (victim.isDead()) victim.spigot().respawn();
            if (match.getState() != MatchState.RUNNING) return;

            match.getAlivePlayers().add(victim.getUniqueId());

            // Teleport to spawn + re-equip this game type's loadout
            var spawn = arena.getRandomBattleSpawn();
            if (spawn != null) victim.teleport(spawn);
            MatchManager.getInstance().applyLoadout(victim, match);

            // Spawn protection
            if (arena.getSpawnProtection() > 0) {
                match.setSpawnProtection(victim.getUniqueId(), arena.getSpawnProtection());
                plugin.getVisual().getEffectManager().playSpawnProtectionEffect(victim);
                plugin.getVisual().getSoundManager().playSpawnProtection(victim);

                // Remove protection after duration
                match.setSpawnProtectionTask(victimId, Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    match.clearSpawnProtectionTask(victimId);
                    match.getSpawnProtection().remove(victimId);
                    Player p = Bukkit.getPlayer(victimId);
                    if (p == null || !p.isOnline()) return;
                    plugin.getVisual().getEffectManager().removeSpawnProtectionEffect(p);
                    plugin.getVisual().getActionBarManager().sendSpawnProtectionExpired(p);
                }, (long) arena.getSpawnProtection() * 20));
            }

            plugin.getVisual().getEffectManager().playSpawnEffect(victim);

        }, delay);
    }
}
