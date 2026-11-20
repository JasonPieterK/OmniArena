package com.omniarena.commands.player;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SmartErrors;
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
        super(PermissionChecker.PLAYER_SPEC, "/oa spectate [arena]", "Spectate an arena (no name opens the arena picker)");
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
            com.omniarena.gui.ArenaPickerMenu.open(player, com.omniarena.gui.ArenaPickerMenu.Mode.SPECTATE);
            return;
        }
        Optional<Arena> arenaOpt = SmartErrors.arena(player, args[0], "/oa spectate ");
        if (arenaOpt.isEmpty()) return;
        if (!MatchManager.getInstance().joinSpectator(player, arenaOpt.get())) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("spectate-not-allowed").replace("{arena}", arenaOpt.get().getName()));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return TabUtil.enabledArenas(args[0]);
        return List.of();
    }
}
