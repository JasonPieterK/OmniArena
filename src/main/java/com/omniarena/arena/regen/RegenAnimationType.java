package com.omniarena.arena.regen;

public enum RegenAnimationType {
    SPIRAL,
    OUTSIDE_IN,
    CENTER_OUT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP,
    LASER_RAIN,
    IRON_MAN,
    RANDOM_BURST,
    INSTANT,
    BEAM_SWEEP,
    SHOCKWAVE,
    CRYSTAL_GROWTH,
    VORTEX_REBUILD,
    METEOR_DROP,
    PRISM_WAVE;

    public static RegenAnimationType fromConfig(String value) {
        if (value == null || value.isBlank()) return CENTER_OUT;
        // legacy name compat
        String v = value.trim().toUpperCase(java.util.Locale.ROOT);
        if (v.equals("TOP_DOWN")) return TOP_TO_BOTTOM;
        if (v.equals("BOTTOM_UP")) return BOTTOM_TO_TOP;
        try {
            return RegenAnimationType.valueOf(v);
        } catch (IllegalArgumentException ignored) {
            return CENTER_OUT;
        }
    }
}
