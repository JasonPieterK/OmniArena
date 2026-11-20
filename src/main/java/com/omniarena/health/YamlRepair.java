package com.omniarena.health;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-logic config repair: back-fills keys missing from a loaded config and restores the default for values
 * whose type cannot be used. Works on any {@link ConfigurationSection} (a {@code MemoryConfiguration} in tests).
 */
public final class YamlRepair {

    private YamlRepair() {}

    /** What a repair pass changed (dotted key paths). */
    public record Result(List<String> added, List<String> retyped) {
        public boolean changed() { return !added.isEmpty() || !retyped.isEmpty(); }
        public int total() { return added.size() + retyped.size(); }
    }

    /** Mutates {@code actual} so that every default key exists with a usable type. */
    public static Result repair(ConfigurationSection actual, ConfigurationSection defaults) {
        List<String> added = new ArrayList<>();
        List<String> retyped = new ArrayList<>();
        for (String key : defaults.getKeys(true)) {
            Object def = defaults.get(key);
            if (def == null || def instanceof ConfigurationSection) continue; // sections are covered by their leaves
            Object cur = actual.get(key);
            if (cur == null) {
                actual.set(key, copy(def));
                added.add(key);
            } else if (cur instanceof ConfigurationSection || !compatible(def, cur)) {
                actual.set(key, copy(def));
                retyped.add(key);
            }
        }
        return new Result(added, retyped);
    }

    /** Whether {@code actual} can stand in for a value of the type of {@code def}. */
    static boolean compatible(Object def, Object actual) {
        if (def instanceof Boolean) {
            return actual instanceof Boolean
                    || (actual instanceof String s && (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")));
        }
        if (def instanceof Number) {
            if (actual instanceof Number) return true;
            if (actual instanceof String s) {
                try { Double.parseDouble(s.trim()); return true; } catch (NumberFormatException e) { return false; }
            }
            return false;
        }
        if (def instanceof String) return actual instanceof String || actual instanceof Number || actual instanceof Boolean;
        if (def instanceof List<?>) return actual instanceof List<?>;
        return true;
    }

    private static Object copy(Object v) {
        return v instanceof List<?> l ? new ArrayList<>(l) : v;
    }
}
