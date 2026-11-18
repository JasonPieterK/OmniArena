package com.omniarena.arena.regen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RegenAnimationTypeTest {

    @Test
    void hasTenRegenStyles() {
        assertEquals(10, RegenAnimationType.values().length);
    }

    @Test
    void currentNamesResolveToThemselves() {
        for (RegenAnimationType t : RegenAnimationType.values()) {
            assertEquals(t, RegenAnimationType.fromConfig(t.name().toLowerCase()));
        }
    }

    @Test
    void legacyNamesMapToClosestStyle() {
        assertEquals(RegenAnimationType.SPHERE, RegenAnimationType.fromConfig("RADIATE_OUTWARD"));
        assertEquals(RegenAnimationType.SPHERE, RegenAnimationType.fromConfig("CENTER_OUT"));
        assertEquals(RegenAnimationType.RISE, RegenAnimationType.fromConfig("UPWARD_SURGE"));
        assertEquals(RegenAnimationType.RISE, RegenAnimationType.fromConfig("BOTTOM_UP"));
        assertEquals(RegenAnimationType.FALL, RegenAnimationType.fromConfig("GRAVITY_DESCENT"));
        assertEquals(RegenAnimationType.FALL, RegenAnimationType.fromConfig("TOP_DOWN"));
        assertEquals(RegenAnimationType.FALL, RegenAnimationType.fromConfig("RAIN_OF_LIGHT"));
        assertEquals(RegenAnimationType.SWEEP, RegenAnimationType.fromConfig("BEACON_WAVE"));
        assertEquals(RegenAnimationType.SWEEP, RegenAnimationType.fromConfig("TSUNAMI_SURGE"));
        assertEquals(RegenAnimationType.GALAXY, RegenAnimationType.fromConfig("SPIRAL_ASCENT"));
        assertEquals(RegenAnimationType.GALAXY, RegenAnimationType.fromConfig("DARK_VORTEX"));
        assertEquals(RegenAnimationType.GALAXY, RegenAnimationType.fromConfig("QUANTUM_MESH"));
        assertEquals(RegenAnimationType.METEOR_SHOWER, RegenAnimationType.fromConfig("METEOR_STRIKE"));
        assertEquals(RegenAnimationType.METEOR_SHOWER, RegenAnimationType.fromConfig("CHAOTIC_ERUPTION"));
        assertEquals(RegenAnimationType.METEOR_SHOWER, RegenAnimationType.fromConfig("PHOENIX_REBIRTH"));
        assertEquals(RegenAnimationType.OVERGROWTH, RegenAnimationType.fromConfig("ICE_CRYSTAL_GROWTH"));
        assertEquals(RegenAnimationType.OVERGROWTH, RegenAnimationType.fromConfig("DIAMOND_FUSION"));
        assertEquals(RegenAnimationType.HOLOGRAM_SCAN, RegenAnimationType.fromConfig("BINARY_FILL"));
        assertEquals(RegenAnimationType.HOLOGRAM_SCAN, RegenAnimationType.fromConfig("PRISM_CASCADE"));
        assertEquals(RegenAnimationType.INSTANT, RegenAnimationType.fromConfig("INSTANT_RESTORE"));
        assertEquals(RegenAnimationType.INSTANT, RegenAnimationType.fromConfig("INSTANT"));
    }

    @Test
    void unknownAndBlankFallBackToSphere() {
        assertEquals(RegenAnimationType.SPHERE, RegenAnimationType.fromConfig(null));
        assertEquals(RegenAnimationType.SPHERE, RegenAnimationType.fromConfig("  "));
        assertEquals(RegenAnimationType.SPHERE, RegenAnimationType.fromConfig("NOT_A_STYLE"));
    }

    @Test
    void strictParseRejectsUnknown() {
        assertNull(RegenAnimationType.parse("NOT_A_STYLE"));
        assertEquals(RegenAnimationType.GALAXY, RegenAnimationType.parse("galaxy"));
    }
}
