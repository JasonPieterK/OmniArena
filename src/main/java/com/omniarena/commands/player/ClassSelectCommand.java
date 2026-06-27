package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.kit.AdminKit;
import com.omniarena.kit.KitManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class ClassSelectCommand extends SubCommand {

    public ClassSelectCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_USE, "/oa class <name>", "Select your class/kit");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        if (args.length < 1) {
            // Open kit GUI instead
            List<AdminKit> publicKits = KitManager.getInstance().getPublicAdminKits();
            com.omniarena.kit.KitGUI.openAdminKitSelector(player, publicKits);
            return;
        }
        Optional<AdminKit> kitOpt = KitManager.getInstance().getAdminKit(args[0]);
        if (kitOpt.isEmpty() || !kitOpt.get().isPublic()) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("class-not-found").replace("{class}", args[0]));
            return;
        }
        if (!PermissionChecker.canUseKit(player, kitOpt.get().getRequiredPermission())) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("kit-no-permission")
                    .replace("{kit}", kitOpt.get().getName()));
            return;
        }
        KitManager.getInstance().equipKit(player, kitOpt.get());
        MessageUtil.send(player, ConfigManager.getInstance().getMessage("class-selected").replace("{class}", args[0]));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1)
            return KitManager.getInstance().getPublicAdminKits().stream()
                    .map(AdminKit::getName).filter(n -> n.startsWith(args[0])).toList();
        return List.of();
    }
}
