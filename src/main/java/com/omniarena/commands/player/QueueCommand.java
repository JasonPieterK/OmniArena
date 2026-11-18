package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.kit.AdminKit;
import com.omniarena.kit.KitManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.queue.QueueManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /oa queue join [kit|mode] | leave | status. A party leader queues the whole party. */
public class QueueCommand extends SubCommand {

    private static final String DEFAULT_QUEUE = "ranked";
    private final OmniArena plugin;

    public QueueCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_QUEUE, "/oa queue <join [kit]|leave|status>", "Ranked matchmaking queue");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        QueueManager qm = QueueManager.getInstance();
        if (qm == null || plugin.isStopped()) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("plugin-stopped"));
            return;
        }
        String action = args.length == 0 ? "status" : args[0].toLowerCase();
        switch (action) {
            case "join" -> {
                String queue = args.length > 1 ? args[1] : DEFAULT_QUEUE;
                if (qm.isQueued(player)) {
                    MessageUtil.send(player, "<red>You are already in a queue. <gray>Use <yellow>/oa queue leave</yellow> first.");
                    return;
                }
                if (plugin.getMatchManager().isLockdown()) {
                    MessageUtil.send(player, "<red>Arenas are currently locked down by an administrator.");
                    return;
                }
                if (!queue.equalsIgnoreCase(DEFAULT_QUEUE) && KitManager.getInstance().getAdminKit(queue).isEmpty()) {
                    MessageUtil.send(player, "<red>Unknown queue/kit <yellow>" + queue + "</yellow>. Use <yellow>"
                            + DEFAULT_QUEUE + "</yellow> or an admin kit name.");
                    return;
                }
                var kit = KitManager.getInstance().getAdminKit(queue);
                qm.joinQueue(player, kit.isPresent() ? kit.get().getName() : DEFAULT_QUEUE);
            }
            case "leave" -> qm.leaveQueue(player);
            case "status" -> {
                QueueManager.QueuedPlayer e = qm.getEntry(player);
                if (e == null) {
                    MessageUtil.send(player, "<gray>You are not in a queue. Use <yellow>/oa queue join [kit]</yellow>.");
                    return;
                }
                var info = qm.getQueueInfo(player);
                long waited = (System.currentTimeMillis() - e.getQueueTime()) / 1000;
                int avg = qm.averageElo(e), range = qm.currentRange(e);
                MessageUtil.send(player, "<gold>=== Queue Status ===");
                MessageUtil.send(player, "<gray>Queue: <yellow>" + e.getKitName()
                        + (info != null ? " <gray>(position " + info.getPosition() + "/" + info.getTotalInQueue() + ")" : ""));
                if (e.isParty()) MessageUtil.send(player, "<gray>Party: <yellow>" + String.join(", ", e.getNames()));
                MessageUtil.send(player, "<gray>Waiting: <yellow>" + waited + "s");
                MessageUtil.send(player, "<gray>" + (e.isParty() ? "Average ELO" : "ELO") + ": <yellow>" + avg
                        + " <gray>- matching range: <yellow>" + (avg - range) + " - " + (avg + range));
            }
            default -> MessageUtil.send(player, ConfigManager.getInstance().getMessage("invalid-args")
                    .replace("{usage}", getUsage()));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return TabUtil.filter(List.of("join", "leave", "status"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            List<String> opts = new ArrayList<>(List.of(DEFAULT_QUEUE));
            for (AdminKit k : KitManager.getInstance().getAllAdminKits()) opts.add(k.getName());
            return TabUtil.filter(opts, args[1]);
        }
        return List.of();
    }
}
