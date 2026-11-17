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

    public PlayerInteractListener(OmniArena plugin) {
        this.plugin = plugin;
    }

    /** Configurable selection-wand material; defaults to a wooden hoe. */
    public static org.bukkit.Material wandMaterial() {
        String name = ConfigManager.getInstance().getConfig().getString("tools.wand-material", "WOODEN_HOE");
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
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        SignManager sm = SignManager.getInstance();

        if (!sm.isArenaSign(block)) return;

        event.setCancelled(true);
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
            player.sendMessage(msg);
            return;
        }

        if (arena.isBanned(player.getUniqueId())) {
            MessageUtil.send(player, "<red>You are banned from this arena, so the sign did nothing. <gray>Ask an admin to lift the ban if this is a mistake.");
            return;
        }

        if (!arena.isWhitelisted(player.getUniqueId())) {
            MessageUtil.send(player, "<red>This arena is whitelist-only and you are not on its whitelist. <gray>Ask an admin to add you.");
            return;
        }

        MatchManager.getInstance().joinArena(player, arena);
    }
}
