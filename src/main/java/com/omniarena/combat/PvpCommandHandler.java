package com.omniarena.combat;

import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The /pvp command: toggle, status, newbie (players) and tag, untag, global, list, override (admins).
 */
public class PvpCommandHandler {

    private static final String ADMIN = "omniarena.pvp.admin";

    private CombatManager cm() { return CombatManager.getInstance(); }
    private ConfigManager cfg() { return ConfigManager.getInstance(); }

    private static final List<String> PLAYER_SUBS = List.of("status", "newbie");
    private static final List<String> ADMIN_SUBS = List.of("tag", "untag", "global", "list", "override");

    /** /pvp (toggle) and its subcommands. */
    public void handle(CommandSender sender, String[] args) {
        if (args.length == 0) { toggle(sender); return; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "status" -> status(sender, rest);
            case "newbie" -> newbie(sender, rest);
            case "tag" -> tag(sender, rest);
            case "untag" -> untag(sender, rest);
            case "global" -> global(sender, rest);
            case "list" -> list(sender);
            case "override" -> override(sender);
            default -> com.omniarena.commands.SmartErrors.unknownSubcommand(sender, "/pvp", args[0], available(sender));
        }
    }

    private List<String> available(CommandSender sender) {
        List<String> all = new java.util.ArrayList<>(PLAYER_SUBS);
        if (isAdminSender(sender)) all.addAll(ADMIN_SUBS);
        return all;
    }

    // /pvp
    private void toggle(CommandSender sender) {
        if (!(sender instanceof Player p)) { MessageUtil.send(sender, cfg().getMessage("player-only")); return; }
        Boolean res = cm().selfTogglePvp(p);
        if (res == null) return;
        MessageUtil.send(p, cfg().getMessage(res ? "pvp-enabled-self" : "pvp-disabled-self"));
    }

