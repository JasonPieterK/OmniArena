package com.omniarena.visual;

import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchResult;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TitleManager {

    private String msg(String key) {
        return ConfigManager.getInstance().getRawMessage(key);
    }

    public void sendMatchStartTitle(Match match) {
        if (!VisualGate.titles(match)) return;
        forEachPlayer(match, player ->
            MessageUtil.sendTitle(player,
                    msg("title-match-start"),
                    msg("title-match-start-sub").replace("{arena}", match.getArena().getName()),
                    10, 40, 10));
    }

    public void sendCountdownTitle(Match match, int seconds) {
        if (!VisualGate.titles(match)) return;
        forEachPlayer(match, player ->
            MessageUtil.sendTitle(player,
                    msg("title-countdown").replace("{seconds}", String.valueOf(seconds)),
                    msg("title-countdown-sub"),
                    5, 15, 5));
    }

    public void sendMatchEndTitle(Match match, MatchResult result) {
        if (!VisualGate.titles(match)) return;
        for (UUID uuid : match.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;

            if (result.isDraw()) {
                MessageUtil.sendTitle(player, msg("title-match-end-draw"), msg("title-match-end-draw-sub"), 10, 60, 20);
            } else if (uuid.equals(result.getWinnerId())) {
                MessageUtil.sendTitle(player, msg("title-match-end-win"), msg("title-match-end-win-sub"), 10, 60, 20);
            } else {
                MessageUtil.sendTitle(player, msg("title-match-end-lose"), msg("title-match-end-lose-sub"), 10, 60, 20);
            }
        }
    }

    public void sendKillstreakTitle(Player player, int streak, String title) {
        if (!VisualGate.titles(player)) return;
        String titleText = msg("title-killstreak")
                .replace("{streak}", String.valueOf(streak))
                .replace("{title}", title);
        String subText = msg("title-killstreak-sub")
                .replace("{streak}", String.valueOf(streak))
                .replace("{title}", title);
        MessageUtil.sendTitle(player, titleText, subText, 5, 30, 10);
    }

    public void sendFirstBloodTitle(Match match, String playerName) {
        if (!VisualGate.titles(match)) return;
        forEachPlayer(match, player ->
            MessageUtil.sendTitle(player,
                    msg("title-first-blood"),
                    msg("title-first-blood-sub").replace("{player}", playerName),
                    10, 40, 10));
    }

    public void sendRevengeTitle(Player player, String target) {
        if (!VisualGate.titles(player)) return;
        MessageUtil.sendTitle(player, "<gold>REVENGE!", "<yellow>You killed " + target + "!", 5, 25, 10);
    }

    public void sendFirstBloodSelf(Player player) {
        if (!VisualGate.titles(player)) return;
        MessageUtil.sendTitle(player, "<dark_red>FIRST BLOOD!", "<red>You drew first blood!", 5, 30, 10);
    }

    private void forEachPlayer(Match match, java.util.function.Consumer<Player> action) {
        for (UUID uuid : match.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) action.accept(p);
        }
    }
}
