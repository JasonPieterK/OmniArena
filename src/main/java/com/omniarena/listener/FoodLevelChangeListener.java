package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.match.MatchManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

import java.util.Optional;

public class FoodLevelChangeListener implements Listener {

    public FoodLevelChangeListener(OmniArena plugin) {}

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!MatchManager.getInstance().isInArena(player)) return;

        Optional<String> arenaName = MatchManager.getInstance().getArenaName(player);
        if (arenaName.isEmpty()) return;

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(arenaName.get());
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        if (!arena.getRules().isHungerDrain()) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }
}
