package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SmartErrors;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import com.omniarena.util.Suggest;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** /oa arena &lt;tp|delete|enable|disable&gt; &lt;arena&gt; */
public class ArenaCommand extends SubCommand {

    private static final List<String> ACTIONS = List.of("tp", "delete", "enable", "disable");

    public ArenaCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa arena <tp|delete|enable|disable> <arena>", "Teleport to, delete, enable or disable an arena");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            SmartErrors.wrongArgs(sender, getUsage(), "/oa arena enable <arena>");
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            SmartErrors.unknownSubcommand(sender, "/oa arena", args[0], ACTIONS);
            return;
        }
        if (args.length < 2) {
            SmartErrors.wrongArgs(sender, "/oa arena " + action + " <arena>", "/oa arena " + action + " <arena>");
            return;
        }
        Optional<Arena> found = SmartErrors.arena(sender, args[1], "/oa arena " + action + " ");
        if (found.isEmpty()) return;
        Arena arena = found.get();
        switch (action) {
            case "tp" -> teleport(sender, arena);
            case "delete" -> {
                kickAll(arena);
                if (ArenaManager.getInstance().delete(arena.getName())) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-deleted").replace("{arena}", arena.getName()));
                }
            }
            case "enable" -> enable(sender, arena);
            case "disable" -> {
                kickAll(arena);
                ArenaManager.getInstance().disable(arena);
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-disabled").replace("{arena}", arena.getName()));
            }
            default -> { }
        }
    }

    private void teleport(CommandSender sender, Arena arena) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        Location dest = arena.getLobbySpawn();
        if (dest == null) dest = arena.getRandomBattleSpawn();
        if (dest == null) {
            MessageUtil.send(player, "<red>Arena <yellow>" + arena.getName() + "</yellow> has no lobby or battle spawn to teleport to. <gray>Stand where you want one and run "
                    + Suggest.click("/oa setup " + arena.getName() + " spawn") + "<gray>.");
            return;
        }
        player.teleport(dest);
        MessageUtil.send(player, "<green>Teleported to <yellow>" + arena.getName() + "</yellow>.");
    }

    private void enable(CommandSender sender, Arena a) {
        if (a.isEnabled()) {
            MessageUtil.send(sender, "<yellow>Arena <white>" + a.getName() + "</white> is already enabled.");
            return;
        }
        if (a.getBattleSpawns().isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-no-spawns")
                    .replace("{arena}", a.getName()).replace("{type}", "BATTLE"));
            for (String line : SmartErrors.setupChecklist(a)) MessageUtil.send(sender, line);
            return;
        }
        if (!a.getRegion().isDefined()) {
            MessageUtil.send(sender, "<yellow>Note: arena <white>" + a.getName() + "</white> has no region, so block protection and regen won't work. <gray>Fix: select two corners with the wand, then "
                    + Suggest.click("/oa setup " + a.getName() + " region") + "<gray>.");
        }
        ArenaManager.getInstance().enable(a);
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-enabled").replace("{arena}", a.getName()));
    }

    /** Removes every player and spectator from the arena's active match (if any). */
    public static void kickAll(Arena arena) {
        var mm = com.omniarena.match.MatchManager.getInstance();
        mm.getMatch(arena).ifPresent(match -> {
            for (java.util.UUID id : new java.util.ArrayList<>(match.getSpectators())) {
                Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null) mm.leaveSpectator(p);
            }
            for (java.util.UUID id : new java.util.ArrayList<>(match.getPlayers())) {
                Player p = org.bukkit.Bukkit.getPlayer(id);
                if (p != null) mm.leaveArena(p);
            }
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(ACTIONS, cur);
        if (args.length == 2) return TabUtil.arenas(cur);
        return List.of();
    }
}
