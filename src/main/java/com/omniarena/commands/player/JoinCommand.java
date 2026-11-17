package com.omniarena.commands.player;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.MatchManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class JoinCommand extends SubCommand {

    public JoinCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_USE, "/oa join <arena>", "Join an arena");
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
        if (MatchManager.getInstance().isInArena(player)) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("already-in-arena"));
            return;
        }
        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[0]);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[0]));
            return;
        }
        Arena arena = arenaOpt.get();
        if (!arena.isEnabled()) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("arena-disabled-join").replace("{arena}", args[0]));
            return;
        }
        if (com.omniarena.queue.QueueManager.getInstance().isQueued(player)) {
            MessageUtil.send(player, "<red>You are in a queue. Leave it first.");
            return;
        }
        boolean bypass = PermissionChecker.canBypassJoin(player);
        if (!bypass && !arena.isWhitelisted(player.getUniqueId())) {
            MessageUtil.send(player, "<red>You are not whitelisted for this arena.");
            return;
        }
        if (!arena.isJoinable()) {
            MessageUtil.send(player, "<red>Arena <yellow>" + arena.getName() + "</yellow> is not joinable right now (" + arena.getState().name().toLowerCase() + ").");
            return;
        }
        int current = MatchManager.getInstance().getMatch(arena).map(com.omniarena.match.Match::getPlayerCount).orElse(0);
        if (!bypass && arena.getMaxPlayers() > 0 && current >= arena.getMaxPlayers()) {
            MessageUtil.send(player, "<red>Arena <yellow>" + arena.getName() + "</yellow> is full.");
            return;
        }
        MatchManager.getInstance().joinArena(player, arena);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return TabUtil.enabledArenas(args[0]);
        return List.of();
    }
}
