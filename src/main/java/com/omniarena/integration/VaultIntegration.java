package com.omniarena.integration;

import com.omniarena.OmniArena;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultIntegration {

    private final OmniArena plugin;
    private Economy economy;

    public VaultIntegration(OmniArena plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public void deposit(Player player, double amount) {
        if (economy == null || amount <= 0) return;
        economy.depositPlayer(player, amount);
    }

    public void withdraw(Player player, double amount) {
        if (economy == null || amount <= 0) return;
        economy.withdrawPlayer(player, amount);
    }

    public double getBalance(Player player) {
        return economy != null ? economy.getBalance(player) : 0;
    }

    public boolean has(Player player, double amount) {
        return economy != null && economy.has(player, amount);
    }

    public String format(double amount) {
        return economy != null ? economy.format(amount) : String.format("%.2f", amount);
    }
}
