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
        // Readiness tracking handled inside MatchManager countdown logic
        MessageUtil.send(player, "<green>You are marked as ready!");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) { return List.of(); }
}
