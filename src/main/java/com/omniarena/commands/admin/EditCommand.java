package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.commands.SmartErrors;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.gui.ArenaEditMenu;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import com.omniarena.util.Suggest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /oa edit &lt;arena&gt; opens the edit menu. Text forms: {@code set <key> <value>}, {@code get <key>},
 * {@code whitelist|blacklist add|remove|list [player]}.
 */
public class EditCommand extends SubCommand {

    private static final List<String> ACTIONS = List.of("set", "get", "whitelist", "blacklist");

    public EditCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa edit <arena> [set <key> <value> | get <key> | whitelist|blacklist add|remove|list [player]]",
                "Edit an arena (menu or text)");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            SmartErrors.wrongArgs(sender, getUsage(), "/oa edit <arena> set max-players 8");
            return;
        }
        Optional<Arena> found = SmartErrors.arena(sender, args[0], "/oa edit ");
        if (found.isEmpty()) return;
        Arena arena = found.get();
        if (args.length == 1) {
            if (sender instanceof Player p) ArenaEditMenu.open(p, arena);
            else MessageUtil.send(sender, "<gray>Text usage: <yellow>/oa edit " + arena.getName() + " set <key> <value></yellow> <gray>(keys: tab-complete).");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "set" -> {
                if (args.length < 4) {
                    SmartErrors.wrongArgs(sender, "/oa edit " + arena.getName() + " set <key> <value>",
                            "/oa edit " + arena.getName() + " set max-players 8");
                    return;
                }
                ArenaSettings.set(sender, arena, args[2], String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
            }
            case "get" -> {
                if (args.length < 3) {
                    SmartErrors.wrongArgs(sender, "/oa edit " + arena.getName() + " get <key>", "/oa edit " + arena.getName() + " get lives");
                    return;
                }
                Object value = ArenaSettings.get(arena, args[2]);
                if (value == null) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-invalid-key").replace("{key}", Suggest.escape(args[2])));
                    return;
                }
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("config-get")
                        .replace("{key}", arena.getName() + "." + Suggest.escape(args[2]))
                        .replace("{value}", Suggest.escape(String.valueOf(value))));
            }
            case "whitelist", "blacklist" -> {
                if (!PermissionChecker.hasAdmin(sender, PermissionChecker.ADMIN_ACCESS)) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("no-permission"));
                    return;
                }
                String[] full = new String[args.length];
                full[0] = arena.getName();
                System.arraycopy(args, 1, full, 1, args.length - 1);
                ArenaAccess.handle(sender, arena, full);
            }
            default -> SmartErrors.unknownSubcommand(sender, "/oa edit " + arena.getName(), args[1], ACTIONS);
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.arenas(cur);
        if (args.length == 2) return TabUtil.filter(ACTIONS, cur);
        Optional<Arena> arena = com.omniarena.arena.ArenaManager.getInstance().get(args[0]);
        if (arena.isEmpty()) return List.of();
        String a = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3) {
            if (a.equals("set") || a.equals("get")) return TabUtil.filter(ArenaSettings.ARENA_KEYS, cur);
            if (a.equals("whitelist") || a.equals("blacklist")) return ArenaAccess.tab(sender, arena.get(), Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 4 && a.equals("set")) return ArenaSettings.tabValue(args[2], cur);
        if (args.length >= 4 && (a.equals("whitelist") || a.equals("blacklist"))) return ArenaAccess.tab(sender, arena.get(), Arrays.copyOfRange(args, 1, args.length));
        return List.of();
    }
}
