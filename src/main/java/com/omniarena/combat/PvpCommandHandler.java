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
 * Handles all the PvP / combat player + admin commands. Wired into /oa as
 * subcommands (pvp, pvpstatus, pvptag, untag, pvpglobal, newbie, pvpinfo,
 * pvplist, pvpoverride) — the old PvPManager /pmr prefix is now /oa.
 */
public class PvpCommandHandler {

    private static final String ADMIN = "omniarena.pvp.admin";

    private CombatManager cm() { return CombatManager.getInstance(); }
    private ConfigManager cfg() { return ConfigManager.getInstance(); }

    public void dispatch(String name, CommandSender sender, String[] args) {
        switch (name) {
            case "pvp"        -> pvp(sender, args);
            case "pvpstatus"  -> pvpstatus(sender, args);
            case "pvptag"     -> pvptag(sender, args);
            case "untag"      -> untag(sender, args);
            case "pvpglobal"  -> pvpglobal(sender, args);
            case "newbie"     -> newbie(sender, args);
            case "pvpinfo"    -> pvpinfo(sender, args);
            case "pvplist"    -> pvplist(sender, args);
            case "pvpoverride"-> pvpoverride(sender, args);
            default -> MessageUtil.send(sender, "<red>Unknown PvP command. <gray>Valid: <white>/oa pvp, pvpstatus, pvptag, untag, pvpglobal, newbie, pvpinfo, pvplist, pvpoverride</white>.");
        }
    }

