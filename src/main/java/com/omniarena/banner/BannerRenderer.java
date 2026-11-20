package com.omniarena.banner;

import java.util.List;
import java.util.Locale;

/**
 * Builds the neubrutalist console banner as plain lines (optionally with ANSI colour). Pure logic: no Bukkit, no
 * I/O, no clock, so every frame is reproducible and unit tested.
 *
 * <p>Layout is designed on a 72 column grid; a wider {@code width} only adds black margin on both sides. Every
 * returned line has exactly {@code width} visible columns.
 */
public final class BannerRenderer {

    /** Logical width of the design; also the minimum width. */
    public static final int MIN_WIDTH = 72;
    public static final int FULL_HEIGHT = 31;
    public static final int COMPACT_HEIGHT = 8;
    public static final int SHUTDOWN_HEIGHT = 6;

    private static final int SLIDE_END = 10;
    private static final int FINAL = 44;

    private static final String[][] LOGO = {
            // O
            {" ███ ", "█   █", "█   █", "█   █", "█   █", " ███ "},
            // M
            {"█   █", "██ ██", "█ █ █", "█   █", "█   █", "█   █"},
            // N
            {"█   █", "██  █", "█ █ █", "█  ██", "█   █", "█   █"},
            // I
            {"█████", "  █  ", "  █  ", "  █  ", "  █  ", "█████"},
            // A
            {" ███ ", "█   █", "█████", "█   █", "█   █", "█   █"},
            // R
            {"████ ", "█   █", "████ ", "█ █  ", "█  █ ", "█   █"},
            // E
            {"█████", "█    ", "████ ", "█    ", "█    ", "█████"},
            // N
            {"█   █", "██  █", "█ █ █", "█  ██", "█   █", "█   █"},
            // A
            {" ███ ", "█   █", "█████", "█   █", "█   █", "█   █"},
    };
    private static final int LOGO_WIDTH = LOGO.length * 6 - 1; // 53
    private static final String TAGLINE = "PVP ARENAS  ·  KITS  ·  RANKED  ·  TOURNAMENTS";

    private final ColorMode mode;
    private final int width;

    public BannerRenderer(ColorMode mode, int width) {
        this.mode = mode == null ? ColorMode.NONE : mode;
        this.width = Math.max(MIN_WIDTH, Math.min(200, width));
    }

    public int width() { return width; }

    /** Index of the last (static) animation frame. */
    public int finalFrame() { return FINAL; }

    /** Number of frames in the animation, including the final one. */
    public int frameCount() { return FINAL + 1; }

    public List<String> fullFinal(BannerData d) { return full(d, FINAL); }

    // ── Full banner ─────────────────────────────────────────────────────────────────────

