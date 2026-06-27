package com.omniarena.visual;

import com.omniarena.match.Match;
import com.omniarena.match.MatchResult;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Color;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.Random;
import java.util.UUID;

public class EffectManager {

    private final ParticleManager particles = new ParticleManager();
    private final Random random = new Random();

    public void playVictoryFireworks(Match match, MatchResult result) {
        if (result.isDraw()) return;
        UUID winnerId = result.getWinnerId();
        if (winnerId == null) return;

        Player winner = Bukkit.getPlayer(winnerId);
        if (winner == null) return;

        for (int i = 0; i < 5; i++) {
            int delay = i * 8;
            Bukkit.getScheduler().runTaskLater(
                    com.omniarena.OmniArena.getInstance(),
                    () -> launchFirework(winner.getLocation()),
                    delay
            );
        }

        for (UUID uuid : match.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) particles.spawnVictoryEffect(player);
        }
    }

    public void playSpawnEffect(Player player) {
        particles.spawnSpawnEffect(player);
    }

    public void playDeathEffect(Location loc) {
        particles.spawnDeathEffect(loc);
    }

    public void playBloodEffect(Location loc) {
        particles.spawnBloodEffect(loc);
    }

    public void playKillstreakTrail(Player player) {
        particles.spawnKillstreakTrail(player);
    }

    public void playSpawnProtectionEffect(Player player) {
        particles.spawnSpawnProtectionEffect(player);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.GLOWING, 60, 0, false, false));
    }

    public void removeSpawnProtectionEffect(Player player) {
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.GLOWING);
    }

    public void playObjectiveCapture(Location loc) {
        particles.spawnObjectiveCaptureEffect(loc);
    }

    private void launchFirework(Location loc) {
        loc.add(random.nextDouble() * 4 - 2, 2, random.nextDouble() * 4 - 2);
        Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        FireworkMeta meta = fw.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BURST)
                .withColor(randomColor(), randomColor())
                .withFade(randomColor())
                .trail(true)
                .flicker(true)
                .build());
        meta.setPower(1);
        fw.setFireworkMeta(meta);
    }

    private Color randomColor() {
        Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.ORANGE,
                Color.PURPLE, Color.AQUA, Color.WHITE, Color.LIME};
        return colors[random.nextInt(colors.length)];
    }
}
