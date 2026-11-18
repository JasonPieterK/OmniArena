package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaState;
import com.omniarena.arena.regen.BlockRestoreModule;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;
import java.util.Optional;

/**
 * Feeds the TIME_REWIND regen style: records, per RUNNING arena, the order in which blocks inside
 * the arena region were changed. Never cancels or modifies events.
 */
public class RegenChangeListener implements Listener {

    private final OmniArena plugin;

    public RegenChangeListener(OmniArena plugin) { this.plugin = plugin; }

    private void record(Block b) {
        if (plugin.isStopped()) return;
        BlockRestoreModule m = plugin.getBlockRestoreModule();
        if (m == null) return;
        Location l = b.getLocation();
        Optional<Arena> a = ArenaManager.getInstance().getArenaAt(l);
        if (a.isEmpty() || a.get().getState() != ArenaState.RUNNING) return;
        m.recordChange(a.get().getName(), b.getX(), b.getY(), b.getZ());
    }

    private void recordAll(List<Block> blocks) {
        if (blocks.isEmpty()) return;
        // blocks of one explosion are almost always in the same arena: resolve once, verify by region
        Arena arena = null;
        BlockRestoreModule m = plugin.getBlockRestoreModule();
        if (m == null || plugin.isStopped()) return;
        for (Block b : blocks) {
            Location l = b.getLocation();
            if (arena == null || !arena.getRegion().contains(l)) {
                Optional<Arena> a = ArenaManager.getInstance().getArenaAt(l);
                arena = a.orElse(null);
            }
            if (arena == null || arena.getState() != ArenaState.RUNNING) continue;
            m.recordChange(arena.getName(), b.getX(), b.getY(), b.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { record(e.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { record(e.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) { recordAll(e.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { recordAll(e.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFluid(BlockFromToEvent e) { record(e.getToBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { record(e.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) { record(e.getBlock()); }

    /** Includes BlockSpreadEvent (a subclass). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) { record(e.getBlock()); }
}
