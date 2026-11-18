package com.omniarena.platform;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.sign.SignSide;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Paper / Purpur implementation: Adventure is native, async chunk APIs available. Compiled from src/paper/java. */
public final class PlatformImpl implements PlatformAdapter {

    @Override public String flavor() { return "paper"; }

    @Override public void init(Plugin plugin) {}

    @Override public void shutdown() {}

    @Override
    public void registerListeners(Plugin plugin) {
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(new PaperChatListener(), plugin);
        pm.registerEvents(new PaperCombatListener(), plugin);
    }

    @Override public void send(CommandSender sender, Component message) { sender.sendMessage(message); }
    @Override public void broadcast(Component message) { Bukkit.broadcast(message); }
    @Override public void actionBar(Player player, Component message) { player.sendActionBar(message); }
    @Override public void title(Player player, Title title) { player.showTitle(title); }
    @Override public void showBossBar(Player player, BossBar bar) { player.showBossBar(bar); }
    @Override public void hideBossBar(Player player, BossBar bar) { player.hideBossBar(bar); }

    @Override public void setDisplayName(ItemMeta meta, Component name) { meta.displayName(name); }
    @Override public Component getDisplayName(ItemMeta meta) { return meta.displayName(); }
    @Override public void setLore(ItemMeta meta, List<Component> lore) { meta.lore(lore); }
    @Override public List<Component> getLore(ItemMeta meta) { return meta.lore(); }
    @Override public Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return Bukkit.createInventory(holder, size, title);
    }
    @Override public void setSignLine(SignSide side, int index, Component line) { side.line(index, line); }
    @Override public Component getSignLine(SignSide side, int index) { return side.line(index); }
    @Override public Component getSignLine(SignChangeEvent event, int index) { return event.line(index); }
    @Override public void setSignLine(SignChangeEvent event, int index, Component line) { event.line(index, line); }
    @Override public void setText(TextDisplay display, Component text) { display.text(text); }

    @Override public boolean wasKicked(org.bukkit.event.player.PlayerQuitEvent event) {
        return event.getReason() == org.bukkit.event.player.PlayerQuitEvent.QuitReason.KICKED;
    }

    @Override public byte[] serializeItems(org.bukkit.inventory.ItemStack[] items) { return org.bukkit.inventory.ItemStack.serializeItemsAsBytes(items); }
    @Override public org.bukkit.inventory.ItemStack[] deserializeItems(byte[] data) { return org.bukkit.inventory.ItemStack.deserializeItemsFromBytes(data); }

    @Override public org.bukkit.block.BlockState getBlockState(org.bukkit.block.Block b) { return b.getState(false); }

    @Override public boolean isServerStopping() { return Bukkit.isStopping(); }

    @Override public CommandMap commandMap() { return Bukkit.getCommandMap(); }

    @Override
    public CompletableFuture<?> loadChunkAsync(World world, int x, int z) {
        return world.getChunkAtAsync(x, z, true);
    }

    @Override public void addChunkTicket(World world, int x, int z, Plugin plugin) { world.addPluginChunkTicket(x, z, plugin); }
    @Override public void removeChunkTicket(World world, int x, int z, Plugin plugin) { world.removePluginChunkTicket(x, z, plugin); }
}
