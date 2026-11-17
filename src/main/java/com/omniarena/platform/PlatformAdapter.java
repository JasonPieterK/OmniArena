package com.omniarena.platform;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
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

/**
 * Everything that differs between Paper-family servers (native Adventure) and Spigot/Bukkit servers
 * (shaded Adventure + adventure-platform-bukkit, legacy string APIs). Common code only talks to this interface;
 * exactly one implementation ({@code com.omniarena.platform.PlatformImpl}) is compiled into each jar, from
 * {@code src/paper/java} or {@code src/spigot/java}.
 */
public interface PlatformAdapter {

    /** "paper" or "spigot" - the flavor this jar was compiled for. */
    String flavor();

    void init(Plugin plugin);

    void shutdown();

    /** Registers listeners that depend on platform-only events (chat, elytra boost...). */
    void registerListeners(Plugin plugin);

    // ── text delivery ────────────────────────────────────────────────
    void send(CommandSender sender, Component message);

    void broadcast(Component message);

    void actionBar(Player player, Component message);

    void title(Player player, Title title);

    void showBossBar(Player player, BossBar bar);

    void hideBossBar(Player player, BossBar bar);

    // ── items / inventories / signs / displays ───────────────────────
    void setDisplayName(ItemMeta meta, Component name);

    /** Never null for items without a custom name: returns null in that case. */
    Component getDisplayName(ItemMeta meta);

    void setLore(ItemMeta meta, List<Component> lore);

    /** Null when the item has no lore. */
    List<Component> getLore(ItemMeta meta);

    Inventory createInventory(InventoryHolder holder, int size, Component title);

    void setSignLine(SignSide side, int index, Component line);

    Component getSignLine(SignSide side, int index);

    Component getSignLine(SignChangeEvent event, int index);

    void setSignLine(SignChangeEvent event, int index, Component line);

    void setText(TextDisplay display, Component text);

    // ── server / world ───────────────────────────────────────────────
    CommandMap commandMap();

    /** Paper: async chunk load. Spigot: synchronous load wrapped in a completed future. */
    CompletableFuture<?> loadChunkAsync(World world, int x, int z);

    void addChunkTicket(World world, int x, int z, Plugin plugin);

    void removeChunkTicket(World world, int x, int z, Plugin plugin);
}
