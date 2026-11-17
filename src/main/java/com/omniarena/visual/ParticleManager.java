package com.omniarena.visual;

import com.omniarena.match.Match;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ParticleManager {

    public void spawnBloodEffect(Location loc) {
        if (!VisualGate.particles(loc)) return;
        loc.getWorld().spawnParticle(Particle.BLOCK, loc, 15,
                0.3, 0.3, 0.3, 0.1,
                org.bukkit.Material.REDSTONE_BLOCK.createBlockData());
    }

    public void spawnDeathEffect(Location loc) {
        if (!VisualGate.particles(loc)) return;
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 10, 0.5, 0.5, 0.5, 0.05);
    }

    public void spawnSpawnEffect(Player player) {
        if (!VisualGate.particles(player)) return;
        Location loc = player.getLocation();
        loc.getWorld().spawnParticle(Particle.PORTAL, loc, 60, 0.5, 1.0, 0.5, 0.5);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, 20, 0.3, 0.8, 0.3, 0.1);
    }

    public void spawnKillstreakTrail(Player player) {
        if (!VisualGate.particles(player)) return;
        Location loc = player.getLocation().add(0, 1, 0);
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 8, 0.3, 0.5, 0.3, 0.05);
    }

    public void spawnVictoryEffect(Player player) {
        if (!VisualGate.particles(player)) return;
        Location loc = player.getLocation().add(0, 1, 0);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 30, 1, 1, 1, 0.2);
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 20, 0.5, 1.5, 0.5, 0.3);
    }

    public void spawnDefeatEffect(Location loc) {
        if (!VisualGate.particles(loc)) return;
        loc.getWorld().spawnParticle(Particle.SMOKE, loc, 15, 0.5, 0.5, 0.5, 0.05);
    }

    public void spawnPowerUpEffect(Location loc) {
        if (!VisualGate.particles(loc)) return;
        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 20, 0.5, 0.5, 0.5, 0.2);
    }

    public void spawnSpawnProtectionEffect(Player player) {
        if (!VisualGate.particles(player)) return;
        Location loc = player.getLocation();
        loc.getWorld().spawnParticle(Particle.BUBBLE_COLUMN_UP, loc, 10, 0.3, 1, 0.3, 0.1);
    }

    public void spawnObjectiveCaptureEffect(Location loc) {
        if (!VisualGate.particles(loc)) return;
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc, 40, 1, 1, 1, 0.3);
    }
}
