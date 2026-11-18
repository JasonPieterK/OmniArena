package com.omniarena.kit;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.ColorUtil;
import com.omniarena.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KitShareHandler {

    private static final long EXPIRY_TICKS = 20 * 60; // 1 minute

    record ShareRequest(UUID senderId, String kitName, long expiryTick) {}

    private final OmniArena plugin;
    private final Map<UUID, ShareRequest> pendingRequests = new HashMap<>();

    public KitShareHandler(OmniArena plugin) {
        this.plugin = plugin;
    }

    public boolean sendShare(Player sender, Player recipient, String kitName) {
        if (!ConfigManager.getInstance().getConfig().getBoolean("kits.sharing-enabled", true)) {
            MessageUtil.send(sender, "<red>Kit sharing is disabled on this server. <gray>Admins: set <white>kits.sharing-enabled: true</white> in config.yml and run <white>/oa reload</white>.");
            return false;
        }

        // Verify sender owns the kit
        KitManager km = KitManager.getInstance();
        if (km.getPlayerKit(sender.getUniqueId(), kitName).isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-not-found")
                    .replace("{kit}", kitName));
            return false;
        }

        // Check recipient limit
        int recipientLimit = PermissionChecker.getKitSlotLimit(recipient);
        if (km.getPlayerKits(recipient.getUniqueId()).size() >= recipientLimit) {
            MessageUtil.send(sender, "<red>" + recipient.getName() + "'s kit slots are full, so they cannot receive <yellow>" + kitName + "</yellow>. <gray>They must delete a kit (<white>/kit delete <name></white>) or get more slots (<white>omniarena.kit.slots.<n></white>).");
            return false;
        }

        long expiryTick = System.currentTimeMillis() + EXPIRY_TICKS * 50L /* ms: Server#getCurrentTick is Paper-only */;
        pendingRequests.put(recipient.getUniqueId(),
                new ShareRequest(sender.getUniqueId(), kitName, expiryTick));

        // Schedule expiry
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ShareRequest req = pendingRequests.get(recipient.getUniqueId());
            if (req != null && req.senderId().equals(sender.getUniqueId())
                    && req.kitName().equals(kitName)) {
                pendingRequests.remove(recipient.getUniqueId());
                if (recipient.isOnline()) {
                    MessageUtil.send(recipient, ConfigManager.getInstance().getMessage("kit-share-expired")
                            .replace("{sender}", sender.getName()));
                }
            }
        }, EXPIRY_TICKS);

        // Send clickable message to recipient
        Component msg = ColorUtil.parse(
                ConfigManager.getInstance().getMessage("kit-share-received")
                        .replace("{sender}", sender.getName())
                        .replace("{kit}", kitName))
                .clickEvent(ClickEvent.runCommand("/kit accept " + kitName))
                .hoverEvent(HoverEvent.showText(ColorUtil.parse("<green>Click to accept kit!")));

        MessageUtil.send(recipient, msg);
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-share-sent")
                .replace("{player}", recipient.getName()));
        return true;
    }

    public boolean acceptShare(Player recipient, String kitName) {
        ShareRequest req = pendingRequests.get(recipient.getUniqueId());
        if (req == null || !req.kitName().equalsIgnoreCase(kitName)) {
            MessageUtil.send(recipient, "<red>No pending kit share request for <yellow>" + kitName + "</yellow>. <gray>It may have expired or been declined; ask the sender to run <white>/kit share " + kitName + " <player></white> again.");
            return false;
        }

        if (System.currentTimeMillis() > req.expiryTick()) {
            pendingRequests.remove(recipient.getUniqueId());
            MessageUtil.send(recipient, "<red>That kit share request expired (requests last a limited time). <gray>Ask the sender to run <white>/kit share <name> <player></white> again.");
            return false;
        }

        Player sender = Bukkit.getPlayer(req.senderId());
        if (sender == null) {
            pendingRequests.remove(recipient.getUniqueId());
            MessageUtil.send(recipient, "<red>The player who shared this kit is no longer online, so it can't be accepted. <gray>Ask them to share it again when they are back.");
            return false;
        }

        KitManager km = KitManager.getInstance();
        km.getPlayerKit(req.senderId(), kitName).ifPresent(kit -> {
            int limit = PermissionChecker.getKitSlotLimit(recipient);
            boolean created = km.createPlayerKit(recipient.getUniqueId(), kit.getName(),
                    kit.getContents(), kit.getArmor(), kit.getOffHand(), limit);

            if (created) {
                MessageUtil.send(recipient, ConfigManager.getInstance().getMessage("kit-share-accepted")
                        .replace("{kit}", kitName));
            } else if (km.getPlayerKit(recipient.getUniqueId(), kit.getName()).isPresent()) {
                MessageUtil.send(recipient, ConfigManager.getInstance().getMessage("kit-already-exists")
                        .replace("{kit}", kit.getName()));
            } else {
                MessageUtil.send(recipient, ConfigManager.getInstance().getMessage("kit-slots-full")
                        .replace("{max}", String.valueOf(limit)));
            }
        });

        pendingRequests.remove(recipient.getUniqueId());
        return true;
    }

    public boolean hasPending(UUID recipientId) {
        return pendingRequests.containsKey(recipientId);
    }
}
