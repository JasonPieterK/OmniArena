package com.omniarena.commands.admin;

import java.util.Locale;
import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.regen.BlockRestoreModule;
import com.omniarena.commands.SmartErrors;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Optional;

public class RegenCommand extends SubCommand {

    private final OmniArena plugin;

    public RegenCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA,
                "/oa regen <arena> | /oa regen snapshot <arena>",
                "Rebuild an arena from its snapshot (regen options live in /oa settings)");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            SmartErrors.wrongArgs(sender, getUsage(), "/oa regen <arena>");
            return;
        }

        if (args[0].equalsIgnoreCase("snapshot")) handleSnapshot(sender, args);
        else restoreArena(sender, args[0]);
    }

    private void restoreArena(CommandSender sender, String arenaName) {
        Optional<Arena> arenaOpt = SmartErrors.arena(sender, arenaName, "/oa regen ");
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        if (restoreBusy(sender, arena)) return;
        if (com.omniarena.match.MatchManager.getInstance().getMatch(arena).isPresent()) {
            MessageUtil.send(sender, "<red>Cannot regenerate <yellow>" + arena.getName() + "</yellow> while a match is active. <gray>Wait for it to end (or have the players leave), then retry.");
            return;
        }
        BlockRestoreModule restore = plugin.getBlockRestoreModule();
        if (!restore.hasSnapshot(arena.getName())) {
            MessageUtil.send(sender, "<red>No snapshot for <yellow>" + arena.getName()
                    + "</yellow>. Run <yellow>/oa regen snapshot " + arena.getName() + "</yellow> first.");
            return;
        }

        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("regen-started").replace("{arena}", arena.getName()));
        final int[] lastPercent = {-1};
        restore.restore(arena,
                (done, total) -> sendProgress(sender, arena.getName(), done, total, lastPercent),
                () -> MessageUtil.send(sender, ConfigManager.getInstance().getMessage("regen-complete").replace("{arena}", arena.getName())),
                () -> {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("regen-failed").replace("{arena}", arena.getName()));
                    String why = restore.getLastError(arena.getName());
                    if (why != null) MessageUtil.send(sender, "<red>" + why);
                });
    }

    private boolean restoreBusy(CommandSender sender, Arena arena) {
        if (plugin.getBlockRestoreModule().isRegenerating(arena.getName())) {
            MessageUtil.send(sender, "<red>Arena <yellow>" + arena.getName() + "</yellow> is already being regenerated.");
            return true;
        }
        return false;
    }

    private void handleSnapshot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            SmartErrors.wrongArgs(sender, "/oa regen snapshot <arena>", "/oa regen snapshot <arena>");
            return;
        }

        Optional<Arena> arenaOpt = SmartErrors.arena(sender, args[1], "/oa regen snapshot ");
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        if (!arena.getRegion().isDefined()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("region-not-set").replace("{arena}", args[1]));
            return;
        }

        MessageUtil.send(sender, "<yellow>Creating snapshot for <yellow>" + args[1] + "</yellow>...");
        plugin.getBlockRestoreModule().createSnapshot(arena,
                () -> MessageUtil.send(sender, "<green>Snapshot saved for <yellow>" + args[1] + "</yellow>."),
                why -> MessageUtil.send(sender, "<red>Snapshot failed: " + why));
    }

    private void sendProgress(CommandSender sender, String arenaName, int done, int total, int[] lastPercent) {
        if (!ConfigManager.getInstance().getConfig().getBoolean("regen.progress-messages", true)) return;
        int percent = total <= 0 ? 100 : (int) Math.floor(done * 100.0 / total);
        int interval = Math.max(1, ConfigManager.getInstance().getConfig().getInt("advanced.regen.progress-interval-percent", 10));
        if (percent < 100 && percent / interval == lastPercent[0] / interval) return;
        lastPercent[0] = percent;
        int bars = Math.min(20, Math.max(0, percent / 5));
        String bar = "<green>" + "█".repeat(bars) + "<dark_gray>" + "░".repeat(20 - bars);
        MessageUtil.send(sender, "<gray>Regen <yellow>" + arenaName + "</yellow> " + bar
                + " <white>" + percent + "%</white> <dark_gray>(" + done + "/" + total + ")");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            List<String> options = new java.util.ArrayList<>(List.of("snapshot"));
            options.addAll(TabUtil.arenas(""));
            return TabUtil.filter(options, cur);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("snapshot")) return TabUtil.arenas(cur);
        return List.of();
    }
}
