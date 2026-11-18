package com.omniarena.commands.admin;

import com.omniarena.commands.TabUtil;
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

public class DebugCommand extends SubCommand {

    public DebugCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa debug <arena>", "Show match debug state");
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
        MessageUtil.send(sender, "<gold>=== Debug: " + arena.getName() + " ===");
        MessageUtil.send(sender, "<gray>State: <white>" + arena.getState());
        MessageUtil.send(sender, "<gray>Mode: <white>" + arena.getGameMode());
        MessageUtil.send(sender, "<gray>Goal: <white>" + arena.getGoalType() + " (" + arena.getGoalValue() + ")");
        MessageUtil.send(sender, "<gray>Spawns: <white>battle=" + arena.getBattleSpawns().size()
                + " lobby=" + arena.getLobbySpawns().size()
                + " spectator=" + arena.getSpectatorSpawns().size());
        MessageUtil.send(sender, "<gray>Region: <white>" + (arena.getRegion().isDefined() ? arena.getRegion().getDisplayString() : "not set"));
        MessageUtil.send(sender, "<gray>Modules: <white>" + arena.getEnabledModules());
        MessageUtil.send(sender, "<gray>Signs: <white>" + arena.getSigns().size());

        Optional<Match> matchOpt = MatchManager.getInstance().getMatch(arena);
        if (matchOpt.isPresent()) {
            Match match = matchOpt.get();
            MessageUtil.send(sender, "<gray>Match State: <white>" + match.getState());
            MessageUtil.send(sender, "<gray>Players: <white>" + match.getPlayerCount() + " | Alive: " + match.getAlivePlayers().size());
            MessageUtil.send(sender, "<gray>Elapsed: <white>" + match.getElapsedSeconds() + "s | Remaining: " + match.getTimeRemaining() + "s");
        } else {
            MessageUtil.send(sender, "<gray>Match: <red>no active match");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return TabUtil.arenas(args[0]);
        return List.of();
    }
}