    /** One animation frame; any index at or beyond {@link #finalFrame()} yields the final static frame. */
    public List<String> full(BannerData d, int frame) {
        boolean fin = frame >= FINAL;
        int f = fin ? FINAL : Math.max(0, frame);
        Canvas c = canvas(FULL_HEIGHT);

        int shift = fin ? 0 : f;
        stripe(c, 0, shift);
        stripe(c, FULL_HEIGHT - 1, -shift);

        drawCard(c, f, fin);

        // Stickers (stamp in one after another)
        String[] labels = {"VERSION", "PLATFORM", "MINECRAFT", "JAVA"};
        String[] values = {"v" + d.version(), d.platform(), d.minecraft(), d.java()};
        int[] colours = {Tone.YELLOW, Tone.PINK, Tone.CYAN, Tone.LIME};
        for (int i = 0; i < 4; i++) {
            int[] o = panel(c, 1 + i * 18, 13, 15, 3, "[ " + labels[i] + " ]", Tone.WHITE, colours[i],
                    stage(f, 12 + 3 * i, fin));
            if (o != null) c.text(o[0] + 2, o[1] + 1, fit(clean(values[i]), 11), Tone.BLACK, colours[i], true);
        }

        // Loaded stats
        int[] o = panel(c, 1, 17, 27, 6, "[ LOADED ]", Tone.PINK, Tone.BLACK, stage(f, 24, fin));
        if (o != null) {
            String[] k = {"Arenas", "Kits", "Players", "Startup"};
            String[] v = {String.valueOf(d.arenas()), String.valueOf(d.kits()), String.valueOf(d.players()), d.startupMs() + " ms"};
            for (int i = 0; i < 4; i++) {
                c.text(o[0] + 2, o[1] + 1 + i, k[i], Tone.WHITE, Tone.BLACK, false);
                c.text(o[0] + 24 - v[i].length(), o[1] + 1 + i, v[i], Tone.PINK, Tone.BLACK, true);
            }
        }

        // Integrations
        o = panel(c, 31, 17, 39, 6, "[ INTEGRATIONS ]", Tone.CYAN, Tone.BLACK, stage(f, 27, fin));
        if (o != null) {
            List<BannerData.Hook> hooks = d.integrations();
            for (int i = 0; i < Math.min(8, hooks.size()); i++) {
                BannerData.Hook h = hooks.get(i);
                int hx = o[0] + 2 + (i / 4) * 18;
                int hy = o[1] + 1 + (i % 4);
                c.text(hx, hy, h.ok() ? "✔" : "✖", h.ok() ? Tone.LIME : Tone.PINK, Tone.BLACK, true);
                c.text(hx + 2, hy, fit(display(h.name()), 14), Tone.WHITE, Tone.BLACK, false);
            }
        }

        // Health and web
        o = panel(c, 1, 25, 27, 3, "[ HEALTH ]", Tone.LIME, Tone.BLACK, stage(f, 30, fin));
        if (o != null) healthLine(c, o[0] + 2, o[1] + 1, d, 23);
        o = panel(c, 31, 25, 39, 3, "[ WEB ]", Tone.YELLOW, Tone.BLACK, stage(f, 33, fin));
        if (o != null) webLine(c, o[0] + 2, o[1] + 1, d, 35);

        // Footer tip bar
        int fs = stage(f, 36, fin);
        if (fs > 0) {
            int bar = fs == 1 ? Tone.WHITE : Tone.LIME;
            c.fill(1, 29, 70, 1, bar);
            c.text(2, 29, " TIP  run /oa guide start", Tone.BLACK, bar, true);
            String by = "by Jason P ";
            c.text(70 - by.length(), 29, by, Tone.BLACK, bar, true);
        }
        return c.render(mode);
    }

    private void drawCard(Canvas c, int f, boolean fin) {
        shadow(c, 1, 1, 69, 10);
        box(c, 1, 1, 69, 10, Tone.WHITE, Tone.BLACK);
        c.text(2, 1, "[ PVP ARENA ]", Tone.BLACK, Tone.PINK, true);

        int lx = 2 + (67 - (LOGO_WIDTH + 1)) / 2;
        int off = fin || f >= SLIDE_END ? 0 : (int) Math.round(Math.pow(1 - f / (double) (SLIDE_END - 1), 2) * 50);
        int wave = fin || f < SLIDE_END ? Integer.MIN_VALUE : (f - 9) * 2 - 2;
        // hard offset shadow of the logo first, then the logo on top
        for (int pass = 0; pass < 2; pass++) {
            for (int li = 0; li < LOGO.length; li++) {
                for (int gy = 0; gy < 6; gy++) {
                    String row = LOGO[li][gy];
                    for (int k = 0; k < 5; k++) {
                        if (row.charAt(k) != '█') continue;
                        int col = li * 6 + k;
                        int x = lx + off + col + (pass == 0 ? 1 : 0);
                        int y = 2 + gy + (pass == 0 ? 1 : 0);
                        if (x < 2 || x > 68) continue;
                        if (pass == 0) c.put(x, y, shadowGlyph(), Tone.PINK, Tone.BLACK, false);
                        else c.put(x, y, '█', waveColour(wave - col), Tone.BLACK, true);
                    }
                }
            }
        }

        c.fill(2, 9, 67, 1, Tone.CYAN);
        int shown = fin ? TAGLINE.length() : Math.min(TAGLINE.length(), f * 4);
        int tx = 2 + (67 - TAGLINE.length()) / 2;
        for (int i = 0; i < shown; i++) c.put(tx + i, 9, TAGLINE.charAt(i), Tone.BLACK, Tone.CYAN, true);
    }

    private static int waveColour(int d) {
        if (d >= 0 && d < 2) return Tone.WHITE;
        if (d >= 2 && d < 5) return Tone.CYAN;
        if (d >= 5 && d < 8) return Tone.LIME;
        return Tone.YELLOW;
    }

    // ── Compact banner (static, 8 lines) ─────────────────────────────────────────────

