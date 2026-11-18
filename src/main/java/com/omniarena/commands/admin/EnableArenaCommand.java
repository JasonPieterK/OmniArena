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
        Arena a = arena.get();
        if (a.isEnabled()) {
            MessageUtil.send(sender, "<yellow>Arena <white>" + a.getName() + "</white> is already enabled.");
            return;
        }
        if (a.getBattleSpawns().isEmpty()) {
            MessageUtil.send(sender, com.omniarena.config.ConfigManager.getInstance().getMessage("arena-no-spawns")
                    .replace("{arena}", a.getName()).replace("{type}", "BATTLE")
                    + " <gray>Then retry <white>/oa enable " + a.getName() + "</white>.");
            return;
        }
        if (!a.getRegion().isDefined()) {
            MessageUtil.send(sender, "<yellow>Warning: arena <white>" + a.getName() + "</white> has no region, so block protection and regen won't work. <gray>Fix: <white>/oa wand</white> then <white>/oa region set " + a.getName() + "</white>.");
        }
        ArenaManager.getInstance().enable(a);
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-enabled").replace("{arena}", args[0]));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return TabUtil.arenas(args[0]);
        return List.of();
    }
}
