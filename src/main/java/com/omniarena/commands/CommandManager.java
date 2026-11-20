package com.omniarena.commands;

import com.omniarena.OmniArena;
import com.omniarena.commands.admin.*;
import com.omniarena.commands.player.*;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import com.omniarena.util.Suggest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Dispatcher for /oa. Two maps: admin subcommands (gated by {@link PermissionChecker#hasAdmin}) and
 * player subcommands (gated by the node each declares). Unknown input gets a did-you-mean answer.
 */
public class CommandManager implements CommandExecutor, TabCompleter {

    private final OmniArena plugin;
    private final Map<String, SubCommand> adminCommands = new LinkedHashMap<>();
    private final Map<String, SubCommand> playerCommands = new LinkedHashMap<>();
    /** Names that work but are not advertised in tab completion or suggestions (shorter forms exist). */
    private static final Set<String> HIDDEN = Set.of("pvp");
    private HelpCommand help;

    public CommandManager(OmniArena plugin) {
        this.plugin = plugin;
        registerAll();
    }

    private void registerAll() {
        // ── Admin ──
        reg(adminCommands, "setup",    new SetupCommand(plugin));
        reg(adminCommands, "edit",     new EditCommand(plugin));
        reg(adminCommands, "arena",    new ArenaCommand(plugin));
        reg(adminCommands, "regen",    new RegenCommand(plugin));
        reg(adminCommands, "kits",     new AdminKitCommand(plugin));
        reg(adminCommands, "sign",     new SignCommand(plugin));
        reg(adminCommands, "leaderboard", new LeaderboardCommand(plugin));
        reg(adminCommands, "npc",      new NpcCommand(plugin));
        reg(adminCommands, "settings", new SettingsCommand());
        reg(adminCommands, "reload",   new ReloadCommand(plugin));
        reg(adminCommands, "plugin",   new LifecycleCommand(plugin));
        reg(adminCommands, "web",      new WebEditorCommand(plugin));
        reg(adminCommands, "doctor",   new DoctorCommand(plugin));

        // ── Player ──
        reg(playerCommands, "menu", new SubCommand(PermissionChecker.PLAYER_USE, "/oa menu", "Open the main menu") {
            @Override public void execute(CommandSender sender, String[] args) { openMenu(sender); }
        });
        reg(playerCommands, "join",       new JoinCommand(plugin));
        reg(playerCommands, "leave",      new LeaveCommand(plugin));
        reg(playerCommands, "spectate",   new SpectateCommand(plugin));
        reg(playerCommands, "list",       new ListCommand(plugin));
        reg(playerCommands, "stats",      new StatsCommand(plugin));
        reg(playerCommands, "queue",      new QueueCommand(plugin));
        reg(playerCommands, "bet",        new BetCommand(plugin));
        reg(playerCommands, "tournament", new TournamentCommand(plugin));
        help = new HelpCommand(plugin, adminCommands, playerCommands);
        reg(playerCommands, "help",       help);

        // /pvp is a standalone command that reuses this dispatcher (hidden from /oa suggestions)
        com.omniarena.combat.PvpCommandHandler pvp = new com.omniarena.combat.PvpCommandHandler();
        reg(playerCommands, "pvp", new SubCommand("omniarena.pvp", "/pvp [status|newbie|tag|untag|global|list]", "Toggle PvP, check status, newbie protection") {
            @Override public void execute(CommandSender sender, String[] args) { pvp.handle(sender, args); }
            @Override public List<String> tabComplete(CommandSender sender, String[] args) { return pvp.tabComplete(sender, args); }
        });
    }

    private void reg(Map<String, SubCommand> map, String name, SubCommand cmd) {
        map.put(name.toLowerCase(Locale.ROOT), cmd);
    }

    private void openMenu(CommandSender sender) {
        if (sender instanceof Player p) com.omniarena.gui.MainMenu.open(p);
        else help.execute(sender, new String[0]);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (plugin.isStopped()) {
            String sub = args != null && args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
            if (!sub.equals("plugin")) {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("plugin-stopped")
                        + " <gray>Admins: " + Suggest.click("/oa plugin start") + "<gray> brings it back.");
                return true;
            }
        }

        if (args == null || args.length == 0) {
            openMenu(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] subArgs = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        SubCommand adminCmd = adminCommands.get(sub);
        if (adminCmd != null) {
            if (!PermissionChecker.hasAdmin(sender, adminCmd.getRequiredPermission())) {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("no-permission"));
                return true;
            }
            if (subArgs.length == 1 && subArgs[0].equalsIgnoreCase("help")) { help.execute(sender, new String[]{sub}); return true; }
            runSafely(sender, sub, adminCmd, subArgs);
            return true;
        }

        SubCommand playerCmd = playerCommands.get(sub);
        if (playerCmd != null) {
            if (!canUsePlayer(sender, playerCmd)) {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("no-permission"));
                return true;
            }
            if (subArgs.length == 1 && subArgs[0].equalsIgnoreCase("help") && !sub.equals("help")) { help.execute(sender, new String[]{sub}); return true; }
            runSafely(sender, sub, playerCmd, subArgs);
            return true;
        }

        SmartErrors.unknownSubcommand(sender, "/oa", args[0], visibleNames(sender));
        return true;
    }

    /** Runs a subcommand; unexpected exceptions are logged in full to the console and never shown to the player. */
    private void runSafely(CommandSender sender, String sub, SubCommand cmd, String[] subArgs) {
        try {
            cmd.execute(sender, subArgs);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Unexpected error while " + sender.getName() + " ran '/oa " + sub + " " + String.join(" ", subArgs)
                            + "' (" + e.getClass().getSimpleName() + ": " + e.getMessage() + "). Full stack trace follows; "
                            + "please report it with your server version and plugin version.", e);
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("error-internal")
                    .replace("{command}", "/oa " + sub)
                    + " <gray>Usage: <white>" + Suggest.escape(cmd.getUsage()) + "</white>");
        }
    }

    private boolean canUsePlayer(CommandSender sender, SubCommand cmd) {
        String perm = cmd.getRequiredPermission();
        if (perm == null || !(sender instanceof Player p)) return true;
        return p.isOp() || p.hasPermission(perm);
    }

    /** Subcommand names the sender may use, without hidden duplicates. */
    private List<String> visibleNames(CommandSender sender) {
        Set<String> names = new LinkedHashSet<>();
        playerCommands.forEach((n, c) -> { if (!HIDDEN.contains(n) && canUsePlayer(sender, c)) names.add(n); });
        adminCommands.forEach((n, c) -> { if (!HIDDEN.contains(n) && PermissionChecker.hasAdmin(sender, c.getRequiredPermission())) names.add(n); });
        return List.copyOf(names);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0) return List.of();
        String typed = args[args.length - 1];
        if (args.length == 1) {
            if (plugin.isStopped()) return TabUtil.filter(List.of("plugin"), typed);
            return TabUtil.filter(visibleNames(sender), typed);
        }
        if (plugin.isStopped() && !args[0].equalsIgnoreCase("plugin")) return List.of();

        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        SubCommand cmd = adminCommands.get(sub);
        if (cmd != null) {
            if (!PermissionChecker.hasAdmin(sender, cmd.getRequiredPermission())) return List.of();
        } else {
            cmd = playerCommands.get(sub);
            if (cmd == null || !canUsePlayer(sender, cmd)) return List.of();
        }
        return TabUtil.filter(cmd.tabComplete(sender, subArgs), typed);
    }
}
