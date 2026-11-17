package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.kit.AdminKit;
import com.omniarena.kit.KitManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * /oa kit create <name>
 * /oa kit delete <name>
 * /oa kit equip <name>
 * /oa kit rename <old> <new>
 * /oa kit setpublic <name>
 * /oa kit setprivate <name>
 * /oa kit icon <name> <material>
 * /oa kit description <name> <text>
 * /oa kit cooldown <name> <seconds>
 * /oa kit permission <name> <node>
 * /oa kit list
 */
public class AdminKitCommand extends SubCommand {

    private final OmniArena plugin;

    public AdminKitCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_KIT, "/oa kit <action> [name] [args]", "Admin kit management");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }

        KitManager km = KitManager.getInstance();
        String action = args[0].toLowerCase();

        // Actions that modify an existing kit: report unknown kits instead of a fake success.
        if (java.util.Set.of("setpublic", "setprivate", "icon", "description", "cooldown", "permission", "rename").contains(action)
                && args.length >= 2 && km.getAdminKit(args[1]).isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-not-found").replace("{kit}", args[1]));
            return;
        }

        switch (action) {
            case "create" -> {
                if (!(sender instanceof Player player)) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only")); return; }
                if (args.length < 2) { MessageUtil.send(sender, "<red>Usage: /oa kit create <name>"); return; }
                String name = args[1];
                if (!KitManager.isValidName(name)) {
                    MessageUtil.send(sender, "<red>Kit names may only use letters, numbers, '_', '-' and '.' (max 32).");
                    return;
                }
                km.createAdminKit(name, player.getInventory().getContents(), player.getInventory().getArmorContents());
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-created").replace("{kit}", name));
            }
            case "delete" -> {
                if (args.length < 2) { MessageUtil.send(sender, "<red>Usage: /oa kit delete <name>"); return; }
                if (!km.deleteAdminKit(args[1])) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-not-found").replace("{kit}", args[1]));
                } else {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-deleted").replace("{kit}", args[1]));
                }
            }
            case "rename" -> {
                if (args.length < 3) { MessageUtil.send(sender, "<red>Usage: /oa kit rename <old> <new>"); return; }
                switch (km.renameAdminKit(args[1], args[2])) {
                    case OK -> MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-renamed")
                            .replace("{old}", args[1]).replace("{new}", args[2]));
                    case NOT_FOUND -> MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-not-found").replace("{kit}", args[1]));
                    case INVALID_NAME -> MessageUtil.send(sender, "<red>Kit names may only use letters, numbers, '_', '-' and '.' (max 32).");
                    case EXISTS -> MessageUtil.send(sender, "<red>An admin kit named <yellow>" + args[2] + "</yellow> already exists.");
                    case FAILED -> MessageUtil.send(sender, "<red>Could not rename the kit file; see console.");
                }
            }
            case "equip" -> {
                if (!(sender instanceof Player player)) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only")); return; }
                if (args.length < 2) { MessageUtil.send(sender, "<red>Usage: /oa kit equip <name>"); return; }
                Optional<AdminKit> kitOpt = km.getAdminKit(args[1]);
                if (kitOpt.isEmpty()) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-not-found").replace("{kit}", args[1]));
                    return;
                }
                km.equipKit(player, kitOpt.get());
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-equipped").replace("{kit}", args[1]));
            }
            case "setpublic" -> {
                if (args.length < 2) { MessageUtil.send(sender, "<red>Usage: /oa kit setpublic <name>"); return; }
                km.setAdminKitPublic(args[1], true);
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-admin-set-public").replace("{kit}", args[1]));
            }
            case "setprivate" -> {
                if (args.length < 2) { MessageUtil.send(sender, "<red>Usage: /oa kit setprivate <name>"); return; }
                km.setAdminKitPublic(args[1], false);
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-admin-set-private").replace("{kit}", args[1]));
            }
            case "icon" -> {
                if (args.length < 3) { MessageUtil.send(sender, "<red>Usage: /oa kit icon <name> <material>"); return; }
                try {
                    Material mat = Material.valueOf(args[2].toUpperCase());
                    if (!mat.isItem() || mat.isAir()) throw new IllegalArgumentException("not an item");
                    km.setAdminKitIcon(args[1], mat);
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-icon-set").replace("{kit}", args[1]));
                } catch (IllegalArgumentException e) {
                    MessageUtil.send(sender, "<red>Unknown material: " + args[2]);
                }
            }
            case "description" -> {
                if (args.length < 3) { MessageUtil.send(sender, "<red>Usage: /oa kit description <name> <text>"); return; }
                String desc = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                km.setAdminKitDescription(args[1], desc);
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-desc-set").replace("{kit}", args[1]));
            }
            case "cooldown" -> {
                if (args.length < 3) { MessageUtil.send(sender, "<red>Usage: /oa kit cooldown <name> <seconds>"); return; }
                try {
                    int cd = Integer.parseInt(args[2]);
                    if (cd < 0) { MessageUtil.send(sender, "<red>Cooldown cannot be negative."); return; }
                    km.setAdminKitCooldown(args[1], cd);
                    MessageUtil.send(sender, "<green>Cooldown set for kit <yellow>" + args[1] + "</yellow>.");
                } catch (NumberFormatException e) { MessageUtil.send(sender, "<red>Invalid number."); }
            }
            case "permission" -> {
                if (args.length < 3) { MessageUtil.send(sender, "<red>Usage: /oa kit permission <name> <node>"); return; }
                boolean clear = args[2].equalsIgnoreCase("none") || args[2].equals("-");
                km.setAdminKitPermission(args[1], clear ? "" : args[2]);
                MessageUtil.send(sender, clear
                        ? "<green>Permission cleared for kit <yellow>" + args[1] + "</yellow>."
                        : "<green>Permission set for kit <yellow>" + args[1] + "</yellow>.");
            }
            case "list" -> {
                MessageUtil.send(sender, "<gold>--- Admin Kits ---");
                for (AdminKit kit : km.getAllAdminKits()) {
                    MessageUtil.send(sender, " <gray>- <yellow>" + kit.getName()
                            + " <dark_gray>[" + (kit.isPublic() ? "<green>PUBLIC" : "<red>PRIVATE") + "<dark_gray>]");
                }
            }
            default -> MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("create", "delete", "equip", "setpublic", "setprivate", "icon", "description", "cooldown", "permission", "rename", "list");
        if (args.length == 2 && !args[0].equalsIgnoreCase("list"))
            return KitManager.getInstance().getAllAdminKits().stream().map(AdminKit::getName).filter(n -> n.startsWith(args[1])).toList();
        return List.of();
    }
}
