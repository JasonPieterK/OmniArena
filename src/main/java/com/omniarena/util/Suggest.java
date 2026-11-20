package com.omniarena.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Pure "did you mean" helpers (no Bukkit): Levenshtein-based closest match and MiniMessage
 * snippets for clickable command suggestions.
 */
public final class Suggest {

    private Suggest() {}

    /** Classic Levenshtein edit distance, case-insensitive. */
    public static int distance(String a, String b) {
        String s = a == null ? "" : a.toLowerCase(Locale.ROOT);
        String t = b == null ? "" : b.toLowerCase(Locale.ROOT);
        int[] prev = new int[t.length() + 1];
        int[] cur = new int[t.length() + 1];
        for (int j = 0; j <= t.length(); j++) prev[j] = j;
        for (int i = 1; i <= s.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= t.length(); j++) {
                int cost = s.charAt(i - 1) == t.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[t.length()];
    }

    /** Largest edit distance still considered "close" for an input of this length. */
    public static int maxDistance(String input) {
        return (input == null ? 0 : input.length()) / 3 + 1;
    }

    /** Closest option within {@link #maxDistance}; ties go to the earliest option. Empty when nothing is close. */
    public static Optional<String> closest(String input, Collection<String> options) {
        List<String> all = closestN(input, options, 1);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /** Up to {@code n} close options, best first (stable for equal distances). */
    public static List<String> closestN(String input, Collection<String> options, int n) {
        if (input == null || input.isBlank() || options == null || n < 1) return List.of();
        int max = maxDistance(input);
        String in = input.toLowerCase(Locale.ROOT);
        List<String> pool = new ArrayList<>();
        List<Integer> dist = new ArrayList<>();
        for (String o : options) {
            if (o == null) continue;
            int d = distance(input, o);
            // a typed prefix of an option ("spec" -> "spectate") is always a good hint
            if (d > max && in.length() >= 2 && o.toLowerCase(Locale.ROOT).startsWith(in)) d = max;
            if (d <= max) {
                pool.add(o);
                dist.add(d);
            }
        }
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < pool.size(); i++) idx.add(i);
        idx.sort(Comparator.comparingInt(dist::get)); // List.sort is stable
        List<String> out = new ArrayList<>();
        for (int i : idx) {
            out.add(pool.get(i));
            if (out.size() >= n) break;
        }
        return out;
    }

    /** Makes arbitrary text safe to embed in a MiniMessage string (no tag injection). */
    public static String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("<", "\\<");
    }

    /** MiniMessage: yellow command text that pre-fills the command in chat when clicked. */
    public static String click(String command) {
        return "<click:suggest_command:'" + quote(command) + "'><yellow>" + escape(command) + "</yellow></click>";
    }

    /** MiniMessage: like {@link #click(String)} with custom visible text and a color tag name. */
    public static String click(String command, String label, String color) {
        return "<click:suggest_command:'" + quote(command) + "'><" + color + ">" + escape(label) + "</" + color + "></click>";
    }

    /** MiniMessage: clicking runs the command immediately. */
    public static String run(String command, String label) {
        return "<click:run_command:'" + quote(command) + "'><yellow>" + escape(label) + "</yellow></click>";
    }

    private static String quote(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
