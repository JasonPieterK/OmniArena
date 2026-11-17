package com.omniarena.commands.admin;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class TeleportArenaCommand extends SubCommand {

    public TeleportArenaCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa tp <arena>", "Teleport to arena");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only")); return; }
        if (args.length < 1) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage())); return; }

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[0]);
        if (arenaOpt.isEmpty()) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[0])); return; }

        Arena arena = arenaOpt.get();
        Location dest = arena.getLobbySpawn();
        if (dest == null) dest = arena.getRandomBattleSpawn();
        if (dest == null) { MessageUtil.send(player, "<red>Arena <yellow>" + arena.getName() + "</yellow> has no lobby or battle spawn to teleport to. <gray>Set one with <white>/oa spawn set " + arena.getName() + " LOBBY</white> (or BATTLE)."); return; }

        player.teleport(dest);
        MessageUtil.send(player, "<green>Teleported to <yellow>" + arena.getName() + "</yellow>.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return TabUtil.arenas(args[0]);
        return List.of();
    }
}
