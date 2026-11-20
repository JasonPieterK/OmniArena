package com.omniarena.arena.regen;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public enum RegenAnimationType {
    INSTANT,          // no animation, batched placement, one whoosh sound
    SPHERE,           // expanding sphere from the region center (default)
    RISE,             // bottom to top
    FALL,             // top to bottom
    SWEEP,            // vertical plane along the longest horizontal axis
    TIME_REWIND,      // reverse of the order blocks were changed during the match
    GALAXY,           // spiral arms around the center
    HOLOGRAM_SCAN,    // scan plane shows glass previews, then real blocks replace them
    METEOR_SHOWER,    // impact points rebuild their surroundings
    OVERGROWTH;       // flood growth from a few seeds

    public static final RegenAnimationType DEFAULT = SPHERE;

    /** Old (20-style era) names and legacy aliases mapped to the closest current style. */
    private static final Map<String, RegenAnimationType> LEGACY = Map.ofEntries(
            // old 20 enum names
            Map.entry("SPIRAL_ASCENT", GALAXY),
            Map.entry("COLLAPSE_INWARD", SPHERE),
            Map.entry("RADIATE_OUTWARD", SPHERE),
            Map.entry("GRAVITY_DESCENT", FALL),
            Map.entry("UPWARD_SURGE", RISE),
            Map.entry("RAIN_OF_LIGHT", FALL),
            Map.entry("ARMORED_DESCENT", FALL),
            Map.entry("CHAOTIC_ERUPTION", METEOR_SHOWER),
            Map.entry("INSTANT_RESTORE", INSTANT),
            Map.entry("BEACON_WAVE", SWEEP),
            Map.entry("VOID_SHOCKWAVE", SPHERE),
            Map.entry("ICE_CRYSTAL_GROWTH", OVERGROWTH),
            Map.entry("DARK_VORTEX", GALAXY),
            Map.entry("METEOR_STRIKE", METEOR_SHOWER),
            Map.entry("PRISM_CASCADE", HOLOGRAM_SCAN),
            Map.entry("BINARY_FILL", HOLOGRAM_SCAN),
            Map.entry("PHOENIX_REBIRTH", METEOR_SHOWER),
            Map.entry("QUANTUM_MESH", GALAXY),
            Map.entry("DIAMOND_FUSION", OVERGROWTH),
            Map.entry("TSUNAMI_SURGE", SWEEP),
            // older aliases
            Map.entry("TOP_DOWN", FALL),
            Map.entry("TOP_TO_BOTTOM", FALL),
            Map.entry("BOTTOM_UP", RISE),
            Map.entry("BOTTOM_TO_TOP", RISE),
            Map.entry("OUTSIDE_IN", SPHERE),
            Map.entry("CENTER_OUT", SPHERE),
            Map.entry("LASER_RAIN", FALL),
            Map.entry("IRON_MAN", FALL),
            Map.entry("RANDOM_BURST", METEOR_SHOWER),
            Map.entry("BEAM_SWEEP", SWEEP),
            Map.entry("SHOCKWAVE", SPHERE),
            Map.entry("CRYSTAL_GROWTH", OVERGROWTH),
            Map.entry("VORTEX_REBUILD", GALAXY),
            Map.entry("METEOR_DROP", METEOR_SHOWER),
            Map.entry("PRISM_WAVE", HOLOGRAM_SCAN),
            Map.entry("SPIRAL", GALAXY),
            Map.entry("WAVE_SHUFFLE", SPHERE));

    /** Comma separated list of the valid style names. */
    public static String validList() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }

    /** Resolves a config value; blank gives SPHERE silently, unknown gives SPHERE with a console warning. */
    public static RegenAnimationType fromConfig(String value) {
        if (value == null || value.isBlank()) return DEFAULT;
        String v = value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(v);
        } catch (IllegalArgumentException ignored) {
            // fall through to legacy names
        }
        RegenAnimationType mapped = LEGACY.get(v);
        if (mapped != null) return mapped;
        try {
            Logger.getLogger("OmniArena").warning("Unknown regen.style '" + value
                    + "', using " + DEFAULT.name() + ". Valid styles: " + validList());
        } catch (Throwable ignored) {
            // logging must never break config parsing
        }
        return DEFAULT;
    }

    /** Strict parse for commands: current names and legacy names, or null when invalid. */
    public static RegenAnimationType parse(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim().toUpperCase(Locale.ROOT);
        try { return valueOf(v); } catch (IllegalArgumentException ignored) { }
        return LEGACY.get(v);
    }
}
