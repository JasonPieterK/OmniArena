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
                "<yellow>1.</yellow> Stand where arena will be.",
                "<yellow>2.</yellow> Run <white>/oa create <name></white>.",
                "<yellow>3.</yellow> Run <white>/oa wand</white>.",
                "<yellow>4.</yellow> Left-click corner 1, right-click corner 2.",
                "<yellow>5.</yellow> Run <white>/oa region set <name></white>.",
                "<yellow>6.</yellow> Add spawns with <white>/oa spawn set <name> battle</white> (once per player spot).",
                "<yellow>7.</yellow> Add lobby with <white>/oa spawn set <name> lobby</white>.",
                "<yellow>8.</yellow> Run <white>/oa regen snapshot <name></white>.",
                "<yellow>9.</yellow> Run <white>/oa enable <name></white>."));
        guides.put("regenarena", List.of(
                "<yellow>1.</yellow> Make sure region is set.",
                "<yellow>2.</yellow> Save original blocks: <white>/oa regen snapshot <arena></white>.",
                "<yellow>3.</yellow> Pick default style: <white>/oa regen style SPHERE</white>.",
                "<yellow>4.</yellow> Rebuild: <white>/oa regen <arena></white>.",
                "<yellow>5.</yellow> Watch chat progress bar until 100%."));
        java.util.List<String> styles = new java.util.ArrayList<>();
        for (com.omniarena.arena.regen.RegenAnimationType t : com.omniarena.arena.regen.RegenAnimationType.values()) {
            styles.add("<yellow>" + t.name() + "</yellow> - <white>/oa regen style " + t.name() + "</white>");
        }
        styles.add("<gray>Old style names (CENTER_OUT, TOP_DOWN, METEOR_STRIKE, ...) still load from config and map to the closest style above.");
        guides.put("regenstyles", styles);
        guides.put("signjoin", List.of(
                "<yellow>1.</yellow> Look at block where sign should go.",
                "<yellow>2.</yellow> Run <white>/oa sign create <arena></white>.",
                "<yellow>3.</yellow> Players right-click sign to join.",
                "<yellow>4.</yellow> Remove with <white>/oa sign remove</white>."));
        guides.put("kits", List.of(
                "<yellow>Players:</yellow> <white>/kit create <name></white>, <white>/kit equip <name></white>, <white>/kit list</white>.",
                "<yellow>Admin preset kits:</yellow> hold items, run <white>/oa kit admin create <name></white> (or <white>/oa adminkit create <name></white>).",
                "<yellow>Other admin actions:</yellow> <white>/oa kit admin <delete|rename|equip|setpublic|setprivate|icon|description|cooldown|permission|list></white>.",
                "<yellow>Public kits:</yellow> run <white>/oa kit admin setpublic <name></white>.",
                "<yellow>Kit menu:</yellow> run <white>/kit</white> or <white>/oa kit</white>."));
        guides.put("startmatch", List.of(
                "<yellow>1.</yellow> Enable arena: <white>/oa enable <arena></white>.",
                "<yellow>2.</yellow> Players join by sign or <white>/oa join <arena></white>.",
                "<yellow>3.</yellow> Match auto-starts when min players joined.",
                "<yellow>4.</yellow> Admin can force start: <white>/oa match start <arena></white>."));
        guides.put("stopmatch", List.of(
                "<yellow>Stop:</yellow> <white>/oa match stop <arena></white>.",
                "<yellow>Reset blocks:</yellow> <white>/oa regen <arena></white>.",
                "<yellow>Leave:</yellow> players use <white>/oa leave</white>."));
        guides.put("settings", List.of(
                "<yellow>Open UI:</yellow> <white>/oa settings</white>.",
                "<yellow>Toggle yes/no:</yellow> click values in chat.",
                "<yellow>Manual set:</yellow> <white>/oa settings set <key> <value></white>.",
                "<yellow>Regen settings:</yellow> <white>/oa regen settings</white>."));
        guides.put("permissions", List.of(
                "<yellow>Player commands</yellow> are open by default.",
                "<yellow>Admin commands</yellow> need OP or omniarena.admin nodes.",
                "<yellow>Kit slots:</yellow> omniarena.kit.slots.5 or omniarena.kit.slots.10.",
                "<yellow>Join bypass:</yellow> omniarena.join.bypass."));
        guides.put("troubleshooting", List.of(
                "<yellow>No regen snapshot?</yellow> Run <white>/oa regen snapshot <arena></white> first.",
                "<yellow>Players cannot join?</yellow> Check <white>/oa info <arena></white> and arena enabled.",
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
