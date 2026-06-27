package com.omniarena.visual;

import com.omniarena.match.Match;
import com.omniarena.match.MatchResult;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

public class SoundManager {

    public void playMatchStart(Match match) {
        playAll(match, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.0f);
    }

    public void playCountdown(Match match, int seconds) {
        Sound sound = seconds <= 3 ? Sound.BLOCK_NOTE_BLOCK_BASS : Sound.BLOCK_NOTE_BLOCK_PLING;
        float pitch = seconds <= 3 ? 1.5f : 1.0f;
        playAll(match, sound, 1.0f, pitch);
    }

    public void playMatchEnd(Match match, MatchResult result) {
        for (UUID uuid : match.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            if (result.isDraw()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f);
            } else if (uuid.equals(result.getWinnerId())) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.7f);
            }
        }
    }

    public void playKill(Player killer, Player victim) {
        killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_DEATH, 0.8f, 0.9f);
    }

    public void playKillstreak(Player player, int streak) {
        float pitch = Math.min(2.0f, 0.8f + (streak * 0.1f));
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, pitch);
    }

    public void playFirstBlood(Match match) {
        playAll(match, Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.3f);
    }

    public void playRevenge(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    public void playSpawnProtection(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.2f);
    }

    public void playPowerUpPickup(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);
    }

    public void playObjectiveCapture(Match match) {
        playAll(match, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.0f);
    }

    private void playAll(Match match, Sound sound, float volume, float pitch) {
        for (UUID uuid : match.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }
}
