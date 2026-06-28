package com.omniarena.arena.regen;

public enum RegenAnimationType {
    // ── Original 15 animations (renamed for clarity) ──
    SPIRAL_ASCENT,           // 1. Blocks spiral upward in a helix pattern
    COLLAPSE_INWARD,         // 2. Walls collapse inward from all sides
    RADIATE_OUTWARD,         // 3. Blocks expand outward from center
    GRAVITY_DESCENT,         // 4. Blocks fall from above like rain
    UPWARD_SURGE,            // 5. Blocks rise up from ground
    RAIN_OF_LIGHT,           // 6. Glowing laser rain descends
    ARMORED_DESCENT,         // 7. Red armored suit pieces fall
    CHAOTIC_ERUPTION,        // 8. Random bursts of glowstone
    INSTANT_RESTORE,         // 9. Instant placement (no animation)
    BEACON_WAVE,             // 10. Beacon light sweeps across arena
    VOID_SHOCKWAVE,          // 11. Purple void shockwave expands
    ICE_CRYSTAL_GROWTH,      // 12. Ice crystals grow from floor
    DARK_VORTEX,             // 13. Emerald vortex spirals inward
    METEOR_STRIKE,           // 14. Meteors rain down with fire
    PRISM_CASCADE,           // 15. Rainbow colored wave pattern

    // ── New 5 animations ──
    BINARY_FILL,             // 16. Digital matrix-style with cyan and lime
    PHOENIX_REBIRTH,         // 17. Fire/lava blocks rise like phoenix
    QUANTUM_MESH,            // 18. Glitchy purple/cyan teleport effect
    DIAMOND_FUSION,          // 19. Blue diamond/ice blocks grow inward
    TSUNAMI_SURGE;           // 20. Water-like wave with aqua blocks

    public static RegenAnimationType fromConfig(String value) {
        if (value == null || value.isBlank()) return RADIATE_OUTWARD;
        // legacy name compat
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        // Old names map to new ones
        if (v.equals("TOP_DOWN") || v.equals("TOP_TO_BOTTOM")) return GRAVITY_DESCENT;
        if (v.equals("BOTTOM_UP") || v.equals("BOTTOM_TO_TOP")) return UPWARD_SURGE;
        if (v.equals("OUTSIDE_IN")) return COLLAPSE_INWARD;
        if (v.equals("CENTER_OUT")) return RADIATE_OUTWARD;
        if (v.equals("LASER_RAIN")) return RAIN_OF_LIGHT;
        if (v.equals("IRON_MAN")) return ARMORED_DESCENT;
        if (v.equals("RANDOM_BURST")) return CHAOTIC_ERUPTION;
        if (v.equals("BEAM_SWEEP")) return BEACON_WAVE;
        if (v.equals("SHOCKWAVE")) return VOID_SHOCKWAVE;
        if (v.equals("CRYSTAL_GROWTH")) return ICE_CRYSTAL_GROWTH;
        if (v.equals("VORTEX_REBUILD")) return DARK_VORTEX;
        if (v.equals("METEOR_DROP")) return METEOR_STRIKE;
        if (v.equals("PRISM_WAVE")) return PRISM_CASCADE;
        if (v.equals("INSTANT")) return INSTANT_RESTORE;
        if (v.equals("SPIRAL")) return SPIRAL_ASCENT;
        try {
            return RegenAnimationType.valueOf(v);
        } catch (IllegalArgumentException ignored) {
            return RADIATE_OUTWARD;
        }
    }
}