    // /oa pvp [player]
    private void pvp(CommandSender sender, String[] args) {
        if (args.length >= 1 && sender.hasPermission(ADMIN)) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { MessageUtil.send(sender, "<red>That player is not online (or the name is misspelled). <gray>Names are exact; use tab-complete after the command."); return; }
            boolean now = cm().togglePvp(target);
            MessageUtil.send(sender, "<green>" + target.getName() + " PvP: " + onOff(now));
            MessageUtil.send(target, cfg().getMessage(now ? "pvp-enabled-self" : "pvp-disabled-self"));
            return;
        }
        if (!(sender instanceof Player p)) { MessageUtil.send(sender, cfg().getMessage("player-only")); return; }
        boolean now = cm().togglePvp(p);
        MessageUtil.send(p, cfg().getMessage(now ? "pvp-enabled-self" : "pvp-disabled-self"));
    }

    // /oa pvpstatus [player]
    private void pvpstatus(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, 0);
        if (target == null) return;
        MessageUtil.send(sender, "<gray>" + target.getName() + " PvP is " + onOff(cm().isPvpEnabled(target))
                + "<gray>. Combat: " + (cm().isTagged(target) ? "<red>tagged (" + cm().getTagSeconds(target.getUniqueId()) + "s)" : "<green>clear"));
    }

    // /oa pvptag [player] [seconds]
    private void pvptag(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) { noPerm(sender); return; }
        Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : (sender instanceof Player p ? p : null);
        if (target == null) { MessageUtil.send(sender, "<red>That player is not online (or the name is misspelled). <gray>Names are exact; use tab-complete after the command."); return; }
        int seconds = cfg().getConfig().getInt("pvp.combat.tag-duration", 15);
        if (args.length >= 2) {
            try { seconds = Integer.parseInt(args[1]); } catch (NumberFormatException e) { seconds = -1; }
            if (seconds < 1 || seconds > 3600) {
                MessageUtil.send(sender, "<red>Invalid duration '" + args[1] + "'. Use a whole number of seconds from 1 to 3600, e.g. <white>/oa pvptag " + target.getName() + " 30");
                return;
            }
        }
        cm().tag(target, null, seconds);
        MessageUtil.send(sender, "<green>Combat-tagged " + target.getName() + " for " + seconds + "s.");
    }

    // /oa untag [player]
    private void untag(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) { noPerm(sender); return; }
        Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : (sender instanceof Player p ? p : null);
        if (target == null) { MessageUtil.send(sender, "<red>That player is not online (or the name is misspelled). <gray>Names are exact; use tab-complete after the command."); return; }
        cm().untag(target);
        MessageUtil.send(sender, "<green>Untagged " + target.getName() + ".");
    }

    // /oa pvpglobal <on|off>
    private void pvpglobal(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) { noPerm(sender); return; }
        if (args.length < 1) {
            MessageUtil.send(sender, "<gray>Global PvP is " + onOff(cm().isGlobalPvp()) + "<gray>. Use /oa pvpglobal <on|off>.");
            return;
        }
        boolean on = args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("true");
        cm().setGlobalPvp(on);
        Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                cfg().getMessage(on ? "pvp-global-on-broadcast" : "pvp-global-off-broadcast")));
    }

    // /oa newbie [add/remove/checktime] [player] [seconds]
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
            default -> MessageUtil.send(sender, "<red>Usage: <yellow>/oa newbie <add|remove|checktime|disable> [player] [seconds]</yellow> <gray>- pick one action, e.g. <white>/oa newbie checktime</white>.");
        }
    }

    // /oa pvpinfo [player]
    private void pvpinfo(CommandSender sender, String[] args) {
        Player target = resolveTarget(sender, args, 0);
        if (target == null) return;
        UUID id = target.getUniqueId();
        MessageUtil.send(sender, "<gold>=== PvP info: " + target.getName() + " ===");
        MessageUtil.send(sender, "<gray>PvP: " + onOff(cm().isPvpEnabled(target)));
        MessageUtil.send(sender, "<gray>Combat tag: " + (cm().isTagged(id) ? "<red>" + cm().getTagSeconds(id) + "s" : "<green>none"));
        MessageUtil.send(sender, "<gray>Newbie: " + (cm().isNewbie(id) ? "<aqua>" + cm().getNewbieSeconds(id) + "s" : "<gray>no"));
        MessageUtil.send(sender, "<gray>Override: " + onOff(cm().isOverriding(target)));
        MessageUtil.send(sender, "<gray>World PvP: " + (cm().isWorldDisabled(target) ? "<red>disabled" : "<green>enabled"));
    }

    // /oa pvplist
    private void pvplist(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) { noPerm(sender); return; }
        MessageUtil.send(sender, "<gold>=== Online PvP states ===");
        for (Player p : Bukkit.getOnlinePlayers()) {
            MessageUtil.send(sender, "<gray>" + p.getName() + ": " + onOff(cm().isPvpEnabled(p))
                    + (cm().isTagged(p) ? " <red>[combat]" : "") + (cm().isNewbie(p.getUniqueId()) ? " <aqua>[newbie]" : ""));
        }
    }

    // /oa pvpoverride
    private void pvpoverride(CommandSender sender, String[] args) {
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

    public List<String> tabComplete(String name, CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        boolean admin = isAdminSender(sender);
        switch (name) {
            case "pvp", "pvpstatus", "pvpinfo", "untag" -> {
                if (args.length == 1 && admin) return TabUtil.players(sender, cur);
            }
            case "pvptag" -> {
                if (!admin) return List.of();
                if (args.length == 1) return TabUtil.players(sender, cur);
                if (args.length == 2) return TabUtil.hint("<seconds>", cur);
            }
            case "pvpglobal" -> {
                if (admin && args.length == 1) return TabUtil.filter(List.of("on", "off"), cur);
            }
            case "newbie" -> {
                if (args.length == 1) {
                    return TabUtil.filter(admin ? List.of("add", "remove", "checktime", "disable") : List.of("disable"), cur);
                }
                if (!admin) return List.of();
                String sub = args[0].toLowerCase(Locale.ROOT);
                if (args.length == 2 && List.of("add", "remove", "checktime").contains(sub)) return TabUtil.players(sender, cur);
                if (args.length == 3 && sub.equals("add")) return TabUtil.hint("<seconds>", cur);
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
