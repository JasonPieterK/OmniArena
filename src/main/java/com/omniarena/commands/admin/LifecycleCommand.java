package com.omniarena.commands.admin;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.MatchManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class LifecycleCommand extends SubCommand {

    private static final List<String> ACTIONS = List.of("start", "stop", "restart", "version");

    private final OmniArena plugin;

    public LifecycleCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ALL, "/oa plugin <start|stop|restart|version>", "Start, stop, restart or show the plugin version");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            com.omniarena.commands.SmartErrors.wrongArgs(sender, getUsage(), "/oa plugin version");
            return;
        }
        String action = args[0].toLowerCase();
        if (!ACTIONS.contains(action)) {
            com.omniarena.commands.SmartErrors.unknownSubcommand(sender, "/oa plugin", args[0], ACTIONS);
            return;
        }
        executeAction(sender, action);
    }

    public void executeAction(CommandSender sender, String action) {
        switch (action) {
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "restart" -> restart(sender);
            case "version" -> MessageUtil.send(sender, ConfigManager.getInstance().getMessage("plugin-version")
                    .replace("{version}", plugin.getDescription().getVersion()));
            default -> showHelp(sender);
        }
    }

    private void start(CommandSender sender) {
        if (!plugin.isStopped()) {
            MessageUtil.send(sender, "<yellow>OmniArena is already running.");
            return;
        }
        plugin.setStopped(false);
        MessageUtil.send(sender, "<yellow>Starting OmniArena systems...");
        try {
            // Reload config
            ConfigManager.getInstance().load();

            // Reinitialize all managers
            plugin.getArenaManager().loadAll();
            plugin.getKitManager().loadAll();
            plugin.getStatsManager().loadAll();
            plugin.getEloManager().loadAll();
            plugin.getQueueManager().start();
            plugin.getPartyManager().start();
            plugin.getTournamentManager().start();

            // Restart web dashboard
            plugin.getWebDashboardServer().start();
            plugin.getSignManager().resetPending();
            plugin.startSignUpdateTask();
            plugin.getCombatManager().start();
            plugin.getVisual().start();

            MessageUtil.send(sender, "<green>✓ OmniArena started successfully");
            MessageUtil.send(sender, "<gray>Loaded: <yellow>" + plugin.getArenaManager().getAll().size() + " arena(s), "
                    + plugin.getKitManager().getAllAdminKits().size() + " kit(s)");
        } catch (Exception e) {
            plugin.setStopped(true);
            MessageUtil.send(sender, "<red>✗ OmniArena failed to start. <gray>See the console for the full error, fix the cause (often a broken config.yml/arena file), then run <white>/oa plugin start</white> again.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Start failed (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "). Fix the cause shown in the trace (commonly invalid YAML in plugins/OmniArena/*.yml) and run /oa plugin start.", e);
        }
    }

    private void stop(CommandSender sender) {
        if (plugin.isStopped()) {
            MessageUtil.send(sender, "<yellow>OmniArena is already stopped.");
            return;
        }
        plugin.setStopped(true);
        MessageUtil.send(sender, "<yellow>Stopping OmniArena systems...");
        try {
            MatchManager mm = MatchManager.getInstance();

            // Finalize running regens (places remaining blocks, removes previews) before tasks are cancelled
            if (plugin.getBlockRestoreModule() != null) plugin.getBlockRestoreModule().cancelAll();

            // Cancel all plugin tasks (regen, countdowns, updates, etc)
            Bukkit.getScheduler().cancelTasks(plugin);

            // End all active matches & kick players
            int kicked = 0;
            for (var match : new java.util.ArrayList<>(mm.getActiveMatches())) {
                for (var uuid : new java.util.ArrayList<>(match.getSpectators())) {
                    Player sp = Bukkit.getPlayer(uuid);
                    if (sp != null) { mm.leaveSpectator(sp); kicked++; }
                }
                for (var uuid : new java.util.ArrayList<>(match.getPlayers())) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        mm.leaveArena(p);
                        kicked++;
                    }
                }
            }

            // Matches that just ended may have started arena regens: finalize those too (restores the world instantly)
            if (plugin.getBlockRestoreModule() != null) plugin.getBlockRestoreModule().cancelAll();

            // Close every open plugin GUI so no viewer keeps a dead menu
            try { com.omniarena.kit.KitGUI.closeAll(); } catch (Throwable ignored) {}
            try { com.omniarena.settings.SettingsGUI.closeAll(); } catch (Throwable ignored) {}

            // Save all data
            plugin.getEloManager().saveAll();
            plugin.getStatsManager().saveAll();
            plugin.getArenaManager().saveAll();
            plugin.getBettingManager().refundAll();
            plugin.getTournamentManager().stop();

            // Stop web and visuals
            plugin.getWebDashboardServer().stop();
            plugin.getVisual().shutdown();
            plugin.getModuleManager().shutdown();
            plugin.getQueueManager().stop();
            plugin.getPartyManager().stop();

            MessageUtil.send(sender, "<green>✓ OmniArena stopped successfully");
            MessageUtil.send(sender, "<gray>Kicked <yellow>" + kicked + "</yellow> player(s), canceled all tasks, data saved");
        } catch (Exception e) {
            MessageUtil.send(sender, "<red>✗ OmniArena failed to stop cleanly. <gray>See the console for the full error; data may not be saved: run <white>/oa plugin stop</white> again or restart the server.");
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Stop failed (" + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "). Some data may not have been saved; retry /oa plugin stop or restart the server.", e);
        }
    }

    private void restart(CommandSender sender) {
        MessageUtil.send(sender, "<red>Restarting OmniArena...");
        Bukkit.getScheduler().runTask(plugin, () -> {
            stop(sender);
            Bukkit.getScheduler().runTaskLater(plugin, () -> start(sender), 20L);
        });
    }

    private void showHelp(CommandSender sender) {
        MessageUtil.send(sender, "<gold>=== OmniArena Lifecycle ===");
        MessageUtil.send(sender, "<yellow>/oa plugin start</yellow> <gray>- initialize all systems");
        MessageUtil.send(sender, "<yellow>/oa plugin stop</yellow> <gray>- end matches, save data, shutdown");
        MessageUtil.send(sender, "<yellow>/oa plugin restart</yellow> <gray>- full system restart");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(ACTIONS, cur);
        return List.of();
    }
}
