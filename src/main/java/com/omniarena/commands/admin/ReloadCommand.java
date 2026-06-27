package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.module.ModuleManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.sign.SignManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.List;

public class ReloadCommand extends SubCommand {

    private final OmniArena plugin;

    public ReloadCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_RELOAD, "/oa reload", "Reload all configs");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ConfigManager.getInstance().reload();
        ArenaManager.getInstance().loadAll();
        plugin.getModuleManager().reload();
        // Re-update all arena signs
        ArenaManager.getInstance().getAll().forEach(arena ->
                SignManager.getInstance().updateAllSigns(arena));
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-reloaded"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
