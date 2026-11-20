package com.omniarena.integration;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What right-clicking a bound NPC does. Each action maps to a normal {@code /oa} command run as the
 * clicking player, so permissions, smart errors and isStopped gating all apply unchanged. Pure, unit tested.
 */
public enum NpcAction {
    /** Opens the main menu. */
    MENU(false, false, "Arena Menu"),
    /** Joins the arena given as argument. */
    JOIN(true, true, "Join {arg}"),
    /** Joins the ranked queue (optional kit argument). */
    QUEUE(true, false, "Queue {arg}"),
    /** Spectates the arena given as argument; without one the arena picker opens. */
    SPECTATE(true, false, "Spectate {arg}");

    private static final Pattern ARG = Pattern.compile("[A-Za-z0-9_.-]{1,32}");

    private final boolean takesArg;
    private final boolean argRequired;
    private final String label;

    NpcAction(boolean takesArg, boolean argRequired, String label) {
        this.takesArg = takesArg;
        this.argRequired = argRequired;
        this.label = label;
    }

    public boolean takesArg() { return takesArg; }
    public boolean argRequired() { return argRequired; }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Optional<NpcAction> parse(String s) {
        if (s == null) return Optional.empty();
        for (NpcAction a : values()) if (a.id().equals(s.trim().toLowerCase(Locale.ROOT))) return Optional.of(a);
        return Optional.empty();
    }

    /** Arena and kit names are restricted to a safe charset (they end up in a command line). */
    public static boolean validArg(String arg) {
        return arg != null && ARG.matcher(arg).matches();
    }

    /** True when this action can run with {@code arg} (null/blank = no argument). */
    public boolean accepts(String arg) {
        boolean has = arg != null && !arg.isBlank();
        if (!takesArg) return !has;
        if (argRequired && !has) return false;
        return !has || validArg(arg);
    }

    /** The command (without leading slash) the clicking player runs. */
    public String command(String arg) {
        String a = arg == null || arg.isBlank() ? "" : " " + arg.trim();
        return switch (this) {
            case MENU -> "oa menu";
            case JOIN -> "oa join" + a;
            case QUEUE -> "oa queue" + a;
            case SPECTATE -> "oa spectate" + a;
        };
    }

    /** Default NPC display name. */
    public String defaultName(String arg) {
        String a = arg == null || arg.isBlank() ? "" : arg.trim();
        return label.replace("{arg}", a).trim();
    }
}
