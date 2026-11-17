package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.match.MatchState;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class ForceStartCommand extends SubCommand {

    public ForceStartCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_MATCH, "/oa force start <arena>", "Force start a match");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[0].equalsIgnoreCase("start")) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[1]);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[1]));
            return;
        }

        Arena arena = arenaOpt.get();
        String arenaName = arena.getName();
        MatchManager matchManager = MatchManager.getInstance();
        Match match = matchManager.getActiveMatches().stream()
                .filter(m -> m.getArena().getName().equals(arenaName))
                .findFirst()
                .orElse(null);

        if (match == null) {
            MessageUtil.send(sender, "<red>No active match in arena <yellow>" + arenaName + "</yellow>.");
            return;
        }

        if (match.getState() == MatchState.RUNNING) {
            MessageUtil.send(sender, "<red>Match is already running in <yellow>" + arenaName + "</yellow>.");
            return;
        }

        if (match.getState() == MatchState.ENDING) {
            MessageUtil.send(sender, "<red>Match is ending in <yellow>" + arenaName + "</yellow>.");
            return;
        }

        if (match.getPlayerCount() < 1) {
            MessageUtil.send(sender, "<red>No players in <yellow>" + arenaName + "</yellow>.");
            return;
        }

        // Cancel countdown and force start
        match.cancelTasks();
        matchManager.startMatch(match);
        MessageUtil.send(sender, "<green>Force started match in <yellow>" + arenaName + "</yellow>.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("start");
        if (args.length == 2) {
            return ArenaManager.getInstance().getAll().stream()
                    .map(Arena::getName)
                    .filter(n -> n.startsWith(args[1]))
                    .toList();
        }
        return List.of();
    }
}
