package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

public class InfoCommand extends SubCommand {

    public InfoCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_USE, "/oa info <arena>", "View arena info");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[0]);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[0]));
            return;
        }
        Arena arena = arenaOpt.get();
        Optional<Match> match = MatchManager.getInstance().getMatch(arena);
        int players = match.map(Match::getPlayerCount).orElse(0);

        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-info-header").replace("{arena}", arena.getName()));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-info-mode").replace("{mode}", arena.getGameMode().name()));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-info-status").replace("{status}", arena.getState().name()));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-info-players")
                .replace("{players}", String.valueOf(players)).replace("{max}", String.valueOf(arena.getMaxPlayers())));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-info-region")
                .replace("{region}", arena.getRegion().isDefined() ? arena.getRegion().getDisplayString() : "not set"));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-info-spawns")
                .replace("{spawns}", String.valueOf(arena.getBattleSpawns().size())));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1)
            return ArenaManager.getInstance().getAll().stream().map(Arena::getName).filter(n -> n.startsWith(args[0])).toList();
        return List.of();
    }
}
