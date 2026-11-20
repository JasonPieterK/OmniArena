package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.commands.SmartErrors;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Short, clickable, categorised command index. Click a line to put the command in chat. */
public class HelpCommand extends SubCommand {

    /** One help line: command text shown/suggested, one-line description. */
    private record Line(String command, String description) {}

    private static final Map<String, List<Line>> PLAYER = new LinkedHashMap<>();
    private static final Map<String, List<Line>> ADMIN = new LinkedHashMap<>();

    static {
        PLAYER.put("Play", List.of(
                new Line("/oa menu", "open the main menu"),
                new Line("/oa join [arena]", "join an arena (no name opens the picker)"),
                new Line("/oa spectate [arena]", "watch a match"),
                new Line("/oa leave", "leave your arena"),
                new Line("/oa list [arena]", "list arenas, or details of one"),
                new Line("/oa queue [kit]", "ranked matchmaking (also: queue leave, queue status)")));
        PLAYER.put("You and others", List.of(
                new Line("/party", "create and manage a party"),
                new Line("/kit", "your kits (create, equip, share, ...)"),
                new Line("/oa tournament", "bracket tournaments"),
                new Line("/oa bet", "bet on running matches"),
                new Line("/pvp", "toggle your PvP; also: status, newbie")));
        PLAYER.put("Stats", List.of(
                new Line("/oa stats [player]", "stats, ELO and rank"),
                new Line("/oa stats top [kills|wins|kd|elo] [n]", "leaderboards")));
        ADMIN.put("Arenas", List.of(
                new Line("/oa setup <arena>", "create + set up an arena (wand and menu)"),
                new Line("/oa edit <arena>", "edit an arena (menu, or: set <key> <value>, whitelist, blacklist)"),
                new Line("/oa arena <tp|delete|enable|disable> <arena>", "manage one arena"),
                new Line("/oa regen <arena>", "rebuild an arena from its snapshot"),
                new Line("/oa sign <create|remove|list>", "arena join signs")));
        ADMIN.put("Server", List.of(
                new Line("/oa kits", "admin preset kits (menu or text actions)"),
                new Line("/oa leaderboard <create|remove|list> <id> [kills|wins|kd|elo] [size]", "leaderboard holograms (DecentHolograms/FancyHolograms)"),
                new Line("/oa npc <create|remove|list> <menu|join|queue|spectate>", "Citizens NPCs that open menus / join / queue"),
                new Line("/oa settings", "plugin settings"),
                new Line("/oa web", "open the web dashboard"),
                new Line("/oa reload", "reload configs"),
                new Line("/oa plugin <start|stop|restart|version>", "plugin lifecycle"),
                new Line("/pvp tag|untag|global|list", "admin PvP controls")));
    }

    private final Map<String, SubCommand> adminCommands;
    private final Map<String, SubCommand> playerCommands;

    public HelpCommand(OmniArena plugin, Map<String, SubCommand> adminCommands, Map<String, SubCommand> playerCommands) {
        super(PermissionChecker.PLAYER_USE, "/oa help [command]", "Show the command index");
        this.adminCommands = adminCommands;
        this.playerCommands = playerCommands;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length > 0) {
            detail(sender, args[0].toLowerCase(java.util.Locale.ROOT));
            return;
        }
        MessageUtil.send(sender, "<gold>=== OmniArena ===  <dark_gray>(click a command to put it in chat)");
        PLAYER.forEach((title, lines) -> section(sender, title, "<green>", lines));
        if (PermissionChecker.hasAdmin(sender, PermissionChecker.ADMIN_ALL)
                || PermissionChecker.hasAdmin(sender, PermissionChecker.ADMIN_ARENA)) {
            MessageUtil.send(sender, "");
            MessageUtil.send(sender, "<red><bold>Admin</bold>");
            ADMIN.forEach((title, lines) -> section(sender, title, "<red>", lines));
        }
        MessageUtil.send(sender, "<dark_gray>Details for one command: <gray>/oa help <command>");
    }

    private void section(CommandSender sender, String title, String color, List<Line> lines) {
        MessageUtil.send(sender, color + title);
        for (Line l : lines) {
            String base = l.command().split(" [<\\[]|\\|")[0];
            Component c = Component.text(" " + l.command(), NamedTextColor.YELLOW)
                    .append(Component.text(" - " + l.description(), NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.suggestCommand(base + " "))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to suggest " + base, NamedTextColor.DARK_GRAY)));
            MessageUtil.send(sender, c);
        }
    }

    private void detail(CommandSender sender, String name) {
        boolean admin = PermissionChecker.hasAdmin(sender, PermissionChecker.ADMIN_ALL)
                || PermissionChecker.hasAdmin(sender, PermissionChecker.ADMIN_ARENA);
        SubCommand cmd = playerCommands.get(name);
        if (cmd == null && admin) cmd = adminCommands.get(name);
        if (cmd == null) {
            List<String> names = new ArrayList<>(playerCommands.keySet());
            if (admin) names.addAll(adminCommands.keySet());
            SmartErrors.unknownSubcommand(sender, "/oa help", name, names);
            return;
        }
        MessageUtil.send(sender, "<gold>=== /oa " + name + " ===");
        MessageUtil.send(sender, "<gray>" + com.omniarena.util.Suggest.escape(cmd.getDescription()));
        Component usage = Component.text("Usage: ", NamedTextColor.GRAY)
                .append(Component.text(cmd.getUsage(), NamedTextColor.YELLOW))
                .clickEvent(ClickEvent.suggestCommand("/oa " + name + " "));
        MessageUtil.send(sender, usage);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>(playerCommands.keySet());
            if (PermissionChecker.hasAdmin(sender, PermissionChecker.ADMIN_ARENA)) names.addAll(adminCommands.keySet());
            return TabUtil.filter(names, args[0]);
        }
        return List.of();
    }
}
