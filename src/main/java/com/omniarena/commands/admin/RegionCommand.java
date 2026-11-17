package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * /oa region set <arena>   — first call sets P1, second call sets P2 and saves
 * /oa region info <arena>  — show current region
 */
public class RegionCommand extends SubCommand {

    private final OmniArena plugin;

    public RegionCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa region <set|info> <arena>", "Define arena region");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        if (args.length < 1) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        String action = args[0].toLowerCase();

        if (action.equals("wand")) {
            org.bukkit.inventory.ItemStack wand = com.omniarena.util.ItemUtil.buildGuiItem(
                    com.omniarena.listener.PlayerInteractListener.wandMaterial(),
                    "&6&lOmniArena Selection Wand",
                    "&7Left-click a block: &eSet Position 1",
                    "&7Right-click a block: &eSet Position 2",
                    "&7Once both points are set, run:",
                    "&b/oa region set <arena>"
            );
            player.getInventory().addItem(wand);
            MessageUtil.send(player, "&aYou have received the OmniArena Selection Wand!");
            return;
        }

        if (args.length < 2) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[1]);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[1]));
            return;
        }

        Arena arena = arenaOpt.get();
        if (!action.equals("set") && !action.equals("info")) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        UUID uid = player.getUniqueId();

        switch (action) {
            case "set" -> {
                ArenaManager am = ArenaManager.getInstance();
                if (!am.hasFullSelection(uid)) {
                    MessageUtil.send(player, "<red>You must select two points first using the selection wand.");
                    MessageUtil.send(player, "<gray>Use <yellow>/oa wand</yellow> to get the wand tool.");
                    return;
                }
                Location p1 = am.getPoint1(uid).get();
                Location p2 = am.getPoint2(uid).get();
                if (p1.getWorld() == null || p2.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
                    MessageUtil.send(player, "<red>Your selected points are in different worlds!");
                    return;
                }
                arena.setRegion(new com.omniarena.arena.ArenaRegion(p1, p2));
                ArenaManager.getInstance().save(arena);
                am.clearSelection(uid);
                MessageUtil.send(player, ConfigManager.getInstance().getMessage("region-set")
                        .replace("{arena}", arena.getName()));
                plugin.getBlockRestoreModule().createSnapshot(arena, () ->
                        MessageUtil.send(player, "<gray>Region snapshot saved for regeneration."));
            }
            case "info" -> {
                if (!arena.getRegion().isDefined()) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("region-not-set")
                            .replace("{arena}", arena.getName()));
                } else {
                    MessageUtil.send(player, "<gray>Region: <white>" + arena.getRegion().getDisplayString());
                }
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("set", "info", "wand");
        if (args.length == 2 && !args[0].equalsIgnoreCase("wand")) {
            return ArenaManager.getInstance().getAll().stream()
                    .map(Arena::getName)
                    .filter(n -> n.startsWith(args[1]))
                    .toList();
        }
        return List.of();
    }
}
