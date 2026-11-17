package com.omniarena.commands.admin;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.sign.ArenaSign;
import com.omniarena.sign.SignManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class SignCommand extends SubCommand {

    public SignCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_SIGN, "/oa sign <create|remove|list> [arena]", "Manage arena signs");
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

        SignManager sm = SignManager.getInstance();

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", "/oa sign create <arena>"));
                    return;
                }
                Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[1]);
                if (arenaOpt.isEmpty()) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[1]));
                    return;
                }
                Block target = player.getTargetBlockExact(5);
                if (target == null) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("sign-target-block"));
                    return;
                }
                if (sm.createSign(arenaOpt.get(), target)) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("sign-created").replace("{arena}", args[1]));
                } else {
                    MessageUtil.send(player, "<red>Target block is not a sign.");
                }
            }
            case "remove" -> {
                Block target = player.getTargetBlockExact(5);
                if (target == null || !sm.isArenaSign(target)) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("sign-not-found"));
                    return;
                }
                sm.removeSign(target);
                MessageUtil.send(player, ConfigManager.getInstance().getMessage("sign-removed"));
            }
            case "list" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", "/oa sign list <arena>"));
                    return;
                }
                Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[1]);
                if (arenaOpt.isEmpty()) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[1]));
                    return;
                }
                Arena arena = arenaOpt.get();
                MessageUtil.send(player, ConfigManager.getInstance().getMessage("sign-list-header")
                        .replace("{arena}", arena.getName())
                        .replace("{count}", String.valueOf(arena.getSigns().size())));
                for (ArenaSign sign : arena.getSigns()) {
                    MessageUtil.send(player, ConfigManager.getInstance().getMessage("sign-list-entry")
                            .replace("{world}", sign.getWorldName())
                            .replace("{x}", String.valueOf(sign.getLocation().getBlockX()))
                            .replace("{y}", String.valueOf(sign.getLocation().getBlockY()))
                            .replace("{z}", String.valueOf(sign.getLocation().getBlockZ())));
                }
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(List.of("create", "remove", "list"), cur);
        if (args.length == 2 && !args[0].equalsIgnoreCase("remove")) return TabUtil.arenas(cur);
        return List.of();
    }
}
