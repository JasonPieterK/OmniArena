package com.omniarena.commands;

import com.omniarena.OmniArena;
import com.omniarena.config.ConfigManager;
import com.omniarena.kit.*;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Handles the /kit command — all player personal kit operations.
 * Permission-checked against player-only nodes. No admin access here.
 */
public class KitCommandExecutor implements CommandExecutor, TabCompleter {

    private final OmniArena plugin;

    public KitCommandExecutor(OmniArena plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (plugin.isStopped()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("plugin-stopped"));
            return true;
        }
        return handle(sender, args);
    }

    public boolean handle(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return true;
        }
        if (args.length == 0) {
            List<PlayerKit> playerKits = KitManager.getInstance().getPlayerKits(player.getUniqueId());
            List<com.omniarena.kit.AdminKit> adminKits = KitManager.getInstance().getAllAdminKits()
                    .stream().filter(k -> k.isPublic() || player.hasPermission("omniarena.admin")).toList();
            KitGUI.openCombinedKitMenu(player, playerKits, adminKits);
            return true;
        }

        String sub = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        KitManager km = KitManager.getInstance();

        if (sub.equals("help") || (subArgs.length > 0 && subArgs[0].equalsIgnoreCase("help"))) {
            showHelp(player, sub.equals("help") ? "" : sub);
            return true;
        }

        switch (sub) {
            case "create" -> {
                if (subArgs.length < 1) { MessageUtil.send(player, "<red>Usage: /kit create <name>"); return true; }
                int limit = PermissionChecker.getKitSlotLimit(player);
                boolean success = km.createPlayerKit(player.getUniqueId(), subArgs[0],
                        player.getInventory().getContents(),
                        player.getInventory().getArmorContents(), limit);
                if (success) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-created").replace("{kit}", subArgs[0]));
                } else {
                    // Could be full or duplicate
                    List<PlayerKit> existing = km.getPlayerKits(player.getUniqueId());
                    if (existing.size() >= limit) {
                        MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-slots-full").replace("{max}", String.valueOf(limit)));
                    } else {
                        MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-already-exists").replace("{kit}", subArgs[0]));
                    }
                }
            }
            case "list" -> {
                List<PlayerKit> kits = km.getPlayerKits(player.getUniqueId());
                int limit = PermissionChecker.getKitSlotLimit(player);
                MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-list-header")
                        .replace("{count}", String.valueOf(kits.size()))
                        .replace("{max}", String.valueOf(limit)));
                for (PlayerKit kit : kits) {
                    int itemCount = kit.getContents() == null ? 0 :
                            (int) java.util.Arrays.stream(kit.getContents())
                                    .filter(i -> i != null && i.getType() != org.bukkit.Material.AIR).count();
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-list-entry")
                            .replace("{kit}", kit.getName()).replace("{items}", String.valueOf(itemCount)));
                }
            }
            case "delete" -> {
                if (subArgs.length < 1) { MessageUtil.send(player, "<red>Usage: /kit delete <name>"); return true; }
                if (!km.deletePlayerKit(player.getUniqueId(), subArgs[0])) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-not-found").replace("{kit}", subArgs[0]));
                } else {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-deleted").replace("{kit}", subArgs[0]));
                }
            }
            case "equip" -> {
                if (subArgs.length < 1) { MessageUtil.send(player, "<red>Usage: /kit equip <name>"); return true; }
                // First check player kits, then check public admin kits
                Optional<PlayerKit> kitOpt = km.getPlayerKit(player.getUniqueId(), subArgs[0]);
                if (kitOpt.isPresent()) {
                    km.equipKit(player, kitOpt.get());
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-equipped").replace("{kit}", subArgs[0]));
                    return true;
                }
                // Check admin/preset kits
                java.util.Optional<AdminKit> adminKitOpt = km.getAdminKit(subArgs[0]);
                if (adminKitOpt.isPresent()) {
                    AdminKit adminKit = adminKitOpt.get();
                    if (adminKit.isPublic() || player.hasPermission("omniarena.admin")) {
                        km.equipKit(player, adminKit);
                        MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-equipped").replace("{kit}", subArgs[0]));
                        return true;
                    } else {
                        MessageUtil.send(player, "<red>That kit is not public.");
                        return true;
                    }
                }
                MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-not-found").replace("{kit}", subArgs[0]));
            }
            case "share" -> {
                if (subArgs.length < 2) { MessageUtil.send(player, "<red>Usage: /kit share <name> <player>"); return true; }
                Player target = Bukkit.getPlayer(subArgs[1]);
                if (target == null) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("player-not-found").replace("{player}", subArgs[1]));
                    return true;
                }
                plugin.getKitShareHandler().sendShare(player, target, subArgs[0]);
            }
            case "accept" -> {
                if (subArgs.length < 1) { MessageUtil.send(player, "<red>Usage: /kit accept <name>"); return true; }
                plugin.getKitShareHandler().acceptShare(player, subArgs[0]);
            }
            case "rename" -> {
                if (subArgs.length < 2) { MessageUtil.send(player, "<red>Usage: /kit rename <old> <new>"); return true; }
                if (!km.renamePlayerKit(player.getUniqueId(), subArgs[0], subArgs[1])) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-not-found").replace("{kit}", subArgs[0]));
                } else {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-renamed")
                            .replace("{old}", subArgs[0]).replace("{new}", subArgs[1]));
                }
            }
            case "preview" -> {
                if (subArgs.length < 1) { MessageUtil.send(player, "<red>Usage: /kit preview <name>"); return true; }
                Optional<PlayerKit> kitOpt = km.getPlayerKit(player.getUniqueId(), subArgs[0]);
                if (kitOpt.isEmpty()) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-not-found").replace("{kit}", subArgs[0]));
                    return true;
                }
                KitGUI.openKitPreview(player, kitOpt.get());
            }
            default -> MessageUtil.send(player, "<red>Unknown kit command. Use <yellow>/kit</yellow> to open kit menu.");
        }
        return true;
    }

    private void showHelp(Player player, String sub) {
        if (sub == null || sub.isBlank()) {
            MessageUtil.send(player, "<gold>=== /kit help ===");
            MessageUtil.send(player, "<yellow>/kit</yellow> <gray>- open your kit menu");
            MessageUtil.send(player, "<yellow>/kit create <name></yellow> <gray>- save current inventory");
            MessageUtil.send(player, "<yellow>/kit list</yellow> <gray>- list your kits");
            MessageUtil.send(player, "<yellow>/kit delete <name></yellow> <gray>- delete kit");
            MessageUtil.send(player, "<yellow>/kit equip <name></yellow> <gray>- equip kit anywhere");
            MessageUtil.send(player, "<yellow>/kit share <name> <player></yellow> <gray>- share kit");
            MessageUtil.send(player, "<yellow>/kit accept <name></yellow> <gray>- accept shared kit");
            MessageUtil.send(player, "<yellow>/kit rename <old> <new></yellow> <gray>- rename kit");
            MessageUtil.send(player, "<yellow>/kit preview <name></yellow> <gray>- view kit contents");
            return;
        }
        switch (sub) {
            case "create" -> MessageUtil.send(player, "<yellow>/kit create <name></yellow> <gray>- saves your current inventory, armor, and safe items.");
            case "list" -> MessageUtil.send(player, "<yellow>/kit list</yellow> <gray>- shows saved kits and slot count.");
            case "delete" -> MessageUtil.send(player, "<yellow>/kit delete <name></yellow> <gray>- removes one of your personal kits.");
            case "equip" -> MessageUtil.send(player, "<yellow>/kit equip <name></yellow> <gray>- equips a personal kit anywhere.");
            case "share" -> MessageUtil.send(player, "<yellow>/kit share <name> <player></yellow> <gray>- sends friend a copy invite.");
            case "accept" -> MessageUtil.send(player, "<yellow>/kit accept <name></yellow> <gray>- accepts pending kit share.");
            case "rename" -> MessageUtil.send(player, "<yellow>/kit rename <old> <new></yellow> <gray>- renames a personal kit.");
            case "preview" -> MessageUtil.send(player, "<yellow>/kit preview <name></yellow> <gray>- opens read-only kit preview.");
            default -> showHelp(player, "");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            return List.of("help", "create", "list", "delete", "equip", "share", "accept", "rename", "preview")
                    .stream().filter(s -> s.startsWith(args[0])).toList();
        }
        if (args.length == 2 && "help".startsWith(args[1].toLowerCase())) return List.of("help");
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (List.of("delete", "equip", "share", "rename", "preview").contains(sub)) {
                return KitManager.getInstance().getPlayerKits(player.getUniqueId())
                        .stream().map(PlayerKit::getName).filter(n -> n.startsWith(args[1])).toList();
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("share")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.startsWith(args[2])).toList();
        }
        return List.of();
    }
}
