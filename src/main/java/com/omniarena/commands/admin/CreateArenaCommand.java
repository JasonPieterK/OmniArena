package com.omniarena.commands.admin;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.List;

public class CreateArenaCommand extends SubCommand {

    private final OmniArena plugin;

    public CreateArenaCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa create <name>", "Create a new arena");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args")
                    .replace("{usage}", getUsage()));
            return;
        }
        String name = args[0];
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
            MessageUtil.send(sender, "<red>Invalid arena name. Use 1-32 characters: letters, numbers, '_' or '-'.");
            return;
        }
        ArenaManager am = ArenaManager.getInstance();
        if (am.exists(name)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-already-exists")
                    .replace("{arena}", name));
            return;
        }
        am.create(name);
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-created")
                .replace("{arena}", name));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? TabUtil.hint("<name>", args[0]) : List.of();
    }
}
