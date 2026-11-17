package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.SpawnType;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.LocationUtil;
import com.omniarena.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class SpawnCommand extends SubCommand {

    public SpawnCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa spawn <set|list|clear> <arena> <type>", "Manage spawns");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
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
        if (args.length >= 3 && !action.equals("list")) {
            try { SpawnType.valueOf(args[2].toUpperCase()); }
            catch (IllegalArgumentException e) {
                MessageUtil.send(sender, "<red>Unknown spawn type <yellow>" + args[2] + "</yellow>. Use BATTLE, LOBBY or SPECTATOR.");
                return;
            }
        }

        switch (action) {
            case "set" -> {
                if (!(sender instanceof Player player)) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only")); return; }
                SpawnType type = args.length >= 3 ? SpawnType.fromString(args[2]) : SpawnType.BATTLE;
                arena.addSpawn(type, player.getLocation());
                ArenaManager.getInstance().save(arena);
                int number = arena.getSpawns(type).size();
                MessageUtil.send(sender, "<green>Set <yellow>" + type.name() + " spawn #" + number
                        + "</yellow> for <yellow>" + arena.getName()
                        + "</yellow>. <gray>(players fill spawns 1→" + number + " in order)");
            }
            case "list" -> {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("spawn-list-header")
                        .replace("{arena}", arena.getName()));
                for (SpawnType type : SpawnType.values()) {
                    List<Location> locs = arena.getSpawns(type);
                    for (int i = 0; i < locs.size(); i++) {
                        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("spawn-entry")
                                .replace("{type}", type.name())
                                .replace("{index}", String.valueOf(i + 1))
                                .replace("{world}", locs.get(i).getWorld() != null ? locs.get(i).getWorld().getName() : "?")
                                .replace("{x}", String.valueOf(locs.get(i).getBlockX()))
                                .replace("{y}", String.valueOf(locs.get(i).getBlockY()))
                                .replace("{z}", String.valueOf(locs.get(i).getBlockZ())));
                    }
                }
            }
            case "clear" -> {
                SpawnType type = args.length >= 3 ? SpawnType.fromString(args[2]) : SpawnType.BATTLE;
                // Optional 4th arg = a specific spawn number to remove (1-based).
                if (args.length >= 4) {
                    try {
                        int idx = Integer.parseInt(args[3]) - 1;
                        List<Location> locs = arena.getSpawns(type);
                        if (idx < 0 || idx >= locs.size()) {
                            MessageUtil.send(sender, "<red>No " + type.name() + " spawn #" + args[3] + ".");
                            return;
                        }
                        locs.remove(idx);
                        ArenaManager.getInstance().save(arena);
                        MessageUtil.send(sender, "<yellow>Removed " + type.name() + " spawn #" + args[3]
                                + " from " + arena.getName() + ".");
                    } catch (NumberFormatException e) {
                        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-number").replace("{input}", args[3]));
                    }
                    return;
                }
                arena.clearSpawns(type);
                ArenaManager.getInstance().save(arena);
                MessageUtil.send(sender, "<yellow>Cleared all " + type.name() + " spawns for " + arena.getName() + ".");
            }
            default -> MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("set", "list", "clear");
        if (args.length == 2) return ArenaManager.getInstance().getAll().stream().map(Arena::getName).filter(n -> n.startsWith(args[1])).toList();
        if (args.length == 3) return List.of("BATTLE", "LOBBY", "SPECTATOR");
        return List.of();
    }
}
