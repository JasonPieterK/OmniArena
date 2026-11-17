package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.regen.BlockRestoreModule;
import com.omniarena.arena.regen.RegenAnimationType;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class RegenCommand extends SubCommand {

    private final OmniArena plugin;

    public RegenCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA,
                "/oa regen <arena|snapshot <arena>|style <type>|batch <amount>|settings>",
                "Regenerate arena blocks");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "snapshot" -> handleSnapshot(sender, args);
            case "style" -> handleStyle(sender, args);
            case "batch" -> handleBatch(sender, args);
            case "settings" -> showSettings(sender);
            case "progress", "particles", "sound" -> handleToggle(sender, args);
            default -> restoreArena(sender, args[0]);
        }
    }

    private void restoreArena(CommandSender sender, String arenaName) {
        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(arenaName);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", arenaName));
            return;
        }

        Arena arena = arenaOpt.get();
        if (com.omniarena.match.MatchManager.getInstance().getMatch(arena).isPresent()) {
            MessageUtil.send(sender, "<red>Cannot regenerate <yellow>" + arena.getName() + "</yellow> while a match is active.");
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
                () -> MessageUtil.send(sender, ConfigManager.getInstance().getMessage("regen-failed").replace("{arena}", arena.getName())));
    }

    private void handleSnapshot(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", "/oa regen snapshot <arena>"));
            return;
        }

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[1]);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[1]));
            return;
        }

        Arena arena = arenaOpt.get();
        if (!arena.getRegion().isDefined()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("region-not-set").replace("{arena}", args[1]));
            return;
        }

        MessageUtil.send(sender, "<yellow>Creating snapshot for <yellow>" + args[1] + "</yellow>...");
        plugin.getBlockRestoreModule().createSnapshot(arena, () ->
                MessageUtil.send(sender, "<green>Snapshot saved for <yellow>" + args[1] + "</yellow>."));
    }

    private void handleStyle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, "<gold>Regen styles: <yellow>" + Arrays.stream(RegenAnimationType.values()).map(Enum::name).reduce((a,b)->a+", "+b).orElse("") + "</yellow>");
            MessageUtil.send(sender, "<gray>Default affects <yellow>/oa regen <arena></yellow>. Use <yellow>/oa regen style <type></yellow>.");
            return;
        }
        RegenAnimationType type;
        try { type = RegenAnimationType.valueOf(args[1].toUpperCase()); }
        catch (IllegalArgumentException e) {
            MessageUtil.send(sender, "<red>Unknown style <yellow>" + args[1] + "</yellow>. Valid: "
                    + Arrays.stream(RegenAnimationType.values()).map(Enum::name).reduce((a, b) -> a + ", " + b).orElse(""));
            return;
        }
        ConfigManager.getInstance().setConfigValue("regen.animation.type", type.name());
        MessageUtil.send(sender, "<green>Default regen animation set to <yellow>" + type.name() + "</yellow>.");
    }

    private void handleBatch(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, "<yellow>Current regen batch size: <white>"
                    + ConfigManager.getInstance().getConfig().getInt("regen.batch-size", 4000));
            return;
        }
        try {
            int amount = Math.max(1, Integer.parseInt(args[1]));
            ConfigManager.getInstance().setConfigValue("regen.batch-size", amount);
            MessageUtil.send(sender, "<green>Regen batch size set to <yellow>" + amount + "</yellow> blocks/tick.");
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-number").replace("{input}", args[1]));
        }
    }

    private void handleToggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.send(sender, "<red>Usage: <yellow>/oa regen " + args[0].toLowerCase() + " <true|false>");
            return;
        }
        String v = args[1].toLowerCase();
        boolean enabled = v.equals("true") || v.equals("yes") || v.equals("on");
        if (!enabled && !(v.equals("false") || v.equals("no") || v.equals("off"))) {
            MessageUtil.send(sender, "<red>Value must be true or false.");
            return;
        }
        String key = switch (args[0].toLowerCase()) {
            case "progress" -> "regen.progress.enabled";
            case "particles" -> "regen.animation.particles";
            case "sound" -> "regen.animation.sound";
            default -> "";
        };
        ConfigManager.getInstance().setConfigValue(key, enabled);
        MessageUtil.send(sender, "<green>" + key + " set to <yellow>" + enabled + "</yellow>.");
    }

    private void showSettings(CommandSender sender) {
        var cfg = ConfigManager.getInstance().getConfig();
        MessageUtil.send(sender, "<gold>=== Regen Settings ===");
        MessageUtil.send(sender, "<gray>Default style: <yellow>" + cfg.getString("regen.animation.type", "WAVE_SHUFFLE"));
        MessageUtil.send(sender, "<gray>Batch size: <yellow>" + cfg.getInt("regen.batch-size", 4000) + "</yellow> blocks/tick");
        MessageUtil.send(sender, "<gray>Progress bar: <yellow>" + cfg.getBoolean("regen.progress.enabled", true));
        MessageUtil.send(sender, "<gray>Particles: <yellow>" + cfg.getBoolean("regen.animation.particles", true));
        MessageUtil.send(sender, "<gray>Sound: <yellow>" + cfg.getBoolean("regen.animation.sound", true));
    }

    private void showHelp(CommandSender sender) {
        MessageUtil.send(sender, "<gold>=== /oa regen help ===");
        MessageUtil.send(sender, "<yellow>/oa regen <arena></yellow> <gray>- rebuild arena using default animation style");
        MessageUtil.send(sender, "<yellow>/oa regen snapshot <arena></yellow> <gray>- save current arena blocks");
        MessageUtil.send(sender, "<yellow>/oa regen style <type></yellow> <gray>- set default style");
        MessageUtil.send(sender, "<yellow>/oa regen batch <amount></yellow> <gray>- set blocks restored per tick");
        MessageUtil.send(sender, "<yellow>/oa regen progress <true|false></yellow> <gray>- toggle chat progress bar");
        MessageUtil.send(sender, "<yellow>/oa regen particles <true|false></yellow> <gray>- toggle particles");
        MessageUtil.send(sender, "<yellow>/oa regen sound <true|false></yellow> <gray>- toggle sound");
        MessageUtil.send(sender, "<gray>Styles: <white>" + Arrays.toString(RegenAnimationType.values()));
    }

    private void sendProgress(CommandSender sender, String arenaName, int done, int total, int[] lastPercent) {
        if (!ConfigManager.getInstance().getConfig().getBoolean("regen.progress.enabled", true)) return;
        int percent = total <= 0 ? 100 : (int) Math.floor(done * 100.0 / total);
        int interval = Math.max(1, ConfigManager.getInstance().getConfig().getInt("regen.progress.interval-percent", 10));
        if (percent < 100 && percent / interval == lastPercent[0] / interval) return;
        lastPercent[0] = percent;
        int bars = Math.min(20, Math.max(0, percent / 5));
        String bar = "<green>" + "█".repeat(bars) + "<dark_gray>" + "░".repeat(20 - bars);
        MessageUtil.send(sender, "<gray>Regen <yellow>" + arenaName + "</yellow> " + bar
                + " <white>" + percent + "%</white> <dark_gray>(" + done + "/" + total + ")");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new java.util.ArrayList<>(List.of("help", "snapshot", "style", "batch", "settings", "progress", "particles", "sound"));
            options.addAll(ArenaManager.getInstance().getAll().stream().map(Arena::getName).toList());
            return options.stream().filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("snapshot")) {
            return ArenaManager.getInstance().getAll().stream().map(Arena::getName).filter(n -> n.startsWith(args[1])).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("style")) {
            return Arrays.stream(RegenAnimationType.values()).map(Enum::name)
                    .filter(n -> n.startsWith(args[1].toUpperCase())).toList();
        }
        if (args.length == 2 && List.of("progress", "particles", "sound").contains(args[0].toLowerCase())) {
            return List.of("true", "false").stream().filter(n -> n.startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