    // /pvp status [player]: full info (PvP state, combat tag, newbie protection, world)
    private void status(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, 0);
        if (target == null) return;
        UUID id = target.getUniqueId();
        MessageUtil.send(sender, "<gold>=== PvP: " + target.getName() + " ===");
        MessageUtil.send(sender, "<gray>PvP: " + onOff(cm().isPvpEnabled(target)));
        MessageUtil.send(sender, "<gray>Combat tag: " + (cm().isTagged(id) ? "<red>" + cm().getTagSeconds(id) + "s" : "<green>none"));
        MessageUtil.send(sender, "<gray>Newbie protection: " + (cm().isNewbie(id) ? "<aqua>" + cm().getNewbieSeconds(id) + "s left" : "<gray>no"));
        MessageUtil.send(sender, "<gray>World PvP: " + (cm().isWorldDisabled(target) ? "<red>disabled" : "<green>enabled"));
    }

    // /pvp tag <player> [seconds]
    private void tag(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) { noPerm(sender); return; }
        Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : (sender instanceof Player p ? p : null);
        if (target == null) { MessageUtil.send(sender, "<red>That player is not online (or the name is misspelled). <gray>Names are exact; use tab-complete after the command."); return; }
        int seconds = cfg().getConfig().getInt("pvp.combat.tag-duration", 15);
        if (args.length >= 2) {
            try { seconds = Integer.parseInt(args[1]); } catch (NumberFormatException e) { seconds = -1; }
            if (seconds < 1 || seconds > 3600) {
                MessageUtil.send(sender, "<red>Invalid duration '" + args[1] + "'. Use a whole number of seconds from 1 to 3600, e.g. <white>/pvp tag " + target.getName() + " 30");
                return;
            }
        }
        cm().tag(target, null, seconds);
        MessageUtil.send(sender, "<green>Combat-tagged " + target.getName() + " for " + seconds + "s.");
    }

    // /pvp untag <player>
    private void untag(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) { noPerm(sender); return; }
        Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : (sender instanceof Player p ? p : null);
        if (target == null) { MessageUtil.send(sender, "<red>That player is not online (or the name is misspelled). <gray>Names are exact; use tab-complete after the command."); return; }
        cm().untag(target);
        MessageUtil.send(sender, "<green>Untagged " + target.getName() + ".");
    }

    // /pvp global <on|off>
    private void global(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) { noPerm(sender); return; }
        if (args.length < 1) {
            MessageUtil.send(sender, "<gray>Global PvP is " + onOff(cm().isGlobalPvp()) + "<gray>. Use /pvp global <on|off>.");
            return;
        }
        boolean on = args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("true");
        cm().setGlobalPvp(on);
        com.omniarena.util.MessageUtil.broadcast(com.omniarena.util.ColorUtil.parse(
                cfg().getMessage(on ? "pvp-global-on-broadcast" : "pvp-global-off-broadcast")));
    }

    // /pvp newbie [checktime|disable] | add|remove <player> [seconds]
    private void newbie(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { MessageUtil.send(sender, cfg().getMessage("player-only")); return; }
            int s = cm().getNewbieSeconds(p.getUniqueId());
            MessageUtil.send(p, s > 0 ? "<green>Newbie protection: " + s + "s left." : "<gray>No newbie protection active.");
            return;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("disable")) {
            if (!(sender instanceof Player p)) { MessageUtil.send(sender, cfg().getMessage("player-only")); return; }
            cm().removeNewbie(p.getUniqueId());
            MessageUtil.send(p, cfg().getMessage("newbie-disabled"));
            return;
        }
        if (sub.equals("checktime") && args.length == 1) {
            if (!(sender instanceof Player p)) { MessageUtil.send(sender, cfg().getMessage("player-only")); return; }
            int s = cm().getNewbieSeconds(p.getUniqueId());
            MessageUtil.send(p, s > 0 ? "<green>Newbie protection: " + s + "s left." : "<gray>No newbie protection active.");
            return;
        }
        if (!List.of("add", "remove", "checktime").contains(sub)) {
            com.omniarena.commands.SmartErrors.unknownSubcommand(sender, "/pvp newbie", args[0],
                    isAdminSender(sender) ? List.of("add", "remove", "checktime", "disable") : List.of("checktime", "disable"));
            return;
        }
        if (!sender.hasPermission(ADMIN)) { noPerm(sender); return; }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null;
        switch (sub) {
            case "add" -> {
                if (target == null) { MessageUtil.send(sender, "<red>That player is not online (or the name is misspelled). <gray>Names are exact; use tab-complete after the command."); return; }
                int secs = args.length >= 3 ? parseInt(args[2], 300) : cfg().getConfig().getInt("pvp.newbie.duration", 300);
                cm().grantNewbie(target.getUniqueId(), secs);
                MessageUtil.send(sender, "<green>Gave " + target.getName() + " " + secs + "s newbie protection.");
            }
            case "remove" -> {
                if (target == null) { MessageUtil.send(sender, "<red>That player is not online (or the name is misspelled). <gray>Names are exact; use tab-complete after the command."); return; }
                cm().removeNewbie(target.getUniqueId());
                MessageUtil.send(sender, "<green>Removed newbie protection from " + target.getName() + ".");
            }
            case "checktime" -> {
                if (target == null) { MessageUtil.send(sender, "<red>That player is not online (or the name is misspelled). <gray>Names are exact; use tab-complete after the command."); return; }
                MessageUtil.send(sender, "<gray>" + target.getName() + " newbie: " + cm().getNewbieSeconds(target.getUniqueId()) + "s left.");
            }
            default -> MessageUtil.send(sender, "<red>Usage: <yellow>/pvp newbie [checktime|disable]</yellow> <gray>(admins: add|remove <player> [seconds]), e.g. <white>/pvp newbie checktime</white>.");
        }
    }

    // /pvp list
    private void list(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) { noPerm(sender); return; }
        MessageUtil.send(sender, "<gold>=== Online PvP states ===");
        for (Player p : Bukkit.getOnlinePlayers()) {
            MessageUtil.send(sender, "<gray>" + p.getName() + ": " + onOff(cm().isPvpEnabled(p))
                    + (cm().isTagged(p) ? " <red>[combat]" : "") + (cm().isNewbie(p.getUniqueId()) ? " <aqua>[newbie]" : ""));
        }
    }

    // /pvp override
    private void override(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) { noPerm(sender); return; }
        if (!(sender instanceof Player p)) { MessageUtil.send(sender, cfg().getMessage("player-only")); return; }
        boolean now = cm().toggleOverride(p);
        MessageUtil.send(p, "<green>Attack override: " + onOff(now) + " <gray>(you can " + (now ? "now" : "no longer") + " attack anywhere).");
    }

    // ── tab completion ─────────────────────────────────────────────────────────
    /** True when the sender may use the admin variants of the PvP commands. */
    public boolean isAdminSender(CommandSender sender) {
        return sender.hasPermission(ADMIN) || sender.isOp();
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        boolean admin = isAdminSender(sender);
        if (args.length == 1) return TabUtil.filter(available(sender), cur);
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                if (args.length == 2 && admin) return TabUtil.players(sender, cur);
            }
            case "tag" -> {
                if (!admin) return List.of();
                if (args.length == 2) return TabUtil.players(sender, cur);
                if (args.length == 3) return TabUtil.hint("<seconds>", cur);
            }
            case "untag" -> {
                if (admin && args.length == 2) return TabUtil.players(sender, cur);
            }
            case "global" -> {
                if (admin && args.length == 2) return TabUtil.filter(List.of("on", "off"), cur);
            }
            case "newbie" -> {
                if (args.length == 2) {
                    return TabUtil.filter(admin ? List.of("add", "remove", "checktime", "disable") : List.of("checktime", "disable"), cur);
                }
                if (!admin) return List.of();
                String a = args[1].toLowerCase(Locale.ROOT);
                if (args.length == 3 && List.of("add", "remove", "checktime").contains(a)) return TabUtil.players(sender, cur);
                if (args.length == 4 && a.equals("add")) return TabUtil.hint("<seconds>", cur);
            }
            default -> { }
        }
        return List.of();
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private Player resolveTarget(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) {
            if (!sender.hasPermission(ADMIN)) { noPerm(sender); return null; }
            Player t = Bukkit.getPlayerExact(args[idx]);
            if (t == null) MessageUtil.send(sender, "<red>That player is not online (or the name is misspelled). <gray>Names are exact; use tab-complete after the command.");
            return t;
        }
        if (sender instanceof Player p) return p;
        MessageUtil.send(sender, cfg().getMessage("player-only"));
        return null;
    }

    private void noPerm(CommandSender sender) { MessageUtil.send(sender, cfg().getMessage("no-permission")); }
    private String onOff(boolean b) { return b ? "<green>ON" : "<red>OFF"; }
    private int parseInt(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }
}
