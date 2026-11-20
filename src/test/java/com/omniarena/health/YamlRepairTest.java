package com.omniarena.health;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YamlRepairTest {

    private static MemoryConfiguration defaults() {
        MemoryConfiguration d = new MemoryConfiguration();
        d.set("a.count", 5);
        d.set("a.ratio", 1.5);
        d.set("a.flag", true);
        d.set("a.name", "x");
        d.set("a.list", List.of("one"));
        d.set("top", 10);
        return d;
    }

    @Test
    void backfillsMissingKeys() {
        MemoryConfiguration actual = new MemoryConfiguration();
        actual.set("a.count", 7);
        YamlRepair.Result r = YamlRepair.repair(actual, defaults());
        assertEquals(7, actual.getInt("a.count"));
        assertEquals(10, actual.getInt("top"));
        assertEquals("x", actual.getString("a.name"));
        assertTrue(r.added().contains("top"));
        assertFalse(r.added().contains("a.count"));
        assertTrue(r.retyped().isEmpty());
    }

    @Test
    void restoresWrongTypedValues() {
        MemoryConfiguration actual = new MemoryConfiguration();
        actual.set("a.count", "lots");
        actual.set("a.flag", "maybe");
        actual.set("a.list", "notalist");
        actual.set("a.ratio", 2);
        actual.set("a.name", "custom");
        actual.set("top", 3);
        YamlRepair.Result r = YamlRepair.repair(actual, defaults());
        assertEquals(5, actual.getInt("a.count"));
        assertTrue(actual.getBoolean("a.flag"));
        assertEquals(List.of("one"), actual.getList("a.list"));
        assertEquals(2, actual.getInt("a.ratio")); // any number is fine for a number default
        assertEquals("custom", actual.getString("a.name"));
        assertEquals(3, actual.getInt("top"));
        assertEquals(3, r.retyped().size());
        assertTrue(r.added().isEmpty());
    }

    @Test
    void acceptsCoercibleValuesAndLeavesUnknownKeys() {
        MemoryConfiguration actual = new MemoryConfiguration();
        actual.set("a.count", "12");
        actual.set("a.flag", "true");
        actual.set("a.ratio", 0.25);
        actual.set("a.name", 42);
        actual.set("a.list", List.of());
        actual.set("top", 1);
        actual.set("custom.extra", "keep me");
        YamlRepair.Result r = YamlRepair.repair(actual, defaults());
        assertFalse(r.changed());
        assertEquals("keep me", actual.getString("custom.extra"));
    }

    @Test
    void scalarShadowingASectionIsReplaced() {
        MemoryConfiguration actual = new MemoryConfiguration();
        actual.set("a", "oops");
        YamlRepair.Result r = YamlRepair.repair(actual, defaults());
        assertTrue(r.changed());
        assertEquals(5, actual.getInt("a.count"));
        assertEquals(10, actual.getInt("top"));
    }

    @Test
    void repairIsIdempotent() {
        MemoryConfiguration actual = new MemoryConfiguration();
        actual.set("a.count", "bad");
        YamlRepair.repair(actual, defaults());
        assertFalse(YamlRepair.repair(actual, defaults()).changed());
    }
}
