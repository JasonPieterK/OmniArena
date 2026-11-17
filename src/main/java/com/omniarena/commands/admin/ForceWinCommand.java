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
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class ForceWinCommand extends SubCommand {

    public ForceWinCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_MATCH, "/oa forcewin <arena> <player|team>", "Force a winner");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[0]);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[0]));
            return;
        }
        Arena arena = arenaOpt.get();
        Optional<Match> matchOpt = MatchManager.getInstance().getMatch(arena);
        if (matchOpt.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("match-not-running").replace("{arena}", arena.getName()));
            return;
        }

        Match m = matchOpt.get();
        if (m.getState() != com.omniarena.match.MatchState.RUNNING) {
            MessageUtil.send(sender, "<red>Match in <yellow>" + arena.getName() + "</yellow> is not running.");
            return;
        }
        String target = args[1];
        Player player = Bukkit.getPlayerExact(target);

        if (player != null) {
            if (!m.hasPlayer(player.getUniqueId())) {
                MessageUtil.send(sender, "<red>" + player.getName() + " is not playing in <yellow>" + arena.getName() + "</yellow>.");
                return;
            }
            MatchManager.getInstance().forceWin(matchOpt.get(), player.getUniqueId());
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("forcewin-player")
                    .replace("{player}", player.getName()).replace("{arena}", arena.getName()));
        } else {
            String teamName = m.getTeams().stream().map(com.omniarena.match.MatchTeam::getName)
                    .filter(n -> n.equalsIgnoreCase(target)).findFirst().orElse(null);
            if (teamName == null) {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-not-found").replace("{player}", target));
                return;
            }
            MatchManager.getInstance().forceWinTeam(m, teamName);
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("forcewin-team")
                    .replace("{team}", teamName).replace("{arena}", arena.getName()));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.arenas(cur);
        if (args.length == 2) return TabUtil.forceWinTargets(sender, args[0], cur);
        return List.of();
    }
}
