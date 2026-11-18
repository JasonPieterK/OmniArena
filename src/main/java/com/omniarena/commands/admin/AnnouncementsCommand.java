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

public class AnnouncementsCommand extends SubCommand {

    public AnnouncementsCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa announcements set <arena> <enable|disable>", "Toggle match announcements");
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
        Arena arena = arenaOpt.get();
        if (!args[0].equalsIgnoreCase("set") || !(args[2].equalsIgnoreCase("enable") || args[2].equalsIgnoreCase("disable"))) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        boolean enabled = args[2].equalsIgnoreCase("enable");
        arena.setAnnouncements(enabled);
        ArenaManager.getInstance().save(arena);
        MessageUtil.send(sender, "<green>Announcements " + (enabled ? "enabled" : "disabled") + " for <yellow>" + arena.getName() + "</yellow>.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(List.of("set"), cur);
        if (args.length == 2) return TabUtil.arenas(cur);
        if (args.length == 3) return TabUtil.filter(List.of("enable", "disable"), cur);
        return List.of();
    }
}
