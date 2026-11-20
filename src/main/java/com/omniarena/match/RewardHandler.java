package com.omniarena.match;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pays the rewards.* section of config.yml: Vault money, experience and commands
 * for kills and match wins. Money needs Vault (integrations.vault); XP/commands do not.
 */
public class RewardHandler {

    private final OmniArena plugin;

    public RewardHandler(OmniArena plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() { return ConfigManager.getInstance().getConfig(); }

    private boolean enabled() { return cfg().getBoolean("rewards.enabled", false); }

    public void onKill(Player killer, Player victim, Match match) {
        if (!enabled() || killer == null) return;
        give(killer, cfg().getDouble("rewards.kill-money", 0), cfg().getInt("rewards.kill-xp", 0), "kill");
        run(cfg().getStringList("rewards.kill-commands"), killer, match.getArena().getName(),
                victim != null ? victim.getName() : "");
    }

    public void onWin(Match match, MatchResult result) {
        if (!enabled() || !result.hasWinner()) return;
        List<UUID> winners = new ArrayList<>();
        if (result.getWinnerId() != null) {
            winners.add(result.getWinnerId());
        } else if (result.getWinnerTeamName() != null) {
            for (MatchTeam t : match.getTeams()) {
                if (t.getName().equals(result.getWinnerTeamName())) winners.addAll(t.getMembers());
            }
        }
        for (UUID u : winners) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            give(p, cfg().getDouble("rewards.win-money", 0), cfg().getInt("rewards.win-xp", 0), "win");
            run(cfg().getStringList("rewards.win-commands"), p, match.getArena().getName(), "");
        }
    }

    private void give(Player p, double money, int xp, String reason) {
        if (money > 0) {
            var im = plugin.getIntegrationManager();
            String paid = im != null ? im.deposit(p, money) : null;
            if (paid != null) MessageUtil.send(p, "<green>+" + paid + " <gray>(" + reason + " reward)");
        }
        if (xp > 0) {
            p.giveExp(xp);
            MessageUtil.send(p, "<green>+" + xp + " XP <gray>(" + reason + " reward)");
        }
    }

    private void run(List<String> commands, Player p, String arena, String victim) {
        for (String raw : commands) {
            String cmd = raw.replace("{player}", p.getName()).replace("{arena}", arena).replace("{victim}", victim);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }
}
