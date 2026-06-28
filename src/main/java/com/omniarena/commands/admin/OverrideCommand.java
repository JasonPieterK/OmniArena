package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.regen.BlockRestoreModule;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.stats.StatsManager;
import com.omniarena.util.MessageUtil;
import com.omniarena.web.WebDashboardServer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class OverrideCommand extends SubCommand {

    private final OmniArena plugin;

    public OverrideCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_OVERRIDE, "/oa override <action> [args]", "Powerful admin overrides");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "killweb" -> killWeb(sender);
            case "forceend" -> forceEnd(sender, args);
            case "endarena" -> forceEnd(sender, args);  // alias
            case "evacuate" -> evacuate(sender, args);
            case "freeze" -> freeze(sender, args);
            case "lockdown" -> setLockdown(sender, true);
            case "unlockdown" -> setLockdown(sender, false);
            case "purge" -> purge(sender, args);
            case "god" -> god(sender, args);
            case "broadcast" -> broadcast(sender, args);
            case "snapshotall" -> snapshotAll(sender);
            case "resetstats" -> resetStats(sender, args);
            case "forcewin" -> forceWin(sender, args);
            case "forcestart" -> forceStart(sender, args);
            case "startarena" -> startArena(sender, args);
            default -> showHelp(sender);
        }
    }

    private void killWeb(CommandSender sender) {
        WebDashboardServer web = plugin.getWebDashboardServer();
        if (web == null) {
            MessageUtil.send(sender, "<red>Web dashboard is not running.");
            return;
        }
        int before = web.activeSessionCount();
        web.killAllSessions();
        MessageUtil.send(sender, "<green>Killed <yellow>" + before + "</yellow> active web session(s).");
    }

    private void forceEnd(CommandSender sender, String[] args) {
        Arena arena = resolveArena(sender, args, 1);
        if (arena == null) return;
        Optional<Match> match = MatchManager.getInstance().getMatch(arena);
        if (match.isEmpty()) {
            MessageUtil.send(sender, "<red>No active match in <yellow>" + arena.getName() + "</yellow>.");
            return;
        }
        MatchManager.getInstance().endMatch(match.get(), new com.omniarena.match.MatchResult(
                arena.getName(), null, null, null,
                com.omniarena.match.MatchResult.Outcome.FORFEIT,
                match.get().getDurationSeconds(),
                new ArrayList<>(match.get().getPlayers())));
        MessageUtil.send(sender, "<green>Forced end on <yellow>" + arena.getName() + "</yellow>.");
    }

    private void evacuate(CommandSender sender, String[] args) {
        Arena arena = resolveArena(sender, args, 1);
        if (arena == null) return;
        MatchManager mm = MatchManager.getInstance();
        Optional<Match> match = mm.getMatch(arena);
        if (match.isEmpty()) {
            MessageUtil.send(sender, "<red>No active match in <yellow>" + arena.getName() + "</yellow>.");
            return;
        }
        List<Player> players = match.get().getPlayers().stream()
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .toList();
        players.forEach(mm::leaveArena);
        MessageUtil.send(sender, "<green>Evacuated <yellow>" + players.size() + "</yellow> player(s) from <yellow>" + arena.getName() + "</yellow>.");
    }

    private void freeze(CommandSender sender, String[] args) {
        Arena arena = resolveArena(sender, args, 1);
        if (arena == null) return;
        MatchManager mm = MatchManager.getInstance();
        boolean enabled = !mm.isArenaFrozen(arena.getName());
        mm.setArenaFrozen(arena.getName(), enabled);
        MessageUtil.send(sender, enabled
                ? "<green>Frozen arena <yellow>" + arena.getName() + "</yellow>."
                : "<yellow>Unfrozen arena <yellow>" + arena.getName() + "</yellow>.");
    }

    private void setLockdown(CommandSender sender, boolean enabled) {
        MatchManager.getInstance().setLockdown(enabled);
        MessageUtil.send(sender, enabled
                ? "<red>Global lockdown enabled."
                : "<green>Global lockdown disabled.");
    }

    private void purge(CommandSender sender, String[] args) {
        Arena arena = resolveArena(sender, args, 1);
        if (arena == null) return;
        MatchManager mm = MatchManager.getInstance();
        Optional<Match> match = mm.getMatch(arena);
        if (match.isPresent()) {
            List<Player> players = match.get().getPlayers().stream()
                    .map(Bukkit::getPlayer)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            players.forEach(mm::leaveArena);
        }
        BlockRestoreModule restore = plugin.getBlockRestoreModule();
        if (!restore.hasSnapshot(arena.getName())) {
            MessageUtil.send(sender, "<red>No snapshot found for <yellow>" + arena.getName() + "</yellow>.");
            return;
        }
        restore.restore(arena,
                () -> MessageUtil.send(sender, "<green>Purged and restored <yellow>" + arena.getName() + "</yellow>."),
                () -> MessageUtil.send(sender, "<red>Purge failed for <yellow>" + arena.getName() + "</yellow>."));
    }

    private void god(CommandSender sender, String[] args) {
        Arena arena = resolveArena(sender, args, 1);
        if (arena == null) return;
        MatchManager mm = MatchManager.getInstance();
        Optional<Match> match = mm.getMatch(arena);
        if (match.isEmpty()) {
            MessageUtil.send(sender, "<red>No active match in <yellow>" + arena.getName() + "</yellow>.");
            return;
        }
        boolean enable = match.get().getPlayers().stream().anyMatch(uuid -> !mm.isGodMode(uuid));
        int affected = 0;
        for (java.util.UUID uuid : match.get().getPlayers()) {
            mm.setGodMode(uuid, enable);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setInvulnerable(enable);
                affected++;
            }
        }
        MessageUtil.send(sender, enable
                ? "<green>God mode enabled for <yellow>" + affected + "</yellow> player(s)."
                : "<yellow>God mode disabled for <yellow>" + affected + "</yellow> player(s).");
    }

    private void broadcast(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, "<red>Usage: <yellow>/oa override broadcast <message>");
            return;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        MatchManager mm = MatchManager.getInstance();
        for (Match match : mm.getActiveMatches()) {
            mm.broadcastArena(match, "<gold>[ADMIN] <white>" + message);
        }
        MessageUtil.send(sender, "<green>Broadcast sent to active arena players.");
    }

    private void snapshotAll(CommandSender sender) {
        BlockRestoreModule restore = plugin.getBlockRestoreModule();
        java.util.Collection<Arena> arenas = ArenaManager.getInstance().getAll();
        if (arenas.isEmpty()) {
            MessageUtil.send(sender, "<red>No arenas found.");
            return;
        }
        for (Arena arena : arenas) {
            if (!arena.getRegion().isDefined()) continue;
            restore.createSnapshot(arena, null);
        }
        MessageUtil.send(sender, "<green>Snapshot requested for <yellow>" + arenas.size() + "</yellow> arena(s).");
    }

    private void resetStats(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, "<red>Usage: <yellow>/oa override resetstats <player>");
            return;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(args[1]);
        StatsManager.getInstance().resetStats(player.getUniqueId());
        MessageUtil.send(sender, "<green>Reset stats for <yellow>" + (player.getName() != null ? player.getName() : args[1]) + "</yellow>.");
    }

    private Arena resolveArena(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            MessageUtil.send(sender, "<red>Usage: <yellow>" + getUsage());
            return null;
        }
        Optional<Arena> arena = ArenaManager.getInstance().get(args[index]);
        if (arena.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[index]));
            return null;
        }
        return arena.get();
    }

    private void forceWin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(sender, "<red>Usage: <yellow>/oa override forcewin <arena> <player|team>");
            return;
        }
        Arena arena = resolveArena(sender, args, 1);
        if (arena == null) return;
        Optional<Match> match = MatchManager.getInstance().getMatch(arena);
        if (match.isEmpty()) {
            MessageUtil.send(sender, "<red>No active match in <yellow>" + arena.getName() + "</yellow>.");
            return;
        }

        String target = args[2];
        Player player = Bukkit.getPlayer(target);
        if (player != null) {
            MatchManager.getInstance().forceWin(match.get(), player.getUniqueId());
            MessageUtil.send(sender, "<green>Forced win for <yellow>" + player.getName() + "</yellow> in <yellow>" + arena.getName() + "</yellow>.");
        } else {
            MatchManager.getInstance().forceWinTeam(match.get(), target);
            MessageUtil.send(sender, "<green>Forced win for team <yellow>" + target + "</yellow> in <yellow>" + arena.getName() + "</yellow>.");
        }
    }

    private void forceStart(CommandSender sender, String[] args) {
        Arena arena = resolveArena(sender, args, 1);
        if (arena == null) return;
        MatchManager matchManager = MatchManager.getInstance();
        Optional<Match> matchOpt = matchManager.getActiveMatches().stream()
                .filter(m -> m.getArena().getName().equals(arena.getName()))
                .findFirst();
        if (matchOpt.isEmpty()) {
            MessageUtil.send(sender, "<red>No active match in <yellow>" + arena.getName() + "</yellow>.");
            return;
        }
        Match match = matchOpt.get();
        match.cancelTasks();
        matchManager.startMatch(match);
        MessageUtil.send(sender, "<green>Force started match in <yellow>" + arena.getName() + "</yellow>.");
    }

    private void startArena(CommandSender sender, String[] args) {
        Arena arena = resolveArena(sender, args, 1);
        if (arena == null) return;
        MatchManager matchManager = MatchManager.getInstance();
        Match match = matchManager.getOrCreateMatch(arena);
        if (match.getState() == com.omniarena.match.MatchState.RUNNING) {
            MessageUtil.send(sender, "<red>Match already running in <yellow>" + arena.getName() + "</yellow>.");
            return;
        }
        match.cancelTasks();
        matchManager.startMatch(match);
        MessageUtil.send(sender, "<green>Started match in <yellow>" + arena.getName() + "</yellow>.");
    }

    private void showHelp(CommandSender sender) {
        MessageUtil.send(sender, "<gold>=== /oa override help ===");
        MessageUtil.send(sender, "<yellow>/oa override killweb</yellow> <gray>- kill active web sessions");
        MessageUtil.send(sender, "<yellow>/oa override forceend <arena></yellow> <gray>- force end a match");
        MessageUtil.send(sender, "<yellow>/oa override endarena <arena></yellow> <gray>- force end a match (alias)");
        MessageUtil.send(sender, "<yellow>/oa override evacuate <arena></yellow> <gray>- kick all players out");
        MessageUtil.send(sender, "<yellow>/oa override freeze <arena></yellow> <gray>- toggle arena freeze");
        MessageUtil.send(sender, "<yellow>/oa override lockdown</yellow> <gray>- block joins server-wide");
        MessageUtil.send(sender, "<yellow>/oa override unlockdown</yellow> <gray>- remove lockdown");
        MessageUtil.send(sender, "<yellow>/oa override purge <arena></yellow> <gray>- evacuate and restore snapshot");
        MessageUtil.send(sender, "<yellow>/oa override god <arena></yellow> <gray>- toggle invulnerable mode");
        MessageUtil.send(sender, "<yellow>/oa override broadcast <message></yellow> <gray>- message active arena players");
        MessageUtil.send(sender, "<yellow>/oa override snapshotall</yellow> <gray>- snapshot every arena");
        MessageUtil.send(sender, "<yellow>/oa override resetstats <player></yellow> <gray>- wipe one player's stats");
        MessageUtil.send(sender, "<yellow>/oa override forcewin <arena> <player|team></yellow> <gray>- force a winner");
        MessageUtil.send(sender, "<yellow>/oa override forcestart <arena></yellow> <gray>- force start a match");
        MessageUtil.send(sender, "<yellow>/oa override startarena <arena></yellow> <gray>- create and start new match");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return List.of("killweb", "forceend", "endarena", "evacuate", "freeze", "lockdown", "unlockdown", "purge", "god", "broadcast", "snapshotall", "resetstats", "forcewin", "forcestart", "startarena")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && List.of("forceend", "endarena", "evacuate", "freeze", "purge", "god", "forcestart", "startarena").contains(args[0].toLowerCase(Locale.ROOT))) {
            return ArenaManager.getInstance().getAll().stream().map(Arena::getName)
                    .filter(n -> n.startsWith(args[1])).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("forcewin")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.startsWith(args[2])).toList();
        }
        return List.of();
    }
}
