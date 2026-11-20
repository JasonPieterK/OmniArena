package com.omniarena;

import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.regen.BlockRestoreModule;
import com.omniarena.commands.CommandManager;
import com.omniarena.commands.KitCommandExecutor;
import com.omniarena.config.ConfigManager;
import com.omniarena.integration.IntegrationManager;
import com.omniarena.kit.KitManager;
import com.omniarena.kit.KitShareHandler;
import com.omniarena.listener.*;
import com.omniarena.match.MatchManager;
import com.omniarena.module.ModuleManager;
import com.omniarena.sign.SignManager;
import com.omniarena.stats.StatsManager;
import com.omniarena.util.AsyncUtil;
import com.omniarena.web.WebDashboardServer;
import com.omniarena.visual.VisualController;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class OmniArena extends JavaPlugin {

    private static OmniArena instance;

    private ConfigManager      configManager;
    private CommandManager     commandManager;
    private ArenaManager       arenaManager;
    private MatchManager       matchManager;
    private StatsManager       statsManager;
    private KitManager         kitManager;
    private KitShareHandler    kitShareHandler;
    private SignManager        signManager;
    private KitCommandExecutor kitCommandExecutor;
    private com.omniarena.combat.CombatManager combatManager;
    private VisualController   visual;
    private ModuleManager      moduleManager;
    private IntegrationManager integrationManager;
    private BlockRestoreModule blockRestoreModule;
    private WebDashboardServer webDashboardServer;
    private com.omniarena.rating.EloManager eloManager;
    private com.omniarena.queue.QueueManager queueManager;
    private com.omniarena.party.PartyManager partyManager;
    private com.omniarena.betting.BettingManager bettingManager;
    private com.omniarena.tournament.TournamentManager tournamentManager;
    private com.omniarena.health.Watchdog watchdog;
    private volatile boolean stopped = false;
    private org.bukkit.scheduler.BukkitTask signUpdateTask;

    public static OmniArena getInstance() { return instance; }
    public boolean isStopped() { return stopped; }
    public void setStopped(boolean stopped) { this.stopped = stopped; }
    public ConfigManager getConfigManager() { return configManager; }
    public CommandManager getCommandManager() { return commandManager; }
    public ArenaManager getArenaManager() { return arenaManager; }
    public MatchManager getMatchManager() { return matchManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public KitManager getKitManager() { return kitManager; }
    public KitShareHandler getKitShareHandler() { return kitShareHandler; }
    public SignManager getSignManager() { return signManager; }
    public KitCommandExecutor getKitCommandExecutor() { return kitCommandExecutor; }
    public com.omniarena.combat.CombatManager getCombatManager() { return combatManager; }
    public VisualController getVisual() { return visual; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public IntegrationManager getIntegrationManager() { return integrationManager; }
    public BlockRestoreModule getBlockRestoreModule() { return blockRestoreModule; }
    public WebDashboardServer getWebDashboardServer() { return webDashboardServer; }
    public com.omniarena.rating.EloManager getEloManager() { return eloManager; }
    public com.omniarena.queue.QueueManager getQueueManager() { return queueManager; }
    public com.omniarena.party.PartyManager getPartyManager() { return partyManager; }
    public com.omniarena.betting.BettingManager getBettingManager() { return bettingManager; }
    public com.omniarena.tournament.TournamentManager getTournamentManager() { return tournamentManager; }
    public com.omniarena.health.Watchdog getWatchdog() { return watchdog; }

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        // ── Platform layer (Paper vs Spigot adapters) ─────────────
        try {
            com.omniarena.platform.Platform.init(this);
        } catch (RuntimeException e) {
            getLogger().severe(e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // ── Config ────────────────────────────────────────────────
        configManager = new ConfigManager(this);
        configManager.load();

        // ── Core systems ──────────────────────────────────────────
        visual              = new VisualController(this);
        statsManager        = new StatsManager(this);
        kitManager          = new KitManager(this);
        kitShareHandler     = new KitShareHandler(this);
        signManager         = new SignManager(this);
        moduleManager       = new ModuleManager(this);
        blockRestoreModule  = new BlockRestoreModule(this);
        arenaManager        = new ArenaManager(this);
        matchManager        = new MatchManager(this);
        kitCommandExecutor  = new KitCommandExecutor(this);
        combatManager       = new com.omniarena.combat.CombatManager(this);
        eloManager          = new com.omniarena.rating.EloManager(this);
        queueManager        = new com.omniarena.queue.QueueManager(this);
        partyManager        = new com.omniarena.party.PartyManager(this);
        bettingManager      = new com.omniarena.betting.BettingManager(this);
        tournamentManager   = new com.omniarena.tournament.TournamentManager(this);

        // ── Load data ─────────────────────────────────────────────
        statsManager.loadAll();
        kitManager.loadAll();
        arenaManager.loadAll();
        eloManager.loadAll();
        queueManager.start();
        partyManager.start();
        tournamentManager.start();

        // ── Integrations ─────────────────────────────────────────
        integrationManager = new IntegrationManager(this);
        integrationManager.load();

        webDashboardServer = new WebDashboardServer(this);
        webDashboardServer.start();

        // ── Commands ──────────────────────────────────────────────
        commandManager = new CommandManager(this);
        Objects.requireNonNull(getCommand("oa")).setExecutor(commandManager);
        Objects.requireNonNull(getCommand("oa")).setTabCompleter(commandManager);

        Objects.requireNonNull(getCommand("kit")).setExecutor(kitCommandExecutor);
        Objects.requireNonNull(getCommand("kit")).setTabCompleter(kitCommandExecutor);

        // Register standalone commands
        var joinCmd = new com.omniarena.commands.standalone.StandaloneJoinCommand(this);
        Objects.requireNonNull(getCommand("join")).setExecutor(joinCmd);
        Objects.requireNonNull(getCommand("join")).setTabCompleter(joinCmd);

        var leaveCmd = new com.omniarena.commands.standalone.StandaloneLeaveCommand(this);
        Objects.requireNonNull(getCommand("leave")).setExecutor(leaveCmd);
        Objects.requireNonNull(getCommand("leave")).setTabCompleter(leaveCmd);
        var partyCmd = new com.omniarena.commands.standalone.PartyCommand(this);
        Objects.requireNonNull(getCommand("party")).setExecutor(partyCmd);
        Objects.requireNonNull(getCommand("party")).setTabCompleter(partyCmd);

        // Register player command forwarders
        var forwarder = new com.omniarena.commands.standalone.PlayerCommandForwarder(this, "spectate");
        Objects.requireNonNull(getCommand("spectate")).setExecutor(forwarder);
        Objects.requireNonNull(getCommand("spectate")).setTabCompleter(forwarder);

        forwarder = new com.omniarena.commands.standalone.PlayerCommandForwarder(this, "pvp");
        Objects.requireNonNull(getCommand("pvp")).setExecutor(forwarder);
        Objects.requireNonNull(getCommand("pvp")).setTabCompleter(forwarder);

        for (String standalone : new String[]{"bet", "tournament"}) {
            var fwd = new com.omniarena.commands.standalone.PlayerCommandForwarder(this, standalone);
            var pc = getCommand(standalone);
            if (pc == null) {
                getLogger().warning("Command '/" + standalone + "' is missing from plugin.yml, so it was not registered. "
                        + "Fix: re-add the '" + standalone + ":' entry under 'commands:' in plugin.yml (use /oa " + standalone + " meanwhile).");
                continue;
            }
            pc.setExecutor(fwd);
            pc.setTabCompleter(fwd);
        }

        // ── Listeners ─────────────────────────────────────────────
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinListener(this),      this);
        pm.registerEvents(new PlayerQuitListener(this),      this);
        pm.registerEvents(new PlayerCommandPreprocessListener(this), this);
        pm.registerEvents(new PlayerDeathListener(this),     this);
        pm.registerEvents(new EntityDamageListener(this),    this);
        pm.registerEvents(new BlockBreakListener(this),      this);
        pm.registerEvents(new BlockPlaceListener(this),      this);
        pm.registerEvents(new com.omniarena.listener.RegenChangeListener(this), this);
        pm.registerEvents(new FoodLevelChangeListener(this), this);
        pm.registerEvents(new PlayerDropItemListener(this), this);
        pm.registerEvents(new PlayerInteractListener(this),  this);
        pm.registerEvents(new com.omniarena.listener.SignListener(this), this);
        pm.registerEvents(new PlayerMoveListener(this),      this);
        pm.registerEvents(new PlayerTeleportListener(this),  this);
        pm.registerEvents(new GameModeRulesListener(this),   this);
        pm.registerEvents(new com.omniarena.combat.CombatListener(this), this);
        pm.registerEvents(new com.omniarena.gui.MenuListener(this), this);
        pm.registerEvents(new WorldListener(this),           this);
        com.omniarena.platform.Platform.get().registerListeners(this); // chat (+ Paper-only elytra boost)

        // ── Sign update task ──────────────────────────────────────
        startSignUpdateTask();

        // ── Self-healing watchdog ─────────────────────────────────
        watchdog = new com.omniarena.health.Watchdog(this);
        watchdog.start();

        long elapsed = System.currentTimeMillis() - start;
        logStartupBanner(elapsed);
    }

    /** (Re)starts the periodic sign refresh; safe to call repeatedly (e.g. after /oa start). */
    public void startSignUpdateTask() {
        if (signUpdateTask != null) {
            signUpdateTask.cancel();
            signUpdateTask = null;
        }
        int signInterval = Math.max(1, configManager.getConfig().getInt("signs.update-interval", 20));
        signUpdateTask = AsyncUtil.runSyncRepeating(com.omniarena.health.ErrorGuard.wrap("signs", () -> {
            if (stopped || arenaManager == null || signManager == null) return;
            arenaManager.getAll().forEach(arena -> signManager.updateAllSigns(arena));
        }), signInterval, signInterval);
    }

    public boolean isSignTaskRunning() {
        return signUpdateTask != null && !signUpdateTask.isCancelled();
    }

    private void logStartupBanner(long elapsed) {
        String line = "==============================================";
        String version = getDescription().getVersion();
        String gameVersion = getServer().getBukkitVersion().split("-")[0] /* Paper-only getMinecraftVersion avoided */;

        org.bukkit.command.ConsoleCommandSender console = getServer().getConsoleSender();
        console.sendMessage(org.bukkit.ChatColor.DARK_BLUE.toString() + org.bukkit.ChatColor.BOLD + line);
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "   OOO   M   M  N   N  III");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "  O   O  MM MM  NN  N   I ");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "  O   O  M M M  N N N   I ");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "  O   O  M   M  N  NN   I ");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "   OOO   M   M  N   N  III");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + " ");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "   AAA   RRRR   EEEEE  N   N   AAA ");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "  A   A  R   R  E      NN  N  A   A");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "  AAAAA  RRRR   EEEE   N N N  AAAAA");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "  A   A  R  R   E      N  NN  A   A");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "  A   A  R   R  EEEEE  N   N  A   A");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + " ");
        console.sendMessage(org.bukkit.ChatColor.LIGHT_PURPLE.toString() + org.bukkit.ChatColor.BOLD + "-- GAME VERSION: " + org.bukkit.ChatColor.AQUA + gameVersion.toUpperCase(java.util.Locale.ROOT) + org.bukkit.ChatColor.LIGHT_PURPLE + " --");
        console.sendMessage(org.bukkit.ChatColor.YELLOW.toString() + org.bukkit.ChatColor.BOLD + "-- PLUGIN VERSION: " + org.bukkit.ChatColor.GREEN + version.toUpperCase(java.util.Locale.ROOT) + org.bukkit.ChatColor.YELLOW + " --");
        console.sendMessage(org.bukkit.ChatColor.AQUA.toString() + org.bukkit.ChatColor.BOLD + "-- MADE BY " + org.bukkit.ChatColor.GOLD + "JASON P" + org.bukkit.ChatColor.AQUA + " --");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "-- LOADED " + arenaManager.getAll().size() + " ARENA(S), " + kitManager.getAllAdminKits().size() + " ADMIN KIT(S) --");
        console.sendMessage(org.bukkit.ChatColor.BLUE.toString() + org.bukkit.ChatColor.BOLD + "-- ENABLED IN " + elapsed + "MS --");
        console.sendMessage(org.bukkit.ChatColor.DARK_BLUE.toString() + org.bukkit.ChatColor.BOLD + line);
    }

    private void logShutdownBanner() {
        String line = "==============================================";
        getLogger().info(line);
        getLogger().info("  OMNIARENA SHUTDOWN");
        getLogger().info("  All active match sessions terminated.");
        getLogger().info("  All player stats and arena configurations saved.");
        getLogger().info("  OmniArena disabled cleanly.");
        getLogger().info(line);
    }

    @Override
    public void onDisable() {
        stopped = true;
        if (watchdog != null) watchdog.stop();
        if (signUpdateTask != null) { signUpdateTask.cancel(); signUpdateTask = null; }

        // Close every open plugin GUI so no viewer is left with a dead menu
        try { com.omniarena.kit.KitGUI.closeAll(); } catch (Throwable t) { getLogger().warning("Kit GUI close failed: " + t.getMessage()); }
        try { com.omniarena.settings.SettingsGUI.closeAll(); } catch (Throwable t) { getLogger().warning("Settings GUI close failed: " + t.getMessage()); }
        try { com.omniarena.gui.MenuListener.closeAll(); } catch (Throwable t) { getLogger().warning("Menu close failed: " + t.getMessage()); }

        // End all active matches cleanly first so their results are included in the saves below
        try {
            if (matchManager != null) {
                new java.util.ArrayList<>(matchManager.getActiveMatches()).forEach(match ->
                    new java.util.ArrayList<>(match.getPlayers()).forEach(uuid -> {
                        org.bukkit.entity.Player player = getServer().getPlayer(uuid);
                        if (player != null) {
                            try { matchManager.leaveArena(player); }
                            catch (Exception e) { getLogger().warning("Failed to remove " + player.getName() + " on disable: " + e.getMessage()); }
                        }
                    }));
            }
        } catch (Exception e) {
            getLogger().severe("Error ending matches on disable (" + e + "). Players may keep match items/positions; they are restored from plugins/OmniArena/player-state on next join.");
        }

        try { if (matchManager != null) matchManager.shutdownStateStore(); } catch (Exception e) { getLogger().warning("Player state flush failed (" + e + "). Fix: check the plugins/OmniArena folder is writable and the disk is not full, then restart the server cleanly so data is saved."); }

        // Finalize running regens/snapshots so no preview blocks are left in the world
        try { if (blockRestoreModule != null) blockRestoreModule.cancelAll(); } catch (Exception e) { getLogger().warning("Regen shutdown failed: " + e.getMessage()); }

        // Stop periodic systems
        try { if (combatManager != null) combatManager.shutdown(); } catch (Exception e) { getLogger().warning("Combat shutdown failed: " + e.getMessage()); }
        try { if (queueManager != null) queueManager.stop(); } catch (Exception e) { getLogger().warning("Queue stop failed: " + e.getMessage()); }
        try { if (partyManager != null) partyManager.stop(); } catch (Exception e) { getLogger().warning("Party stop failed: " + e.getMessage()); }

        // Save all data (each guarded so one failure does not skip the rest)
        try { if (tournamentManager != null) tournamentManager.stop(); } catch (Exception e) { getLogger().severe("Tournament save failed (" + e + "). Fix: check the plugins/OmniArena folder is writable and the disk is not full, then restart the server cleanly so data is saved."); }
        try { if (bettingManager != null) bettingManager.refundAll(); } catch (Exception e) { getLogger().severe("Bet refund failed (" + e + "). Fix: check the plugins/OmniArena folder is writable and the disk is not full, then restart the server cleanly so data is saved."); }
        try { if (statsManager != null) statsManager.saveAll(); } catch (Exception e) { getLogger().severe("Stats save failed (" + e + "). Fix: check the plugins/OmniArena folder is writable and the disk is not full, then restart the server cleanly so data is saved."); }
        try { if (arenaManager != null) arenaManager.saveAll(); } catch (Exception e) { getLogger().severe("Arena save failed (" + e + "). Fix: check the plugins/OmniArena folder is writable and the disk is not full, then restart the server cleanly so data is saved."); }
        try { if (eloManager != null) { eloManager.shutdown(); eloManager.saveAll(); } } catch (Exception e) { getLogger().severe("Elo save failed (" + e + "). Fix: check the plugins/OmniArena folder is writable and the disk is not full, then restart the server cleanly so data is saved."); }

        // Shutdown visual systems
        try { if (visual != null) visual.shutdown(); } catch (Exception e) { getLogger().warning("Visual shutdown failed: " + e.getMessage()); }
        try { if (moduleManager != null) moduleManager.shutdown(); } catch (Exception e) { getLogger().warning("Module shutdown failed: " + e.getMessage()); }
        try { if (webDashboardServer != null) webDashboardServer.stop(); } catch (Exception e) { getLogger().warning("Web stop failed: " + e.getMessage()); }
        try { if (integrationManager != null) integrationManager.shutdown(); } catch (Exception e) { getLogger().warning("Integration shutdown failed: " + e.getMessage()); }

        try { com.omniarena.platform.Platform.shutdown(); } catch (Throwable t) { getLogger().warning("Platform shutdown failed: " + t.getMessage()); }

        logShutdownBanner();
    }
}
