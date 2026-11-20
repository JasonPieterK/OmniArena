package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.commands.SmartErrors;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.integration.IntegrationManager;
import com.omniarena.integration.LeaderboardLines;
import com.omniarena.integration.LeaderboardManager;
import com.omniarena.integration.LeaderboardMetric;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import com.omniarena.util.Suggest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /oa leaderboard &lt;create|remove|list&gt; &lt;id&gt; [kills|wins|kd|elo] [size]: leaderboard holograms in
 * DecentHolograms or FancyHolograms, filled with OmniArena's placeholders.
 */
public class LeaderboardCommand extends SubCommand {

    private static final List<String> ACTIONS = List.of("create", "remove", "list");
    private static final List<String> METRICS = Arrays.stream(LeaderboardMetric.values()).map(LeaderboardMetric::id).toList();

    private final OmniArena plugin;

    public LeaderboardCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_LEADERBOARD, "/oa leaderboard <create|remove|list> <id> [kills|wins|kd|elo] [size]",
                "Leaderboard holograms (DecentHolograms / FancyHolograms)");
        this.plugin = plugin;
    }

    private static String msg(String key, String... kv) {
        String m = ConfigManager.getInstance().getMessage(key);
        for (int i = 0; i + 1 < kv.length; i += 2) m = m.replace("{" + kv[i] + "}", kv[i + 1]);
        return m;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        IntegrationManager im = plugin.getIntegrationManager();
        if (im == null) return;
        if (args.length < 1) {
            SmartErrors.wrongArgs(sender, getUsage(), "/oa leaderboard create top_kills kills 10");
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            SmartErrors.unknownSubcommand(sender, "/oa leaderboard", args[0], ACTIONS);
            return;
        }
        LeaderboardManager lm = im.leaderboards();
        switch (action) {
            case "list" -> list(sender, lm);
            case "remove" -> {
                if (args.length < 2) { SmartErrors.wrongArgs(sender, "/oa leaderboard remove <id>", "/oa leaderboard list"); return; }
                if (lm.remove(args[1]) == LeaderboardManager.Status.OK) {
                    MessageUtil.send(sender, msg("leaderboard-removed", "id", Suggest.escape(args[1])));
                } else {
                    MessageUtil.send(sender, msg("leaderboard-not-found", "id", Suggest.escape(args[1]),
                            "list", Suggest.click("/oa leaderboard list")));
                }
            }
            default -> create(sender, im, lm, args);
        }
    }

    private void create(CommandSender sender, IntegrationManager im, LeaderboardManager lm, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        if (args.length < 2) {
            SmartErrors.wrongArgs(sender, "/oa leaderboard create <id> [kills|wins|kd|elo] [size]", "/oa leaderboard create top_kills kills 10");
            return;
        }
        String id = args[1];
        LeaderboardMetric metric = LeaderboardMetric.KILLS;
        if (args.length >= 3) {
            Optional<LeaderboardMetric> m = LeaderboardMetric.parse(args[2]);
            if (m.isEmpty()) {
                SmartErrors.unknownSubcommand(sender, "/oa leaderboard create " + id, args[2], METRICS);
                return;
            }
            metric = m.get();
        }
        int size = LeaderboardLines.DEFAULT_SIZE;
        if (args.length >= 4) {
            try {
                size = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                SmartErrors.wrongArgs(sender, "size must be a number from " + LeaderboardLines.MIN_SIZE + " to " + LeaderboardLines.MAX_SIZE, "/oa leaderboard create " + id + " " + metric.id() + " 10");
                return;
            }
        }
        LeaderboardManager.Status st = lm.create(player.getLocation(), id, metric, size);
        switch (st) {
            case OK -> {
                MessageUtil.send(sender, msg("leaderboard-created", "id", Suggest.escape(id), "metric", metric.title(),
                        "size", String.valueOf(LeaderboardLines.clampSize(size)),
                        "hook", im.hologramHook() != null ? im.hologramHook().displayName() : "hologram plugin"));
                if (!im.hasPlaceholderAPI()) MessageUtil.send(sender, ConfigManager.getInstance().getMessage("leaderboard-static-note"));
            }
            case NO_HOOK -> MessageUtil.send(sender, msg("leaderboard-no-hook"));
            case EXISTS -> MessageUtil.send(sender, msg("leaderboard-exists", "id", Suggest.escape(id)));
            case INVALID_ID -> MessageUtil.send(sender, msg("leaderboard-invalid-id", "id", Suggest.escape(id)));
            default -> MessageUtil.send(sender, msg("leaderboard-failed"));
        }
    }

    private void list(CommandSender sender, LeaderboardManager lm) {
        var boards = lm.list();
        if (boards.isEmpty()) {
            MessageUtil.send(sender, msg("leaderboard-list-empty", "create", Suggest.click("/oa leaderboard create top_kills kills")));
            return;
        }
        MessageUtil.send(sender, msg("leaderboard-list-header", "count", String.valueOf(boards.size())));
        for (var b : boards) {
            MessageUtil.send(sender, msg("leaderboard-list-line", "id", Suggest.escape(b.id()), "metric", b.metric().id(),
                    "size", String.valueOf(b.size()), "hook", b.hookKey(), "pos", Suggest.escape(b.position())));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(ACTIONS, cur);
        if (args.length == 2 && args[0].equalsIgnoreCase("remove") && plugin.getIntegrationManager() != null) {
            return TabUtil.filter(plugin.getIntegrationManager().leaderboards().list().stream().map(LeaderboardManager.Board::id).toList(), cur);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) return TabUtil.filter(METRICS, cur);
        if (args.length == 4 && args[0].equalsIgnoreCase("create")) return TabUtil.filter(List.of("3", "5", "10"), cur);
        return List.of();
    }
}
