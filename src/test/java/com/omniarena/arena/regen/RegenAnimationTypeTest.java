package com.omniarena.arena.regen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegenAnimationTypeTest {

    @Test
    void hasFifteenRegenStyles() {
        assertEquals(15, RegenAnimationType.values().length);
    }
}
