package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.List;

public class DeleteArenaCommand extends SubCommand {

    public DeleteArenaCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa delete <arena>", "Delete an arena");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args")
                    .replace("{usage}", getUsage()));
            return;
        }
        String name = args[0];
        if (!ArenaManager.getInstance().delete(name)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found")
                    .replace("{arena}", name));
            return;
        }
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-deleted")
                .replace("{arena}", name));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1)
            return ArenaManager.getInstance().getAll().stream()
                    .map(a -> a.getName())
                    .filter(n -> n.startsWith(args[0]))
                    .toList();
        return List.of();
    }
}
