package com.omniarena.permission;

import com.omniarena.config.ConfigManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PermissionChecker {

    public static final String ADMIN_ALL     = "omniarena.admin";
    public static final String ADMIN_ARENA   = "omniarena.admin.arena";
    public static final String ADMIN_KIT     = "omniarena.admin.kit";
    public static final String ADMIN_SIGN    = "omniarena.admin.sign";
    public static final String ADMIN_HOLOGRAM = "omniarena.admin.hologram";
    public static final String ADMIN_STATS   = "omniarena.admin.stats";
    public static final String ADMIN_RELOAD  = "omniarena.admin.reload";
    public static final String ADMIN_ACCESS  = "omniarena.admin.access";

    public static final String PLAYER_USE    = "omniarena.use";
    public static final String PLAYER_KIT    = "omniarena.kit.use";
    public static final String PLAYER_QUEUE  = "omniarena.queue";
    public static final String PLAYER_SPEC   = "omniarena.spectate";
    public static final String JOIN_BYPASS   = "omniarena.join.bypass";

    private PermissionChecker() {}

    /**
     * Admin check: OP always passes. Permission-plugin mode: checks node.
     * Falls back to isOp() when no permission plugin is installed.
     */
    public static boolean hasAdmin(CommandSender sender, String node) {
        if (!(sender instanceof Player player)) return true; // console always passes
        if (player.isOp()) return true;
        return player.hasPermission(ADMIN_ALL) || player.hasPermission(node);
    }

    public static boolean hasAdminArena(CommandSender sender) {
        return hasAdmin(sender, ADMIN_ARENA);
    }

    public static boolean hasAdminKit(CommandSender sender) {
        return hasAdmin(sender, ADMIN_KIT);
    }

    public static boolean hasAdminSign(CommandSender sender) {
        return hasAdmin(sender, ADMIN_SIGN);
    }

    public static boolean hasAdminStats(CommandSender sender) {
        return hasAdmin(sender, ADMIN_STATS);
    }

    public static boolean hasAdminReload(CommandSender sender) {
        return hasAdmin(sender, ADMIN_RELOAD);
    }

    public static boolean canUse(Player player) {
        return player.hasPermission(PLAYER_USE);
    }

    public static boolean canSpectate(Player player) {
        return player.hasPermission(PLAYER_SPEC);
    }

    public static boolean canBypassJoin(Player player) {
        return player.isOp() || player.hasPermission(JOIN_BYPASS);
    }

    public static int getKitSlotLimit(Player player) {
        if (player.isOp() || player.hasPermission("omniarena.kit.slots.10")) return 10;
        if (player.hasPermission("omniarena.kit.slots.5")) return 5;
        return ConfigManager.getInstance().getConfig().getInt("kits.default-slot-limit", 3);
    }

    public static boolean canUseKit(Player player, String permissionNode) {
        if (permissionNode == null || permissionNode.isBlank()) return true;
        return player.isOp() || player.hasPermission(permissionNode);
    }
}
