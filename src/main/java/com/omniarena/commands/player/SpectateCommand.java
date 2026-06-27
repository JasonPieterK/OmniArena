package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.MatchManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class SpectateCommand extends SubCommand {

    public SpectateCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_SPEC, "/oa spectate <arena>", "Spectate an arena");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        if (!PermissionChecker.canSpectate(player)) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("no-permission"));
            return;
        }
        if (args.length < 1) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[0]);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[0]));
            return;
        }
        if (!MatchManager.getInstance().joinSpectator(player, arenaOpt.get())) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("spectate-not-allowed"));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1)
            return ArenaManager.getInstance().getEnabled().stream()
                    .map(Arena::getName).filter(n -> n.startsWith(args[0])).toList();
        return List.of();
    }
}
