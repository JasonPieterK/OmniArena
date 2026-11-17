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

    private static final Random RNG = new Random();

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
    private final Map<String, List<Runnable>> queuedCallbacks = new java.util.concurrent.ConcurrentHashMap<>();
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

    public void createSnapshot(Arena arena, Runnable onComplete) {
        ArenaRegion region = arena.getRegion();
        if (!region.isDefined()) { if (onComplete != null) onComplete.run(); return; }
        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) {
            plugin.getLogger().warning("Cannot snapshot arena " + arena.getName() + ": world not loaded.");
            if (onComplete != null) onComplete.run();
            return;
        }
        shuttingDown = false;
        final int minX = region.getMinX(), maxX = region.getMaxX();
        final int minY = region.getMinY(), maxY = region.getMaxY();
        final int minZ = region.getMinZ(), maxZ = region.getMaxZ();
        final int height = maxY - minY + 1;
        final int columnsPerTick = Math.max(1, SNAPSHOT_BLOCKS_PER_TICK / Math.max(1, height));
        final String arenaName = arena.getName();
        final String worldName = region.getWorldName();

        // Palette + vertical run-length encoding: identical consecutive blocks in a column share one run.
        final Map<String, Integer> paletteIndex = new HashMap<>();
        final List<String> palette = new ArrayList<>();
        final Map<BlockData, Integer> bdCache = new HashMap<>();
        final List<int[]> runs = new ArrayList<>(); // x, z, y0, len, paletteIdx
        final int[] cursor = {minX, minZ};
        final BukkitTask[] holder = new BukkitTask[1];

        Runnable step = () -> {
            int cols = 0;
            while (cols < columnsPerTick && cursor[0] <= maxX) {
                int x = cursor[0], z = cursor[1];
                int runStart = minY, runIdx = -1;
                for (int y = minY; y <= maxY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    BlockData bd = b.getBlockData();
                    Integer idx = bdCache.get(bd);
                    if (idx == null) {
                        String key = b.getType().name() + "|" + bd.getAsString();
                        idx = paletteIndex.get(key);
                        if (idx == null) { idx = palette.size(); palette.add(key); paletteIndex.put(key, idx); }
                        bdCache.put(bd, idx);
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
            // Serialise + write the file off the main thread
            AsyncUtil.runAsyncThenSync(() -> {
                saveSnapshot(arenaName, worldName, palette, runs);
                return runs.size();
            }, count -> {
                plugin.getLogger().info("Snapshot saved for arena " + arenaName + " (" + count + " runs).");
                if (onComplete != null) onComplete.run();
            });
        };

        BukkitTask task = AsyncUtil.runSyncRepeating(step, 0L, 1L);
        if (task == null) { // plugin not enabled: nothing to slice against
            plugin.getLogger().warning("Cannot snapshot arena " + arenaName + ": plugin disabled.");
            return;
        }
        holder[0] = task;
        snapshotTasks.add(task);
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
        for (Map.Entry<String, Runnable> en : new ArrayList<>(activeRegens.entrySet())) {
            try { en.getValue().run(); }
            catch (Exception ex) { plugin.getLogger().warning("Regen finalize failed for " + en.getKey() + ": " + ex.getMessage()); }
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
        File file = snapshotFile(arena.getName());
        if (!file.exists()) { if (onFail != null) onFail.run(); return; }
        final String key = arena.getName().toLowerCase();
        shuttingDown = false;
        // Guard: a second restore for the same arena just runs its completion when the first finishes
        if (activeRegens.putIfAbsent(key, () -> {}) != null) {
            if (onComplete != null) queuedCallbacks.computeIfAbsent(key, k -> new ArrayList<>()).add(onComplete);
            return;
        }
        final Runnable origComplete = onComplete, origFail = onFail;
        onComplete = () -> {
            activeRegens.remove(key);
            if (origComplete != null) origComplete.run();
            List<Runnable> q = queuedCallbacks.remove(key);
            if (q != null) q.forEach(Runnable::run);
        };
        onFail = () -> {
            activeRegens.remove(key);
            queuedCallbacks.remove(key);
            if (origFail != null) origFail.run();
        };
        final Runnable fComplete = onComplete, fFail = onFail;
        AsyncUtil.runAsyncThenSync(
            () -> {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                List<BlockEntry> loaded = loadEntries(cfg);
                // Sort off the main thread: ordering a large region is O(n log n)
                RegenAnimationType t = getAnimationType();
                return new LoadResult(cfg.getString("world"), orderEntries(loaded, arena.getRegion(), t), t);
            },
            result -> {
                World world = result.worldName() != null ? Bukkit.getWorld(result.worldName()) : null;
                if (shuttingDown) { fFail.run(); return; }
                if (world == null || result.entries().isEmpty()) {
                    plugin.getLogger().warning("Cannot restore arena " + arena.getName());
                    fFail.run();
                    return;
                }
                RegenAnimationType type = result.type();
                List<BlockEntry> ordered = result.entries();
                startPlacing(key, world, ordered.iterator(), ordered.size(), type, onProgress, fComplete);
            }
        );
    }

    // ── Placement engine ───────────────────────────────────────────────────────

    private void startPlacing(String key, World world, Iterator<BlockEntry> iterator, int total,
                              RegenAnimationType type,
                              BiConsumer<Integer, Integer> onProgress, Runnable onComplete) {

        // Non-animated types: fast direct placement, no preview
        if (type == RegenAnimationType.INSTANT_RESTORE) {
            fastPlace(key, world, iterator, total, onProgress, onComplete);
            return;
        }

        int configBatch = Math.max(1, ConfigManager.getInstance().getConfig()
                .getInt("regen.batch-size", DEFAULT_BATCH_SIZE));
        // Visual types use higher batch for faster, smoother animations
        int batchSize = Math.min(configBatch,
                (type == RegenAnimationType.ARMORED_DESCENT || type == RegenAnimationType.RAIN_OF_LIGHT || type == RegenAnimationType.METEOR_STRIKE ||
                 type == RegenAnimationType.BINARY_FILL || type == RegenAnimationType.QUANTUM_MESH)
                ? ULTRA_FAST_ANIM_BATCH_SIZE : ANIM_BATCH_SIZE);

        boolean sfx = ConfigManager.getInstance().getConfig().getBoolean("regen.animation.sound", true);

        BukkitTask[] taskHolder = new BukkitTask[1];
        int[] restored = {0};

        // Queue: each AnimBatch holds the preview block locations + the real entries
        Deque<AnimBatch> queue = new ArrayDeque<>();
        Map<String, BlockData> dataCache = new HashMap<>();

        activeRegens.put(key, () -> {
            if (taskHolder[0] != null) taskHolder[0].cancel();
            for (AnimBatch ab : queue) {
                for (PreviewBlock pb : ab.preview()) {
                    Block b = world.getBlockAt(pb.loc());
                    if (b.getType() == pb.mat()) b.setType(Material.AIR, false);
                }
                for (BlockEntry e : ab.entries()) placeEntry(world, e, dataCache);
            }
            queue.clear();
            while (iterator.hasNext()) placeEntry(world, iterator.next(), dataCache);
        });

        taskHolder[0] = AsyncUtil.runSyncRepeating(() -> {

            // ── Step 1: Finalize previous batch ──────────────────────────────
            if (!queue.isEmpty()) {
                AnimBatch prev = queue.poll();

                // Remove preview blocks (always remove to prevent permanent blocks)
                for (PreviewBlock pb : prev.preview()) {
                    Block b = world.getBlockAt(pb.loc());
                    // Only remove previews we actually placed (never touch real restored blocks)
                    if (b.getType() == pb.mat()) {
                        b.setType(Material.AIR, false);
                    }
                }

                // Place real blocks
                for (BlockEntry e : prev.entries()) {
                    placeEntry(world, e, dataCache);
                    restored[0]++;
                }
                if (onProgress != null) onProgress.accept(restored[0], total);
                if (sfx) playWaveSound(world, prev.entries().get(0), type);
            }

            // ── Step 2: Build and show next batch preview ─────────────────────
            if (iterator.hasNext()) {
                List<BlockEntry> batch = new ArrayList<>(batchSize);
                for (int i = 0; i < batchSize && iterator.hasNext(); i++)
                    batch.add(iterator.next());

                List<PreviewBlock> preview = buildPreview(world, batch, type);

                // Place preview blocks; keep only those really placed so cleanup is exact
                preview.removeIf(pb -> {
                    Block b = world.getBlockAt(pb.loc());
                    // Only place preview if the block is currently air (don't overwrite already-restored blocks)
                    if (b.getType() != Material.AIR) return true;
                    b.setType(pb.mat(), false);
                    return false;
                });

                // Descent types: spawn real falling-block entities that streak
                // down from the sky and vanish on impact (meteors, suit pieces…).
                spawnDescenders(world, batch, type);

                queue.add(new AnimBatch(batch, preview));
            }

            // ── Done when queue empty AND no more entries ─────────────────────
            if (queue.isEmpty() && !iterator.hasNext()) {
                taskHolder[0].cancel();
                if (onComplete != null) onComplete.run();
            }

        }, 1L, 1L);
        if (taskHolder[0] == null) { // plugin disabled: finish synchronously so nothing is left half-restored
            activeRegens.remove(key);
            while (iterator.hasNext()) placeEntry(world, iterator.next(), dataCache);
            if (onComplete != null) onComplete.run();
        }
    }

    /** Fast path for INSTANT: no preview, but still spread across ticks so big arenas never freeze the server. */
    private void fastPlace(String key, World world, Iterator<BlockEntry> iterator, int total,
                           BiConsumer<Integer, Integer> onProgress, Runnable onComplete) {
        int batchSize = Math.max(1, ConfigManager.getInstance().getConfig()
                .getInt("regen.batch-size", DEFAULT_BATCH_SIZE));
        BukkitTask[] taskHolder = new BukkitTask[1];
        int[] restored = {0};
        Map<String, BlockData> dataCache = new HashMap<>();

        activeRegens.put(key, () -> {
            if (taskHolder[0] != null) taskHolder[0].cancel();
            while (iterator.hasNext()) placeEntry(world, iterator.next(), dataCache);
        });
        taskHolder[0] = AsyncUtil.runSyncRepeating(() -> {
            for (int i = 0; i < batchSize && iterator.hasNext(); i++) {
                placeEntry(world, iterator.next(), dataCache);
                restored[0]++;
            }
            if (onProgress != null) onProgress.accept(restored[0], total);
            if (!iterator.hasNext()) {
                taskHolder[0].cancel();
                if (onComplete != null) onComplete.run();
            }
        }, 1L, 1L);
        if (taskHolder[0] == null) {
            activeRegens.remove(key);
            while (iterator.hasNext()) placeEntry(world, iterator.next(), dataCache);
            if (onComplete != null) onComplete.run();
        }
    }

    /** Places one snapshot block with a single block-data application (parsed data is cached). */
    private void placeEntry(World world, BlockEntry e, Map<String, BlockData> cache) {
        Block b = world.getBlockAt(e.x, e.y, e.z);
        if (e.material == Material.AIR || e.blockData == null) {
            b.setType(e.material, false);
            return;
        }
        BlockData data = cache.get(e.blockData);
        if (data == null) {
            try {
                data = Bukkit.createBlockData(e.blockData);
            } catch (IllegalArgumentException ex) {
                data = e.material.createBlockData();
            }
            cache.put(e.blockData, data);
        }
        b.setBlockData(data, false);
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

    private List<BlockEntry> orderEntries(List<BlockEntry> entries, ArenaRegion region, RegenAnimationType type) {
        List<BlockEntry> ordered = new ArrayList<>(entries);
        double cx = (region.getMinX() + region.getMaxX()) / 2.0;
        double cy = (region.getMinY() + region.getMaxY()) / 2.0;
        double cz = (region.getMinZ() + region.getMaxZ()) / 2.0;

        switch (type) {
            case SPIRAL_ASCENT -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> {
                        double angle = Math.atan2(e.z - cz, e.x - cx);
                        double dist = Math.sqrt(distSq(e, cx, cy, cz));
                        return dist * 6.28 + (angle < 0 ? angle + 6.28 : angle);
                    })
                    .thenComparingInt(e -> e.y));

            case COLLAPSE_INWARD -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> distSq(e, cx, cy, cz))
                    .reversed()
                    .thenComparingInt(e -> e.y));

            case RADIATE_OUTWARD -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> distSq(e, cx, cy, cz))
                    .thenComparingInt(e -> e.y));

            case GRAVITY_DESCENT -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> e.y).reversed()
                    .thenComparingDouble(e -> distSq(e, cx, cy, cz)));

            case UPWARD_SURGE -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> e.y)
                    .thenComparingDouble(e -> distSq(e, cx, cy, cz)));

            case RAIN_OF_LIGHT -> ordered.sort(Comparator
                    .<BlockEntry, Double>comparing(e -> Math.hypot(e.x - cx, e.z - cz))
                    .thenComparingInt(e -> -e.y));

            case ARMORED_DESCENT -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> e.y)
                    .thenComparingDouble(e -> Math.hypot(e.x - cx, e.z - cz)));

            case CHAOTIC_ERUPTION -> Collections.shuffle(ordered, new Random(System.currentTimeMillis()));

            case INSTANT_RESTORE -> { /* as-stored */ }

            case BEACON_WAVE -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> {
                        double a = Math.atan2(e.z - cz, e.x - cx);
                        return a < 0 ? a + 6.28318 : a;
                    })
                    .thenComparingDouble(e -> distSq(e, cx, cy, cz)));

            case VOID_SHOCKWAVE -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> (int) Math.sqrt(distSq(e, cx, cy, cz)))
                    .thenComparingInt(e -> e.y));

            case ICE_CRYSTAL_GROWTH -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> Math.hypot(e.x - cx, e.z - cz))
                    .thenComparingInt(e -> e.y));

            case DARK_VORTEX -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> {
                        double dist = Math.sqrt(distSq(e, cx, cy, cz));
                        double angle = Math.atan2(e.z - cz, e.x - cx);
                        if (angle < 0) angle += 6.28318;
                        return -dist * 6.28318 + angle;
                    })
                    .thenComparingInt(e -> -e.y));

            case METEOR_STRIKE -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> (e.x > cx ? 1 : 0) * 2 + (e.z > cz ? 1 : 0))
                    .thenComparingInt(e -> -e.y)
                    .thenComparingDouble(e -> Math.hypot(e.x - cx, e.z - cz)));

            case PRISM_CASCADE -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> e.x + e.z)
                    .thenComparingInt(e -> e.y));

            // New animations use sensible orderings
            case BINARY_FILL -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> (e.x + e.z) % 2)
                    .thenComparingDouble(e -> distSq(e, cx, cy, cz)));

            case PHOENIX_REBIRTH -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> e.y)
                    .thenComparingDouble(e -> distSq(e, cx, cy, cz)));

            case QUANTUM_MESH -> Collections.shuffle(ordered, new Random(System.currentTimeMillis()));

            case DIAMOND_FUSION -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> distSq(e, cx, cy, cz)));

            case TSUNAMI_SURGE -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> e.y));
        }
        return ordered;
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

    private void saveSnapshot(String arenaName, String worldName, List<String> palette, List<int[]> runs) {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("world", worldName);
        cfg.set("format", 2);
        cfg.set("palette", palette);
        List<String> serialized = new ArrayList<>(runs.size());
        for (int[] r : runs) // x,z,y0,len,paletteIdx
            serialized.add(r[0] + "," + r[1] + "," + r[2] + "," + r[3] + "," + r[4]);
        cfg.set("runs", serialized);
        YamlUtil.save(cfg, snapshotFile(arenaName), plugin.getLogger());
    }

    private List<BlockEntry> loadEntries(FileConfiguration cfg) {
        List<BlockEntry> entries = new ArrayList<>();
        // Format 2: palette + vertical runs
        if (cfg.isList("palette") && cfg.isList("runs")) {
            List<String> palette = cfg.getStringList("palette");
            Material[] mats = new Material[palette.size()];
            String[] datas = new String[palette.size()];
            for (int i = 0; i < mats.length; i++) {
                String[] pp = palette.get(i).split("\\|", 2);
                try { mats[i] = Material.valueOf(pp[0]); } catch (Exception ex) { mats[i] = Material.AIR; }
                datas[i] = pp.length > 1 ? pp[1] : null;
            }
            for (String line : cfg.getStringList("runs")) {
                String[] p = line.split(",");
                if (p.length != 5) continue;
                try {
                    int x = Integer.parseInt(p[0]), z = Integer.parseInt(p[1]);
                    int y0 = Integer.parseInt(p[2]), len = Integer.parseInt(p[3]);
                    int idx = Integer.parseInt(p[4]);
                    if (idx < 0 || idx >= mats.length) continue;
                    for (int y = y0; y < y0 + len; y++)
                        entries.add(new BlockEntry(x, y, z, mats[idx], datas[idx]));
                } catch (Exception ignored) {}
            }
            return entries;
        }
        // Legacy format 1: one line per block "x,y,z|MATERIAL|blockdata"
        for (String line : cfg.getStringList("blocks")) {
            String[] parts = line.split("\\|", 3);
            if (parts.length < 2) continue;
            String[] coords = parts[0].split(",");
            if (coords.length != 3) continue;
            try {
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]);
                Material mat = Material.valueOf(parts[1]);
                String bd = parts.length > 2 ? parts[2] : null;
                entries.add(new BlockEntry(x, y, z, mat, bd));
            } catch (Exception ignored) {}
        }
        return entries;
    }

    // ── Records ────────────────────────────────────────────────────────────────

    private record BlockEntry(int x, int y, int z, Material material, String blockData) {}
    private record LoadResult(String worldName, List<BlockEntry> entries, RegenAnimationType type) {}
    private record PreviewBlock(Location loc, Material mat) {}
    private record AnimBatch(List<BlockEntry> entries, List<PreviewBlock> preview) {}
}
