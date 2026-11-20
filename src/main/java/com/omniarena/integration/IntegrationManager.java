package com.omniarena.integration;

import com.omniarena.OmniArena;
import org.bukkit.Bukkit;

public class IntegrationManager {

    private VaultIntegration vault;
    private PlaceholderAPIIntegration placeholderAPI;
    private WorldGuardIntegration worldGuard;
    private LuckPermsIntegration luckPerms;

    private final OmniArena plugin;

    public IntegrationManager(OmniArena plugin) {
        this.plugin = plugin;
    }

    public VaultIntegration getVault() { return vault; }
    public PlaceholderAPIIntegration getPlaceholderAPI() { return placeholderAPI; }
    public WorldGuardIntegration getWorldGuard() { return worldGuard; }
    public LuckPermsIntegration getLuckPerms() { return luckPerms; }

    public void load() {
        if (enabled("vault") && isPresent("Vault")) {
            vault = new VaultIntegration(plugin);
            if (vault.setup()) plugin.getLogger().info("[Integrations] Vault hooked.");
            else { vault = null; plugin.getLogger().warning("[Integrations] Vault is installed but no economy provider is registered, so betting/rewards are disabled. Fix: install an economy plugin that supports Vault (e.g. EssentialsX) and restart."); }
        }

        if (enabled("placeholderapi") && isPresent("PlaceholderAPI")) {
            placeholderAPI = new PlaceholderAPIIntegration(plugin);
            placeholderAPI.tryRegister();
            plugin.getLogger().info("[Integrations] PlaceholderAPI hooked.");
        }

        if (enabled("worldguard") && isPresent("WorldGuard")) {
            worldGuard = new WorldGuardIntegration();
            plugin.getLogger().info("[Integrations] WorldGuard hooked.");
        }

        if (enabled("luckperms") && isPresent("LuckPerms")) {
            luckPerms = new LuckPermsIntegration();
            plugin.getLogger().info("[Integrations] LuckPerms hooked.");
        }
    }

    /** Releases all hooks. Call from OmniArena#onDisable (and before re-running load() on reload). */
    public void shutdown() {
        if (placeholderAPI != null) {
            placeholderAPI.unregisterExpansion();
            placeholderAPI = null;
        }
        vault = null;
        worldGuard = null;
        luckPerms = null;
    }

    public boolean hasVault()          { return vault != null; }
    public boolean hasPlaceholderAPI() { return placeholderAPI != null; }
    public boolean hasWorldGuard()     { return worldGuard != null; }
    public boolean hasLuckPerms()      { return luckPerms != null; }

    // TEMPORARY (self-healing branch): minimal status list for /oa doctor; superseded by the integration rework.
    public java.util.List<IntegrationStatus> statuses() {
        return java.util.List.of(
                new IntegrationStatus("Vault", isPresent("Vault"), vault != null, null),
                new IntegrationStatus("PlaceholderAPI", isPresent("PlaceholderAPI"), placeholderAPI != null, null),
                new IntegrationStatus("WorldGuard", isPresent("WorldGuard"), worldGuard != null, null),
                new IntegrationStatus("LuckPerms", isPresent("LuckPerms"), luckPerms != null, null));
    }

    /** Honours the integrations.<name> toggle in config.yml (default true). */
    private boolean enabled(String key) {
        var cm = com.omniarena.config.ConfigManager.getInstance();
        return cm == null || cm.isIntegrationEnabled(key);
    }

    private boolean isPresent(String pluginName) {
        return Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }
}
