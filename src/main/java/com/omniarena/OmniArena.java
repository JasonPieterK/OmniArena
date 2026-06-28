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

    public static OmniArena getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
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

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

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

        // ── Load data ─────────────────────────────────────────────
        statsManager.loadAll();
        kitManager.loadAll();
        arenaManager.loadAll();
        eloManager.loadAll();
        queueManager.start();
        partyManager.start();

        // ── Integrations ─────────────────────────────────────────
        integrationManager = new IntegrationManager(this);
        integrationManager.load();

        webDashboardServer = new WebDashboardServer(this);
        webDashboardServer.start();

        // ── Commands ──────────────────────────────────────────────
        CommandManager commandManager = new CommandManager(this);
        Objects.requireNonNull(getCommand("oa")).setExecutor(commandManager);
        Objects.requireNonNull(getCommand("oa")).setTabCompleter(commandManager);

        Objects.requireNonNull(getCommand("kit")).setExecutor(kitCommandExecutor);
        Objects.requireNonNull(getCommand("kit")).setTabCompleter(kitCommandExecutor);

        // Register standalone commands
        Objects.requireNonNull(getCommand("join")).setExecutor(new com.omniarena.commands.standalone.StandaloneJoinCommand(this));
        Objects.requireNonNull(getCommand("leave")).setExecutor(new com.omniarena.commands.standalone.StandaloneLeaveCommand(this));
        Objects.requireNonNull(getCommand("party")).setExecutor(new com.omniarena.commands.standalone.PartyCommand(this));
        Objects.requireNonNull(getCommand("party")).setTabCompleter(new com.omniarena.commands.standalone.PartyCommand(this));

        // ── Listeners ─────────────────────────────────────────────
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerJoinListener(this),      this);
        pm.registerEvents(new PlayerQuitListener(this),      this);
        pm.registerEvents(new PlayerCommandPreprocessListener(this), this);
        pm.registerEvents(new PlayerDeathListener(this),     this);
        pm.registerEvents(new EntityDamageListener(this),    this);
        pm.registerEvents(new BlockBreakListener(this),      this);
        pm.registerEvents(new BlockPlaceListener(this),      this);
        pm.registerEvents(new FoodLevelChangeListener(this), this);
        pm.registerEvents(new PlayerInteractListener(this),  this);
        pm.registerEvents(new PlayerMoveListener(this),      this);
        pm.registerEvents(new GameModeRulesListener(this),   this);
        pm.registerEvents(new com.omniarena.combat.CombatListener(this), this);
        pm.registerEvents(new com.omniarena.settings.SettingsMenuListener(this), this);
        pm.registerEvents(new ChatListener(this),            this);
        pm.registerEvents(new InventoryClickListener(this),  this);

        // ── Sign update task ──────────────────────────────────────
        int signInterval = configManager.getConfig().getInt("signs.update-interval", 20);
        AsyncUtil.runSyncRepeating(() ->
            arenaManager.getAll().forEach(arena ->
                signManager.updateAllSigns(arena)), signInterval, signInterval);

        long elapsed = System.currentTimeMillis() - start;
        logStartupBanner(elapsed);
    }

    private void logStartupBanner(long elapsed) {
        String line = "==============================================";
        String version = getDescription().getVersion();
        String gameVersion = getServer().getMinecraftVersion();

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
        // Save all data
        if (statsManager != null) statsManager.saveAll();
        if (arenaManager != null) arenaManager.saveAll();
        if (bettingManager != null) bettingManager.refundAll();

        // End all active matches cleanly
        if (matchManager != null) {
            matchManager.getActiveMatches().forEach(match -> {
                new java.util.ArrayList<>(match.getPlayers()).forEach(uuid -> {
                    org.bukkit.entity.Player player = getServer().getPlayer(uuid);
                    if (player != null) matchManager.leaveArena(player);
                });
            });
        }

        // Shutdown visual systems
        if (visual != null) visual.shutdown();
        if (moduleManager != null) moduleManager.shutdown();
        if (webDashboardServer != null) webDashboardServer.stop();

        logShutdownBanner();
    }
}
