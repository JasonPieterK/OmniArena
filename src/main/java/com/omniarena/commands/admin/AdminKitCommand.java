package com.omniarena.commands.admin;

import java.util.Locale;
import com.omniarena.commands.TabUtil;
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
 * Reachable as "/oa kit admin <action>" or "/oa adminkit <action>" (plain "/oa kit" is the player kit menu).
 * /oa kit admin create <name>
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
        super(PermissionChecker.ADMIN_KIT, "/oa kit admin <action> [name] [args]  (alias: /oa adminkit <action> ...)", "Admin preset kit management");
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
                if (args.length < 2) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/oa kit admin create <name>")); return; }
                String name = args[1];
                if (!KitManager.isValidName(name)) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-name").replace("{input}", args[1]));
                    return;
                }
                km.createAdminKit(name, player.getInventory().getContents(), player.getInventory().getArmorContents());
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-created").replace("{kit}", name));
            }
            case "delete" -> {
                if (args.length < 2) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/oa kit admin delete <name>")); return; }
                if (!km.deleteAdminKit(args[1])) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-not-found").replace("{kit}", args[1]));
                } else {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-deleted").replace("{kit}", args[1]));
                }
            }
            case "rename" -> {
                if (args.length < 3) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/oa kit admin rename <old> <new>")); return; }
                switch (km.renameAdminKit(args[1], args[2])) {
                    case OK -> MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-renamed")
                            .replace("{old}", args[1]).replace("{new}", args[2]));
                    case NOT_FOUND -> MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-not-found").replace("{kit}", args[1]));
                    case INVALID_NAME -> MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-name").replace("{input}", args[2]));
                    case EXISTS -> MessageUtil.send(sender, "<red>An admin kit named <yellow>" + args[2] + "</yellow> already exists, so <yellow>" + args[1] + "</yellow> can't be renamed to it. <gray>Pick a different name or delete it first: <white>/oa kit admin delete " + args[2] + "</white>.");
                    case FAILED -> MessageUtil.send(sender, "<red>Could not rename the kit file for <yellow>" + args[1] + "</yellow> (the server could not write to plugins/OmniArena/kits). <gray>Check the console for the exact IO error, make sure the folder is writable, then retry.");
                }
            }
            case "equip" -> {
                if (!(sender instanceof Player player)) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only")); return; }
                if (args.length < 2) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/oa kit admin equip <name>")); return; }
                Optional<AdminKit> kitOpt = km.getAdminKit(args[1]);
                if (kitOpt.isEmpty()) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-not-found").replace("{kit}", args[1]));
                    return;
                }
                km.equipKit(player, kitOpt.get());
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-equipped").replace("{kit}", args[1]));
            }
            case "setpublic" -> {
                if (args.length < 2) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/oa kit admin setpublic <name>")); return; }
                km.setAdminKitPublic(args[1], true);
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-admin-set-public").replace("{kit}", args[1]));
            }
            case "setprivate" -> {
                if (args.length < 2) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/oa kit admin setprivate <name>")); return; }
                km.setAdminKitPublic(args[1], false);
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-admin-set-private").replace("{kit}", args[1]));
            }
            case "icon" -> {
                if (args.length < 3) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/oa kit admin icon <name> <material>")); return; }
                try {
                    Material mat = Material.valueOf(args[2].toUpperCase());
                    if (!mat.isItem() || mat.isAir()) throw new IllegalArgumentException("not an item");
                    km.setAdminKitIcon(args[1], mat);
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-icon-set").replace("{kit}", args[1]));
                } catch (IllegalArgumentException e) {
                    MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-material").replace("{input}", args[2]));
                }
            }
            case "description" -> {
                if (args.length < 3) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/oa kit admin description <name> <text>")); return; }
                String desc = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
                km.setAdminKitDescription(args[1], desc);
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("kit-desc-set").replace("{kit}", args[1]));
            }
            case "cooldown" -> {
                if (args.length < 3) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/oa kit admin cooldown <name> <seconds>")); return; }
                try {
                    int cd = Integer.parseInt(args[2]);
                    if (cd < 0) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("negative-number").replace("{input}", args[2])); return; }
                    km.setAdminKitCooldown(args[1], cd);
                    MessageUtil.send(sender, "<green>Cooldown set for kit <yellow>" + args[1] + "</yellow>.");
                } catch (NumberFormatException e) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-number").replace("{input}", args[2])); }
            }
            case "permission" -> {
                if (args.length < 3) { MessageUtil.send(sender, ConfigManager.getInstance().getMessage("usage-line").replace("{usage}", "/oa kit admin permission <name> <node>")); return; }
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
            default -> MessageUtil.send(sender, "<red>Unknown admin kit action <yellow>" + action + "</yellow>. <gray>Valid: <white>create, delete, equip, rename, setpublic, setprivate, icon, description, cooldown, permission, list</white>. Usage: <yellow>" + getUsage() + "</yellow>");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(List.of("create", "delete", "equip", "setpublic", "setprivate", "icon", "description", "cooldown", "permission", "rename", "list"), cur);
        String a = args[0].toLowerCase(java.util.Locale.ROOT);
        if (args.length == 2) {
            if (a.equals("create")) return TabUtil.hint("<name>", cur);
            if (a.equals("list")) return List.of();
            return TabUtil.adminKits(cur);
        }
        if (args.length == 3) {
            return switch (a) {
                case "icon" -> TabUtil.materials(cur);
                case "description" -> TabUtil.hint("<text>", cur);
                case "cooldown" -> TabUtil.hint("<seconds>", cur);
                case "permission" -> {
                    List<String> r = new java.util.ArrayList<>(TabUtil.filter(List.of("none"), cur));
                    r.addAll(TabUtil.hint("<node>", cur));
                    yield r;
                }
                case "rename" -> TabUtil.hint("<new-name>", cur);
                default -> List.of();
            };
        }
        return List.of();
    }
}
