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

public class LeaveCommand extends SubCommand {

    public LeaveCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_USE, "/oa leave", "Leave current arena");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        MatchManager mm = MatchManager.getInstance();
        if (mm.isSpectating(player)) {
            mm.leaveSpectator(player);
        } else if (mm.isInArena(player)) {
            mm.leaveArena(player);
        } else {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("not-in-arena"));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) { return List.of(); }
}
