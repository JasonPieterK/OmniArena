package com.omniarena.banner;

import java.util.Arrays;

/** Fixed-size character grid with per-cell colours; serialises to one ANSI string per row. Pure, no Bukkit. */
final class Canvas {

    static final int DEFAULT = -1;

    private static final int[] ANSI16_RGB = {
            0x000000, 0xCD0000, 0x00CD00, 0xCDCD00, 0x0000EE, 0xCD00CD, 0x00CDCD, 0xE5E5E5,
            0x7F7F7F, 0xFF0000, 0x00FF00, 0xFFFF00, 0x5C5CFF, 0xFF00FF, 0x00FFFF, 0xFFFFFF};

    final int width;
    final int height;
    /** Horizontal offset applied to every draw call (used to centre the banner in a wider console). */
    int originX;

    private final char[] ch;
    private final int[] fg;
    private final int[] bg;
    private final boolean[] bold;

    Canvas(int width, int height, int background) {
        this.width = width;
        this.height = height;
        int n = width * height;
        ch = new char[n];
        fg = new int[n];
        bg = new int[n];
        bold = new boolean[n];
        Arrays.fill(ch, ' ');
        Arrays.fill(fg, DEFAULT);
        Arrays.fill(bg, background);
    }

    void put(int x, int y, char c, int fgColor, int bgColor, boolean isBold) {
        x += originX;
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        int i = y * width + x;
        ch[i] = c;
        fg[i] = fgColor;
        if (bgColor != DEFAULT) bg[i] = bgColor;
        bold[i] = isBold;
    }

    void text(int x, int y, String s, int fgColor, int bgColor, boolean isBold) {
        for (int i = 0; i < s.length(); i++) put(x + i, y, s.charAt(i), fgColor, bgColor, isBold);
    }

    void fill(int x, int y, int w, int h, int bgColor) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) put(xx, yy, ' ', DEFAULT, bgColor, false);
        }
    }

    /** Renders every row. With {@link ColorMode#NONE} the rows contain no escape codes at all. */
    java.util.List<String> render(ColorMode mode) {
        java.util.List<String> rows = new java.util.ArrayList<>(height);
        for (int y = 0; y < height; y++) {
            StringBuilder sb = new StringBuilder(width * 3);
            int lastFg = Integer.MIN_VALUE, lastBg = Integer.MIN_VALUE;
            boolean lastBold = false, first = true;
            for (int x = 0; x < width; x++) {
                int i = y * width + x;
                if (mode != ColorMode.NONE && (first || fg[i] != lastFg || bg[i] != lastBg || bold[i] != lastBold)) {
                    sb.append("\u001b[0");
                    if (bold[i]) sb.append(";1");
                    if (fg[i] != DEFAULT) sb.append(';').append(code(mode, fg[i], false));
                    if (bg[i] != DEFAULT) sb.append(';').append(code(mode, bg[i], true));
                    sb.append('m');
                    lastFg = fg[i];
                    lastBg = bg[i];
                    lastBold = bold[i];
                    first = false;
                }
                sb.append(ch[i]);
            }
            if (mode != ColorMode.NONE) sb.append("\u001b[0m");
            rows.add(sb.toString());
        }
        return rows;
    }

    /** SGR parameter for a colour (without the leading ESC[ and trailing m). */
    static String code(ColorMode mode, int rgb, boolean background) {
        switch (mode) {
            case TRUECOLOR:
                return (background ? "48;2;" : "38;2;") + ((rgb >> 16) & 255) + ";" + ((rgb >> 8) & 255) + ";" + (rgb & 255);
            case ANSI256:
                return (background ? "48;5;" : "38;5;") + to256(rgb);
            default: {
                int idx = Tone.ansi16(rgb);
                int base = idx < 8 ? (background ? 40 : 30) : (background ? 100 : 90);
                return String.valueOf(base + (idx & 7));
            }
        }
    }

    static int to256(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int[] levels = {0, 95, 135, 175, 215, 255};
        int ri = nearestLevel(levels, r), gi = nearestLevel(levels, g), bi = nearestLevel(levels, b);
        int cubeDist = dist(r, g, b, levels[ri], levels[gi], levels[bi]);
        int avg = (r + g + b) / 3;
        int gi2 = Math.max(0, Math.min(23, Math.round((avg - 8) / 10f)));
        int grey = 8 + 10 * gi2;
        int greyDist = dist(r, g, b, grey, grey, grey);
        return greyDist < cubeDist ? 232 + gi2 : 16 + 36 * ri + 6 * gi + bi;
    }

    private static int nearestLevel(int[] levels, int v) {
        int best = 0;
        for (int i = 1; i < levels.length; i++) if (Math.abs(levels[i] - v) < Math.abs(levels[best] - v)) best = i;
        return best;
    }

    private static int dist(int r1, int g1, int b1, int r2, int g2, int b2) {
        return (r1 - r2) * (r1 - r2) + (g1 - g2) * (g1 - g2) + (b1 - b2) * (b1 - b2);
    }

    static int nearest16(int rgb) {
        int best = 0, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < ANSI16_RGB.length; i++) {
            int c = ANSI16_RGB[i];
            int d = dist((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (c >> 16) & 255, (c >> 8) & 255, c & 255);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }
}
