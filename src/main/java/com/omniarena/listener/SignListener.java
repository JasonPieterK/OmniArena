package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.sign.SignManager;
import com.omniarena.util.ColorUtil;
import com.omniarena.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Arena sign lifecycle: creation by header, protected removal, explosion/piston protection, chunk-load refresh. */
public class SignListener implements Listener {

    private final OmniArena plugin;

    public SignListener(OmniArena plugin) { this.plugin = plugin; }

    private static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c).trim();
    }

    private boolean isRegistered(Block b) {
        SignManager sm = SignManager.getInstance();
        return sm != null && sm.isArenaSign(b);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (plugin.isStopped()) return;
        String header = plain(event.line(0));
        if (!header.equalsIgnoreCase("[OmniArena]") && !header.equalsIgnoreCase("[oa]")) return;

        Player player = event.getPlayer();
        if (!PermissionChecker.hasAdminSign(player)) {
            event.setCancelled(true);
            MessageUtil.send(player, "<red>You don't have permission to create arena signs. <gray>Ask an admin for the <white>omniarena.admin.sign</white> permission.");
            return;
        }

        String name = plain(event.line(1));
        Optional<Arena> arenaOpt = name.isEmpty() ? Optional.empty() : ArenaManager.getInstance().get(name);
        if (arenaOpt.isEmpty()) {
            String known = ArenaManager.getInstance().getAll().stream().map(Arena::getName).sorted().collect(Collectors.joining(", "));
            MessageUtil.send(player, "<red>No arena named <yellow>" + (name.isEmpty() ? "(empty)" : name)
                    + "</yellow>. <gray>Write the arena name on line 2 (existing arenas: <white>" + (known.isEmpty() ? "none yet" : known)
                    + "</white>), or place a blank sign and run <white>/oa sign create <arena></white>.");
            event.line(0, ColorUtil.parse("<dark_red>[OmniArena]"));
            event.line(2, ColorUtil.parse("<red>Unknown arena"));
            return;
        }

        Arena arena = arenaOpt.get();
        Block block = event.getBlock();
        // Block state is not updated until the event finishes: register next tick
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            SignManager sm = SignManager.getInstance();
            if (sm != null && !plugin.isStopped() && sm.createSign(arena, block)) {
                MessageUtil.send(player, ConfigManager.getInstance().getMessage("sign-created").replace("{arena}", arena.getName()));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.isStopped()) return;
        Block block = event.getBlock();
        SignManager sm = SignManager.getInstance();
        if (sm == null || !sm.isArenaSign(block)) return;
        Player player = event.getPlayer();
        if (!PermissionChecker.hasAdminSign(player)) {
            event.setCancelled(true);
            MessageUtil.send(player, "<red>This is an arena sign and only admins can break it.");
            return;
        }
        sm.removeSign(block);
        MessageUtil.send(player, ConfigManager.getInstance().getMessage("sign-removed"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (plugin.isStopped()) return;
        event.blockList().removeIf(this::isRegistered);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (plugin.isStopped()) return;
        event.blockList().removeIf(this::isRegistered);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (plugin.isStopped()) return;
        if (event.getBlocks().stream().anyMatch(this::isRegistered)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (plugin.isStopped()) return;
        if (event.getBlocks().stream().anyMatch(this::isRegistered)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (plugin.isStopped()) return;
        SignManager sm = SignManager.getInstance();
        if (sm == null) return;
        var chunk = event.getChunk();
        // Defer a tick: block entities are not safe to touch mid-load
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isStopped() && chunk.isLoaded()) sm.updateChunk(chunk);
        });
    }
}
