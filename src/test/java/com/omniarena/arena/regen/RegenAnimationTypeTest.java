package com.omniarena.arena.regen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegenAnimationTypeTest {

    @Test
    void hasTwentyRegenStyles() {
        assertEquals(20, RegenAnimationType.values().length);
    }
}
