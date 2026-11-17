package com.omniarena.commands.admin;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

public class ModuleCommand extends SubCommand {

    private static final List<String> KNOWN_MODULES = List.of(
            "killstreak", "combo", "power-up", "item-spawner", "environmental", "leveling");

    public ModuleCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa module <enable|disable> <arena> <module>", "Toggle arena module");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        String action = args[0].toLowerCase();
        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[1]);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[1]));
            return;
        }
        Arena arena = arenaOpt.get();
        String module = args[2].toLowerCase();
        if (!action.equals("enable") && !action.equals("disable")) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        if (!KNOWN_MODULES.contains(module)) {
            MessageUtil.send(sender, "<red>Unknown module <yellow>" + args[2] + "</yellow>. <gray>Valid modules: <white>" + String.join(", ", KNOWN_MODULES) + "</white>. Example: <white>/oa module " + action + " " + arena.getName() + " " + KNOWN_MODULES.iterator().next() + "</white>");
            return;
        }

        if (action.equals("enable")) {
            arena.enableModule(module);
            ArenaManager.getInstance().save(arena);
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("module-enabled")
                    .replace("{module}", module).replace("{arena}", arena.getName()));
        } else if (action.equals("disable")) {
            arena.disableModule(module);
            ArenaManager.getInstance().save(arena);
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("module-disabled")
                    .replace("{module}", module).replace("{arena}", arena.getName()));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(List.of("enable", "disable"), cur);
        if (args.length == 2) return TabUtil.arenas(cur);
        if (args.length == 3) return TabUtil.filter(KNOWN_MODULES, cur);
        return List.of();
    }
}
