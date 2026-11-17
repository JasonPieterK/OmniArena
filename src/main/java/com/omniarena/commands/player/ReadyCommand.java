package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.MatchManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ReadyCommand extends SubCommand {

    public ReadyCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_USE, "/oa ready", "Mark yourself ready");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        if (!MatchManager.getInstance().isInArena(player)) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("not-in-arena"));
            return;
        }
        if (MatchManager.getInstance().isSpectating(player)) {
            MessageUtil.send(player, "<red>Spectators cannot ready up.");
            return;
        }
        // Matches start automatically via the arena countdown once min-players is reached.
        var match = MatchManager.getInstance().getArenaName(player)
                .flatMap(n -> com.omniarena.arena.ArenaManager.getInstance().get(n))
                .flatMap(a -> MatchManager.getInstance().getMatch(a));
        if (match.isEmpty()) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("not-in-arena"));
            return;
        }
        var m = match.get();
        switch (m.getState()) {
            case WAITING -> MessageUtil.send(player, "<yellow>You are ready. Waiting for players: <white>" + m.getPlayerCount()
                    + "/" + m.getArena().getMinPlayers() + "</white> (match starts automatically).");
            case STARTING -> MessageUtil.send(player, "<green>You are ready. The match is starting shortly.");
            default -> MessageUtil.send(player, "<gray>The match is already in progress.");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) { return List.of(); }
}
