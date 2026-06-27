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

        String target = args[1];
        Player player = Bukkit.getPlayer(target);

        if (player != null) {
            MatchManager.getInstance().forceWin(matchOpt.get(), player.getUniqueId());
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("forcewin-player")
                    .replace("{player}", player.getName()).replace("{arena}", arena.getName()));
        } else {
            // Try team
            MatchManager.getInstance().forceWinTeam(matchOpt.get(), target);
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("forcewin-team")
                    .replace("{team}", target).replace("{arena}", arena.getName()));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return ArenaManager.getInstance().getAll().stream().map(Arena::getName).filter(n -> n.startsWith(args[0])).toList();
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.startsWith(args[1])).toList();
        return List.of();
    }
}
