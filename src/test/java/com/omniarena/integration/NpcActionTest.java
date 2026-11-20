package com.omniarena.integration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcActionTest {

    @Test
    void parseAndCommands() {
        assertEquals(NpcAction.JOIN, NpcAction.parse("JOIN").orElseThrow());
        assertTrue(NpcAction.parse("fly").isEmpty());
        assertEquals("oa menu", NpcAction.MENU.command(null));
        assertEquals("oa join duel_1", NpcAction.JOIN.command("duel_1"));
        assertEquals("oa queue", NpcAction.QUEUE.command(null));
        assertEquals("oa queue ranked", NpcAction.QUEUE.command("ranked"));
        assertEquals("oa spectate", NpcAction.SPECTATE.command(""));
    }

    @Test
    void argumentRules() {
        assertTrue(NpcAction.MENU.accepts(null));
        assertFalse(NpcAction.MENU.accepts("x"));
        assertFalse(NpcAction.JOIN.accepts(null));
        assertTrue(NpcAction.JOIN.accepts("arena-1"));
        assertTrue(NpcAction.QUEUE.accepts(null));
        assertTrue(NpcAction.SPECTATE.accepts(null));
        // never splice odd text into a command line
        assertFalse(NpcAction.JOIN.accepts("a b"));
        assertFalse(NpcAction.JOIN.accepts("a;op me"));
        assertFalse(NpcAction.QUEUE.accepts("x".repeat(33)));
    }

    @Test
    void defaultNames() {
        assertEquals("Arena Menu", NpcAction.MENU.defaultName(null));
        assertEquals("Join duel_1", NpcAction.JOIN.defaultName("duel_1"));
        assertEquals("Queue", NpcAction.QUEUE.defaultName(null));
    }

    // Reflect helper (used for optional plugin APIs)

    public static class Sample {
        public String create(String name, int n, boolean flag, List<String> lines) { return name + n + flag + lines.size(); }
        public String one(Object o) { return "obj"; }
    }

    @Test
    void reflectMatchesBoxedPrimitivesAndAssignableArgs() throws Exception {
        Sample s = new Sample();
        assertEquals("a3true2", Reflect.invoke(s, "create", "a", 3, true, new java.util.ArrayList<>(List.of("x", "y"))));
        assertEquals("obj", Reflect.invoke(s, "one", (Object) null));
        Method m = Reflect.find(Sample.class, "create", "a", 3, true, List.of());
        assertNotNull(m);
        assertNull(Reflect.find(Sample.class, "create", "a", "notint", true, List.of()));
        assertNull(Reflect.find(Sample.class, "missing"));
        assertThrows(IllegalStateException.class, () -> Reflect.invoke(s, "missing"));
        assertThrows(IllegalStateException.class, () -> Reflect.invoke(null, "x"));
    }
}
