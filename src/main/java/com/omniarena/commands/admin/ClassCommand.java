package com.omniarena.commands.admin;

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

public class ClassCommand extends SubCommand {

    public ClassCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa class <create|delete> <arena> <name>", "Manage arena classes");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[1]);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[1]));
            return;
        }
        String action = args[0].toLowerCase();
        String className = args[2];
        Arena arena = arenaOpt.get();

        if (action.equals("create")) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("class-created")
                    .replace("{class}", className).replace("{arena}", arena.getName()));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("create", "delete");
        if (args.length == 2) return ArenaManager.getInstance().getAll().stream().map(Arena::getName).filter(n -> n.startsWith(args[1])).toList();
        return List.of();
    }
}
