package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.commands.SmartErrors;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.kit.AdminKit;
import com.omniarena.kit.KitManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.queue.QueueManager;
import com.omniarena.util.MessageUtil;
import com.omniarena.util.Suggest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /oa queue [kit] | leave | status. A party leader queues the whole party. */
public class QueueCommand extends SubCommand {

    private static final String DEFAULT_QUEUE = "ranked";
    private final OmniArena plugin;

    public QueueCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_QUEUE, "/oa queue [kit] | /oa queue leave | /oa queue status", "Join the ranked matchmaking queue");
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
        String action = args.length == 0 ? "join" : args[0].toLowerCase();
        switch (action) {
            case "leave" -> qm.leaveQueue(player);
            case "status" -> status(player, qm);
            default -> {
                // anything else is a queue/kit name: "/oa queue duel" joins the "duel" queue
                String queue = args.length == 0 ? DEFAULT_QUEUE : args[0];
                if (qm.isQueued(player)) {
                    MessageUtil.send(player, "<red>You are already in a queue. <gray>Leave it first with " + Suggest.click("/oa queue leave") + "<gray>.");
                    return;
                }
                if (!queue.equalsIgnoreCase(DEFAULT_QUEUE) && KitManager.getInstance().getAdminKit(queue).isEmpty()) {
                    List<String> opts = new ArrayList<>(List.of(DEFAULT_QUEUE, "leave", "status"));
                    for (AdminKit k : KitManager.getInstance().getAllAdminKits()) opts.add(k.getName());
                    SmartErrors.unknownSubcommand(player, "/oa queue", queue, opts);
                    return;
                }
                var kit = KitManager.getInstance().getAdminKit(queue);
                qm.joinQueue(player, kit.isPresent() ? kit.get().getName() : DEFAULT_QUEUE);
            }
        }
    }

    private void status(Player player, QueueManager qm) {
        QueueManager.QueuedPlayer e = qm.getEntry(player);
        if (e == null) {
            MessageUtil.send(player, "<gray>You are not in a queue. Join one with " + Suggest.click("/oa queue") + "<gray>.");
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

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> opts = new ArrayList<>(List.of("leave", "status", DEFAULT_QUEUE));
            for (AdminKit k : KitManager.getInstance().getAllAdminKits()) opts.add(k.getName());
            return TabUtil.filter(opts, args[0]);
        }
        return List.of();
    }
}
