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
        try {
            ConfigManager.getInstance().reload();
            com.omniarena.kit.KitManager.getInstance().loadAll();
            // Reloading arenas while matches run would orphan live arena objects; skip it then.
            if (com.omniarena.match.MatchManager.getInstance().getActiveMatches().isEmpty()) {
                ArenaManager.getInstance().loadAll();
            } else {
                MessageUtil.send(sender, "<yellow>Arenas not reloaded: matches are active. Re-run /oa reload when idle.");
            }
            plugin.getModuleManager().reload();
            plugin.startSignUpdateTask();
            if (plugin.getWatchdog() != null) plugin.getWatchdog().start();
            if (plugin.getIntegrationManager() != null) {
                plugin.getIntegrationManager().shutdown();
                plugin.getIntegrationManager().load();
            }
            plugin.getCombatManager().start();
            // Re-update all arena signs
            ArenaManager.getInstance().getAll().forEach(arena ->
                    SignManager.getInstance().updateAllSigns(arena));
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-reloaded"));
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Reload failed (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "). Most likely a YAML syntax error in plugins/OmniArena/config.yml or messages.yml: check indentation/quotes "
                    + "(validate at yamllint.com) or delete the file to regenerate defaults, then run /oa reload again.", e);
            MessageUtil.send(sender, "<red>Reload failed. <gray>Most likely a syntax error in <white>config.yml</white> or <white>messages.yml</white> (bad indentation/quotes). See the console for the exact line; fix it or delete the file to regenerate defaults, then run <white>/oa reload</white>.");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
