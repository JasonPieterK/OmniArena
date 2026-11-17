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

public class DisableArenaCommand extends SubCommand {

    public DisableArenaCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa disable <arena>", "Disable an arena");
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
        kickAll(arena.get());
        ArenaManager.getInstance().disable(arena.get());
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-disabled").replace("{arena}", args[0]));
    }

    /** Removes every player and spectator from the arena's active match (if any). */
    static void kickAll(Arena arena) {
        var mm = com.omniarena.match.MatchManager.getInstance();
        mm.getMatch(arena).ifPresent(match -> {
            for (java.util.UUID id : new java.util.ArrayList<>(match.getSpectators())) {
                org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null) mm.leaveSpectator(p);
            }
            for (java.util.UUID id : new java.util.ArrayList<>(match.getPlayers())) {
                org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null) mm.leaveArena(p);
            }
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1)
            return ArenaManager.getInstance().getAll().stream().map(Arena::getName)
                    .filter(n -> n.startsWith(args[0])).toList();
        return List.of();
    }
}
