package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.ItemUtil;
import com.omniarena.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class WandCommand extends SubCommand {

    public WandCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa wand", "Get the arena region selection tool");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }

        ItemStack wand = ItemUtil.buildGuiItem(
                com.omniarena.listener.PlayerInteractListener.wandMaterial(),
                "&6&lOmniArena Selection Wand",
                "&7Left-click a block: &eSet Position 1",
                "&7Right-click a block: &eSet Position 2",
                "&7Once both points are set, run:",
                "&b/oa region set <arena>"
        );

        player.getInventory().addItem(wand);
        MessageUtil.send(player, "&aYou have received the OmniArena Selection Wand!");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