    public List<String> compact(BannerData d) {
        Canvas c = canvas(COMPACT_HEIGHT);
        shadow(c, 1, 0, 69, 6);
        box(c, 1, 0, 69, 6, Tone.WHITE, Tone.BLACK);
        c.text(2, 0, "[ v" + fit(clean(d.version()), 20) + " ]", Tone.BLACK, Tone.PINK, true);

        c.fill(2, 1, 67, 1, Tone.YELLOW);
        c.text(3, 1, " OMNIARENA ", Tone.BLACK, Tone.YELLOW, true);
        String right = fit(clean(d.platform()), 14) + " " + fit(clean(d.minecraft()), 12) + " · Java " + fit(clean(d.java()), 14) + " ";
        c.text(68 - right.length(), 1, right, Tone.BLACK, Tone.YELLOW, true);

        c.text(3, 2, d.arenas() + " arenas · " + d.kits() + " kits · " + d.players() + " players · " + d.startupMs() + " ms",
                Tone.WHITE, Tone.BLACK, false);

        int x = 3;
        for (BannerData.Hook h : d.integrations()) {
            String name = fit(display(h.name()), 14);
            if (x + name.length() + 2 > 67) break;
            c.text(x, 3, h.ok() ? "✔" : "✖", h.ok() ? Tone.LIME : Tone.PINK, Tone.BLACK, true);
            c.text(x + 1, 3, name, Tone.WHITE, Tone.BLACK, false);
            x += name.length() + 3;
        }

        healthLine(c, 3, 4, d, 20);
        webLine(c, 30, 4, d, 37);

        c.fill(1, 7, 70, 1, Tone.LIME);
        c.text(2, 7, " TIP  run /oa guide start", Tone.BLACK, Tone.LIME, true);
        return c.render(mode);
    }

    // ── Shutdown banner (static, 6 lines) ─────────────────────────────────────────────

    public List<String> shutdown(String version, long uptimeMs) {
        Canvas c = canvas(SHUTDOWN_HEIGHT);
        shadow(c, 1, 0, 69, 4);
        box(c, 1, 0, 69, 4, Tone.WHITE, Tone.BLACK);
        c.text(2, 0, "[ SHUTDOWN ]", Tone.BLACK, Tone.PINK, true);

        c.fill(2, 1, 67, 1, Tone.YELLOW);
        c.text(3, 1, " OMNIARENA ", Tone.BLACK, Tone.YELLOW, true);
        String v = "v" + fit(clean(version), 20) + " ";
        c.text(68 - v.length(), 1, v, Tone.BLACK, Tone.YELLOW, true);

        c.text(3, 2, "✔", Tone.LIME, Tone.BLACK, true);
        c.text(5, 2, "matches ended · stats and arenas saved", Tone.WHITE, Tone.BLACK, false);
        String up = "up " + formatUptime(uptimeMs);
        c.text(68 - up.length(), 2, up, Tone.CYAN, Tone.BLACK, true);

        c.fill(1, 5, 70, 1, Tone.CYAN);
        c.text(2, 5, " SEE YOU NEXT ROUND", Tone.BLACK, Tone.CYAN, true);
        return c.render(mode);
    }

    // ── Plain log line ─────────────────────────────────────────────────────────────────

    /** One-line summary for log files; never contains colour codes. */
    public static String plainSummary(BannerData d) {
        StringBuilder sb = new StringBuilder();
        sb.append("OmniArena v").append(d.version()).append(" enabled in ").append(d.startupMs()).append(" ms | ")
                .append(d.platform()).append(' ').append(d.minecraft()).append(" | Java ").append(d.java())
                .append(" | ").append(d.arenas()).append(" arena(s), ").append(d.kits()).append(" kit(s), ")
                .append(d.players()).append(" player(s) with stats | integrations: ");
        if (d.integrations().isEmpty()) sb.append("none");
        for (int i = 0; i < d.integrations().size(); i++) {
            BannerData.Hook h = d.integrations().get(i);
            if (i > 0) sb.append(", ");
            sb.append(h.name()).append(h.ok() ? " on" : " off");
        }
        sb.append(" | health: ").append(d.repairs() == 0 ? "ok" : d.repairs() + " repair(s), run /oa doctor");
        sb.append(" | web: ").append(d.webUrl() == null ? "off" : clean(d.webUrl()));
        return sb.toString();
    }

