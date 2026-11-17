package com.omniarena.commands.player;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HelpCommand extends SubCommand {

    private final Map<String, SubCommand> adminCommands;
    private final Map<String, SubCommand> playerCommands;
    private final Map<String, List<String>> guides = new LinkedHashMap<>();

    public HelpCommand(OmniArena plugin, Map<String, SubCommand> adminCommands, Map<String, SubCommand> playerCommands) {
        super(PermissionChecker.PLAYER_USE, "/oa help [guide]", "Show command help and guides");
        this.adminCommands = adminCommands;
        this.playerCommands = playerCommands;
        loadGuides();
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length > 0) {
            String key = normalize(args[0]);
            if (guides.containsKey(key)) {
                showGuide(sender, key);
                return;
            }
            MessageUtil.send(sender, "<red>Unknown guide <yellow>" + args[0] + "</yellow>. <gray>Available guides: <white>" + String.join(", ", guides.keySet()) + "</white>. Example: <white>/oa help makingarena</white>");
            return;
        }

        MessageUtil.send(sender, "<gold>=== OmniArena Help ===");
        MessageUtil.send(sender, "<gray>Use <yellow>/oa <command> help</yellow> for command-specific help.");
        MessageUtil.send(sender, "<gray>Use <yellow>/oa help <guide></yellow> for step-by-step guides. <dark_gray>(click a command to put it in chat)");
        MessageUtil.send(sender, "");
        MessageUtil.send(sender, "<green>Player commands:");
        playerCommands.forEach((name, cmd) -> {
            if (canSee(sender, cmd, false)) MessageUtil.send(sender, entry(name, cmd));
        });
        List<Map.Entry<String, SubCommand>> admin = adminCommands.entrySet().stream()
                .filter(e -> !playerCommands.containsKey(e.getKey()) && canSee(sender, e.getValue(), true)).toList(); // "kit" is listed once (player entry); admin kit actions show under /oa adminkit
        if (!admin.isEmpty()) {
            MessageUtil.send(sender, "");
            MessageUtil.send(sender, "<red>Admin commands:");
            admin.forEach(e -> MessageUtil.send(sender, entry(e.getKey(), e.getValue())));
        }
        MessageUtil.send(sender, "");
        MessageUtil.send(sender, "<aqua>Guides:");
        MessageUtil.send(sender, "<gray>" + String.join(", ", guides.keySet()));
    }

    private boolean canSee(CommandSender sender, SubCommand cmd, boolean admin) {
        String perm = cmd.getRequiredPermission();
        if (admin) return PermissionChecker.hasAdmin(sender, perm);
        return perm == null || !(sender instanceof org.bukkit.entity.Player p) || p.isOp() || p.hasPermission(perm);
    }

    /** One clickable help line: click suggests the command, hover shows usage + description. */
    private Component entry(String name, SubCommand cmd) {
        String base = "/oa " + name;
        Component hover = Component.text(cmd.getUsage(), NamedTextColor.YELLOW)
                .append(Component.newline())
                .append(Component.text(cmd.getDescription(), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text("Click to suggest", NamedTextColor.DARK_GRAY));
        return Component.text(base, NamedTextColor.YELLOW)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(cmd.getDescription(), NamedTextColor.GRAY))
                .clickEvent(ClickEvent.suggestCommand(base + " "))
                .hoverEvent(HoverEvent.showText(hover));
    }

    private void showGuide(CommandSender sender, String key) {
        MessageUtil.send(sender, "<gold>=== Guide: " + key + " ===");
        for (String line : guides.get(key)) {
            MessageUtil.send(sender, line);
        }
    }

    private void loadGuides() {
        guides.put("makingarena", List.of(
                "<yellow>1.</yellow> Stand where the arena will be and run <white>/oa create <name></white>.",
                "<yellow>2.</yellow> Get the selection wand with <white>/oa wand</white> (or <white>/oa region wand</white>).",
                "<yellow>3.</yellow> Left-click corner 1, right-click corner 2.",
                "<yellow>4.</yellow> Add spawns: <white>/oa spawn set <name> battle</white> (once per player spot), <white>lobby</white> and optionally <white>spectator</white>.",
                "<yellow>5.</yellow> Tune it: <white>/oa config <name> set <key> <value></white> (game mode, players, respawn type, ...) or <white>/oa settings</white>.",
                "<yellow>6.</yellow> Run <white>/oa region set <name></white> LAST: it saves the region and takes the regen snapshot of the finished build.",
                "<yellow>7.</yellow> Run <white>/oa enable <name></white>.",
                "<yellow>8.</yellow> Join signs: place a sign with <white>[OmniArena]</white> on line 1 and the arena name on line 2 (or <white>/oa sign create <name></white>).",
                "<yellow>Optional:</yellow> <white>/oa access <name> whitelist|blacklist ...</white> limits who may join; <white>/oa hologram create <id> <kills|wins|kd|elo></white> adds a leaderboard."));
        guides.put("regenarena", List.of(
                "<yellow>Automatic:</yellow> after every played match the arena rebuilds itself from its snapshot (<white>regen.auto</white>). It is not joinable until the rebuild finishes.",
                "<yellow>Snapshot:</yellow> <white>/oa region set <arena></white> takes it; re-take with <white>/oa regen snapshot <arena></white> after you change the build.",
                "<yellow>Per arena:</yellow> <white>/oa regen auto <arena> <true|false|default></white> (default = follow <white>regen.auto</white>).",
                "<yellow>Style:</yellow> <white>/oa regen style <type></white>, 10 styles, see <white>/oa help regenstyles</white>.",
                "<yellow>Manual rebuild:</yellow> <white>/oa regen <arena></white> (only while no match runs) or <white>/oa override purge <arena></white>.",
                "<yellow>Check:</yellow> <white>/oa regen settings</white> shows style, batch size and the auto setting."));
        java.util.List<String> styles = new java.util.ArrayList<>();
        for (com.omniarena.arena.regen.RegenAnimationType t : com.omniarena.arena.regen.RegenAnimationType.values()) {
            styles.add("<yellow>" + t.name() + "</yellow> - <white>/oa regen style " + t.name() + "</white>");
        }
        styles.add("<gray>Old style names (CENTER_OUT, TOP_DOWN, METEOR_STRIKE, ...) still load from config and map to the closest style above.");
        guides.put("regenstyles", styles);
        guides.put("signjoin", List.of(
                "<yellow>1.</yellow> Place a sign. On line 1 write <white>[OmniArena]</white> (or <white>[oa]</white>), on line 2 the arena name.",
                "<yellow>2.</yellow> Or look at a placed sign and run <white>/oa sign create <arena></white>. Needs <white>omniarena.admin.sign</white>.",
                "<yellow>3.</yellow> The sign shows the arena state and player count; players right-click it to join.",
                "<yellow>4.</yellow> Remove with <white>/oa sign remove</white> or break the sign; <white>/oa sign list <arena></white> lists them."));
        guides.put("kits", List.of(
                "<yellow>Players:</yellow> <white>/kit create <name></white>, <white>/kit equip <name></white>, <white>/kit list</white>.",
                "<yellow>Admin preset kits:</yellow> hold items, run <white>/oa kit admin create <name></white> (or <white>/oa adminkit create <name></white>).",
                "<yellow>Other admin actions:</yellow> <white>/oa kit admin <delete|rename|equip|setpublic|setprivate|icon|description|cooldown|permission|list></white>.",
                "<yellow>Public kits:</yellow> run <white>/oa kit admin setpublic <name></white>.",
                "<yellow>Kit menu:</yellow> run <white>/kit</white> or <white>/oa kit</white>."));
        guides.put("startmatch", List.of(
                "<yellow>1.</yellow> Enable the arena: <white>/oa enable <arena></white>.",
                "<yellow>2.</yellow> Players join by sign or <white>/oa join <arena></white>; the countdown starts at min players.",
                "<yellow>3.</yellow> Ranked matches: <white>/oa queue join [kit]</white> (ELO matchmaking; a party leader queues the party). <white>/oa elo</white> shows ratings.",
                "<yellow>4.</yellow> Admin force start: <white>/oa match start <arena></white>. Watch with <white>/oa spectate <arena></white>.",
                "<yellow>5.</yellow> Limit who may join with <white>/oa access <arena> ...</white> (blacklist always, whitelist when enabled)."));
        guides.put("stopmatch", List.of(
                "<yellow>Stop:</yellow> <white>/oa match stop <arena></white> or <white>/oa override forceend <arena></white>.",
                "<yellow>Pick a winner:</yellow> <white>/oa forcewin <arena> <player|team></white>.",
                "<yellow>Blocks:</yellow> the arena rebuilds itself afterwards (<white>regen.auto</white>); manual rebuild: <white>/oa regen <arena></white>.",
                "<yellow>Leave:</yellow> players use <white>/oa leave</white>."));
        guides.put("settings", List.of(
                "<yellow>Open UI:</yellow> <white>/oa settings</white>.",
                "<yellow>Toggle yes/no:</yellow> click values in chat.",
                "<yellow>Manual set:</yellow> <white>/oa settings set <key> <value></white>.",
                "<yellow>Regen settings:</yellow> <white>/oa regen settings</white>; per arena <white>/oa regen auto <arena> <true|false|default></white>.",
                "<yellow>Debug logging:</yellow> <white>plugin.debug: true</white> adds verbose console output (matchmaking, regen timings, web requests)."));
        guides.put("permissions", List.of(
                "<yellow>Player commands</yellow> are open by default.",
                "<yellow>Admin commands</yellow> need OP or omniarena.admin nodes.",
                "<yellow>Kit slots:</yellow> omniarena.kit.slots.5 or omniarena.kit.slots.10.",
                "<yellow>Join bypass:</yellow> omniarena.join.bypass (skips whitelist and full checks).",
                "<yellow>Access lists:</yellow> omniarena.admin.access for <white>/oa access</white>.",
                "<yellow>Ranked queue:</yellow> omniarena.queue for <white>/oa queue</white>."));
        guides.put("troubleshooting", List.of(
                "<yellow>No regen snapshot?</yellow> Run <white>/oa regen snapshot <arena></white> first (or <white>/oa region set <arena></white>).",
                "<yellow>Players cannot join?</yellow> Check <white>/oa info <arena></white>: arena enabled, not rebuilding, not whitelist-only (<white>/oa access <arena></white>).",
                "<yellow>Arena not rebuilt after a match?</yellow> It needs a region and snapshot; check <white>/oa regen settings</white>.",
                "<yellow>Kits hidden?</yellow> Run <white>/oa kit admin setpublic <name></white>.",
                "<yellow>Need reload?</yellow> Run <white>/oa reload</white>."));
        guides.put("performance", List.of(
                "<yellow>Fast regen:</yellow> <white>/oa regen batch 4000</white>.",
                "<yellow>Smoother regen:</yellow> <white>/oa regen batch 1000</white>.",
                "<yellow>Less noise:</yellow> <white>/oa regen sound false</white> and <white>/oa regen particles false</white>."));
    }

    private String normalize(String value) {
        return value.toLowerCase().replace("-", "").replace("_", "");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return TabUtil.filter(new java.util.ArrayList<>(guides.keySet()), normalize(args[0]));
        return List.of();
    }
}
