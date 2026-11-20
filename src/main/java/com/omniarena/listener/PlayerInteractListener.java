package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.MatchManager;
import com.omniarena.match.MatchState;
import com.omniarena.sign.ArenaSign;
import com.omniarena.sign.SignManager;
import com.omniarena.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import com.omniarena.util.ColorUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

public class PlayerInteractListener implements Listener {

    private final OmniArena plugin;
    private final java.util.Map<java.util.UUID, Long> signClickCooldown = new java.util.concurrent.ConcurrentHashMap<>();

    public PlayerInteractListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    /** Configurable selection-wand material; defaults to a wooden hoe. */
    public static org.bukkit.Material wandMaterial() {
        String name = ConfigManager.getInstance().getConfig().getString("general.wand-material", "WOODEN_HOE");
        try { return org.bukkit.Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return org.bukkit.Material.WOODEN_HOE; }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRegionSelect(PlayerInteractEvent event) {
        if (plugin.isStopped()) return;
        // Right-click fires for BOTH hands; only handle the main hand so the
        // "Position 2 set" message isn't sent twice.
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!com.omniarena.permission.PermissionChecker.hasAdminArena(player)) return;

        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() != wandMaterial()) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            ArenaManager.getInstance().setPoint1(player.getUniqueId(), block.getLocation());
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("region-first-point")
                    .replace("{x}", String.valueOf(block.getX()))
                    .replace("{y}", String.valueOf(block.getY()))
                    .replace("{z}", String.valueOf(block.getZ())));
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            ArenaManager.getInstance().setPoint2(player.getUniqueId(), block.getLocation());
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("region-second-point")
                    .replace("{x}", String.valueOf(block.getX()))
                    .replace("{y}", String.valueOf(block.getY()))
                    .replace("{z}", String.valueOf(block.getZ())));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.isStopped()) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // Right-click fires once per hand: only react to the main hand so we don't double-handle
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        SignManager sm = SignManager.getInstance();

        if (sm == null || !sm.isArenaSign(block)) return;

        event.setCancelled(true);
        // Admins sneaking with an item in hand can edit/dye/wax the sign normally
        if (player.isSneaking() && player.getInventory().getItemInMainHand().getType() != org.bukkit.Material.AIR
                && com.omniarena.permission.PermissionChecker.hasAdminSign(player)) {
            event.setCancelled(false);
            return;
        }

        // Click cooldown (ms) so double-clicks / spam don't fire twice
        long now = System.currentTimeMillis();
        Long last = signClickCooldown.get(player.getUniqueId());
        if (last != null && now - last < 750) return;
        signClickCooldown.put(player.getUniqueId(), now);

        if (!com.omniarena.permission.PermissionChecker.canUse(player)) {
            MessageUtil.send(player, "<red>You don't have permission to join arenas. <gray>Ask an admin for the <white>omniarena.use</white> permission.");
            return;
        }
        Optional<ArenaSign> signOpt = sm.getSign(block);
        if (signOpt.isEmpty()) return;

        String arenaName = signOpt.get().getArenaName();
        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(arenaName);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("arena-not-found")
                    .replace("{arena}", arenaName));
            return;
        }

        Arena arena = arenaOpt.get();

        if (!arena.isEnabled()) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("arena-disabled-join")
                    .replace("{arena}", arenaName));
            return;
        }

        if (MatchManager.getInstance().isInArena(player)) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("already-in-arena"));
            return;
        }

        if (arena.isRunning() && !arena.isMidMatchJoin()) {
            // Offer spectate
            Component msg = ColorUtil.parse(
                    ConfigManager.getInstance().getMessage("arena-in-progress")
                            .replace("{arena}", arenaName))
                    .clickEvent(ClickEvent.runCommand("/oa spectate " + arenaName))
                    .hoverEvent(HoverEvent.showText(ColorUtil.parse("<gray>Click to spectate")));
            com.omniarena.util.MessageUtil.send(player, msg);
            return;
        }

        var qm = com.omniarena.queue.QueueManager.getInstance();
        if (qm != null && qm.isQueued(player)) {
            MessageUtil.send(player, "<red>You are in a queue, so you can't join an arena directly. <gray>Leave the queue first with <white>/oa queue leave</white>.");
            return;
        }

        if (arena.isBanned(player.getUniqueId())) {
            MessageUtil.send(player, "<red>You are banned from this arena, so the sign did nothing. <gray>Ask an admin to lift the ban if this is a mistake.");
            return;
        }

        if (!arena.isWhitelisted(player.getUniqueId()) && !com.omniarena.permission.PermissionChecker.canBypassJoin(player)) {
            MessageUtil.send(player, "<red>This arena is whitelist-only and you are not on its whitelist. <gray>Ask an admin to add you.");
            return;
        }

        MatchManager.getInstance().joinArena(player, arena);
    }
}
