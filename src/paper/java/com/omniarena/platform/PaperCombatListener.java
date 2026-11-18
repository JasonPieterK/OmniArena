package com.omniarena.platform;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.omniarena.combat.CombatListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Paper-only elytra boost (firework) blocking while combat-tagged. Only loaded on Paper. */
final class PaperCombatListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent event) {
        if (CombatListener.blockElytraUse(event.getPlayer())) event.setCancelled(true);
    }
}
