package com.omniarena.banner;

import java.util.Locale;
import java.util.Map;

/**
 * Decides how (and whether) the startup banner is shown from the config value and the environment.
 * Pure: environment, TTY state and terminal size are passed in, so it is unit tested.
 */
public final class BannerTerminal {

    public enum Style { FULL_ANIMATED, FULL_STATIC, COMPACT, OFF }

    public record Decision(Style style, ColorMode color) {}

    /** Environment variables that identify a hosting panel, whose consoles cannot redraw lines in place. */
    private static final String[] PANEL_VARS = {"P_SERVER_UUID", "P_SERVER_LOCATION"};

    private BannerTerminal() {}

    /**
     * @param config   value of {@code general.startup-banner} (full, compact, off; anything else means full)
     * @param env      process environment
     * @param tty      true when stdout is attached to a real terminal
     * @param rows     terminal rows if known, else 0
     * @param columns  terminal columns if known, else 0
     */
    public static Decision decide(String config, Map<String, String> env, boolean tty, int rows, int columns) {
        String cfg = config == null ? "full" : config.trim().toLowerCase(Locale.ROOT);
        ColorMode color = colorMode(env);
        if (cfg.equals("off") || cfg.equals("false") || cfg.equals("none")) return new Decision(Style.OFF, color);
        if (cfg.equals("compact")) return new Decision(Style.COMPACT, color);

        boolean dumb = "dumb".equalsIgnoreCase(get(env, "TERM"));
        boolean animate = tty && !dumb && !isPanel(env) && color != ColorMode.NONE
                && (rows <= 0 || rows >= BannerRenderer.FULL_HEIGHT + 2)
                && (columns <= 0 || columns >= BannerRenderer.MIN_WIDTH);
        return new Decision(animate ? Style.FULL_ANIMATED : Style.FULL_STATIC, color);
    }

    public static boolean isPanel(Map<String, String> env) {
        for (String v : PANEL_VARS) if (!get(env, v).isEmpty()) return true;
        return false;
    }

    /** NO_COLOR (any non-empty value) turns colour off; otherwise the richest mode the environment advertises. */
    public static ColorMode colorMode(Map<String, String> env) {
        if (!get(env, "NO_COLOR").isEmpty()) return ColorMode.NONE;
        String colorTerm = get(env, "COLORTERM").toLowerCase(Locale.ROOT);
        if (colorTerm.contains("truecolor") || colorTerm.contains("24bit")) return ColorMode.TRUECOLOR;
        if (isPanel(env) || !get(env, "WT_SESSION").isEmpty()) return ColorMode.TRUECOLOR;
        String term = get(env, "TERM").toLowerCase(Locale.ROOT);
        if (term.contains("256")) return ColorMode.ANSI256;
        for (String t : new String[]{"xterm", "screen", "tmux", "alacritty", "kitty", "wezterm", "foot"}) {
            if (term.contains(t)) return ColorMode.ANSI256;
        }
        return ColorMode.ANSI16;
    }

    private static String get(Map<String, String> env, String key) {
        String v = env == null ? null : env.get(key);
        return v == null ? "" : v.trim();
    }
}
