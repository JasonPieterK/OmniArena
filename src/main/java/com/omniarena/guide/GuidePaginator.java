package com.omniarena.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure logic (no Bukkit) that splits MiniMessage guide text into written-book pages.
 *
 * <p>A book page shows about {@link #ROWS} lines of about {@link #COLS} characters. Source text is handled line by
 * line: a line is wrapped by a word-wrap simulation (bold text counts wider) to find how many rows it needs, lines
 * are packed onto a page until it is full, and a line that alone needs more than a page is cut at spaces. A line
 * that is exactly {@value #PAGE_BREAK} forces a new page. Keep tags that must stay together (a click or hover tag
 * and its text) on one source line; a cut inside a tag is repaired by MiniMessage, which tolerates unclosed tags.
 */
public final class GuidePaginator {

    public static final int ROWS = 14;
    public static final double COLS = 19.0;
    /** Width of a bold character relative to a normal one. */
    static final double BOLD_WIDTH = 1.2;
    public static final String PAGE_BREAK = "<page>";
    /** Written books hold at most 100 pages. */
    public static final int MAX_PAGES = 100;

    private GuidePaginator() {}

    /** Splits {@code text} into pages (MiniMessage strings, lines joined by {@code \n}). Never returns an empty list. */
    public static List<String> paginate(String text) {
        List<String> pages = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int used = 0;
        String src = text == null ? "" : text.replace("\r", "");
        for (String raw : src.split("\n", -1)) {
            if (raw.trim().equalsIgnoreCase(PAGE_BREAK)) {
                used = flush(pages, current);
                continue;
            }
            for (String line : fit(raw)) {
                int r = rows(line);
                if (used + r > ROWS) used = flush(pages, current);
                if (current.isEmpty() && line.isBlank()) continue; // no blank line at the top of a page
                current.add(line);
                used += r;
            }
        }
        flush(pages, current);
        if (pages.isEmpty()) pages.add("");
        return pages.size() > MAX_PAGES ? new ArrayList<>(pages.subList(0, MAX_PAGES)) : pages;
    }

    private static int flush(List<String> pages, List<String> current) {
        while (!current.isEmpty() && current.get(current.size() - 1).isBlank()) current.remove(current.size() - 1);
        if (!current.isEmpty()) pages.add(String.join("\n", current));
        current.clear();
        return 0;
    }

    /** The line itself when it fits on one page, else chunks cut at spaces that each fit. */
    static List<String> fit(String line) {
        if (rows(line) <= ROWS) return List.of(line);
        List<String> out = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (String word : wordsOutsideTags(line)) {
            String candidate = chunk.length() == 0 ? word : chunk + " " + word;
            if (chunk.length() > 0 && rows(candidate) > ROWS) {
                out.add(chunk.toString());
                chunk.setLength(0);
                chunk.append(word);
            } else {
                chunk.setLength(0);
                chunk.append(candidate);
            }
        }
        if (chunk.length() > 0) out.add(chunk.toString());
        return out;
    }

    /** Splits at spaces that are not inside a tag. */
    private static List<String> wordsOutsideTags(String line) {
        List<String> words = new ArrayList<>();
        StringBuilder w = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                w.append(c).append(line.charAt(i + 1));
                i += 2;
            } else if (c == '<') {
                int end = tagEnd(line, i);
                if (end < 0) { w.append(c); i++; }
                else { w.append(line, i, end + 1); i = end + 1; }
            } else if (c == ' ') {
                words.add(w.toString());
                w.setLength(0);
                i++;
            } else {
                w.append(c);
                i++;
            }
        }
        words.add(w.toString());
        return words;
    }

    /** Index of the {@code >} that closes the tag opening at {@code start}, honouring quotes; -1 when none. */
    static int tagEnd(String s, int start) {
        char quote = 0;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') { i++; continue; }
            if (quote != 0) { if (c == quote) quote = 0; continue; }
            if (c == '\'' || c == '"') quote = c;
            else if (c == '>') return i;
            else if (c == '<') return -1;
        }
        return -1;
    }

    /** Number of book rows the line occupies once word-wrapped (a blank line is one row). */
    public static int rows(String line) {
        if (line == null || line.isBlank()) return 1;
        int rows = 1;
        double x = 0;
        double word = 0;
        boolean bold = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                word += width(bold);
                i += 2;
                continue;
            }
            if (c == '<') {
                int end = tagEnd(line, i);
                if (end > 0) {
                    String name = tagName(line.substring(i + 1, end));
                    switch (name) {
                        case "bold", "b" -> bold = true;
                        case "/bold", "/b", "reset" -> bold = false;
                        case "br", "newline" -> {
                            // explicit line break
                            double[] r = place(rows, x, word);
                            rows = (int) r[0] + 1;
                            x = 0;
                            word = 0;
                        }
                        default -> { }
                    }
                    i = end + 1;
                    continue;
                }
            }
            if (c == ' ') {
                double[] r = place(rows, x, word);
                rows = (int) r[0];
                x = r[1];
                word = 0;
            } else {
                word += width(bold);
            }
            i++;
        }
        double[] r = place(rows, x, word);
        return (int) r[0];
    }

    private static double width(boolean bold) { return bold ? BOLD_WIDTH : 1.0; }

    private static String tagName(String tag) {
        int colon = tag.indexOf(':');
        String n = colon >= 0 ? tag.substring(0, colon) : tag;
        return n.trim().toLowerCase(Locale.ROOT);
    }

    /** Places a word at the current row/column; returns {rows, x}. */
    private static double[] place(int rows, double x, double w) {
        if (w <= 0) return new double[]{rows, x};
        if (x == 0) {
            x = w;
        } else if (x + 1 + w <= COLS) {
            x += 1 + w;
        } else {
            rows++;
            x = w;
        }
        while (x > COLS) { // a word wider than a row is broken across rows
            rows++;
            x -= COLS;
        }
        return new double[]{rows, x};
    }

    /** The text a reader sees: tags removed, escapes resolved. */
    public static String visibleText(String line) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) { sb.append(line.charAt(i + 1)); i += 2; continue; }
            if (c == '<') {
                int end = tagEnd(line, i);
                if (end > 0) {
                    String name = tagName(line.substring(i + 1, end));
                    if (name.equals("br") || name.equals("newline")) sb.append('\n');
                    i = end + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}
