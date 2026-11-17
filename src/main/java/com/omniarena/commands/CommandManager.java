package com.omniarena.commands;

import com.omniarena.OmniArena;
import com.omniarena.commands.admin.*;
import com.omniarena.commands.player.*;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final OmniArena plugin;
    private final Map<String, SubCommand> adminCommands = new LinkedHashMap<>();
    private final Map<String, SubCommand> playerCommands = new LinkedHashMap<>();

    public CommandManager(OmniArena plugin) {
        this.plugin = plugin;
        registerAll();
    }

    private void registerAll() {
        reg(adminCommands, "create",        new CreateArenaCommand(plugin));
        reg(adminCommands, "delete",        new DeleteArenaCommand(plugin));
        reg(adminCommands, "enable",        new EnableArenaCommand(plugin));
        reg(adminCommands, "disable",       new DisableArenaCommand(plugin));
        reg(adminCommands, "config",        new ConfigCommand(plugin));
        reg(adminCommands, "spawn",         new SpawnCommand(plugin));
        reg(adminCommands, "region",        new RegionCommand(plugin));
        reg(adminCommands, "goal",          new GoalCommand(plugin));
        reg(adminCommands, "module",        new ModuleCommand(plugin));
        reg(adminCommands, "match",         new MatchAdminCommand(plugin));
        reg(adminCommands, "regen",         new RegenCommand(plugin));
        reg(adminCommands, "sign",          new SignCommand(plugin));
        reg(adminCommands, "kit",           new AdminKitCommand(plugin));
        reg(adminCommands, "tp",            new TeleportArenaCommand(plugin));
        reg(adminCommands, "debug",         new DebugCommand(plugin));
        reg(adminCommands, "announcements", new AnnouncementsCommand(plugin));
        reg(adminCommands, "chat",          new ChatModeCommand(plugin));
        reg(adminCommands, "override",      new OverrideCommand(plugin));
        reg(adminCommands, "reload",        new ReloadCommand(plugin));
        reg(adminCommands, "version",       new VersionCommand(plugin));
        reg(adminCommands, "wand",          new WandCommand(plugin));
        reg(adminCommands, "website",      new WebEditorCommand(plugin));
        reg(adminCommands, "settings",      new SettingsCommand());
        com.omniarena.commands.admin.LifecycleCommand lifecycle = new LifecycleCommand(plugin);
        reg(adminCommands, "start",         new SubCommand(PermissionChecker.ADMIN_ALL, "/oa start", "Start all systems") {
            @Override public void execute(CommandSender sender, String[] args) { lifecycle.executeAction(sender, "start"); }
            @Override public List<String> tabComplete(CommandSender sender, String[] args) { return List.of(); }
        });
        reg(adminCommands, "stop",          new SubCommand(PermissionChecker.ADMIN_ALL, "/oa stop", "Stop all systems") {
            @Override public void execute(CommandSender sender, String[] args) { lifecycle.executeAction(sender, "stop"); }
            @Override public List<String> tabComplete(CommandSender sender, String[] args) { return List.of(); }
        });
        reg(adminCommands, "restart",       new SubCommand(PermissionChecker.ADMIN_ALL, "/oa restart", "Restart all systems") {
            @Override public void execute(CommandSender sender, String[] args) { lifecycle.executeAction(sender, "restart"); }
            @Override public List<String> tabComplete(CommandSender sender, String[] args) { return List.of(); }
        });
        reg(adminCommands, "pmr",           new SubCommand(PermissionChecker.ADMIN_ALL, "/oa pmr <help|cleanup|reload|worlds>", "PvP manager admin") {
            @Override public void execute(CommandSender sender, String[] args) {
                String a = args.length > 0 ? args[0].toLowerCase() : "help";
                switch (a) {
                    case "cleanup" -> {
                        com.omniarena.combat.CombatManager.getInstance().cleanup();
                        MessageUtil.send(sender, "<green>Combat database cleaned.");
                    }
                    case "reload" -> {
                        ConfigManager.getInstance().reload();
                        plugin.startSignUpdateTask();
                        plugin.getCombatManager().start();
                        MessageUtil.send(sender, "<green>OmniArena PvP config reloaded.");
                    }
                    case "worlds" -> {
                        if (args.length < 2) { MessageUtil.send(sender, "<yellow>Usage: /oa pmr worlds <world></yellow> <gray>— toggles PvP off in that world."); return; }
                        var cfg = ConfigManager.getInstance().getConfig();
                        java.util.List<String> off = new java.util.ArrayList<>(cfg.getStringList("pvp.disabled-worlds"));
                        String w = args[1];
                        if (off.removeIf(x -> x.equalsIgnoreCase(w))) MessageUtil.send(sender, "<green>PvP re-enabled in <yellow>" + w + "</yellow>.");
                        else { off.add(w); MessageUtil.send(sender, "<red>PvP disabled in <yellow>" + w + "</yellow>."); }
                        ConfigManager.getInstance().setValue("pvp.disabled-worlds", off);
                    }
                    case "locale", "update" -> MessageUtil.send(sender, "<gray>Handled by OmniArena directly — use <yellow>/oa reload</yellow> and <yellow>/oa settings</yellow>.");
                    default -> {
                        MessageUtil.send(sender, "<gold>=== OmniArena PvP (pmr) ===");
                        MessageUtil.send(sender, "<yellow>/oa pmr cleanup</yellow> <gray>- clear combat database");
                        MessageUtil.send(sender, "<yellow>/oa pmr reload</yellow> <gray>- reload config");
                        MessageUtil.send(sender, "<yellow>/oa pmr worlds <world></yellow> <gray>- toggle world PvP");
                        MessageUtil.send(sender, "<yellow>/oa pvp, /oa pvpstatus, /oa pvptag, /oa newbie, /oa pvpglobal</yellow>");
                    }
                }
            }
            @Override public java.util.List<String> tabComplete(CommandSender sender, String[] args) {
                if (args.length == 1) return java.util.List.of("help","cleanup","reload","worlds");
                if (args.length == 2 && args[0].equalsIgnoreCase("worlds"))
                    return org.bukkit.Bukkit.getWorlds().stream().map(org.bukkit.World::getName).toList();
                return java.util.List.of();
            }
        });

        reg(playerCommands, "join",     new JoinCommand(plugin));
        reg(playerCommands, "leave",    new LeaveCommand(plugin));
        reg(playerCommands, "spectate", new SpectateCommand(plugin));
        reg(playerCommands, "list",     new ListCommand(plugin));
        reg(playerCommands, "info",     new InfoCommand(plugin));
        reg(playerCommands, "stats",    new StatsCommand(plugin));
        reg(playerCommands, "ready",    new ReadyCommand(plugin));
        // /oa kit for players mirrors /kit exactly; admin-only actions use /oa kit as admin
        KitCommandExecutor kitExec = plugin.getKitCommandExecutor();
        reg(playerCommands, "kit", new SubCommand(PermissionChecker.PLAYER_KIT, "/oa kit [action] [args]", "Player kit management") {
            @Override public void execute(CommandSender sender, String[] args) { kitExec.handle(sender, args); }
            @Override public java.util.List<String> tabComplete(CommandSender sender, String[] args) {
                if (!(sender instanceof org.bukkit.entity.Player p)) return java.util.List.of();
                if (args.length == 1) return java.util.List.of("help","create","list","delete","equip","share","accept","rename","preview").stream().filter(s->s.startsWith(args[0])).toList();
                if (args.length == 2) return com.omniarena.kit.KitManager.getInstance().getPlayerKits(p.getUniqueId()).stream().map(com.omniarena.kit.PlayerKit::getName).filter(n->n.startsWith(args[1])).toList();
                return java.util.List.of();
            }
        });
        // /oa party mirrors /party exactly
        com.omniarena.commands.standalone.PartyCommand partyCmd = new com.omniarena.commands.standalone.PartyCommand(plugin);
        reg(playerCommands, "party", new SubCommand(PermissionChecker.PLAYER_USE, "/oa party [action] [args]", "Party management") {
            @Override public void execute(CommandSender sender, String[] args) { partyCmd.onCommand(sender, null, "party", args); }
            @Override public java.util.List<String> tabComplete(CommandSender sender, String[] args) { return partyCmd.onTabComplete(sender, null, "party", args); }
        });
        reg(playerCommands, "bet",      new BetCommand(plugin));
        reg(playerCommands, "help",     new HelpCommand(plugin, adminCommands, playerCommands));

        // ── PvP / combat commands (PvPManager-style; /pmr is now /oa) ──
        com.omniarena.combat.PvpCommandHandler pvp = new com.omniarena.combat.PvpCommandHandler();
        for (String name : new String[]{"pvp","pvpstatus","pvptag","untag","pvpglobal","newbie","pvpinfo","pvplist","pvpoverride"}) {
            reg(playerCommands, name, new SubCommand("omniarena.pvp", "/oa " + name, "PvP/combat command") {
                @Override public void execute(CommandSender sender, String[] args) { pvp.dispatch(name, sender, args); }
                @Override public java.util.List<String> tabComplete(CommandSender sender, String[] args) {
                    if (args.length == 1 && name.equals("pvpglobal")) return java.util.List.of("on","off");
                    if (args.length == 1 && name.equals("newbie")) return java.util.List.of("add","remove","checktime","disable");
                    if (args.length == 1)
                        return org.bukkit.Bukkit.getOnlinePlayers().stream().map(org.bukkit.entity.Player::getName)
                                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
                    return java.util.List.of();
                }
            });
        }
    }

    private void reg(Map<String, SubCommand> map, String name, SubCommand cmd) {
        map.put(name.toLowerCase(), cmd);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        // Check if plugin is stopped - only allow 'start' command
        if (plugin.isStopped()) {
            String sub = args != null && args.length > 0 ? args[0].toLowerCase() : "";
            if (!sub.equals("start")) {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("plugin-stopped")
                        + " <gray>Use <yellow>/oa start</yellow> to restart.");
                return true;
            }
        }

        if (args == null || args.length == 0) {
            showHelp(sender);
            return true;
        }

        // Convert subcommand to lowercase for case-insensitivity
        String sub = args[0].toLowerCase();
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        SubCommand adminCmd = adminCommands.get(sub);
        if (adminCmd != null) {
            if (subArgs.length > 0 && subArgs[0].equalsIgnoreCase("help") && !sub.equals("regen")) {
                showCommandHelp(sender, sub, adminCmd);
                return true;
            }
            SubCommand playerCmd = playerCommands.get(sub);
            if (playerCmd != null && shouldUsePlayerCommand(sender, sub, subArgs, adminCmd)) {
                try {
                    playerCmd.execute(sender, subArgs);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error executing player command " + sub + ": " + e.getMessage());
                    e.printStackTrace();
                    MessageUtil.send(sender, "<red>An error occurred while executing this command.");
                }
                return true;
            }
            if (!PermissionChecker.hasAdmin(sender, adminCmd.getRequiredPermission())) {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("no-permission"));
                return true;
            }
            try {
                adminCmd.execute(sender, subArgs);
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing admin command " + sub + ": " + e.getMessage());
                e.printStackTrace();
                MessageUtil.send(sender, "<red>An error occurred while executing this command.");
            }
            return true;
        }

        SubCommand playerCmd = playerCommands.get(sub);
        if (playerCmd != null) {
            if (subArgs.length > 0 && subArgs[0].equalsIgnoreCase("help")) {
                showCommandHelp(sender, sub, playerCmd);
                return true;
            }
            try {
                playerCmd.execute(sender, subArgs);
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing player command " + sub + ": " + e.getMessage());
                e.printStackTrace();
                MessageUtil.send(sender, "<red>An error occurred while executing this command.");
            }
            return true;
        }

        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("unknown-command"));
        return true;
    }

    private boolean shouldUsePlayerCommand(CommandSender sender, String sub, String[] args, SubCommand adminCmd) {
        if (!PermissionChecker.hasAdmin(sender, adminCmd.getRequiredPermission())) return true;
        // "kit" — player command for personal kit ops; admin command for setpublic/setprivate/icon/description/cooldown/permission
        if (sub.equals("kit")) {
            if (args.length == 0) return true; // open GUI — player side
            String action = args[0].toLowerCase();
            return !Set.of("setpublic", "setprivate", "icon", "description", "cooldown", "permission").contains(action);
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(playerCommands.keySet());
            if (PermissionChecker.hasAdmin(sender, PermissionChecker.ADMIN_ALL)) {
                completions.addAll(adminCommands.keySet());
            }
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }

        String sub = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        if (subArgs.length == 1 && "help".startsWith(subArgs[0].toLowerCase())) {
            return List.of("help");
        }

        SubCommand adminCmd = adminCommands.get(sub);
        SubCommand playerCmd = playerCommands.get(sub);
        SubCommand cmd = adminCmd != null && !shouldUsePlayerCommand(sender, sub, subArgs, adminCmd) ? adminCmd : playerCmd;
        if (cmd != null) return cmd.tabComplete(sender, subArgs);

        return List.of();
    }

    private void showHelp(CommandSender sender) {
        MessageUtil.send(sender, "<gold>=== OmniArena Help ===");
        MessageUtil.send(sender, "<yellow>/oa <subcommand> help</yellow> <gray>- command help");
        MessageUtil.send(sender, "<yellow>/oa help</yellow> <gray>- full help and guides");
    }

    private void showCommandHelp(CommandSender sender, String name, SubCommand command) {
        MessageUtil.send(sender, "<gold>=== /oa " + name + " help ===");
        MessageUtil.send(sender, "<gray>Description: <white>" + command.getDescription());
        MessageUtil.send(sender, "<gray>Usage: <yellow>" + command.getUsage());
        MessageUtil.send(sender, "<gray>Permission: <white>" + command.getRequiredPermission());
        MessageUtil.send(sender, "<dark_gray>Tip: use tab-complete after command to see valid arguments.");
    }
}
