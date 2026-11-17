package com.omniarena.commands.admin;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ChatMode;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ChatModeCommand extends SubCommand {

    public ChatModeCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa chat set <arena> <ARENA|TEAM|GLOBAL>", "Set arena chat mode");
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
        if (!args[0].equalsIgnoreCase("set")) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        ChatMode mode;
        try { mode = ChatMode.valueOf(args[2].toUpperCase()); }
        catch (IllegalArgumentException e) {
            MessageUtil.send(sender, "<red>Unknown chat mode <yellow>" + args[2] + "</yellow>. <gray>Valid modes: <white>"
                    + Arrays.stream(ChatMode.values()).map(ChatMode::name).collect(java.util.stream.Collectors.joining(", ")) + "</white>. Example: <white>/oa chat set " + arena.getName() + " " + ChatMode.values()[0].name() + "</white>");
            return;
        }
        arena.setChatMode(mode);
        ArenaManager.getInstance().save(arena);
        MessageUtil.send(sender, "<green>Chat mode set to <yellow>" + mode.name() + "</yellow> for <yellow>" + arena.getName() + "</yellow>.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(List.of("set"), cur);
        if (args.length == 2) return TabUtil.arenas(cur);
        if (args.length == 3) return TabUtil.enums(ChatMode.class, cur);
        return List.of();
    }
}
