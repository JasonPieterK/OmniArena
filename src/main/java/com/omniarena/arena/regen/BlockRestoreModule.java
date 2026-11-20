package com.omniarena.arena.regen;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaRegion;
import com.omniarena.config.ConfigManager;
import com.omniarena.util.AsyncUtil;
import com.omniarena.util.YamlUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Block-based regen animations: each type places temporary "preview" blocks
 * one tick before the real restore, creating visible animated patterns.
 *
 * Flow per tick:
 *   1. Finalize previous batch: remove preview blocks, place real blocks.
 *   2. Build next batch: place preview blocks at their positions.
 *
 * This makes animations using actual Minecraft blocks — columns falling from
 * the sky, glass lasers, crystal waves, vortex pillars, etc.
 */
public class BlockRestoreModule {

    private static final int DEFAULT_BATCH_SIZE = 4000;
    // Animated styles place at most this many blocks per tick (smooth animation)
    private static final int ANIM_BATCH_SIZE = 1200;
    // Block comparisons per tick while looking for blocks that differ from the snapshot
    private static final int SCAN_PER_TICK = 40000;
    // Vertical runs are split into pieces this long so animations keep block-level granularity
    private static final int MAX_PIECE_LEN = 8;

    private static final Random RNG = new Random();

    // Blocks whose tile-entity contents are snapshotted/restored (container items, sign text)
    private static final Set<Material> CONTAINER_MATS = EnumSet.noneOf(Material.class);
    static {
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock()) continue;
            String n = m.name();
            if (n.endsWith("SHULKER_BOX") || n.equals("CHEST") || n.equals("TRAPPED_CHEST") || n.equals("BARREL")
                    || n.equals("HOPPER") || n.equals("DISPENSER") || n.equals("DROPPER") || n.equals("FURNACE")
                    || n.equals("BLAST_FURNACE") || n.equals("SMOKER") || n.equals("BREWING_STAND")) {
                CONTAINER_MATS.add(m);
            }
        }
    }

    private static boolean isTileMaterial(Material m) {
        return CONTAINER_MATS.contains(m) || Tag.ALL_SIGNS.isTagged(m) || Tag.BANNERS.isTagged(m);
    }

    // Cap on concurrent decorative falling-block entities, so a big arena
    // regen never floods the server with thousands of entities at once.
    private static final int MAX_DESCENDERS = 90;

    private final OmniArena plugin;
    private final File snapshotsDir;
    private final java.util.concurrent.atomic.AtomicInteger liveDescenders =
            new java.util.concurrent.atomic.AtomicInteger(0);
    // Counter used to throttle how many blocks in a batch spawn a descender.
    private int descenderTick = 0;

    // Active regens per arena (lowercase name) -> finalizer that cancels and completes instantly.
    private final Map<String, Runnable> activeRegens = new java.util.concurrent.ConcurrentHashMap<>();
    // Callbacks of restore() calls made while the same arena was already restoring.
    private final Map<String, List<Waiter>> queuedCallbacks = new java.util.concurrent.ConcurrentHashMap<>();
    // Arenas currently being snapshotted (lowercase name)
    private final Set<String> snapshotting = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Human-readable reason of the last failed restore/snapshot per arena
    private final Map<String, String> lastErrors = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<BukkitTask> snapshotTasks = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Chunk-ticket releasers of running snapshots (arena key -> idempotent release), run by cancelAll
    private final Map<String, Runnable> snapshotReleasers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<FallingBlock> descenderEntities = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean shuttingDown = false;
    // Columns*height scanned per tick while snapshotting
    private static final int SNAPSHOT_BLOCKS_PER_TICK = 30000;

    public BlockRestoreModule(OmniArena plugin) {
        this.plugin = plugin;
        this.snapshotsDir = new File(plugin.getDataFolder(), "snapshots");
        if (!snapshotsDir.exists()) snapshotsDir.mkdirs();
    }

    public boolean hasSnapshot(String arenaName) {
        return snapshotFile(arenaName).exists();
    }

    /** True while a restore for this arena is running (including chunk preloading). */
    public boolean isRegenerating(String arenaName) {
        return activeRegens.containsKey(arenaName.toLowerCase());
    }

    /** Reason of the last failed restore/snapshot of this arena, or null. Includes a fix instruction. */
    public String getLastError(String arenaName) {
        return lastErrors.get(arenaName.toLowerCase());
    }

    /** messages.yml entry as plain text (tags stripped) for console/last-error use. */
    private String plainMsg(String key, String... kv) {
        String m = ConfigManager.getInstance().getRawMessage(key);
        for (int i = 0; i + 1 < kv.length; i += 2) m = m.replace("{" + kv[i] + "}", kv[i + 1]);
        return m.replaceAll("<[^>]*>", "");
    }

    public void createSnapshot(Arena arena, Runnable onComplete) {
        createSnapshot(arena, onComplete, null);
    }

    /** Snapshots the arena region over several ticks. onFail receives a human-readable reason. */
    public void createSnapshot(Arena arena, Runnable onComplete, Consumer<String> onFail) {
        final String arenaName = arena.getName();
        final String key = arenaName.toLowerCase();
        ArenaRegion region = arena.getRegion();
        if (!region.isDefined()) { snapFail(key, onFail, plainMsg("arena-no-region", "arena", arenaName)); return; }
        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) {
            snapFail(key, onFail, plainMsg("world-not-found", "world", region.getWorldName()));
            return;
        }
        if (activeRegens.containsKey(key)) {
            snapFail(key, onFail, "Arena " + arenaName + " is being regenerated right now; wait for it to finish.");
            return;
        }
        if (!snapshotting.add(key)) {
            snapFail(key, onFail, "A snapshot of arena " + arenaName + " is already running.");
            return;
        }
        shuttingDown = false;
        final int minX = region.getMinX(), maxX = region.getMaxX();
        // Clamp to the world's build height (e.g. -64..319) so out-of-range Y never produces bogus runs
        final int minY = Math.max(region.getMinY(), world.getMinHeight());
        final int maxY = Math.min(region.getMaxY(), world.getMaxHeight() - 1);
        final int minZ = region.getMinZ(), maxZ = region.getMaxZ();
        if (minY > maxY || minX > maxX || minZ > maxZ) {
            snapshotting.remove(key);
            snapFail(key, onFail, "Region of arena " + arenaName + " is empty or outside the world height limits.");
            return;
        }
        final int height = maxY - minY + 1;
        ConfigManager.debugLog(() -> "regen: snapshot of arena " + arenaName + " started (" + (maxX - minX + 1) + "x" + height + "x" + (maxZ - minZ + 1)
                + " = " + (long) (maxX - minX + 1) * height * (maxZ - minZ + 1) + " blocks)");
        final int columnsPerTick = Math.max(1, SNAPSHOT_BLOCKS_PER_TICK / Math.max(1, height));
        final String worldName = region.getWorldName();

        // Palette + vertical run-length encoding: identical consecutive blocks in a column share one run.
        final Map<String, Integer> paletteIndex = new HashMap<>();
        final List<String> palette = new ArrayList<>();
        final List<Boolean> tileFlags = new ArrayList<>();
        final Map<BlockData, Integer> bdCache = new HashMap<>();
        final List<int[]> runs = new ArrayList<>(); // x, z, y0, len, paletteIdx
        final List<String> tiles = new ArrayList<>(); // "x,y,z|C|b64" / "x,y,z|S|b64|b64"
        final int[] cursor = {minX, minZ};
        final BukkitTask[] holder = new BukkitTask[1];
        // Chunks of the region are loaded async and pinned with plugin tickets for the duration of the scan.
        final List<long[]> chunkCoords = new ArrayList<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) chunkCoords.add(new long[]{cx, cz});
        final java.util.concurrent.atomic.AtomicBoolean ticketed = new java.util.concurrent.atomic.AtomicBoolean(false);
        final Runnable release = () -> {
            if (!ticketed.compareAndSet(true, false)) return;
            for (long[] c : chunkCoords) {
                try { com.omniarena.platform.Platform.get().removeChunkTicket(world, (int) c[0], (int) c[1], plugin); } catch (Throwable ignored) {}
            }
        };

        snapshotReleasers.put(key, release);
        Runnable step = () -> {
            try {
                if (shuttingDown || Bukkit.getWorld(worldName) != world) {
                    abortSnapshot(holder[0], key);
                    release.run();
                    snapFail(key, onFail, "World '" + worldName + "' was unloaded or the plugin stopped during the snapshot.");
                    return;
                }
                int cols = 0;
                while (cols < columnsPerTick && cursor[0] <= maxX) {
                    int x = cursor[0], z = cursor[1];
                    int runStart = minY, runIdx = -1;
                    for (int y = minY; y <= maxY; y++) {
                        Block b = world.getBlockAt(x, y, z);
                        BlockData bd = b.getBlockData();
                        Integer idx = bdCache.get(bd);
                        if (idx == null) {
                            String pk = b.getType().name() + "|" + bd.getAsString();
                            idx = paletteIndex.get(pk);
                            if (idx == null) {
                                idx = palette.size(); palette.add(pk); paletteIndex.put(pk, idx);
                                tileFlags.add(isTileMaterial(b.getType()));
                            }
                            bdCache.put(bd, idx);
                        }
                        if (tileFlags.get(idx)) {
                            String t = captureTile(b);
                            if (t != null) tiles.add(t);
                        }
                        if (idx != runIdx) {
                            if (runIdx >= 0) runs.add(new int[]{x, z, runStart, y - runStart, runIdx});
                            runStart = y; runIdx = idx;
                        }
                    }
                    if (runIdx >= 0) runs.add(new int[]{x, z, runStart, maxY - runStart + 1, runIdx});
                    cols++;
                    if (++cursor[1] > maxZ) { cursor[1] = minZ; cursor[0]++; }
                }
                if (cursor[0] <= maxX) return;
                if (holder[0] != null) { holder[0].cancel(); snapshotTasks.remove(holder[0]); }
                cursor[0] = Integer.MAX_VALUE;
                release.run();
                // Serialise + write the file off the main thread
                AsyncUtil.runAsyncThenSync(() -> {
                    try {
                        saveSnapshot(arenaName, worldName, palette, runs, tiles);
                        return runs.size();
                    } catch (Exception ex) {
                        plugin.getLogger().severe("Snapshot write failed for " + arenaName + " (" + ex + "). Check free disk space and write permission on plugins/OmniArena/snapshots.");
                        return -1;
                    }
                }, count -> {
                    snapshotting.remove(key);
                    if (count < 0) {
                        snapFail(key, onFail, "Could not write the snapshot file (check disk space/permissions of plugins/OmniArena/snapshots).");
                        return;
                    }
                    lastErrors.remove(key);
                    plugin.getLogger().info("Snapshot saved for arena " + arenaName + " (" + count + " runs, " + tiles.size() + " tile entities).");
                    if (onComplete != null) onComplete.run();
                });
            } catch (Throwable ex) {
                abortSnapshot(holder[0], key);
                release.run();
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Snapshot of " + arenaName + " failed", ex);
                snapFail(key, onFail, "Snapshot failed with an internal error (" + ex.getClass().getSimpleName() + (ex.getMessage() != null ? ": " + ex.getMessage() : "") + "). Check the console for the stack trace, then retry /oa regen snapshot " + arenaName + ".");
            }
        };

        final Runnable start = () -> {
            if (shuttingDown) { release.run(); snapshotting.remove(key); snapFail(key, onFail, "Snapshot of arena " + arenaName + " cancelled: the plugin is stopping."); return; }
            BukkitTask task = AsyncUtil.runSyncRepeating(step, 0L, 1L);
            if (task == null) { // plugin not enabled: nothing to slice against
                release.run();
                snapshotting.remove(key);
                plugin.getLogger().warning("Cannot snapshot arena " + arenaName + ": the plugin is disabled/shutting down. Retry after the server finished starting.");
                return;
            }
            holder[0] = task;
            snapshotTasks.add(task);
        };
        List<java.util.concurrent.CompletableFuture<?>> futures = new ArrayList<>();
        for (long[] c : chunkCoords) {
            try { futures.add(com.omniarena.platform.Platform.get().loadChunkAsync(world, (int) c[0], (int) c[1])); } catch (Throwable ignored) {}
        }
        final Runnable pinAndStart = () -> {
            for (long[] c : chunkCoords) {
                try { com.omniarena.platform.Platform.get().addChunkTicket(world, (int) c[0], (int) c[1], plugin); ticketed.set(true); } catch (Throwable ignored) {}
            }
            start.run();
        };
        if (futures.isEmpty()) { pinAndStart.run(); return; }
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .whenComplete((v, ex) -> {
                    if (AsyncUtil.runSync(pinAndStart) == null) snapshotting.remove(key); // plugin disabled
                });
    }

    private void abortSnapshot(BukkitTask task, String key) {
        if (task != null) { try { task.cancel(); } catch (Exception ignored) {} snapshotTasks.remove(task); }
        snapshotting.remove(key);
    }

    private void snapFail(String key, Consumer<String> onFail, String msg) {
        plugin.getLogger().warning(msg);
        lastErrors.put(key, msg);
        if (onFail != null) onFail.accept(msg);
    }

    /**
     * Cancels all running regen/snapshot tasks and finalizes them: remaining blocks are
     * placed instantly and preview blocks are removed so no previews are left in the world.
     * Call on plugin disable and /oa stop.
     */
    public void cancelAll() {
        shuttingDown = true;
        for (BukkitTask t : new ArrayList<>(snapshotTasks)) { try { t.cancel(); } catch (Exception ignored) {} }
        snapshotTasks.clear();
        snapshotting.clear();
        for (Runnable r : new ArrayList<>(snapshotReleasers.values())) { try { r.run(); } catch (Exception ignored) {} }
        snapshotReleasers.clear();
        // Each finalizer places what is left and then runs the completion callbacks (so arenas leave RESETTING)
        for (Map.Entry<String, Runnable> en : new ArrayList<>(activeRegens.entrySet())) {
            try { en.getValue().run(); }
            catch (Exception ex) { plugin.getLogger().warning("Regen finalize on shutdown failed for arena " + en.getKey() + ": " + ex + ". Some blocks may be unrestored; run /oa regen " + en.getKey() + " after startup."); }
        }
        activeRegens.clear();
        queuedCallbacks.clear();
        for (FallingBlock fb : new ArrayList<>(descenderEntities)) { try { fb.remove(); } catch (Exception ignored) {} }
        descenderEntities.clear();
        liveDescenders.set(0);
    }

    /** Alias for {@link #cancelAll()}. */
    public void shutdown() { cancelAll(); }

    public void restore(Arena arena, Runnable onComplete, Runnable onFail) {
        restore(arena, null, onComplete, onFail);
    }

    public void restore(Arena arena, BiConsumer<Integer, Integer> onProgress,
                        Runnable onComplete, Runnable onFail) {
        final String name = arena.getName();
        final String key = name.toLowerCase();
        File file = snapshotFile(name);
        if (!file.exists()) {
            restoreFail(key, onFail, "No snapshot file for arena " + name + ". Fix: stand in the arena setup and run /oa regen snapshot " + name + ".");
            return;
        }
        if (!arena.getRegion().isDefined()) {
            restoreFail(key, onFail, plainMsg("arena-no-region", "arena", name) + " Then run /oa regen snapshot " + name + ".");
            return;
        }
        if (snapshotting.contains(key)) {
            restoreFail(key, onFail, "A snapshot of arena " + name + " is still being written; try again in a moment.");
            return;
        }
        shuttingDown = false;
        // Guard: a second restore for the same arena just runs its completion when the first finishes
        if (activeRegens.putIfAbsent(key, () -> {}) != null) {
            queuedCallbacks.computeIfAbsent(key, k -> new ArrayList<>()).add(new Waiter(onComplete, onFail));
            return;
        }
        lastErrors.remove(key);
        final long restoreT0 = System.nanoTime();
        ConfigManager.debugLog(() -> "regen: restore of arena " + name + " started (style " + getAnimationType() + ", batch-size "
                + ConfigManager.getInstance().getConfig().getInt("advanced.regen.batch-size", 4000) + ")");
        final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);
        final Runnable fComplete = () -> {
            if (!finished.compareAndSet(false, true)) return;
            activeRegens.remove(key);
            ConfigManager.debugLog(() -> "regen: restore of arena " + name + " completed in " + (System.nanoTime() - restoreT0) / 1_000_000L + " ms");
            List<Waiter> q = queuedCallbacks.remove(key);
            safeRun(onComplete);
            if (q != null) q.forEach(w -> safeRun(w.ok()));
        };
        final Runnable fFail = () -> {
            if (!finished.compareAndSet(false, true)) return;
            activeRegens.remove(key);
            ConfigManager.debugLog(() -> "regen: restore of arena " + name + " FAILED after " + (System.nanoTime() - restoreT0) / 1_000_000L + " ms");
            List<Waiter> q = queuedCallbacks.remove(key);
            safeRun(onFail);
            if (q != null) q.forEach(w -> safeRun(w.fail()));
        };
        final ArenaRegion region = arena.getRegion();
        final String regionWorld = region.getWorldName();
        final RegenAnimationType t = getAnimationType();
        final double cx = (region.getMinX() + region.getMaxX()) / 2.0;
        final double cy = (region.getMinY() + region.getMaxY()) / 2.0;
        final double cz = (region.getMinZ() + region.getMaxZ()) / 2.0;
        final int[] bounds = {region.getMinX(), region.getMinY(), region.getMinZ(),
                region.getMaxX(), region.getMaxY(), region.getMaxZ()};
        final ChangeLog changeLog = changeLogs.get(key);
        final PlanCtx ctx = new PlanCtx(cx, cy, cz,
                (region.getMaxX() - region.getMinX()) >= (region.getMaxZ() - region.getMinZ()),
                changeLog != null ? changeLog.map : Map.of());
        // While the plan loads/chunks preload, a cancel (plugin stop/disable) restores synchronously instead of
        // leaving the arena unrestored; the async continuation then sees syncDone and only frees its tickets.
        final java.util.concurrent.atomic.AtomicBoolean syncDone = new java.util.concurrent.atomic.AtomicBoolean(false);
        activeRegens.put(key, () -> {
            if (!syncDone.compareAndSet(false, true)) return;
            try {
                Plan p = loadPlan(YamlConfiguration.loadConfiguration(file), t, ctx);
                World w = p.worldName != null ? Bukkit.getWorld(p.worldName) : null;
                if (w == null || p.order.length == 0 || (regionWorld != null && !regionWorld.equals(p.worldName))) {
                    restoreFail(key, null, "Regen of " + name + " could not finish during shutdown (snapshot missing/corrupt or world unloaded). Run /oa regen " + name + " after startup.");
                    fFail.run();
                    return;
                }
                while (p.hasMore()) {
                    for (BlockEntry e : p.nextBatch(w, Integer.MAX_VALUE, Integer.MAX_VALUE)) placeEntry(w, p, e);
                }
                clearTransientEntities(w, bounds);
                fComplete.run();
            } catch (Throwable ex) {
                restoreFail(key, null, "Regen of " + name + " failed during shutdown (" + ex + "). Run /oa regen " + name + " after startup.");
                fFail.run();
            }
        });
        AsyncUtil.runAsyncThenSync(
            () -> {
                try {
                    FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                    Plan plan = loadPlan(cfg, t, ctx);
                    return new LoadResult(plan, null);
                } catch (Throwable ex) {
                    return new LoadResult(null, ex.toString());
                }
            },
            result -> {
                if (syncDone.get()) return; // finalized synchronously by cancelAll
                if (shuttingDown) { restoreFail(key, null, "Regen of " + name + " cancelled (plugin stopping)."); fFail.run(); return; }
                String fix = " Fix: run /oa regen snapshot " + name + " to recreate it.";
                if (result.plan() == null || result.plan().order.length == 0) {
                    restoreFail(key, null, "Snapshot file of arena " + name + " is corrupt or empty"
                            + (result.error() != null ? " (" + result.error() + ")" : "") + "." + fix);
                    fFail.run();
                    return;
                }
                Plan plan = result.plan();
                World world = plan.worldName != null ? Bukkit.getWorld(plan.worldName) : null;
                if (world == null) {
                    restoreFail(key, null, plainMsg("world-not-found", "world", String.valueOf(plan.worldName)) + " The snapshot of " + name + " was taken there; load it or re-snapshot if the arena moved.");
                    fFail.run();
                    return;
                }
                if (regionWorld != null && !regionWorld.equals(plan.worldName)) {
                    restoreFail(key, null, "Snapshot of arena " + name + " was taken in world '" + plan.worldName
                            + "' but the arena region is now in '" + regionWorld + "'." + fix);
                    fFail.run();
                    return;
                }
                preloadChunks(world, plan, () -> {
                    if (syncDone.get()) { releaseTickets(world, plan); return; }
                    if (shuttingDown) { releaseTickets(world, plan); syncDone.set(true); fFail.run(); return; }
                    syncDone.set(true); // startPlacing installs its own finalizer
                    startPlacing(key, world, plan, bounds, result.plan().type, onProgress, fComplete, fFail);
                });
            }
        );
    }

    private void restoreFail(String key, Runnable onFail, String msg) {
        plugin.getLogger().warning(msg);
        lastErrors.put(key, msg);
        safeRun(onFail);
    }

    private void safeRun(Runnable r) {
        if (r == null) return;
        try { r.run(); } catch (Exception ex) { plugin.getLogger().warning("A regen completion callback threw " + ex + "; the regen itself finished, but follow-up actions (e.g. teleport/messages) may be missing. See console."); }
    }

    /** Loads every chunk the snapshot touches (async) and pins them with plugin tickets for the restore. */
    private void preloadChunks(World world, Plan plan, Runnable then) {
        List<java.util.concurrent.CompletableFuture<?>> futures = new ArrayList<>();
        for (long ck : plan.chunks) {
            int cx = (int) (ck >> 32), cz = (int) ck;
            try { futures.add(com.omniarena.platform.Platform.get().loadChunkAsync(world, cx, cz)); } catch (Throwable ignored) {}
        }
        Runnable go = () -> {
            for (long ck : plan.chunks) {
                try { com.omniarena.platform.Platform.get().addChunkTicket(world, (int) (ck >> 32), (int) ck, plugin); plan.ticketed = true; }
                catch (Throwable ignored) {}
            }
            then.run();
        };
        if (futures.isEmpty()) { go.run(); return; }
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                .whenComplete((v, ex) -> {
                    if (AsyncUtil.runSync(go) == null) { /* plugin disabled: nothing more to do */ }
                });
    }

    private void releaseTickets(World world, Plan plan) {
        if (!plan.ticketed) return;
        plan.ticketed = false;
        for (long ck : plan.chunks) {
            try { com.omniarena.platform.Platform.get().removeChunkTicket(world, (int) (ck >> 32), (int) ck, plugin); } catch (Throwable ignored) {}
        }
    }

    // ── Placement engine ───────────────────────────────────────────────────────

    /** Growable list of packed block positions (HOLOGRAM_SCAN preview tracking). */
    private static final class LongBuf {
        long[] a = new long[256];
        int n = 0;
        void add(long v) { if (n == a.length) a = Arrays.copyOf(a, n * 2); a[n++] = v; }
        void clear() { n = 0; }
    }

    private void startPlacing(String key, World world, Plan plan, int[] bounds, RegenAnimationType type,
                              BiConsumer<Integer, Integer> onProgressRaw, Runnable onComplete, Runnable onFail) {
        final boolean instant = type == RegenAnimationType.INSTANT;
        final boolean holo = type == RegenAnimationType.HOLOGRAM_SCAN;
        final boolean meteor = type == RegenAnimationType.METEOR_SHOWER;
        final int total = (int) Math.min(Integer.MAX_VALUE, plan.totalBlocks);

        int configBatch = Math.max(1, ConfigManager.getInstance().getConfig()
                .getInt("advanced.regen.batch-size", DEFAULT_BATCH_SIZE));
        final int batchSize = instant ? configBatch : Math.min(configBatch, ANIM_BATCH_SIZE);
        final boolean sfx = ConfigManager.getInstance().getConfig().getBoolean("regen.sound", true);

        // Progress callback is monotonic: never reports a smaller value than before
        final int[] lastProgress = {-1};
        final BiConsumer<Integer, Integer> onProgress = onProgressRaw == null ? null : (d, t) -> {
            if (d > lastProgress[0]) { lastProgress[0] = d; onProgressRaw.accept(d, t); }
        };

        final BukkitTask[] taskHolder = new BukkitTask[1];
        final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean(false);
        final int[] pass = {0};                 // HOLOGRAM_SCAN: 0 = scan/preview pass, 1 = real placement pass
        final int[] tick = {0};
        final LongBuf previews = new LongBuf(); // HOLOGRAM_SCAN glass previews currently in the world

        // Stray entities (arrows, dropped items, primed TNT, falling blocks...) are cleared before and after
        clearTransientEntities(world, bounds);

        final Runnable finish = () -> {
            if (!done.compareAndSet(false, true)) return;
            if (taskHolder[0] != null) taskHolder[0].cancel();
            releaseTickets(world, plan);
            clearTransientEntities(world, bounds);
            if (onProgress != null) { try { onProgress.accept(total, total); } catch (Exception ignored) {} }
            changeLogs.remove(key);
            onComplete.run();
        };

        // Removes the temporary glass previews. Only valid in pass 0: afterwards previews sit on target
        // positions that the real placement overwrites anyway, and removing could erase real glass.
        final Runnable cleanupPreviews = () -> {
            if (pass[0] == 0) removePreviews(world, previews);
            previews.clear();
        };

        // Finalizer used by cancelAll()/plugin disable: places everything that is left right now
        activeRegens.put(key, () -> {
            if (done.get()) return;
            if (taskHolder[0] != null) taskHolder[0].cancel();
            if (holo && pass[0] == 0) { cleanupPreviews.run(); plan.reset(); pass[0] = 1; }
            while (plan.hasMore()) {
                for (BlockEntry e : plan.nextBatch(world, Integer.MAX_VALUE, Integer.MAX_VALUE)) placeEntry(world, plan, e);
            }
            finish.run();
        });

        if (instant && sfx) {
            double[] c = regionCenter(bounds);
            world.playSound(new Location(world, c[0], c[1], c[2]), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.4f);
        }

        taskHolder[0] = AsyncUtil.runSyncRepeating(() -> {
            if (done.get()) return;
            try {
                tick[0]++;
                double progress = total <= 0 ? 1.0 : Math.min(1.0, (double) plan.scanned / total);
                int want = batchSize;
                if (meteor) want = Math.max(40, (int) (batchSize * (0.15 + 0.85 * progress))); // impacts accelerate
                List<BlockEntry> batch = plan.nextBatch(world, want, SCAN_PER_TICK);

                if (!batch.isEmpty()) {
                    if (holo && pass[0] == 0) showPreviews(world, batch, previews);
                    else for (BlockEntry e : batch) placeEntry(world, plan, e);
                    if (!instant) {
                        double shown = holo ? (pass[0] * (double) total + plan.scanned) / (2.0 * total) : progress;
                        playEffects(type, world, plan, bounds, batch, shown, tick[0], sfx, pass[0]);
                        if (meteor) spawnDescenders(world, batch);
                    }
                }
                if (onProgress != null) {
                    long shownBlocks = holo ? (pass[0] * (long) total + plan.scanned) / 2 : plan.scanned;
                    onProgress.accept((int) Math.min(total, shownBlocks), total);
                }

                if (!plan.hasMore()) {
                    if (holo && pass[0] == 0) {
                        // Pass 0 done: previews stay until pass 1 replaces them with real blocks
                        pass[0] = 1;
                        plan.reset();
                        previews.clear();
                    } else {
                        finish.run();
                    }
                }
            } catch (Throwable ex) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Regen of " + key + " failed mid-way (" + ex + "); stack trace follows. Re-run /oa regen " + key + " once fixed", ex);
                if (done.compareAndSet(false, true)) {
                    if (taskHolder[0] != null) taskHolder[0].cancel();
                    try { cleanupPreviews.run(); } catch (Throwable ignored) {}
                    releaseTickets(world, plan);
                    lastErrors.put(key, "Regen failed with an internal error (" + ex.getClass().getSimpleName()
                            + "). Check the console, then re-run /oa regen " + key + ".");
                    onFail.run();
                }
            }
        }, 1L, 1L);
        if (taskHolder[0] == null) { // plugin disabled: finish synchronously so nothing is left half-restored
            Runnable fin = activeRegens.get(key);
            if (fin != null) fin.run();
        }
    }

    // HOLOGRAM_SCAN previews: temporary light-blue glass at differing non-air target positions
    private static final int MAX_PREVIEWS = 300_000;
    private static final Material PREVIEW_MAT = Material.LIGHT_BLUE_STAINED_GLASS;

    private void showPreviews(World world, List<BlockEntry> batch, LongBuf previews) {
        for (BlockEntry e : batch) {
            if (previews.n >= MAX_PREVIEWS) return;
            if (e.material == null || e.material.isAir()) continue;
            Block b = world.getBlockAt(e.x, e.y, e.z);
            if (b.getType() == PREVIEW_MAT) continue;
            b.setType(PREVIEW_MAT, false);
            previews.add(pack(e.x, e.y, e.z));
        }
    }

    private void removePreviews(World world, LongBuf previews) {
        for (int i = 0; i < previews.n; i++) {
            long k = previews.a[i];
            int x = unpackX(k), y = unpackY(k, world), z = unpackZ(k);
            Block b = world.getBlockAt(x, y, z);
            // Only remove previews we actually placed (never touch anything else)
            if (b.getType() == PREVIEW_MAT) b.setType(Material.AIR, false);
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }
    private static int unpackX(long k) { return (int) (k >> 38) << 6 >> 6; }
    private static int unpackZ(long k) { return (int) ((k >> 12) & 0x3FFFFFF) << 6 >> 6; }
    private static int unpackY(long k, World w) {
        int y = (int) (k & 0xFFF);
        return y >= 2048 ? y - 4096 : y;
    }

    private void clearTransientEntities(World world, int[] bb) {
        try {
            org.bukkit.util.BoundingBox box = new org.bukkit.util.BoundingBox(
                    bb[0], bb[1], bb[2], bb[3] + 1.0, bb[4] + 1.0, bb[5] + 1.0);
            for (org.bukkit.entity.Entity en : world.getNearbyEntities(box)) {
                if (en instanceof org.bukkit.entity.Item || en instanceof org.bukkit.entity.Projectile
                        || en instanceof org.bukkit.entity.ExperienceOrb || en instanceof org.bukkit.entity.TNTPrimed
                        || en instanceof FallingBlock || en instanceof org.bukkit.entity.AreaEffectCloud) {
                    descenderEntities.remove(en);
                    en.remove();
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Regen entity cleanup failed (" + ex + "); leftover item/arrow entities may remain in the arena. Remove them with /kill @e[type=item] if needed.");
        }
    }

    /**
     * Places one snapshot block without neighbour physics (so attachables never pop off while their
     * support is still missing, and liquids/sand do not start flowing/falling). Tile data is applied after.
     */
    private void placeEntry(World world, Plan plan, BlockEntry e) {
        Block b = world.getBlockAt(e.x, e.y, e.z);
        BlockData data = plan.parsed(e.idx);
        if (data == null) return;
        if (!b.getBlockData().equals(data)) b.setBlockData(data, false);
        if (plan.tileCap[e.idx]) applyTile(b, plan.tiles.get(e.x + "," + e.y + "," + e.z));
    }

    // ── Tile entities (container contents, sign text) ─────────────────────────

    private static String b64(byte[] d) { return Base64.getEncoder().encodeToString(d); }
    private static String b64(String s) { return b64(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static String unb64s(String s) {
        return new String(Base64.getDecoder().decode(s), java.nio.charset.StandardCharsets.UTF_8);
    }

    @SuppressWarnings("deprecation")
    private String captureTile(Block b) {
        try {
            BlockState st = com.omniarena.platform.Platform.get().getBlockState(b);
            String pos = b.getX() + "," + b.getY() + "," + b.getZ();
            if (st instanceof Sign sign) {
                String[] f = new String[4], k = new String[4];
                for (int i = 0; i < 4; i++) { f[i] = sign.getSide(Side.FRONT).getLine(i); k[i] = sign.getSide(Side.BACK).getLine(i); }
                // 4th field (optional, older snapshots lack it): frontColor,backColor,frontGlow,backGlow,waxed
                org.bukkit.block.sign.SignSide fs = sign.getSide(Side.FRONT), bs = sign.getSide(Side.BACK);
                String style = (fs.getColor() == null ? "" : fs.getColor().name()) + "," + (bs.getColor() == null ? "" : bs.getColor().name())
                        + "," + fs.isGlowingText() + "," + bs.isGlowingText() + "," + sign.isWaxed();
                return pos + "|S|" + b64(String.join("\n", f)) + "|" + b64(String.join("\n", k)) + "|" + style;
            }
            if (st instanceof org.bukkit.block.Banner banner) {
                if (banner.numberOfPatterns() == 0) return null;
                StringBuilder sb = new StringBuilder();
                for (org.bukkit.block.banner.Pattern pt : banner.getPatterns()) {
                    if (sb.length() > 0) sb.append(';');
                    sb.append(pt.getColor().name()).append(':').append(pt.getPattern().getKey());
                }
                return pos + "|B|" + sb;
            }
            if (st instanceof Container c) {
                Inventory inv = c instanceof Chest ch ? ch.getBlockInventory() : c.getInventory();
                ItemStack[] items = inv.getContents();
                boolean any = false;
                for (ItemStack is : items) if (is != null && !is.getType().isAir()) { any = true; break; }
                if (!any) return null;
                return pos + "|C|" + b64(com.omniarena.platform.Platform.get().serializeItems(items));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Tile snapshot failed for " + b.getType() + " at " + b.getX() + "," + b.getY() + "," + b.getZ() + " (" + ex + "); its contents/text will not be restored. Re-run /oa regen snapshot after fixing the block.");
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private void applyTile(Block b, String tile) {
        try {
            BlockState st = b.getState();
            String[] p = tile == null ? new String[0] : tile.split("\\|");
            if (st instanceof Sign sign) {
                for (int s = 0; s < 2; s++) {
                    String[] l = p.length > s + 1 && p[0].equals("S") ? unb64s(p[s + 1]).split("\n", -1) : new String[0];
                    for (int i = 0; i < 4; i++)
                        sign.getSide(s == 0 ? Side.FRONT : Side.BACK).setLine(i, i < l.length ? l[i] : "");
                }
                if (p.length > 3 && p[0].equals("S")) {
                    String[] st2 = p[3].split(",", -1);
                    if (st2.length >= 5) {
                        for (int s = 0; s < 2; s++) {
                            org.bukkit.block.sign.SignSide side = sign.getSide(s == 0 ? Side.FRONT : Side.BACK);
                            try { if (!st2[s].isEmpty()) side.setColor(org.bukkit.DyeColor.valueOf(st2[s])); } catch (IllegalArgumentException ignored) {}
                            side.setGlowingText(Boolean.parseBoolean(st2[2 + s]));
                        }
                        sign.setWaxed(Boolean.parseBoolean(st2[4]));
                    }
                }
                sign.update(true, false);
            } else if (st instanceof org.bukkit.block.Banner banner) {
                List<org.bukkit.block.banner.Pattern> pats = new ArrayList<>();
                if (p.length > 1 && p[0].equals("B") && !p[1].isEmpty()) {
                    for (String e : p[1].split(";")) {
                        int c = e.indexOf(':');
                        if (c <= 0) continue;
                        try {
                            org.bukkit.NamespacedKey nk = org.bukkit.NamespacedKey.fromString(e.substring(c + 1));
                            org.bukkit.block.banner.PatternType pt = nk == null ? null : org.bukkit.Registry.BANNER_PATTERN.get(nk);
                            if (pt != null) pats.add(new org.bukkit.block.banner.Pattern(org.bukkit.DyeColor.valueOf(e.substring(0, c)), pt));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
                banner.setPatterns(pats);
                banner.update(true, false);
            } else if (st instanceof Container c) {
                Inventory inv = c instanceof Chest ch ? ch.getBlockInventory() : c.getInventory();
                inv.clear(); // players may have stored/removed items: always reset to the snapshot
                if (p.length > 1 && p[0].equals("C")) {
                    ItemStack[] items = com.omniarena.platform.Platform.get().deserializeItems(Base64.getDecoder().decode(p[1]));
                    for (int i = 0; i < items.length && i < inv.getSize(); i++) inv.setItem(i, items[i]);
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Tile restore failed for " + b.getType() + " at " + b.getX() + "," + b.getY() + "," + b.getZ() + " (" + ex + "); the block was placed but its contents/text are default. The snapshot may be corrupt: re-run /oa regen snapshot.");
        }
    }

    // ── Streaming restore plan ────────────────────────────────────────────────

    /**
     * Compact snapshot representation: palette + vertical run "pieces" (max {@link #MAX_PIECE_LEN} tall)
     * in animation order. Blocks are expanded lazily per batch, never all at once, and blocks that
     * already match the snapshot are skipped.
     */
    private static final class Plan {
        String worldName;
        RegenAnimationType type;
        Material[] mats;
        String[] datas;
        BlockData[] parsed;
        boolean[] tileCap;
        int[] pieces;          // x, z, y0, len, paletteIdx per piece
        int[] order;           // piece indices in animation order
        Set<Long> chunks = new HashSet<>();
        Map<String, String> tiles = new HashMap<>();
        long totalBlocks;
        boolean ticketed;
        // cursor
        int pos = 0, sub = 0;
        long scanned = 0;

        boolean hasMore() { return pos < order.length; }

        /** Rewinds the cursor to the start (HOLOGRAM_SCAN second pass / finalizer). */
        void reset() { pos = 0; sub = 0; scanned = 0; }

        BlockData parsed(int idx) {
            BlockData d = parsed[idx];
            if (d == null) {
                try {
                    d = datas[idx] != null ? Bukkit.createBlockData(datas[idx]) : mats[idx].createBlockData();
                } catch (IllegalArgumentException ex) {
                    d = mats[idx].createBlockData();
                }
                parsed[idx] = d;
            }
            return d;
        }

        boolean needsChange(Block b, int idx) {
            if (tileCap[idx]) return true;
            Material m = mats[idx];
            if (b.getType() != m) return true;
            if (datas[idx] == null) return false;
            return !b.getBlockData().equals(parsed(idx));
        }

        /** Next blocks that differ from the world, at most {@code want}, scanning at most {@code scanCap} blocks. */
        List<BlockEntry> nextBatch(World w, int want, int scanCap) {
            List<BlockEntry> out = new ArrayList<>();
            int scannedNow = 0;
            int minH = w.getMinHeight(), maxH = w.getMaxHeight();
            while (out.size() < want && scannedNow < scanCap && pos < order.length) {
                int pi = order[pos] * 5;
                int x = pieces[pi], z = pieces[pi + 1], y0 = pieces[pi + 2], len = pieces[pi + 3], idx = pieces[pi + 4];
                while (sub < len && out.size() < want && scannedNow < scanCap) {
                    int y = y0 + sub;
                    sub++; scannedNow++; scanned++;
                    if (y < minH || y >= maxH) continue;
                    Block b = w.getBlockAt(x, y, z);
                    if (needsChange(b, idx)) out.add(new BlockEntry(x, y, z, mats[idx], idx));
                }
                if (sub >= len) { pos++; sub = 0; }
            }
            return out;
        }
    }

    private static final class IntBuf {
        int[] a = new int[1024];
        int n = 0;
        void add(int v) { if (n == a.length) a = Arrays.copyOf(a, n * 2); a[n++] = v; }
        int[] toArray() { return Arrays.copyOf(a, n); }
    }

    private Plan loadPlan(FileConfiguration cfg, RegenAnimationType type, PlanCtx ctx) {
        Plan plan = new Plan();
        plan.type = type;
        plan.worldName = cfg.getString("world");
        IntBuf buf = new IntBuf();
        List<Material> matList = new ArrayList<>();
        List<String> dataList = new ArrayList<>();

        if (cfg.isList("palette") && cfg.isList("runs")) {
            // Format 2: palette + vertical runs
            List<String> palette = cfg.getStringList("palette");
            int skipped = 0;
            for (String pk : palette) {
                String[] pp = pk.split("\\|", 2);
                Material m;
                try { m = Material.valueOf(pp[0]); } catch (Exception ex) { m = null; }
                matList.add(m);
                dataList.add(pp.length > 1 ? pp[1] : null);
            }
            for (String line : cfg.getStringList("runs")) {
                String[] p = line.split(",");
                if (p.length != 5) continue;
                try {
                    int x = Integer.parseInt(p[0].trim()), z = Integer.parseInt(p[1].trim());
                    int y0 = Integer.parseInt(p[2].trim()), len = Integer.parseInt(p[3].trim());
                    int idx = Integer.parseInt(p[4].trim());
                    if (idx < 0 || idx >= matList.size() || len <= 0 || len > 4096) continue;
                    if (matList.get(idx) == null) { skipped += len; continue; } // unknown material: leave world as is
                    for (int o = 0; o < len; o += MAX_PIECE_LEN) {
                        buf.add(x); buf.add(z); buf.add(y0 + o); buf.add(Math.min(MAX_PIECE_LEN, len - o)); buf.add(idx);
                    }
                } catch (NumberFormatException ignored) {}
            }
            if (skipped > 0) plugin.getLogger().warning("Snapshot contains " + skipped + " blocks of materials unknown to this server version; they are left untouched.");
        } else {
            // Legacy format 1: one line per block "x,y,z|MATERIAL|blockdata"
            Map<String, Integer> index = new HashMap<>();
            for (String line : cfg.getStringList("blocks")) {
                String[] parts = line.split("\\|", 3);
                if (parts.length < 2) continue;
                String[] c = parts[0].split(",");
                if (c.length != 3) continue;
                try {
                    int x = Integer.parseInt(c[0].trim()), y = Integer.parseInt(c[1].trim()), z = Integer.parseInt(c[2].trim());
                    Material m = Material.valueOf(parts[1]);
                    String bd = parts.length > 2 ? parts[2] : null;
                    String pk = parts[1] + "|" + bd;
                    Integer idx = index.get(pk);
                    if (idx == null) { idx = matList.size(); matList.add(m); dataList.add(bd); index.put(pk, idx); }
                    buf.add(x); buf.add(z); buf.add(y); buf.add(1); buf.add(idx);
                } catch (Exception ignored) {}
            }
        }

        int n = buf.n / 5;
        plan.pieces = buf.toArray();
        plan.mats = matList.toArray(new Material[0]);
        plan.datas = dataList.toArray(new String[0]);
        plan.parsed = new BlockData[plan.mats.length];
        plan.tileCap = new boolean[plan.mats.length];
        for (int i = 0; i < plan.mats.length; i++) plan.tileCap[i] = plan.mats[i] != null && isTileMaterial(plan.mats[i]);

        for (String t : cfg.getStringList("tiles")) {
            int bar = t.indexOf('|');
            if (bar > 0) plan.tiles.put(t.substring(0, bar), t.substring(bar + 1));
        }

        for (int i = 0; i < n; i++) {
            int x = plan.pieces[i * 5], z = plan.pieces[i * 5 + 1], len = plan.pieces[i * 5 + 3];
            plan.totalBlocks += len;
            plan.chunks.add(((long) (x >> 4) << 32) | ((z >> 4) & 0xffffffffL));
        }
        plan.order = computeOrder(type, plan.pieces, n, ctx);
        return plan;
    }

    // ── Effects (particles + sounds), capped per tick and sent only to nearby players ────────────

    private static final int VIEW_DISTANCE = 64;
    private static final int PARTICLE_EMITS_PER_TICK = 40;
    private static final int GALAXY_ARMS = 4;
    private static final double GALAXY_K = 0.15;      // spiral tightness: radians per block of radius
    private static final double TWO_PI = Math.PI * 2;
    private int particleBudget = 0;

    private double[] regionCenter(int[] b) {
        return new double[]{(b[0] + b[3] + 1) / 2.0, (b[1] + b[4] + 1) / 2.0, (b[2] + b[5] + 1) / 2.0};
    }

    /** Players within {@link #VIEW_DISTANCE} blocks of the region box. */
    private List<org.bukkit.entity.Player> viewers(World world, int[] b) {
        List<org.bukkit.entity.Player> out = new ArrayList<>();
        double lim = (double) VIEW_DISTANCE * VIEW_DISTANCE;
        for (org.bukkit.entity.Player p : world.getPlayers()) {
            Location l = p.getLocation();
            double dx = Math.max(Math.max(b[0] - l.getX(), 0), l.getX() - (b[3] + 1));
            double dy = Math.max(Math.max(b[1] - l.getY(), 0), l.getY() - (b[4] + 1));
            double dz = Math.max(Math.max(b[2] - l.getZ(), 0), l.getZ() - (b[5] + 1));
            if (dx * dx + dy * dy + dz * dz <= lim) out.add(p);
        }
        return out;
    }

    private boolean inBounds(int[] b, double x, double y, double z) {
        return x >= b[0] && x <= b[3] + 1 && y >= b[1] && y <= b[4] + 1 && z >= b[2] && z <= b[5] + 1;
    }

    private void emit(List<org.bukkit.entity.Player> v, Particle particle, double x, double y, double z,
                      int count, double off, double speed, Object data) {
        if (particleBudget <= 0) return;
        particleBudget--;
        for (org.bukkit.entity.Player p : v) {
            if (data != null) p.spawnParticle(particle, x, y, z, count, off, off, off, speed, data);
            else p.spawnParticle(particle, x, y, z, count, off, off, off, speed);
        }
    }

    private void emit(List<org.bukkit.entity.Player> v, Particle particle, double x, double y, double z,
                      int count, double off, double speed) {
        emit(v, particle, x, y, z, count, off, speed, null);
    }

    private void sound(World world, double x, double y, double z, Sound s, float vol, float pitch) {
        world.playSound(new Location(world, x, y, z), s, vol, Math.max(0.5f, Math.min(2.0f, pitch)));
    }

    /** Evenly spaced sample of the batch, at most {@code k} entries. */
    private List<BlockEntry> sample(List<BlockEntry> batch, int k) {
        if (batch.size() <= k) return batch;
        List<BlockEntry> out = new ArrayList<>(k);
        double step = (double) batch.size() / k;
        for (int i = 0; i < k; i++) out.add(batch.get((int) (i * step)));
        return out;
    }

    private void playEffects(RegenAnimationType type, World world, Plan plan, int[] b, List<BlockEntry> batch,
                             double progress, int tick, boolean sfx, int pass) {
        List<org.bukkit.entity.Player> v = viewers(world, b);
        if (v.isEmpty() && !sfx) return;
        boolean particlesOn = ConfigManager.getInstance().getConfig().getBoolean("regen.particles", true);
        particleBudget = v.isEmpty() || !particlesOn ? 0 : PARTICLE_EMITS_PER_TICK;
        double[] c = regionCenter(b);
        BlockEntry first = batch.get(0), last = batch.get(batch.size() - 1);
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        float pitchUp = (float) (0.5 + 1.5 * progress);
        double w = b[3] - b[0] + 1, d = b[5] - b[2] + 1;

        switch (type) {
            case SPHERE -> {
                double r = Math.sqrt(distSq3(first.x + 0.5, first.y + 0.5, first.z + 0.5, c[0], c[1], c[2]));
                int placed = 0;
                for (int i = 0; i < 48 && placed < 24; i++) {
                    double ux = rnd.nextGaussian(), uy = rnd.nextGaussian(), uz = rnd.nextGaussian();
                    double len = Math.sqrt(ux * ux + uy * uy + uz * uz);
                    if (len < 1e-6) continue;
                    double px = c[0] + r * ux / len, py = c[1] + r * uy / len, pz = c[2] + r * uz / len;
                    if (!inBounds(b, px, py, pz)) continue;
                    emit(v, Particle.END_ROD, px, py, pz, 1, 0, 0);
                    placed++;
                }
                if (sfx && tick % 3 == 0) sound(world, first.x, first.y, first.z, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, pitchUp);
            }
            case RISE, FALL -> {
                boolean rise = type == RegenAnimationType.RISE;
                double y = (rise ? last.y : first.y) + 1.0;
                for (int i = 0; i < 24; i++)
                    emit(v, rise ? Particle.END_ROD : Particle.SNOWFLAKE, b[0] + rnd.nextDouble() * w, y, b[2] + rnd.nextDouble() * d, 1, 0, 0);
                if (sfx && tick % 3 == 0) {
                    if (rise) sound(world, first.x, first.y, first.z, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4f, pitchUp);
                    else sound(world, first.x, first.y, first.z, Sound.BLOCK_GRAVEL_PLACE, 0.5f, (float) (1.4 - progress * 0.8));
                }
            }
            case SWEEP -> {
                boolean alongX = w >= d;
                double plane = (alongX ? first.x : first.z) + 0.5;
                double h = b[4] - b[1] + 1;
                for (int i = 0; i < 24; i++) {
                    double y = b[1] + rnd.nextDouble() * h;
                    if (alongX) emit(v, Particle.ELECTRIC_SPARK, plane, y, b[2] + rnd.nextDouble() * d, 1, 0, 0);
                    else emit(v, Particle.ELECTRIC_SPARK, b[0] + rnd.nextDouble() * w, y, plane, 1, 0, 0);
                }
                if (sfx && tick % 3 == 0) sound(world, first.x, first.y, first.z, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, pitchUp);
            }
            case TIME_REWIND -> {
                // Counter-clockwise clock face above the region: the hand spins backwards as progress advances
                double r = Math.max(2, Math.min(6, Math.min(w, d) / 2));
                double cy = b[4] + 4.0;
                if (tick % 2 == 0)
                    for (int i = 0; i < 12; i++) {
                        double a = i * TWO_PI / 12;
                        emit(v, Particle.END_ROD, c[0] + r * Math.cos(a), cy + r * Math.sin(a), c[2], 1, 0, 0);
                    }
                double hand = Math.PI / 2 + progress * TWO_PI * 6;
                for (int i = 1; i <= 6; i++) {
                    double rr = r * i / 6.0;
                    emit(v, Particle.ENCHANT, c[0] + rr * Math.cos(hand), cy + rr * Math.sin(hand), c[2], 2, 0.05, 0.1);
                }
                for (BlockEntry e : sample(batch, 8))
                    emit(v, Particle.ENCHANT, e.x + 0.5, e.y + 1.0, e.z + 0.5, 3, 0.4, 0.3);
                if (sfx && tick % 2 == 0) sound(world, first.x, first.y, first.z, Sound.BLOCK_NOTE_BLOCK_BELL, 0.4f, pitchUp);
            }
            case GALAXY -> {
                double hx = Math.max(w, d) / 2.0 + 1;
                double fr = Math.hypot(first.x + 0.5 - c[0], first.z + 0.5 - c[2]);
                double fphase = ((Math.atan2(first.z + 0.5 - c[2], first.x + 0.5 - c[0]) + TWO_PI) % TWO_PI + GALAXY_K * fr) % TWO_PI;
                int arm = (int) Math.floor(fphase * GALAXY_ARMS / TWO_PI);
                double py = first.y + 1.0;
                for (int i = 1; i <= 18; i++) {
                    double rr = hx * i / 18.0;
                    double a = (arm + 0.5) * TWO_PI / GALAXY_ARMS - GALAXY_K * rr;
                    double px = c[0] + rr * Math.cos(a), pz = c[2] + rr * Math.sin(a);
                    if (!inBounds(b, px, py, pz)) continue;
                    emit(v, (i & 1) == 0 ? Particle.PORTAL : Particle.END_ROD, px, py, pz, (i & 1) == 0 ? 6 : 1, 0.3, 0.1);
                }
                if (sfx && tick % 6 == 0) sound(world, c[0], first.y, c[2], Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, pitchUp);
            }
            case HOLOGRAM_SCAN -> {
                if (pass == 0) {
                    Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.1f);
                    double y = last.y + 1.0;
                    for (int i = 0; i < 24; i++)
                        emit(v, Particle.DUST, b[0] + rnd.nextDouble() * w, y, b[2] + rnd.nextDouble() * d, 1, 0, 0, dust);
                    if (sfx && tick % 3 == 0) sound(world, first.x, first.y, first.z, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, pitchUp);
                } else {
                    for (BlockEntry e : sample(batch, 12))
                        emit(v, Particle.ELECTRIC_SPARK, e.x + 0.5, e.y + 0.5, e.z + 0.5, 2, 0.4, 0.05);
                    if (sfx && tick % 3 == 0) sound(world, first.x, first.y, first.z, Sound.BLOCK_GLASS_BREAK, 0.25f, 1.6f);
                }
            }
            case METEOR_SHOWER -> {
                for (BlockEntry e : sample(batch, 2)) {
                    for (int i = 1; i <= 6; i++) {
                        double y = e.y + 3.0 * i;
                        emit(v, i % 2 == 0 ? Particle.LAVA : Particle.FLAME, e.x + 0.5, y, e.z + 0.5, 2, 0.1, 0.02);
                    }
                    emit(v, Particle.EXPLOSION, e.x + 0.5, e.y + 1.0, e.z + 0.5, 1, 0.3, 0);
                    emit(v, Particle.LARGE_SMOKE, e.x + 0.5, e.y + 1.0, e.z + 0.5, 4, 0.4, 0.02);
                }
                if (sfx && tick % 2 == 0) sound(world, first.x, first.y, first.z, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 0.7f + (float) progress * 0.5f);
            }
            case OVERGROWTH -> {
                for (BlockEntry e : sample(batch, 12))
                    emit(v, Particle.SPORE_BLOSSOM_AIR, e.x + 0.5, e.y + 1.0, e.z + 0.5, 2, 0.5, 0.0);
                for (BlockEntry e : sample(batch, 4))
                    emit(v, Particle.HAPPY_VILLAGER, e.x + 0.5, e.y + 0.8, e.z + 0.5, 1, 0.4, 0.0);
                if (sfx && tick % 2 == 0)
                    sound(world, first.x, first.y, first.z, (tick & 2) == 0 ? Sound.BLOCK_GRASS_PLACE : Sound.BLOCK_AZALEA_LEAVES_PLACE, 0.4f, 0.9f + rnd.nextFloat() * 0.4f);
            }
            default -> {}
        }
    }

    private static double distSq3(double x, double y, double z, double cx, double cy, double cz) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    // ── Meteor descenders ────────────────────────────────────────────────────────

    /**
     * METEOR_SHOWER: spawns a real {@link FallingBlock} high above the first block of the batch. It is purely
     * visual (no damage, never lands: removed just above the target, then the real block is already in place),
     * limited by {@link #MAX_DESCENDERS}.
     */
    private void spawnDescenders(World world, List<BlockEntry> batch) {
        for (BlockEntry e : batch) {
            if (e.material == Material.AIR) continue;
            if (descenderTick++ % 12 != 0) continue;
            if (liveDescenders.get() >= MAX_DESCENDERS) return;
            spawnDescender(world, e);
            return; // at most one new meteor per tick
        }
    }

    private void spawnDescender(World world, BlockEntry e) {
        final int impactY = e.y + 1;
        final int lifeTicks = 26;
        Location spawn = new Location(world, e.x + 0.5, e.y + 28, e.z + 0.5);
        if (!world.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) return;

        FallingBlock fb;
        try {
            fb = world.spawnFallingBlock(spawn, Bukkit.createBlockData(Material.MAGMA_BLOCK));
        } catch (Exception ex) {
            return;
        }
        fb.setDropItem(false);
        fb.setHurtEntities(false);
        fb.setVelocity(new Vector((RNG.nextDouble() - 0.5) * 0.04, -1.2, (RNG.nextDouble() - 0.5) * 0.04));
        liveDescenders.incrementAndGet();
        descenderEntities.add(fb);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks++;
                boolean landed = fb.isDead() || fb.isOnGround()
                        || fb.getLocation().getY() <= impactY || ticks >= lifeTicks || shuttingDown;
                if (!landed) {
                    world.spawnParticle(Particle.FLAME, fb.getLocation(), 6, 0.12, 0.12, 0.12, 0.01);
                    return;
                }
                Location crater = new Location(world, e.x + 0.5, e.y + 1.0, e.z + 0.5);
                if (!fb.isDead()) fb.remove();
                world.spawnParticle(Particle.EXPLOSION, crater, 2, 0.2, 0.1, 0.2, 0.0);
                world.spawnParticle(Particle.LAVA, crater, 8, 0.4, 0.2, 0.4, 0.0);
                world.playSound(crater, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.6f);
                liveDescenders.decrementAndGet();
                descenderEntities.remove(fb);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ── Ordering (computed off-main when the plan is built) ──────────────────────────────────

    private int[] computeOrder(RegenAnimationType type, int[] pc, int n, PlanCtx ctx) {
        int[] order = new int[n];
        if (type == RegenAnimationType.INSTANT || n == 0) {
            for (int i = 0; i < n; i++) order[i] = i;
            return order;
        }
        double[] k1 = new double[n], k2 = new double[n];
        switch (type) {
            case TIME_REWIND -> rewindKeys(pc, n, ctx, k1, k2);
            case METEOR_SHOWER -> meteorKeys(pc, n, ctx, k1, k2);
            case OVERGROWTH -> overgrowthKeys(pc, n, ctx, k1, k2);
            default -> {
                for (int i = 0; i < n; i++) {
                    int len = pc[i * 5 + 3];
                    computeKeys(type, pc[i * 5], pc[i * 5 + 2] + (len - 1) / 2.0, pc[i * 5 + 1], ctx, k1, k2, i);
                }
            }
        }
        Integer[] ord = new Integer[n];
        for (int i = 0; i < n; i++) ord[i] = i;
        Arrays.sort(ord, (a, b) -> {
            int c = Double.compare(k1[a], k1[b]);
            if (c != 0) return c;
            c = Double.compare(k2[a], k2[b]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        for (int i = 0; i < n; i++) order[i] = ord[i];
        return order;
    }

    /** Two-level sort key (k1 then k2) of a piece at (x, y, z) for the geometric styles. */
    private void computeKeys(RegenAnimationType type, int x, double y, int z, PlanCtx c,
                             double[] k1, double[] k2, int i) {
        double dx = x - c.cx(), dy = y - c.cy(), dz = z - c.cz();
        double distSq = dx * dx + dy * dy + dz * dz;
        double hyp = Math.hypot(dx, dz);
        double a = 0, b = 0;
        switch (type) {
            case SPHERE -> { a = distSq; b = y; }
            case RISE -> { a = y; b = distSq; }
            case FALL -> { a = -y; b = distSq; }
            case SWEEP -> { a = c.alongX() ? x : z; b = y; }
            case GALAXY -> {
                double angle = Math.atan2(dz, dx);
                double angle2pi = angle < 0 ? angle + TWO_PI : angle;
                double phase = (angle2pi + GALAXY_K * hyp) % TWO_PI;
                a = Math.floor(phase * GALAXY_ARMS / TWO_PI); // arm index
                b = hyp;
            }
            case HOLOGRAM_SCAN -> { a = y; b = distSq; }
            default -> { a = i; }
        }
        k1[i] = a;
        k2[i] = b;
    }

    private static int pieceSeq(int[] pc, int i, Map<Long, Integer> log) {
        int x = pc[i * 5], z = pc[i * 5 + 1], y0 = pc[i * 5 + 2], len = pc[i * 5 + 3];
        int best = 0;
        for (int o = 0; o < len; o++) {
            Integer s = log.get(pack(x, y0 + o, z));
            if (s != null && s > best) best = s;
        }
        return best;
    }

    /** Pieces that contain at least one block changed during the match (empty when nothing was logged). */
    private static int[] changedPieces(int[] pc, int n, Map<Long, Integer> log) {
        if (log.isEmpty()) return new int[0];
        IntBuf out = new IntBuf();
        for (int i = 0; i < n; i++) if (pieceSeq(pc, i, log) > 0) out.add(i);
        return out.toArray();
    }

    /** TIME_REWIND: highest change sequence first; never-logged pieces afterwards in SPHERE order. */
    private void rewindKeys(int[] pc, int n, PlanCtx ctx, double[] k1, double[] k2) {
        for (int i = 0; i < n; i++) {
            int len = pc[i * 5 + 3];
            double y = pc[i * 5 + 2] + (len - 1) / 2.0;
            int seq = ctx.changes().isEmpty() ? 0 : pieceSeq(pc, i, ctx.changes());
            double dx = pc[i * 5] - ctx.cx(), dy = y - ctx.cy(), dz = pc[i * 5 + 1] - ctx.cz();
            if (seq > 0) { k1[i] = -seq; k2[i] = 0; }
            else { k1[i] = 1; k2[i] = dx * dx + dy * dy + dz * dz; }
        }
    }

    /** METEOR_SHOWER: impacts in random order; every piece belongs to its nearest impact, nearest first. */
    private void meteorKeys(int[] pc, int n, PlanCtx ctx, double[] k1, double[] k2) {
        Random rng = new Random(n * 31L + 7);
        int[] cand = changedPieces(pc, n, ctx.changes());
        int candN = cand.length > 0 ? cand.length : n;
        int k = Math.max(1, Math.min(candN, Math.max(6, Math.min(120, (int) (Math.sqrt(n) / 6)))));
        double[][] imp = new double[k][3];
        for (int j = 0; j < k; j++) {
            int pi = cand.length > 0 ? cand[rng.nextInt(candN)] : rng.nextInt(n);
            imp[j][0] = pc[pi * 5];
            imp[j][1] = pc[pi * 5 + 2] + (pc[pi * 5 + 3] - 1) / 2.0;
            imp[j][2] = pc[pi * 5 + 1];
        }
        for (int i = 0; i < n; i++) {
            double x = pc[i * 5], y = pc[i * 5 + 2] + (pc[i * 5 + 3] - 1) / 2.0, z = pc[i * 5 + 1];
            int bj = 0;
            double bd = Double.MAX_VALUE;
            for (int j = 0; j < k; j++) {
                double dd = distSq3(x, y, z, imp[j][0], imp[j][1], imp[j][2]);
                if (dd < bd) { bd = dd; bj = j; }
            }
            k1[i] = bj;      // impact order
            k2[i] = bd;      // closest to the impact first
        }
    }

    /** OVERGROWTH: BFS from 3-6 seeds over 6-adjacent pieces; unreachable pieces follow in SPHERE order. */
    private void overgrowthKeys(int[] pc, int n, PlanCtx ctx, double[] k1, double[] k2) {
        Random rng = new Random(n * 17L + 3);
        // pieces sorted by (column, y0) so neighbours in a column are adjacent
        Integer[] byCol = new Integer[n];
        for (int i = 0; i < n; i++) byCol[i] = i;
        Arrays.sort(byCol, (a, b) -> {
            int c = Long.compare(colKey(pc[a * 5], pc[a * 5 + 1]), colKey(pc[b * 5], pc[b * 5 + 1]));
            return c != 0 ? c : Integer.compare(pc[a * 5 + 2], pc[b * 5 + 2]);
        });
        int[] posOf = new int[n];
        Map<Long, int[]> range = new HashMap<>();
        for (int p = 0; p < n; p++) {
            int pi = byCol[p];
            posOf[pi] = p;
            long ck = colKey(pc[pi * 5], pc[pi * 5 + 1]);
            int[] r = range.get(ck);
            if (r == null) range.put(ck, new int[]{p, p});
            else r[1] = p;
        }
        int[] cand = changedPieces(pc, n, ctx.changes());
        int seeds = 3 + rng.nextInt(4);
        int[] queue = new int[n];
        boolean[] seen = new boolean[n];
        int qh = 0, qt = 0;
        for (int s = 0; s < seeds; s++) {
            int pi = cand.length > 0 ? cand[rng.nextInt(cand.length)] : rng.nextInt(n);
            if (!seen[pi]) { seen[pi] = true; queue[qt++] = pi; }
        }
        final int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (qh < qt) {
            int cur = queue[qh++];
            int x = pc[cur * 5], z = pc[cur * 5 + 1], y0 = pc[cur * 5 + 2], len = pc[cur * 5 + 3];
            int p = posOf[cur];
            int[] own = range.get(colKey(x, z));
            if (p > own[0]) { int nb = byCol[p - 1]; if (!seen[nb] && pc[nb * 5 + 2] + pc[nb * 5 + 3] >= y0) { seen[nb] = true; queue[qt++] = nb; } }
            if (p < own[1]) { int nb = byCol[p + 1]; if (!seen[nb] && pc[nb * 5 + 2] <= y0 + len) { seen[nb] = true; queue[qt++] = nb; } }
            for (int[] dd : dirs) {
                int[] r = range.get(colKey(x + dd[0], z + dd[1]));
                if (r == null) continue;
                for (int q = r[0]; q <= r[1]; q++) {
                    int nb = byCol[q];
                    if (seen[nb]) continue;
                    int ny0 = pc[nb * 5 + 2], nlen = pc[nb * 5 + 3];
                    if (ny0 < y0 + len && y0 < ny0 + nlen) { seen[nb] = true; queue[qt++] = nb; }
                }
            }
        }
        for (int j = 0; j < qt; j++) { k1[queue[j]] = j; k2[queue[j]] = 0; }
        for (int i = 0; i < n; i++) {
            if (seen[i]) continue;
            double y = pc[i * 5 + 2] + (pc[i * 5 + 3] - 1) / 2.0;
            k1[i] = n + 1;
            k2[i] = distSq3(pc[i * 5], y, pc[i * 5 + 1], ctx.cx(), ctx.cy(), ctx.cz());
        }
    }

    private static long colKey(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

    // ── Change log (TIME_REWIND) ───────────────────────────────────────────────

    private static final int MAX_CHANGE_LOG = 200_000;

    private static final class ChangeLog {
        final Map<Long, Integer> map = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger(0);
    }

    private final Map<String, ChangeLog> changeLogs = new java.util.concurrent.ConcurrentHashMap<>();

    /** Records that a block of this arena changed during the match (main thread). Bounded; ignored while regenerating. */
    public void recordChange(String arenaName, int x, int y, int z) {
        String key = arenaName.toLowerCase();
        if (activeRegens.containsKey(key)) return;
        ChangeLog log = changeLogs.computeIfAbsent(key, k -> new ChangeLog());
        long p = pack(x, y, z);
        if (log.map.size() >= MAX_CHANGE_LOG && !log.map.containsKey(p)) return;
        log.map.put(p, log.seq.incrementAndGet());
    }

    public void clearChangeLog(String arenaName) {
        changeLogs.remove(arenaName.toLowerCase());
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private RegenAnimationType getAnimationType() {
        String v = ConfigManager.getInstance().getConfig()
                .getString("regen.style", "SPHERE");
        return RegenAnimationType.fromConfig(v);
    }

    private File snapshotFile(String arenaName) {
        return new File(snapshotsDir, arenaName.toLowerCase() + ".yml");
    }

    /** Serialises and writes atomically (temp file + move) so a crash never leaves a half-written snapshot. */
    private void saveSnapshot(String arenaName, String worldName, List<String> palette, List<int[]> runs,
                              List<String> tiles) throws java.io.IOException {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("world", worldName);
        cfg.set("format", 2);
        cfg.set("palette", palette);
        List<String> serialized = new ArrayList<>(runs.size());
        for (int[] r : runs) // x,z,y0,len,paletteIdx
            serialized.add(r[0] + "," + r[1] + "," + r[2] + "," + r[3] + "," + r[4]);
        cfg.set("runs", serialized);
        if (!tiles.isEmpty()) cfg.set("tiles", tiles);
        String yaml = cfg.saveToString();
        File target = snapshotFile(arenaName);
        java.nio.file.Path tmp = new File(snapshotsDir, target.getName() + ".tmp").toPath();
        java.nio.file.Files.writeString(tmp, yaml, java.nio.charset.StandardCharsets.UTF_8);
        try {
            java.nio.file.Files.move(tmp, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            java.nio.file.Files.move(tmp, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ── Records ────────────────────────────────────────────────────────────────

    private record BlockEntry(int x, int y, int z, Material material, int idx) {}
    private record LoadResult(Plan plan, String error) {}
    private record Waiter(Runnable ok, Runnable fail) {}
    private record PlanCtx(double cx, double cy, double cz, boolean alongX, Map<Long, Integer> changes) {}
}
