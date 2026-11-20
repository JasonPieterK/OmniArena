package com.omniarena.commands;

import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaState;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import com.omniarena.util.Suggest;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Friendly, actionable command errors: did-you-mean with clickable suggestions, usage plus a
 * clickable example, unknown arena with the list of real arenas, and "what exactly is missing" for
 * arenas that cannot be joined. Texts live in messages.yml.
 */
public final class SmartErrors {

    private SmartErrors() {}

    private static String msg(String key, String... kv) {
        String m = ConfigManager.getInstance().getMessage(key);
        for (int i = 0; i + 1 < kv.length; i += 2) m = m.replace("{" + kv[i] + "}", kv[i + 1]);
        return m;
    }

    private static String raw(String key, String... kv) {
        String m = ConfigManager.getInstance().getRawMessage(key);
        for (int i = 0; i + 1 < kv.length; i += 2) m = m.replace("{" + kv[i] + "}", kv[i + 1]);
        return m;
    }

    /** Unknown subcommand under {@code base} (e.g. "/oa"): did-you-mean or pointer to help. */
    public static void unknownSubcommand(CommandSender sender, String base, String input, Collection<String> options) {
        Optional<String> best = Suggest.closest(input, options);
        if (best.isPresent()) {
            MessageUtil.send(sender, msg("unknown-command-suggest",
                    "input", Suggest.escape(input),
                    "suggestion", Suggest.click(base + " " + best.get())));
        } else {
            MessageUtil.send(sender, msg("unknown-command",
                    "input", Suggest.escape(input),
                    "help", Suggest.run(helpCommand(base), helpCommand(base))));
        }
    }

    private static String helpCommand(String base) {
        return base.equals("/oa") ? "/oa help" : base + " help";
    }

    /** Wrong or missing arguments: usage line plus an optional clickable example. */
    public static void wrongArgs(CommandSender sender, String usage, String example) {
        String text = msg("wrong-args", "usage", Suggest.escape(usage));
        if (example != null && !example.isBlank()) {
            text += raw("wrong-args-example", "example", Suggest.click(example, example, "green"));
        }
        MessageUtil.send(sender, text);
    }

    /** Resolves an arena or tells the sender what went wrong; {@code clickPrefix} e.g. "/oa join ". */
    public static Optional<Arena> arena(CommandSender sender, String input, String clickPrefix) {
        Optional<Arena> found = ArenaManager.getInstance().get(input);
        if (found.isEmpty()) unknownArena(sender, input, clickPrefix);
        return found;
    }

