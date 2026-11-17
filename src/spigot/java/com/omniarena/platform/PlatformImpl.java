package com.omniarena.platform;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Spigot / Bukkit implementation. Adventure is shaded (relocated to com.omniarena.libs.kyori) and delivered via
 * adventure-platform-bukkit; Components stored in item/sign/inventory APIs are flattened to legacy section strings.
 * Compiled from src/spigot/java. Also runs fine on Paper, just without the Paper-only fast paths.
 */
public final class PlatformImpl implements PlatformAdapter {

    /** Section-sign serializer with hex support (1.16+ clients). */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private BukkitAudiences audiences;
    private volatile CommandMap commandMap;

    @Override public String flavor() { return "spigot"; }

    @Override public void init(Plugin plugin) { audiences = BukkitAudiences.create(plugin); }

    @Override public void shutdown() {
        if (audiences != null) { audiences.close(); audiences = null; }
    }

    @Override
    public void registerListeners(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new SpigotChatListener(), plugin);
    }

    // ── text delivery ────────────────────────────────────────────────
    @Override public void send(CommandSender sender, Component message) { audiences.sender(sender).sendMessage(message); }
    @Override public void broadcast(Component message) { audiences.all().sendMessage(message); }
    @Override public void actionBar(Player player, Component message) { audiences.player(player).sendActionBar(message); }
    @Override public void title(Player player, Title title) { audiences.player(player).showTitle(title); }
    @Override public void showBossBar(Player player, BossBar bar) { audiences.player(player).showBossBar(bar); }
    @Override public void hideBossBar(Player player, BossBar bar) { audiences.player(player).hideBossBar(bar); }

    // ── legacy string bridges ────────────────────────────────────────
    /** Legacy strings cannot express "italic off"; a leading reset removes the client's default italics on item text. */
    private static String legacy(Component c) { return "§r" + LEGACY.serialize(c); }

    private static Component fromLegacy(String s) {
        if (s == null) return null;
        return LEGACY.deserialize(s.startsWith("§r") ? s.substring(2) : s);
    }

    @Override public void setDisplayName(ItemMeta meta, Component name) { meta.setDisplayName(legacy(name)); }
    @Override public Component getDisplayName(ItemMeta meta) { return meta.hasDisplayName() ? fromLegacy(meta.getDisplayName()) : null; }

    @Override public void setLore(ItemMeta meta, List<Component> lore) {
        List<String> out = new ArrayList<>(lore.size());
        for (Component c : lore) out.add(legacy(c));
        meta.setLore(out);
    }

    @Override public List<Component> getLore(ItemMeta meta) {
        List<String> lore = meta.getLore();
        if (lore == null) return null;
        List<Component> out = new ArrayList<>(lore.size());
        for (String s : lore) out.add(fromLegacy(s));
        return out;
    }

    @Override public Inventory createInventory(InventoryHolder holder, int size, Component title) {
        return Bukkit.createInventory(holder, size, LEGACY.serialize(title));
    }

    @Override public void setSignLine(SignSide side, int index, Component line) { side.setLine(index, LEGACY.serialize(line)); }
    @Override public Component getSignLine(SignSide side, int index) { return LEGACY.deserialize(side.getLine(index)); }
    @Override public Component getSignLine(SignChangeEvent event, int index) {
        String s = event.getLine(index);
        return LEGACY.deserialize(s == null ? "" : s);
    }
    @Override public void setSignLine(SignChangeEvent event, int index, Component line) { event.setLine(index, LEGACY.serialize(line)); }
    @Override public void setText(TextDisplay display, Component text) { display.setText(LEGACY.serialize(text)); }

    // ── server / world ───────────────────────────────────────────────
    /** Players kicked and not yet quit (filled by SpigotChatListener's PlayerKickEvent hook). */
    static final java.util.Set<java.util.UUID> KICKED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override public boolean wasKicked(org.bukkit.event.player.PlayerQuitEvent event) {
        return KICKED.remove(event.getPlayer().getUniqueId());
    }

    @Override public byte[] serializeItems(org.bukkit.inventory.ItemStack[] items) {
        try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
             org.bukkit.util.io.BukkitObjectOutputStream out = new org.bukkit.util.io.BukkitObjectOutputStream(bos)) {
            out.writeInt(items.length);
            for (org.bukkit.inventory.ItemStack it : items) out.writeObject(it);
            out.flush();
            return bos.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot serialize items: " + e, e);
        }
    }

    @Override public org.bukkit.inventory.ItemStack[] deserializeItems(byte[] data) {
        try (org.bukkit.util.io.BukkitObjectInputStream in = new org.bukkit.util.io.BukkitObjectInputStream(new java.io.ByteArrayInputStream(data))) {
            org.bukkit.inventory.ItemStack[] items = new org.bukkit.inventory.ItemStack[in.readInt()];
            for (int i = 0; i < items.length; i++) items[i] = (org.bukkit.inventory.ItemStack) in.readObject();
            return items;
        } catch (java.io.IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Cannot deserialize items: " + e, e);
        }
    }

    @Override public org.bukkit.block.BlockState getBlockState(org.bukkit.block.Block b) { return b.getState(); }

    @Override public boolean isServerStopping() { return false; }

    @Override
    public CommandMap commandMap() {
        CommandMap m = commandMap;
        if (m == null) {
            try {
                m = (CommandMap) Bukkit.getServer().getClass().getMethod("getCommandMap").invoke(Bukkit.getServer());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot access the server command map: " + e, e);
            }
            commandMap = m;
        }
        return m;
    }

    @Override
    public CompletableFuture<?> loadChunkAsync(World world, int x, int z) {
        return CompletableFuture.completedFuture(world.getChunkAt(x, z)); // no async chunk API on Spigot
    }

    @Override public void addChunkTicket(World world, int x, int z, Plugin plugin) { world.addPluginChunkTicket(x, z, plugin); }
    @Override public void removeChunkTicket(World world, int x, int z, Plugin plugin) { world.removePluginChunkTicket(x, z, plugin); }
}
