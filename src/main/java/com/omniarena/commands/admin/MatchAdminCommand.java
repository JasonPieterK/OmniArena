package com.omniarena.commands.admin;

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

public class MatchAdminCommand extends SubCommand {

    private final OmniArena plugin;

    public MatchAdminCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_MATCH, "/oa match <start|stop|reset> <arena>", "Manage match state");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        String action = args[0].toLowerCase();
        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[1]);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[1]));
            return;
        }
        Arena arena = arenaOpt.get();
        MatchManager mm = MatchManager.getInstance();

        switch (action) {
            case "start" -> {
                Optional<Match> existing = mm.getMatch(arena);
                if (existing.isPresent()) {
                    mm.startMatch(existing.get());
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("match-force-started").replace("{arena}", arena.getName()));
                } else {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("match-not-running").replace("{arena}", arena.getName()));
                }
            }
            case "stop" -> {
                Optional<Match> match = mm.getMatch(arena);
                if (match.isEmpty()) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("match-not-running").replace("{arena}", arena.getName()));
                    return;
                }
                mm.endMatch(match.get(), new com.omniarena.match.MatchResult(
                        arena.getName(), null, null, null,
                        com.omniarena.match.MatchResult.Outcome.FORFEIT,
                        match.get().getDurationSeconds(),
                        new java.util.ArrayList<>(match.get().getPlayers())));
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("match-force-stopped").replace("{arena}", arena.getName()));
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("start", "stop", "reset");
        if (args.length == 2) return ArenaManager.getInstance().getAll().stream().map(Arena::getName).filter(n -> n.startsWith(args[1])).toList();
        return List.of();
    }
}