    public static String formatUptime(long ms) {
        long s = Math.max(0, ms) / 1000;
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    // ── Drawing helpers ─────────────────────────────────────────────────────────────────

    private Canvas canvas(int height) {
        Canvas c = new Canvas(width, height, Tone.BLACK);
        c.originX = (width - MIN_WIDTH) / 2;
        return c;
    }

    private char shadowGlyph() { return mode == ColorMode.NONE ? '░' : '█'; }

    private void stripe(Canvas c, int y, int shift) {
        String plain = "█▓▒░";
        for (int x = 1; x <= 70; x++) {
            int idx = Math.floorMod(Math.floorDiv(x + shift, 4), 4);
            c.put(x, y, mode == ColorMode.NONE ? plain.charAt(idx) : '█', Tone.CYCLE[idx], Tone.BLACK, true);
        }
    }

    /** Hard offset drop shadow: a column on the right and a row underneath, both shifted by one. */
    private void shadow(Canvas c, int x, int y, int w, int h) {
        for (int yy = y + 1; yy < y + h; yy++) c.put(x + w, yy, '▓', Tone.SHADOW, Tone.BLACK, false);
        for (int xx = x + 1; xx <= x + w; xx++) c.put(xx, y + h, '▓', Tone.SHADOW, Tone.BLACK, false);
    }

    private void box(Canvas c, int x, int y, int w, int h, int border, int fill) {
        c.fill(x, y, w, h, fill);
        for (int xx = x + 1; xx < x + w - 1; xx++) {
            c.put(xx, y, '━', border, fill, true);
            c.put(xx, y + h - 1, '━', border, fill, true);
        }
        for (int yy = y + 1; yy < y + h - 1; yy++) {
            c.put(x, yy, '┃', border, fill, true);
            c.put(x + w - 1, yy, '┃', border, fill, true);
        }
        c.put(x, y, '┏', border, fill, true);
        c.put(x + w - 1, y, '┓', border, fill, true);
        c.put(x, y + h - 1, '┗', border, fill, true);
        c.put(x + w - 1, y + h - 1, '┛', border, fill, true);
    }

    /** 0 = not yet drawn, 1 = lifted (the "stamp" is about to land), 2 = settled. */
    private static int stage(int f, int appear, boolean fin) {
        if (fin || f > appear) return 2;
        return f == appear ? 1 : 0;
    }

    /** Draws a sticker/panel and returns the origin of its (possibly lifted) body, or null while hidden. */
    private int[] panel(Canvas c, int x, int y, int w, int h, String tag, int tagBg, int fill, int stage) {
        if (stage == 0) return null;
        shadow(c, x, y, w, h);
        int bx = stage == 1 ? x - 1 : x;
        int by = stage == 1 ? y - 1 : y;
        box(c, bx, by, w, h, Tone.WHITE, fill);
        c.text(bx + 1, by, tag, Tone.BLACK, tagBg, true);
        return new int[]{bx, by};
    }

    private void healthLine(Canvas c, int x, int y, BannerData d, int max) {
        if (d.repairs() == 0) {
            c.text(x, y, "✔", Tone.LIME, Tone.BLACK, true);
            c.text(x + 2, y, "all clear", Tone.WHITE, Tone.BLACK, false);
        } else {
            c.text(x, y, "✖", Tone.PINK, Tone.BLACK, true);
            c.text(x + 2, y, fit(d.repairs() + " repaired: /oa doctor", max - 2), Tone.WHITE, Tone.BLACK, false);
        }
    }

    private void webLine(Canvas c, int x, int y, BannerData d, int max) {
        if (d.webUrl() == null) {
            c.text(x, y, "✖", Tone.PINK, Tone.BLACK, true);
            c.text(x + 2, y, "dashboard off", Tone.WHITE, Tone.BLACK, false);
        } else {
            c.text(x, y, "✔", Tone.LIME, Tone.BLACK, true);
            c.text(x + 2, y, fit(clean(d.webUrl()), max - 2), Tone.CYAN, Tone.BLACK, false);
        }
    }

    // ── Text helpers ────────────────────────────────────────────────────────────────────

    /** Strips control characters so untrusted text can never inject escape sequences. */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            sb.append(ch < 32 || ch == 127 || (ch >= 0x80 && ch < 0xA0) ? '?' : ch);
        }
        return sb.toString();
    }

    static String fit(String s, int max) {
        if (s.length() <= max) return s;
        return max <= 1 ? s.substring(0, Math.max(0, max)) : s.substring(0, max - 1) + "…";
    }

    static String display(String hook) {
        String h = clean(hook).toLowerCase(Locale.ROOT);
        return switch (h) {
            case "vault" -> "Vault";
            case "placeholderapi" -> "PlaceholderAPI";
            case "worldguard" -> "WorldGuard";
            case "luckperms" -> "LuckPerms";
            case "decentholograms" -> "DecentHolo";
            case "fancyholograms" -> "FancyHolo";
            case "citizens" -> "Citizens";
            case "multiverse" -> "Multiverse";
            default -> h.isEmpty() ? "?" : Character.toUpperCase(h.charAt(0)) + h.substring(1);
        };
    }
}
