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
        if (isPresent("Vault")) {
            vault = new VaultIntegration(plugin);
            if (vault.setup()) plugin.getLogger().info("[Integrations] Vault hooked.");
            else { vault = null; plugin.getLogger().warning("[Integrations] Vault found but economy provider missing."); }
        }

        if (isPresent("PlaceholderAPI")) {
            placeholderAPI = new PlaceholderAPIIntegration(plugin);
            placeholderAPI.tryRegister();
            plugin.getLogger().info("[Integrations] PlaceholderAPI hooked.");
        }

        if (isPresent("WorldGuard")) {
            worldGuard = new WorldGuardIntegration();
            plugin.getLogger().info("[Integrations] WorldGuard hooked.");
        }

        if (isPresent("LuckPerms")) {
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

    private boolean isPresent(String pluginName) {
        return Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }
}