    public static void unknownArena(CommandSender sender, String input, String clickPrefix) {
        Collection<Arena> all = ArenaManager.getInstance().getAll();
        List<String> names = all.stream().map(Arena::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (names.isEmpty()) {
            MessageUtil.send(sender, msg("arena-none", "input", Suggest.escape(input),
                    "setup", Suggest.click("/oa setup " + sanitize(input))));
            return;
        }
        Optional<String> best = Suggest.closest(input, names);
        StringBuilder out = new StringBuilder();
        if (best.isPresent()) {
            out.append(msg("arena-unknown-suggest", "input", Suggest.escape(input),
                    "suggestion", Suggest.click(clickPrefix + best.get())));
        } else {
            out.append(msg("arena-unknown", "input", Suggest.escape(input)));
        }
        String list = names.stream().limit(12)
                .map(n -> Suggest.click(clickPrefix + n, n, "white"))
                .collect(Collectors.joining("<gray>, "));
        out.append(raw("arena-unknown-list", "list", list + (names.size() > 12 ? "<gray>, ..." : "")));
        MessageUtil.send(sender, out.toString());
    }

    private static String sanitize(String input) {
        String s = input == null ? "" : input.replaceAll("[^A-Za-z0-9_-]", "");
        if (s.length() > 32) s = s.substring(0, 32);
        return s.isEmpty() ? "name" : s;
    }

    /**
     * Admin action (teleport, setup): tries to load the arena's missing worlds (through Multiverse-Core when
     * installed). Tells the admin when a world still is not loaded. Returns true when the arena is fully available.
     */
    public static boolean ensureWorldsOrExplain(CommandSender sender, Arena arena) {
        if (!arena.hasMissingWorld()) return true;
        if (ArenaManager.getInstance().ensureWorlds(arena)) {
            MessageUtil.send(sender, msg("arena-world-loaded", "arena", Suggest.escape(arena.getName())));
            return true;
        }
        String w = Suggest.escape(arena.getMissingWorlds().iterator().next());
        MessageUtil.send(sender, msg("arena-world-missing", "world", w)
                + raw("arena-world-missing-hint", "world", w));
        return false;
    }

    /** What is missing before this arena can be played in (admin-facing facts; empty when ready). */
    public static List<String> setupProblems(Arena arena) {
        List<String> missing = new ArrayList<>();
        if (arena.getBattleSpawns().isEmpty()) missing.add("spawn");
        return missing;
    }

    /** Blocking and non-blocking setup gaps as chat lines with the exact fix command, for admins. */
    public static List<String> setupChecklist(Arena arena) {
        String n = arena.getName();
        List<String> lines = new ArrayList<>();
        boolean spawns = !arena.getBattleSpawns().isEmpty();
        lines.add((spawns ? "<green>[ok]" : "<red>[missing]") + " <gray>Battle spawns: <white>" + arena.getBattleSpawns().size()
                + (spawns ? "" : " <gray>- stand on a spawn point and run " + Suggest.click("/oa setup " + n + " spawn")));
        boolean lobby = !arena.getLobbySpawns().isEmpty();
        lines.add((lobby ? "<green>[ok]" : "<yellow>[optional]") + " <gray>Lobby spawn: <white>" + (lobby ? "set" : "not set (battle spawn is used)")
                + (lobby ? "" : " <gray>- " + Suggest.click("/oa setup " + n + " lobby")));
        boolean region = arena.getRegion().isDefined();
        lines.add((region ? "<green>[ok]" : "<yellow>[recommended]") + " <gray>Region: <white>" + (region ? "set" : "not set (no block protection or regen)")
                + (region ? "" : " <gray>- select two corners with the wand, then " + Suggest.click("/oa setup " + n + " region")));
        lines.add((arena.isEnabled() ? "<green>[ok]" : "<red>[off]") + " <gray>Enabled: <white>" + arena.isEnabled()
                + (arena.isEnabled() ? "" : " <gray>- " + Suggest.click("/oa arena enable " + n)));
        return lines;
    }

    /**
     * Checks whether the player can join this arena right now. Returns true when joining may proceed;
     * otherwise tells the player (and admins the fix) exactly what is missing and returns false.
     */
    public static boolean explainNotJoinable(CommandSender sender, Arena arena) {
        String n = arena.getName();
        boolean admin = PermissionChecker.hasAdmin(sender, PermissionChecker.ADMIN_ARENA);
        List<String> reasons = new ArrayList<>();
        if (arena.hasMissingWorld()) {
            String w = Suggest.escape(arena.getMissingWorlds().iterator().next());
            reasons.add("its world '" + w + "' is not loaded" + (admin
                    ? " - load it (e.g. <white>/mv load " + w + "</white> with Multiverse) or run " + Suggest.click("/oa arena tp " + n) + " to try loading it"
                    : " (an admin has to load it)"));
            // nothing else can be judged reliably while the arena's locations are unresolved
            MessageUtil.send(sender, msg("arena-not-ready", "arena", Suggest.escape(n)));
            for (String r : reasons) MessageUtil.send(sender, msg("arena-not-ready-line", "reason", r));
            return false;
        }
        if (!arena.isEnabled()) {
            reasons.add("it is disabled" + (admin ? " - enable it with " + Suggest.click("/oa arena enable " + n) : " (an admin has to enable it)"));
        }
        if (arena.getBattleSpawns().isEmpty()) {
            reasons.add("it has no battle spawn points" + (admin ? " - stand where players should start and run "
                    + Suggest.click("/oa setup " + n + " spawn") : " (an admin has to finish setting it up)"));
        }
        if (reasons.isEmpty() && !arena.isJoinable() && !(arena.isRunning() && arena.isMidMatchJoin())) {
            ArenaState st = arena.getState();
            String why = switch (st) {
                case RUNNING -> "a match is already running (mid-match joining is off) - " + Suggest.click("/oa spectate " + n, "watch it instead", "yellow");
                case ENDING -> "its match is just ending - try again in a few seconds";
                case RESETTING -> "it is being rebuilt after the last match - try again in a moment";
                default -> "it is " + st.name().toLowerCase() + " right now";
            };
            reasons.add(why);
        }
        if (reasons.isEmpty()) return true;
        MessageUtil.send(sender, msg("arena-not-ready", "arena", Suggest.escape(n)));
        for (String r : reasons) MessageUtil.send(sender, msg("arena-not-ready-line", "reason", r));
        MessageUtil.send(sender, msg("arena-not-ready-list", "list", Suggest.click("/oa join")));
        return false;
    }
}
