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
    // Animated types use a tighter batch so you see the wave/pattern clearly
    private static final int ANIM_BATCH_SIZE = 150;
    // Faster batch sizes for slow animations (iron man, laser, meteor)
    private static final int FAST_ANIM_BATCH_SIZE = 800;

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
        List<BlockEntry> entries = new ArrayList<>();
        for (int x = region.getMinX(); x <= region.getMaxX(); x++)
            for (int y = region.getMinY(); y <= region.getMaxY(); y++)
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    Block b = world.getBlockAt(x, y, z);
                    entries.add(new BlockEntry(x, y, z, b.getType(), b.getBlockData().getAsString()));
                }
        saveSnapshot(arena.getName(), region.getWorldName(), entries);
        plugin.getLogger().info("Snapshot saved for arena " + arena.getName() + " (" + entries.size() + " blocks).");
        if (onComplete != null) onComplete.run();
    }

    public void restore(Arena arena, Runnable onComplete, Runnable onFail) {
        restore(arena, null, onComplete, onFail);
    }

    public void restore(Arena arena, BiConsumer<Integer, Integer> onProgress,
                        Runnable onComplete, Runnable onFail) {
        File file = snapshotFile(arena.getName());
        if (!file.exists()) { if (onFail != null) onFail.run(); return; }
        AsyncUtil.runAsyncThenSync(
            () -> {
                FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
                return new LoadResult(cfg.getString("world"), loadEntries(cfg));
            },
            result -> {
                World world = result.worldName() != null ? Bukkit.getWorld(result.worldName()) : null;
                if (world == null || result.entries().isEmpty()) {
                    plugin.getLogger().warning("Cannot restore arena " + arena.getName());
                    if (onFail != null) onFail.run();
                    return;
                }
                RegenAnimationType type = getAnimationType();
                List<BlockEntry> ordered = orderEntries(result.entries(), arena.getRegion(), type);
                startPlacing(world, ordered.iterator(), ordered.size(), type, onProgress, onComplete);
            }
        );
    }

    // ── Placement engine ───────────────────────────────────────────────────────

    private void startPlacing(World world, Iterator<BlockEntry> iterator, int total,
                              RegenAnimationType type,
                              BiConsumer<Integer, Integer> onProgress, Runnable onComplete) {

        // Non-animated types: fast direct placement, no preview
        if (type == RegenAnimationType.INSTANT) {
            fastPlace(world, iterator, total, onProgress, onComplete);
            return;
        }

        int configBatch = Math.max(1, ConfigManager.getInstance().getConfig()
                .getInt("regen.batch-size", DEFAULT_BATCH_SIZE));
        // Visual types cap batch to ANIM_BATCH_SIZE so the pattern is visible
        // Use faster batch for slow animations (iron man, laser, meteor)
        int batchSize = Math.min(configBatch, 
                (type == RegenAnimationType.IRON_MAN || type == RegenAnimationType.LASER_RAIN || type == RegenAnimationType.METEOR_DROP) 
                ? FAST_ANIM_BATCH_SIZE : ANIM_BATCH_SIZE);

        boolean sfx = ConfigManager.getInstance().getConfig().getBoolean("regen.animation.sound", true);

        BukkitTask[] taskHolder = new BukkitTask[1];
        int[] restored = {0};

        // Queue: each AnimBatch holds the preview block locations + the real entries
        Deque<AnimBatch> queue = new ArrayDeque<>();

        taskHolder[0] = AsyncUtil.runSyncRepeating(() -> {

            // ── Step 1: Finalize previous batch ──────────────────────────────
            if (!queue.isEmpty()) {
                AnimBatch prev = queue.poll();

                // Remove preview blocks (always remove to prevent permanent blocks)
                for (PreviewBlock pb : prev.preview()) {
                    Block b = world.getBlockAt(pb.loc());
                    // Remove if it's still the preview material OR if it's a temporary block
                    // that shouldn't be there (prevents trash blocks)
                    if (b.getType() == pb.mat() || isTemporaryBlock(b.getType())) {
                        b.setType(Material.AIR, false);
                    }
                }

                // Place real blocks
                for (BlockEntry e : prev.entries()) {
                    Block b = world.getBlockAt(e.x, e.y, e.z);
                    b.setType(e.material, false);
                    if (e.material != Material.AIR && e.blockData != null)
                        b.setBlockData(Bukkit.createBlockData(e.blockData), false);
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

                // Place preview blocks
                for (PreviewBlock pb : preview) {
                    Block b = world.getBlockAt(pb.loc());
                    // Only place preview if the block is currently air (don't overwrite already-restored blocks)
                    if (b.getType() == Material.AIR)
                        b.setType(pb.mat(), false);
                }

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
    }

    /** Fast path for INSTANT — no preview, no delay, truly instant */
    private void fastPlace(World world, Iterator<BlockEntry> iterator, int total,
                           BiConsumer<Integer, Integer> onProgress, Runnable onComplete) {
        // Place ALL blocks in a single tick - truly instant
        int restored = 0;
        while (iterator.hasNext()) {
            BlockEntry e = iterator.next();
            Block b = world.getBlockAt(e.x, e.y, e.z);
            b.setType(e.material, false);
            if (e.material != Material.AIR && e.blockData != null)
                b.setBlockData(Bukkit.createBlockData(e.blockData), false);
            restored++;
        }
        if (onProgress != null) onProgress.accept(restored, total);
        if (onComplete != null) onComplete.run();
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

            // 1. SPIRAL — gold spiral path lights up just before blocks appear
            case SPIRAL -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.GOLD_BLOCK));

            // 2. OUTSIDE_IN — cyan ring shrinks inward; cyan concrete marks the frontier
            case OUTSIDE_IN -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.CYAN_CONCRETE));

            // 3. CENTER_OUT — yellow/orange shockwave expanding outward
            case CENTER_OUT -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.YELLOW_CONCRETE));

            // 4. TOP_TO_BOTTOM — gravel (gravity block) placed 2 above each spot, looks like it's about to fall
            case TOP_TO_BOTTOM -> {
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.GRAVEL));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 2, e.z), Material.SAND));
            }

            // 5. BOTTOM_TO_TOP — lime block rises up; honeycomb gives "bubbling up" feel
            case BOTTOM_TO_TOP -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.LIME_CONCRETE));

            // 6. LASER_RAIN — the descent is done by real falling blocks
            //    (see spawnDescenders). Leave a single glowing impact pad so the
            //    spot lights up the instant the round lands.
            case LASER_RAIN -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.SEA_LANTERN));

            // 7. IRON_MAN — descent done by real falling red-concrete suit pieces.
            //    The landing spot flashes an arc-reactor cyan pad as it locks in.
            case IRON_MAN -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.LIGHT_BLUE_CONCRETE));

            // 8. RANDOM_BURST — glowstone pops at random offsets around each block
            case RANDOM_BURST -> {
                int ox = RNG.nextInt(3) - 1;
                int oz = RNG.nextInt(3) - 1;
                out.add(new PreviewBlock(loc(world, e.x + ox, e.y, e.z + oz), Material.GLOWSTONE));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.SEA_LANTERN));
            }

            // 9. BEAM_SWEEP — beacon beam: beacon + sea lantern column rotates as the sweep angle advances
            case BEAM_SWEEP -> {
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.SEA_LANTERN));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.BEACON));
            }

            // 10. SHOCKWAVE — crying obsidian ring: eerie purple ring expands outward
            case SHOCKWAVE -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.CRYING_OBSIDIAN));

            // 11. CRYSTAL_GROWTH — amethyst bud, then cluster above, grows column-by-column:
            //     ice crystals form from the floor up
            case CRYSTAL_GROWTH -> {
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.AMETHYST_BLOCK));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.BLUE_ICE));
            }

            // 12. VORTEX_REBUILD — emerald + end stone: spiraling pillar sucks blocks into vortex
            case VORTEX_REBUILD -> {
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.EMERALD_BLOCK));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 1, e.z), Material.END_STONE));
                out.add(new PreviewBlock(loc(world, e.x, e.y + 2, e.z), Material.PURPUR_BLOCK));
            }

            // 13. METEOR_DROP — descent done by real flaming falling magma meteors
            //    (see spawnDescenders). Leave a charred impact pad at the crater.
            case METEOR_DROP -> out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), Material.MAGMA_BLOCK));

            // 14. PRISM_WAVE — diagonal rainbow wave of colored concrete (7 hues, X+Z wavefront)
            case PRISM_WAVE -> {
                Material[] rainbow = {
                    Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE,
                    Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
                    Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE
                };
                Material mat = rainbow[Math.abs(e.x + e.z) % 7];
                out.add(new PreviewBlock(loc(world, e.x, e.y, e.z), mat));
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
            case METEOR_DROP -> step = 20;  // a few big, dramatic meteors (increased from 28)
            case IRON_MAN    -> step = 5;    // a steady rain of suit pieces (increased from 9)
            case LASER_RAIN  -> step = 4;    // dense rain of laser rounds (increased from 7)
            default -> { return; }          // ground types don't descend
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
            case METEOR_DROP -> {
                spawnHeight = 26; fallMat = Material.MAGMA_BLOCK; downVel = -1.1;
                trail = Particle.FLAME; dust = null; lifeTicks = 24;
            }
            case IRON_MAN -> {
                spawnHeight = 20; fallMat = Material.RED_CONCRETE; downVel = -1.4;
                trail = Particle.SOUL_FIRE_FLAME;  // arc-reactor blue jet
                dust = new Particle.DustOptions(Color.fromRGB(255, 196, 0), 1.2f); // gold trim sparks
                lifeTicks = 16;
            }
            default -> { // LASER_RAIN
                spawnHeight = 24; fallMat = Material.WHITE_STAINED_GLASS; downVel = -2.2;
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
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void playImpact(World world, Location loc, RegenAnimationType type) {
        switch (type) {
            case METEOR_DROP -> {
                world.spawnParticle(Particle.EXPLOSION, loc, 2, 0.2, 0.1, 0.2, 0.0);
                world.spawnParticle(Particle.LAVA, loc, 8, 0.4, 0.2, 0.4, 0.0);
                world.spawnParticle(Particle.LARGE_SMOKE, loc, 10, 0.5, 0.3, 0.5, 0.02);
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.6f);
            }
            case IRON_MAN -> {
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 14, 0.3, 0.2, 0.3, 0.1);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 6, 0.25, 0.15, 0.25, 0.02);
                world.playSound(loc, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.7f, 0.9f);
                world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.3f, 1.6f);
            }
            default -> { // LASER_RAIN
                world.spawnParticle(Particle.FLASH, loc, 1, 0.0, 0.0, 0.0, 0.0);
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 10, 0.3, 0.1, 0.3, 0.12);
                world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 2.0f);
            }
        }
    }

    // ── Ordering ───────────────────────────────────────────────────────────────

    private List<BlockEntry> orderEntries(List<BlockEntry> entries, ArenaRegion region, RegenAnimationType type) {
        List<BlockEntry> ordered = new ArrayList<>(entries);
        double cx = (region.getMinX() + region.getMaxX()) / 2.0;
        double cy = (region.getMinY() + region.getMaxY()) / 2.0;
        double cz = (region.getMinZ() + region.getMaxZ()) / 2.0;

        switch (type) {
            case SPIRAL -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> {
                        double angle = Math.atan2(e.z - cz, e.x - cx);
                        double dist = Math.sqrt(distSq(e, cx, cy, cz));
                        return dist * 6.28 + (angle < 0 ? angle + 6.28 : angle);
                    })
                    .thenComparingInt(e -> e.y));

            case OUTSIDE_IN -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> distSq(e, cx, cy, cz))
                    .reversed()
                    .thenComparingInt(e -> e.y));

            case CENTER_OUT -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> distSq(e, cx, cy, cz))
                    .thenComparingInt(e -> e.y));

            case TOP_TO_BOTTOM -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> e.y).reversed()
                    .thenComparingDouble(e -> distSq(e, cx, cy, cz)));

            case BOTTOM_TO_TOP -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> e.y)
                    .thenComparingDouble(e -> distSq(e, cx, cy, cz)));

            // LASER_RAIN: nearest columns first, each column top→bottom
            case LASER_RAIN -> ordered.sort(Comparator
                    .<BlockEntry, Double>comparing(e -> Math.hypot(e.x - cx, e.z - cz))
                    .thenComparingInt(e -> -e.y));

            // IRON_MAN: scan-line (left→right, front→back, bottom→top) like suit assembly
            // IRON_MAN: suit assembles onto the body bottom-up (feet → head),
            // each layer filling from the core outward like plates locking on.
            case IRON_MAN -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> e.y)
                    .thenComparingDouble(e -> Math.hypot(e.x - cx, e.z - cz)));

            case RANDOM_BURST -> Collections.shuffle(ordered, new Random(System.currentTimeMillis()));

            case INSTANT -> { /* as-stored */ }

            // BEAM_SWEEP: pure angular sweep (radar arm)
            case BEAM_SWEEP -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> {
                        double a = Math.atan2(e.z - cz, e.x - cx);
                        return a < 0 ? a + 6.28318 : a;
                    })
                    .thenComparingDouble(e -> distSq(e, cx, cy, cz)));

            // SHOCKWAVE: concentric rings expanding outward
            case SHOCKWAVE -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> (int) Math.sqrt(distSq(e, cx, cy, cz)))
                    .thenComparingInt(e -> e.y));

            // CRYSTAL_GROWTH: floor columns closest-first, growing upward
            case CRYSTAL_GROWTH -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> Math.hypot(e.x - cx, e.z - cz))
                    .thenComparingInt(e -> e.y));

            // VORTEX_REBUILD: anti-clockwise inward spiral, top-down
            case VORTEX_REBUILD -> ordered.sort(Comparator
                    .comparingDouble((BlockEntry e) -> {
                        double dist = Math.sqrt(distSq(e, cx, cy, cz));
                        double angle = Math.atan2(e.z - cz, e.x - cx);
                        if (angle < 0) angle += 6.28318;
                        return -dist * 6.28318 + angle;
                    })
                    .thenComparingInt(e -> -e.y));

            // METEOR_DROP: quadrant clusters, highest Y first per cluster
            case METEOR_DROP -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> (e.x > cx ? 1 : 0) * 2 + (e.z > cz ? 1 : 0))
                    .thenComparingInt(e -> -e.y)
                    .thenComparingDouble(e -> Math.hypot(e.x - cx, e.z - cz)));

            // PRISM_WAVE: diagonal X+Z wavefront (rainbow sweeps corner to corner)
            case PRISM_WAVE -> ordered.sort(Comparator
                    .comparingInt((BlockEntry e) -> e.x + e.z)
                    .thenComparingInt(e -> e.y));
        }
        return ordered;
    }

    // ── Sound per wave ─────────────────────────────────────────────────────────

    private void playWaveSound(World world, BlockEntry e, RegenAnimationType type) {
        Location loc = new Location(world, e.x + 0.5, e.y + 0.5, e.z + 0.5);
        switch (type) {
            case SPIRAL        -> world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.3f, 1.5f);
            case OUTSIDE_IN    -> world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM,      0.3f, 1.2f);
            case CENTER_OUT    -> world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE,         0.2f, 1.8f);
            case TOP_TO_BOTTOM -> world.playSound(loc, Sound.BLOCK_GRAVEL_PLACE,             0.4f, 0.8f);
            case BOTTOM_TO_TOP -> world.playSound(loc, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.3f, 1.4f);
            case LASER_RAIN -> {
                world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE,           0.4f, 2.0f);
                world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,   0.15f, 1.5f);
            }
            case IRON_MAN -> {
                world.playSound(loc, Sound.ITEM_ARMOR_EQUIP_IRON,            0.5f, 0.7f);
                world.playSound(loc, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE,  0.4f, 1.3f);
                if (RNG.nextInt(6) == 0)
                    world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE,       0.2f, 1.5f);
            }
            case RANDOM_BURST  -> world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST,  0.3f, 1.0f + RNG.nextFloat());
            case BEAM_SWEEP    -> world.playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT,      0.3f, 1.8f);
            case SHOCKWAVE     -> world.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM,       0.4f, 0.7f);
            case CRYSTAL_GROWTH -> {
                world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME,  0.5f, 1.4f);
                world.playSound(loc, Sound.BLOCK_GLASS_PLACE,            0.25f, 1.8f);
            }
            case VORTEX_REBUILD -> world.playSound(loc, Sound.ENTITY_ENDER_EYE_LAUNCH,      0.35f, 1.2f);
            case METEOR_DROP -> {
                world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE,  0.3f, 0.6f);
                world.playSound(loc, Sound.ENTITY_BLAZE_HURT,        0.2f, 0.5f);
            }
            case PRISM_WAVE    -> world.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_PLACE,   0.3f,
                    1.0f + (Math.abs(e.x + e.z) % 7) * 0.1f);
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

    private void saveSnapshot(String arenaName, String worldName, List<BlockEntry> entries) {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("world", worldName);
        cfg.set("count", entries.size());
        List<String> serialized = new ArrayList<>(entries.size());
        for (BlockEntry e : entries)
            serialized.add(e.x + "," + e.y + "," + e.z + "|" + e.material.name() + "|" + e.blockData);
        cfg.set("blocks", serialized);
        YamlUtil.save(cfg, snapshotFile(arenaName), plugin.getLogger());
    }

    private List<BlockEntry> loadEntries(FileConfiguration cfg) {
        List<BlockEntry> entries = new ArrayList<>();
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
    private record LoadResult(String worldName, List<BlockEntry> entries) {}
    private record PreviewBlock(Location loc, Material mat) {}
    private record AnimBatch(List<BlockEntry> entries, List<PreviewBlock> preview) {}
}
