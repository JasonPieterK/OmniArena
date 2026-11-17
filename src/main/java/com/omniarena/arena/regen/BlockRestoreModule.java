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
    // Animated types use higher batch for faster, smoother animations
    private static final int ANIM_BATCH_SIZE = 400;
    // Ultra-fast batch sizes for visual animations with descent effects
    private static final int ULTRA_FAST_ANIM_BATCH_SIZE = 1200;
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
                try { world.removePluginChunkTicket((int) c[0], (int) c[1], plugin); } catch (Throwable ignored) {}
            }
        };

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
            try { futures.add(world.getChunkAtAsync((int) c[0], (int) c[1], true)); } catch (Throwable ignored) {}
        }
        final Runnable pinAndStart = () -> {
            for (long[] c : chunkCoords) {
                try { world.addPluginChunkTicket((int) c[0], (int) c[1], plugin); ticketed.set(true); } catch (Throwable ignored) {}
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
        final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean(false);
        final Runnable fComplete = () -> {
            if (!finished.compareAndSet(false, true)) return;
            activeRegens.remove(key);
            List<Waiter> q = queuedCallbacks.remove(key);
            safeRun(onComplete);
            if (q != null) q.forEach(w -> safeRun(w.ok()));
        };
        final Runnable fFail = () -> {
            if (!finished.compareAndSet(false, true)) return;
            activeRegens.remove(key);
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
        AsyncUtil.runAsyncThenSync(
            () -> {
                try {
                    FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                    Plan plan = loadPlan(cfg, t, cx, cy, cz);
                    return new LoadResult(plan, null);
                } catch (Throwable ex) {
                    return new LoadResult(null, ex.toString());
                }
            },
            result -> {
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
                    if (shuttingDown) { releaseTickets(world, plan); fFail.run(); return; }
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
            try { futures.add(world.getChunkAtAsync(cx, cz, true)); } catch (Throwable ignored) {}
        }
        Runnable go = () -> {
            for (long ck : plan.chunks) {
                try { world.addPluginChunkTicket((int) (ck >> 32), (int) ck, plugin); plan.ticketed = true; }
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
            try { world.removePluginChunkTicket((int) (ck >> 32), (int) ck, plugin); } catch (Throwable ignored) {}
        }
    }

    // ── Placement engine ───────────────────────────────────────────────────────

    private void startPlacing(String key, World world, Plan plan, int[] bounds, RegenAnimationType type,
                              BiConsumer<Integer, Integer> onProgress, Runnable onComplete, Runnable onFail) {
        final boolean instant = type == RegenAnimationType.INSTANT_RESTORE;
        final int total = (int) Math.min(Integer.MAX_VALUE, plan.totalBlocks);

        int configBatch = Math.max(1, ConfigManager.getInstance().getConfig()
                .getInt("regen.batch-size", DEFAULT_BATCH_SIZE));
        // Visual types use higher batch for faster, smoother animations
        final int batchSize = instant ? configBatch : Math.min(configBatch,
                (type == RegenAnimationType.ARMORED_DESCENT || type == RegenAnimationType.RAIN_OF_LIGHT || type == RegenAnimationType.METEOR_STRIKE ||
                 type == RegenAnimationType.BINARY_FILL || type == RegenAnimationType.QUANTUM_MESH)
                ? ULTRA_FAST_ANIM_BATCH_SIZE : ANIM_BATCH_SIZE);

        final boolean sfx = !instant && ConfigManager.getInstance().getConfig().getBoolean("regen.animation.sound", true);

        final BukkitTask[] taskHolder = new BukkitTask[1];
        final java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean(false);

        // Queue: each AnimBatch holds the preview block locations + the real entries
        final Deque<AnimBatch> queue = new ArrayDeque<>();

        // Stray entities (arrows, dropped items, primed TNT, falling blocks...) are cleared before and after
        clearTransientEntities(world, bounds);

        final Runnable finish = () -> {
            if (!done.compareAndSet(false, true)) return;
            if (taskHolder[0] != null) taskHolder[0].cancel();
            releaseTickets(world, plan);
            clearTransientEntities(world, bounds);
            if (onProgress != null) { try { onProgress.accept(total, total); } catch (Exception ignored) {} }
            onComplete.run();
        };

        final Runnable cleanupPreviews = () -> {
            for (AnimBatch ab : queue) removePreviews(world, ab.preview());
        };

        // Finalizer used by cancelAll()/plugin disable: places everything that is left right now
        activeRegens.put(key, () -> {
            if (done.get()) return;
            if (taskHolder[0] != null) taskHolder[0].cancel();
            cleanupPreviews.run();
            for (AnimBatch ab : queue) for (BlockEntry e : ab.entries()) placeEntry(world, plan, e);
            queue.clear();
            while (plan.hasMore()) {
                for (BlockEntry e : plan.nextBatch(world, Integer.MAX_VALUE, Integer.MAX_VALUE)) placeEntry(world, plan, e);
            }
            finish.run();
        });

        taskHolder[0] = AsyncUtil.runSyncRepeating(() -> {
            if (done.get()) return;
            try {
                if (instant) {
                    for (BlockEntry e : plan.nextBatch(world, batchSize, SCAN_PER_TICK)) placeEntry(world, plan, e);
                    if (onProgress != null) onProgress.accept((int) Math.min(total, plan.scanned), total);
                    if (!plan.hasMore()) finish.run();
                    return;
                }

                // ── Step 1: Finalize previous batch ──────────────────────────
                if (!queue.isEmpty()) {
                    AnimBatch prev = queue.poll();
                    // Remove preview blocks (always remove to prevent permanent blocks)
                    removePreviews(world, prev.preview());
                    // Place real blocks
                    for (BlockEntry e : prev.entries()) placeEntry(world, plan, e);
                    if (onProgress != null) onProgress.accept((int) Math.min(total, prev.scannedMark()), total);
                    if (sfx && !prev.entries().isEmpty()) playWaveSound(world, prev.entries().get(0), type);
                }

                // ── Step 2: Build and show next batch preview ─────────────────
                if (plan.hasMore()) {
                    List<BlockEntry> batch = plan.nextBatch(world, batchSize, SCAN_PER_TICK);
                    if (batch.isEmpty()) {
                        // Only unchanged blocks were scanned this tick; report progress and carry on
                        if (queue.isEmpty() && onProgress != null) onProgress.accept((int) Math.min(total, plan.scanned), total);
                    } else {
                        List<PreviewBlock> preview = buildPreview(world, batch, type);
                        final int wMin = world.getMinHeight(), wMax = world.getMaxHeight();
                        // Place preview blocks; keep only those really placed so cleanup is exact
                        preview.removeIf(pb -> {
                            int py = pb.loc().getBlockY();
                            if (py < wMin || py >= wMax) return true;
                            if (!world.isChunkLoaded(pb.loc().getBlockX() >> 4, pb.loc().getBlockZ() >> 4)) return true;
                            Block b = world.getBlockAt(pb.loc());
                            // Only place preview if the block is currently air (don't overwrite real blocks)
                            if (b.getType() != Material.AIR) return true;
                            b.setType(pb.mat(), false);
                            return false;
                        });
                        // Descent types: real falling-block entities that streak down and vanish on impact
                        spawnDescenders(world, batch, type);
                        queue.add(new AnimBatch(batch, preview, plan.scanned));
                    }
                }

                // ── Done when queue empty AND no more entries ─────────────────
                if (queue.isEmpty() && !plan.hasMore()) finish.run();
            } catch (Throwable ex) {
                plugin.getLogger().severe("Regen of " + key + " failed mid-way (" + ex + "); stack trace follows. Re-run /oa regen " + key + " once fixed");
                ex.printStackTrace();
                if (done.compareAndSet(false, true)) {
                    if (taskHolder[0] != null) taskHolder[0].cancel();
                    cleanupPreviews.run();
                    queue.clear();
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

    private void removePreviews(World world, List<PreviewBlock> previews) {
        for (PreviewBlock pb : previews) {
            Block b = world.getBlockAt(pb.loc());
            // Only remove previews we actually placed (never touch real restored blocks)
            if (b.getType() == pb.mat()) b.setType(Material.AIR, false);
        }
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
            BlockState st = b.getState(false);
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
                return pos + "|C|" + b64(ItemStack.serializeItemsAsBytes(items));
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
                    ItemStack[] items = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(p[1]));
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

    private Plan loadPlan(FileConfiguration cfg, RegenAnimationType type, double cx, double cy, double cz) {
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

        double[] k1 = new double[n], k2 = new double[n];
        for (int i = 0; i < n; i++) {
            int x = plan.pieces[i * 5], z = plan.pieces[i * 5 + 1], y0 = plan.pieces[i * 5 + 2], len = plan.pieces[i * 5 + 3];
            plan.totalBlocks += len;
            plan.chunks.add(((long) (x >> 4) << 32) | ((z >> 4) & 0xffffffffL));
            computeKeys(type, x, y0 + (len - 1) / 2.0, z, cx, cy, cz, k1, k2, i);
        }
        Integer[] ord = new Integer[n];
        for (int i = 0; i < n; i++) ord[i] = i;
        if (type != RegenAnimationType.INSTANT_RESTORE) {
            Arrays.sort(ord, (a, b) -> {
                int c = Double.compare(k1[a], k1[b]);
                if (c != 0) return c;
                c = Double.compare(k2[a], k2[b]);
                return c != 0 ? c : Integer.compare(a, b);
            });
        }
        plan.order = new int[n];
        for (int i = 0; i < n; i++) plan.order[i] = ord[i];
        return plan;
    }

    // ── Preview builders ───────────────────────────────────────────────────────

    /**
     * Builds the list of temporary blocks to place for this batch's animation.
     * Each PreviewBlock = (Location, Material) — placed one tick, removed the next.
     */
    private List<PreviewBlock> buildPreview(World world, List<BlockEntry> batch, RegenAnimationType type) {
        List<PreviewBlock> out = new ArrayList<>();
        for (BlockEntry e : batch) {
            if (e.material == Material.AIR) continue;
            buildEntryPreview(world, e, type, batch, out);
        }
        return out;
    }

    private void buildEntryPreview(World world, BlockEntry e, RegenAnimationType type,
                                   List<BlockEntry> batch, List<PreviewBlock> out) {
        switch (type) {

            // 1. SPIRAL_ASCENT — gold spiral path lights up as blocks appear
            case SPIRAL_ASCENT -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.GOLD_BLOCK));

            // 2. COLLAPSE_INWARD — cyan ring shrinks inward
            case COLLAPSE_INWARD -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.CYAN_CONCRETE));

            // 3. RADIATE_OUTWARD — yellow/orange shockwave expanding outward
            case RADIATE_OUTWARD -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.YELLOW_CONCRETE));

            // 4. GRAVITY_DESCENT — heavy blocks fall from above
            case GRAVITY_DESCENT -> {
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.GRAVEL));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 2, e.z), Material.BLACKSTONE));
            }

            // 5. UPWARD_SURGE — bright lime block rises up
            case UPWARD_SURGE -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.LIME_CONCRETE));

            // 6. RAIN_OF_LIGHT — descent done by real glowing falling blocks
            case RAIN_OF_LIGHT -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.GLOWSTONE));

            // 7. ARMORED_DESCENT — descent done by real falling red suit pieces
            case ARMORED_DESCENT -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.LIGHT_BLUE_CONCRETE));

            // 8. CHAOTIC_ERUPTION — glowstone and nether wart blocks burst outward
            case CHAOTIC_ERUPTION -> {
                int ox = RNG.nextInt(3) - 1;
                int oz = RNG.nextInt(3) - 1;
                out.add(new PreviewBlock(loc(world, e.x + ox, e.y, e.z + oz), Material.NETHER_WART_BLOCK));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.GLOWSTONE));
            }

            // 9. BEACON_WAVE — bright beacon light sweeps
            case BEACON_WAVE -> {
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.BEACON));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.GOLD_BLOCK));
            }

            // 10. VOID_SHOCKWAVE — purple void ring expands outward
            case VOID_SHOCKWAVE -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.CRYING_OBSIDIAN));

            // 11. ICE_CRYSTAL_GROWTH — cyan ice crystals grow upward
            case ICE_CRYSTAL_GROWTH -> {
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.PACKED_ICE));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.BLUE_ICE));
            }

            // 12. DARK_VORTEX — emerald and purple spiral inward
            case DARK_VORTEX -> {
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.EMERALD_BLOCK));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.AMETHYST_BLOCK));
            }

            // 13. METEOR_STRIKE — descent done by real flaming meteors
            case METEOR_STRIKE -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.MAGMA_BLOCK));

            // 14. PRISM_CASCADE — rainbow colored wave pattern
            case PRISM_CASCADE -> {
                Material[] rainbow = {
                    Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE,
                    Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
                    Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE
                };
                Material mat = rainbow[Math.abs(e.x + e.z) % 7];
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), mat));
            }

            // ── NEW ANIMATIONS (5 new animations) ──

            // 16. BINARY_FILL — digital matrix-style with cyan and lime alternating
            case BINARY_FILL -> {
                boolean isOdd = (Math.abs(e.x) + Math.abs(e.z)) % 2 == 0;
                Material mat = isOdd ? Material.CYAN_CONCRETE : Material.LIME_CONCRETE;
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), mat));
            }

            // 17. PHOENIX_REBIRTH — fire and lava blocks rise like phoenix
            case PHOENIX_REBIRTH -> {
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.MAGMA_BLOCK));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.FIRE));
            }

            // 18. QUANTUM_MESH — glitchy purple/cyan teleport effect
            case QUANTUM_MESH -> {
                boolean alternate = (e.x + e.z) % 2 == 0;
                Material mat = alternate ? Material.PURPLE_CONCRETE : Material.CYAN_CONCRETE;
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), mat));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.AMETHYST_BLOCK));
            }

            // 19. DIAMOND_FUSION — blue diamond blocks grow inward
            case DIAMOND_FUSION -> {
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.DIAMOND_BLOCK));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.BLUE_ICE));
            }

            // 20. TSUNAMI_SURGE — aqua/light blue wave rises like water
            case TSUNAMI_SURGE -> {
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.LIGHT_BLUE_CONCRETE));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.CYAN_CONCRETE));
            }
        }
    }

    // ── Falling-block descenders ─────────────────────────────────────────────────

    /**
     * For descent-style animations, spawn real {@link FallingBlock} entities high
     * above a throttled subset of this batch's blocks. They physically fall, trail
     * particles, and are removed (with an impact burst) just before they land —
     * "shot from the sky, disappears on impact". The real block is restored
     * underneath by the normal engine, so the falling entity is pure theatre and
     * never litters the arena.
     */
    private void spawnDescenders(World world, List<BlockEntry> batch, RegenAnimationType type) {
        int step; // 1-in-N blocks gets a descender
        switch (type) {
            case METEOR_STRIKE -> step = 15;  // dramatic meteors
            case ARMORED_DESCENT -> step = 3; // dense rain of suit pieces
            case RAIN_OF_LIGHT -> step = 3;   // dense rain of light
            case PHOENIX_REBIRTH -> step = 8; // occasional phoenix particles
            default -> { return; }            // ground types don't descend
        }

        for (BlockEntry e : batch) {
            if (e.material == Material.AIR) continue;
            if (descenderTick++ % step != 0) continue;
            if (liveDescenders.get() >= MAX_DESCENDERS) break;
            spawnDescender(world, e, type);
        }
    }

    private void spawnDescender(World world, BlockEntry e, RegenAnimationType type) {
        final int impactY = e.y + 1;             // remove ~1 block above the target
        final double spawnHeight;
        final Material fallMat;
        final double downVel;
        final Particle trail;
        final Particle.DustOptions dust;
        final int lifeTicks;

        switch (type) {
            case METEOR_STRIKE -> {
                spawnHeight = 28; fallMat = Material.MAGMA_BLOCK; downVel = -1.2;
                trail = Particle.FLAME; dust = null; lifeTicks = 26;
            }
            case ARMORED_DESCENT -> {
                spawnHeight = 22; fallMat = Material.RED_CONCRETE; downVel = -1.5;
                trail = Particle.SOUL_FIRE_FLAME;
                dust = new Particle.DustOptions(Color.fromRGB(255, 196, 0), 1.2f);
                lifeTicks = 18;
            }
            case RAIN_OF_LIGHT -> {
                spawnHeight = 26; fallMat = Material.GLOWSTONE; downVel = -2.0;
                trail = Particle.END_ROD; dust = null; lifeTicks = 14;
            }
            case PHOENIX_REBIRTH -> {
                spawnHeight = 24; fallMat = Material.FIRE; downVel = -0.8;
                trail = Particle.FLAME; dust = null; lifeTicks = 20;
            }
            default -> { // fallback
                spawnHeight = 24; fallMat = Material.WHITE_STAINED_GLASS; downVel = -2.0;
                trail = Particle.END_ROD; dust = null; lifeTicks = 12;
            }
        }

        Location spawn = new Location(world, e.x + 0.5, e.y + spawnHeight, e.z + 0.5);
        if (!world.isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) return;

        FallingBlock fb;
        try {
            fb = world.spawnFallingBlock(spawn, Bukkit.createBlockData(fallMat));
        } catch (Exception ex) {
            return;
        }
        fb.setDropItem(false);
        fb.setHurtEntities(false);
        fb.setVelocity(new Vector((RNG.nextDouble() - 0.5) * 0.04, downVel, (RNG.nextDouble() - 0.5) * 0.04));
        liveDescenders.incrementAndGet();
        descenderEntities.add(fb);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks++;
                boolean landed = fb.isDead() || fb.isOnGround()
                        || fb.getLocation().getY() <= impactY || ticks >= lifeTicks;

                if (!landed) {
                    Location at = fb.getLocation();
                    world.spawnParticle(trail, at, 6, 0.12, 0.12, 0.12, 0.01);
                    if (dust != null)
                        world.spawnParticle(Particle.DUST, at, 4, 0.15, 0.15, 0.15, dust);
                    return;
                }

                // Impact: remove the entity and burst at the crater
                Location crater = new Location(world, e.x + 0.5, e.y + 1.0, e.z + 0.5);
                if (!fb.isDead()) fb.remove();
                playImpact(world, crater, type);
                liveDescenders.decrementAndGet();
                descenderEntities.remove(fb);
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void playImpact(World world, Location loc, RegenAnimationType type) {
        switch (type) {
            case METEOR_STRIKE -> {
                world.spawnParticle(Particle.EXPLOSION, loc, 2, 0.2, 0.1, 0.2, 0.0);
                world.spawnParticle(Particle.LAVA, loc, 8, 0.4, 0.2, 0.4, 0.0);
                world.spawnParticle(Particle.LARGE_SMOKE, loc, 10, 0.5, 0.3, 0.5, 0.02);
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.6f);
            }
            case ARMORED_DESCENT -> {
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 14, 0.3, 0.2, 0.3, 0.1);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 6, 0.25, 0.15, 0.25, 0.02);
                world.playSound(loc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.7f, 0.9f);
                world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.3f, 1.6f);
            }
            case RAIN_OF_LIGHT -> {
                world.spawnParticle(Particle.FLASH, loc, 1, 0.0, 0.0, 0.0, 0.0);
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 10, 0.3, 0.1, 0.3, 0.12);
                world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 2.0f);
            }
            case PHOENIX_REBIRTH -> {
                world.spawnParticle(Particle.FLAME, loc, 12, 0.3, 0.2, 0.3, 0.05);
                world.spawnParticle(Particle.LAVA, loc, 6, 0.2, 0.15, 0.2, 0.02);
                world.playSound(loc, Sound.ENTITY_BLAZE_DEATH, 0.5f, 0.8f);
            }
            default -> {}
        }
    }

    // ── Ordering ───────────────────────────────────────────────────────────────

    /** Two-level sort key (k1 then k2) of a piece at (x, y, z) for the given animation. */
    private void computeKeys(RegenAnimationType type, int x, double y, int z,
                             double cx, double cy, double cz, double[] k1, double[] k2, int i) {
        double dx = x - cx, dy = y - cy, dz = z - cz;
        double distSq = dx * dx + dy * dy + dz * dz;
        double dist = Math.sqrt(distSq);
        double hyp = Math.hypot(dx, dz);
        double angle = Math.atan2(dz, dx);
        double angle2pi = angle < 0 ? angle + 6.28318 : angle;
        double a = 0, b = 0;
        switch (type) {
            case SPIRAL_ASCENT -> { a = dist * 6.28 + (angle < 0 ? angle + 6.28 : angle); b = y; }
            case COLLAPSE_INWARD -> { a = -distSq; b = y; }
            case RADIATE_OUTWARD -> { a = distSq; b = y; }
            case GRAVITY_DESCENT -> { a = -y; b = distSq; }
            case UPWARD_SURGE -> { a = y; b = distSq; }
            case RAIN_OF_LIGHT -> { a = hyp; b = -y; }
            case ARMORED_DESCENT -> { a = y; b = hyp; }
            case CHAOTIC_ERUPTION, QUANTUM_MESH -> { a = RNG.nextDouble(); }
            case INSTANT_RESTORE -> { a = i; }
            case BEACON_WAVE -> { a = angle2pi; b = distSq; }
            case VOID_SHOCKWAVE -> { a = (int) dist; b = y; }
            case ICE_CRYSTAL_GROWTH -> { a = hyp; b = y; }
            case DARK_VORTEX -> { a = -dist * 6.28318 + angle2pi; b = -y; }
            case METEOR_STRIKE -> { a = ((x > cx ? 1 : 0) * 2 + (z > cz ? 1 : 0)) * 100000.0 - y; b = hyp; }
            case PRISM_CASCADE -> { a = x + z; b = y; }
            case BINARY_FILL -> { a = Math.floorMod(x + z, 2); b = distSq; }
            case PHOENIX_REBIRTH -> { a = y; b = distSq; }
            case DIAMOND_FUSION -> { a = distSq; }
            case TSUNAMI_SURGE -> { a = y; }
        }
        k1[i] = a;
        k2[i] = b;
    }

    // ── Sound per wave ─────────────────────────────────────────────────────────

    private void playWaveSound(World world, BlockEntry e, RegenAnimationType type) {
        Location loc = new Location(world, e.x + 0.5, e.y + 0.5, e.z + 0.5);
        switch (type) {
            case SPIRAL_ASCENT -> world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.3f, 1.5f);
            case COLLAPSE_INWARD -> world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.3f, 1.2f);
            case RADIATE_OUTWARD -> world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.2f, 1.8f);
            case GRAVITY_DESCENT -> world.playSound(loc, Sound.BLOCK_GRAVEL_PLACE, 0.4f, 0.8f);
            case UPWARD_SURGE -> world.playSound(loc, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.3f, 1.4f);
            case RAIN_OF_LIGHT -> {
                world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 2.0f);
                world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.15f, 1.5f);
            }
            case ARMORED_DESCENT -> {
                world.playSound(loc, Sound.ITEM_ARMOR_EQUIP_IRON, 0.5f, 0.7f);
                world.playSound(loc, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.4f, 1.3f);
                if (RNG.nextInt(6) == 0)
                    world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.2f, 1.5f);
            }
            case CHAOTIC_ERUPTION -> world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.3f, 1.0f + RNG.nextFloat());
            case BEACON_WAVE -> world.playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 0.3f, 1.8f);
            case VOID_SHOCKWAVE -> world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 0.7f);
            case ICE_CRYSTAL_GROWTH -> {
                world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.4f);
                world.playSound(loc, Sound.BLOCK_GLASS_PLACE, 0.25f, 1.8f);
            }
            case DARK_VORTEX -> world.playSound(loc, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.35f, 1.2f);
            case METEOR_STRIKE -> {
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 0.6f);
                world.playSound(loc, Sound.ENTITY_BLAZE_HURT, 0.2f, 0.5f);
            }
            case PRISM_CASCADE -> world.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.3f,
                    1.0f + (Math.abs(e.x + e.z) % 7) * 0.1f);
            case BINARY_FILL -> world.playSound(loc, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.3f, 1.2f);
            case PHOENIX_REBIRTH -> world.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 0.4f, 0.8f);
            case QUANTUM_MESH -> world.playSound(loc, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 0.3f, 1.5f);
            case DIAMOND_FUSION -> world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
            case TSUNAMI_SURGE -> world.playSound(loc, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.4f, 0.9f);
            default -> {}
        }
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private RegenAnimationType getAnimationType() {
        String v = ConfigManager.getInstance().getConfig()
                .getString("regen.animation.type", "CENTER_OUT");
        return RegenAnimationType.fromConfig(v);
    }

    private double distSq(BlockEntry e, double x, double y, double z) {
        double dx = e.x - x, dy = e.y - y, dz = e.z - z;
        return dx*dx + dy*dy + dz*dz;
    }

    private Location loc(World world, int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    /** Check if a block type is a temporary preview block that should be cleaned up */
    private boolean isTemporaryBlock(Material mat) {
        return switch (mat) {
            case GOLD_BLOCK, CYAN_CONCRETE, YELLOW_CONCRETE, GRAVEL, SAND,
                 LIME_CONCRETE, SEA_LANTERN, LIGHT_BLUE_CONCRETE, GLOWSTONE,
                 BEACON, CRYING_OBSIDIAN, AMETHYST_BLOCK, BLUE_ICE,
                 EMERALD_BLOCK, END_STONE, PURPUR_BLOCK, MAGMA_BLOCK,
                 RED_CONCRETE, WHITE_STAINED_GLASS -> true;
            default -> false;
        };
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
    private record PreviewBlock(Location loc, Material mat) {}
    private record AnimBatch(List<BlockEntry> entries, List<PreviewBlock> preview, long scannedMark) {}
}
