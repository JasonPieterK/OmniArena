package com.omniarena.commands.admin;

import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.MatchManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * /oa edit &lt;arena&gt; whitelist|blacklist add|remove|list [player]  and
 * /oa edit &lt;arena&gt; whitelist-enabled true|false.
 * Lists are stored per arena in arenas/&lt;name&gt;.yml (access.*). The blacklist always applies; the whitelist
 * only while whitelist-enabled is true. Players with omniarena.join.bypass (and OPs) skip the whitelist.
 */
public final class ArenaAccess {

    private static final String USAGE = "/oa edit <arena> <whitelist|blacklist> <add|remove|list> [player]";

    private ArenaAccess() {}

    /** {@code args}: [arena, whitelist|blacklist, add|remove|list, player]. */
    public static void handle(CommandSender sender, Arena arena, String[] args) {
        if (args.length < 2) { summary(sender, arena); return; }

        String what = args[1].toLowerCase(Locale.ROOT);
        switch (what) {
            case "whitelist", "blacklist" -> list(sender, arena, what, args);
            default -> MessageUtil.send(sender, "<red>Unknown option <yellow>" + args[1] + "</yellow>. <gray>Use whitelist or blacklist. Usage: <white>" + USAGE);
        }
    }

    private static void list(CommandSender sender, Arena arena, String which, String[] args) {
        boolean white = which.equals("whitelist");
        Set<UUID> set = white ? arena.getWhitelist() : arena.getBlacklist();
        if (args.length < 3) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args")
                    .replace("{usage}", "/oa edit " + arena.getName() + " " + which + " <add|remove|list> [player]"));
            return;
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "list" -> {
                MessageUtil.send(sender, "<gold>=== " + arena.getName() + " " + which + " (" + set.size() + ") ===");
                if (white) MessageUtil.send(sender, "<gray>Whitelist is <yellow>" + (arena.isWhitelistEnabled() ? "enabled" : "disabled (not enforced)") + "</yellow>.");
                if (set.isEmpty()) MessageUtil.send(sender, "<gray>Nobody is on the " + which + ".");
                for (UUID id : set) MessageUtil.send(sender, "<gray>- <white>" + nameOf(id));
            }
            case "add", "remove" -> {
                if (args.length < 4) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args")
                            .replace("{usage}", "/oa edit " + arena.getName() + " " + which + " " + action + " <player>"));
                    return;
                }
                OfflinePlayer target = resolve(args[3]);
                UUID id = target != null ? target.getUniqueId() : uuidInSet(set, args[3]);
                if (id == null) {
                    MessageUtil.send(sender, "<red>Unknown player <yellow>" + args[3] + "</yellow>. <gray>They must be online or have joined this server before"
                            + (action.equals("remove") ? ", or already be on the " + which : "") + ". Check the spelling.");
                    return;
                }
                String shown = target != null && target.getName() != null ? target.getName() : nameOf(id);
                if (action.equals("add")) {
                    if (!set.add(id)) {
                        MessageUtil.send(sender, "<yellow>" + shown + "</yellow> <gray>is already on the " + which + " of <yellow>" + arena.getName() + "</yellow>.");
                        return;
                    }
                    ArenaManager.getInstance().save(arena);
                    MessageUtil.send(sender, "<green>Added <yellow>" + shown + "</yellow> to the " + which + " of <yellow>" + arena.getName() + "</yellow>."
                            + (white && !arena.isWhitelistEnabled() ? " <gray>The whitelist is not enforced yet: <white>/oa edit " + arena.getName() + " set whitelist-enabled true</white>." : ""));
                    if (!white) removeFromArena(arena, id, sender);
                } else {
                    if (!set.remove(id)) {
                        MessageUtil.send(sender, "<yellow>" + shown + "</yellow> <gray>is not on the " + which + " of <yellow>" + arena.getName() + "</yellow>. <gray>See <white>/oa edit " + arena.getName() + " " + which + " list</white>.");
                        return;
                    }
                    ArenaManager.getInstance().save(arena);
                    MessageUtil.send(sender, "<green>Removed <yellow>" + shown + "</yellow> from the " + which + " of <yellow>" + arena.getName() + "</yellow>.");
                }
            }
            default -> MessageUtil.send(sender, "<red>Unknown action <yellow>" + args[2] + "</yellow>. <gray>Use add, remove or list.");
        }
    }

    /** A freshly blacklisted player who is inside the arena right now is removed from it. */
    private static void removeFromArena(Arena arena, UUID id, CommandSender sender) {
        Player online = Bukkit.getPlayer(id);
        if (online == null) return;
        MatchManager mm = MatchManager.getInstance();
        Optional<String> in = mm.getArenaName(online);
        if (in.isPresent() && in.get().equalsIgnoreCase(arena.getName())) {
            if (mm.isSpectating(online)) mm.leaveSpectator(online); else mm.leaveArena(online);
            MessageUtil.send(online, ConfigManager.getInstance().getMessage("arena-banned"));
            MessageUtil.send(sender, "<gray>" + online.getName() + " was removed from the arena.");
        }
    }

    private static void summary(CommandSender sender, Arena arena) {
        MessageUtil.send(sender, "<gold>=== Access: " + arena.getName() + " ===");
        MessageUtil.send(sender, "<gray>Whitelist: <yellow>" + (arena.isWhitelistEnabled() ? "enabled" : "disabled") + "</yellow> <gray>(" + arena.getWhitelist().size() + " players)");
        MessageUtil.send(sender, "<gray>Blacklist: <yellow>" + arena.getBlacklist().size() + "</yellow> players <gray>(always enforced)");
        MessageUtil.send(sender, "<gray>Usage: <white>" + USAGE);
    }

    private static void usage(CommandSender sender) {
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", USAGE));
    }

    /** Online player, else a player that has joined this server before (no network lookup). */
    private static OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) return op;
        }
        return null;
    }

    private static UUID uuidInSet(Set<UUID> set, String name) {
        for (UUID id : set) if (nameOf(id).equalsIgnoreCase(name)) return id;
        return null;
    }

    private static String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString();
    }

    /** Tab completion for the args after the arena name: [whitelist|blacklist, add|remove|list, player]. */
    public static List<String> tab(CommandSender sender, Arena arena, String[] rest) {
        String cur = rest.length == 0 ? "" : rest[rest.length - 1];
        if (rest.length == 1) return TabUtil.filter(List.of("whitelist", "blacklist"), cur);
        String w = rest[0].toLowerCase(Locale.ROOT);
        if (rest.length == 2 && (w.equals("whitelist") || w.equals("blacklist"))) return TabUtil.filter(List.of("add", "remove", "list"), cur);
        if (rest.length == 3 && (w.equals("whitelist") || w.equals("blacklist"))) {
            String act = rest[1].toLowerCase(Locale.ROOT);
            if (act.equals("add")) return TabUtil.players(sender, cur);
            if (act.equals("remove")) {
                List<String> names = new ArrayList<>();
                for (UUID id : (w.equals("whitelist") ? arena.getWhitelist() : arena.getBlacklist())) names.add(nameOf(id));
                return TabUtil.filter(names, cur);
            }
        }
        return List.of();
    }
}
