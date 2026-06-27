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

public class EnableArenaCommand extends SubCommand {

    public EnableArenaCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa enable <arena>", "Enable an arena");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        Optional<Arena> arena = ArenaManager.getInstance().get(args[0]);
        if (arena.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[0]));
            return;
        }
        ArenaManager.getInstance().enable(arena.get());
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-enabled").replace("{arena}", args[0]));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1)
            return ArenaManager.getInstance().getAll().stream().map(Arena::getName)
                    .filter(n -> n.startsWith(args[0])).toList();
        return List.of();
    }
}
