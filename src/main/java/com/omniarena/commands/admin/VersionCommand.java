package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.List;

public class VersionCommand extends SubCommand {

    private final OmniArena plugin;

    public VersionCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_RELOAD, "/oa version", "Show plugin version");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("plugin-version")
                .replace("{version}", plugin.getDescription().getVersion()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) { return List.of(); }
}
