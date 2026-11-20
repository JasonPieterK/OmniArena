package com.omniarena.arena;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArenaWorldsTest {

    @Test
    void worldOfReadsTheFirstSegment() {
        assertEquals("world_nether", Arena.worldOf("world_nether:1.0:64.0:2.0:0.0:0.0"));
        assertEquals("w", Arena.worldOf("w"));
        assertNull(Arena.worldOf(""));
        assertNull(Arena.worldOf(null));
        assertNull(Arena.worldOf(":1:2:3"));
    }

    @Test
    void missingWorldsMakeArenaUnavailableUntilLoaded() {
        Arena a = new Arena("duel");
        a.setState(ArenaState.WAITING);
        assertTrue(a.isJoinable());

        a.getUnresolvedSpawns(SpawnType.BATTLE).add("pvp_world:1.0:64.0:1.0:0.0:0.0");
        a.getUnresolvedSpawns(SpawnType.LOBBY).add("hub:0.0:70.0:0.0:0.0:0.0");
        assertTrue(a.recomputeMissingWorlds(w -> w.equals("hub")));
        assertEquals(Set.of("pvp_world"), a.getMissingWorlds());
        assertTrue(a.hasMissingWorld());
        assertFalse(a.isJoinable());
        assertTrue(a.isEnabled(), "state (and so the saved 'enabled' flag) is untouched");

        assertFalse(a.recomputeMissingWorlds(w -> w.equals("hub")), "no change reported twice");

        a.getUnresolvedSpawns(SpawnType.BATTLE).clear();
        assertTrue(a.recomputeMissingWorlds(w -> true));
        assertFalse(a.hasMissingWorld());
        assertTrue(a.isJoinable());
    }

    @Test
    void regionWorldCountsAsNeeded() {
        Arena a = new Arena("r");
        ArenaRegion r = new ArenaRegion();
        r.setWorldName("arena_world");
        a.setRegion(r);
        assertTrue(a.recomputeMissingWorlds(w -> false));
        assertEquals(Set.of("arena_world"), a.getMissingWorlds());
        assertTrue(a.recomputeMissingWorlds(w -> true));
        assertTrue(a.getMissingWorlds().isEmpty());
    }
}
