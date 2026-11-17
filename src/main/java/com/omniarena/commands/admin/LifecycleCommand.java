package com.omniarena.commands.admin;

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

    private final OmniArena plugin;

    public LifecycleCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ALL, "/oa <start|stop|restart>", "Plugin lifecycle control");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        String action = args.length > 0 ? args[0].toLowerCase() : "help";
        executeAction(sender, action);
    }

    public void executeAction(CommandSender sender, String action) {
        switch (action) {
            case "start" -> start(sender);
            case "stop" -> stop(sender);
            case "restart" -> restart(sender);
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

            // Restart web dashboard
            plugin.getWebDashboardServer().start();
            plugin.startSignUpdateTask();
            plugin.getCombatManager().start();

            MessageUtil.send(sender, "<green>✓ OmniArena started successfully");
            MessageUtil.send(sender, "<gray>Loaded: <yellow>" + plugin.getArenaManager().getAll().size() + " arena(s), "
                    + plugin.getKitManager().getAllAdminKits().size() + " kit(s)");
        } catch (Exception e) {
            plugin.setStopped(true);
            MessageUtil.send(sender, "<red>✗ Failed to start: " + e.getMessage());
            plugin.getLogger().severe("Start failed: " + e.getMessage());
            e.printStackTrace();
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

            // Cancel all plugin tasks (regen, countdowns, updates, etc)
            Bukkit.getScheduler().cancelTasks(plugin);

            // End all active matches & kick players
            int kicked = 0;
            for (var match : new java.util.ArrayList<>(mm.getActiveMatches())) {
                for (var uuid : new java.util.ArrayList<>(match.getPlayers())) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        mm.leaveArena(p);
                        kicked++;
                    }
                }
            }

            // Save all data
            plugin.getStatsManager().saveAll();
            plugin.getArenaManager().saveAll();
            plugin.getBettingManager().refundAll();

            // Stop web and visuals
            plugin.getWebDashboardServer().stop();
            plugin.getVisual().shutdown();
            plugin.getModuleManager().shutdown();
            plugin.getQueueManager().stop();
            plugin.getPartyManager().stop();

            MessageUtil.send(sender, "<green>✓ OmniArena stopped successfully");
            MessageUtil.send(sender, "<gray>Kicked <yellow>" + kicked + "</yellow> player(s), canceled all tasks, data saved");
        } catch (Exception e) {
            MessageUtil.send(sender, "<red>✗ Failed to stop: " + e.getMessage());
            plugin.getLogger().severe("Stop failed: " + e.getMessage());
            e.printStackTrace();
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
        MessageUtil.send(sender, "<yellow>/oa start</yellow> <gray>- initialize all systems");
        MessageUtil.send(sender, "<yellow>/oa stop</yellow> <gray>- end matches, save data, shutdown");
        MessageUtil.send(sender, "<yellow>/oa restart</yellow> <gray>- full system restart");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("start", "stop", "restart").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
