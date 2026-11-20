package com.omniarena.commands.player;

import com.omniarena.commands.TabUtil;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SmartErrors;
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
        super(PermissionChecker.PLAYER_USE, "/oa join [arena]", "Join an arena (no name opens the arena picker)");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        if (MatchManager.getInstance().isInArena(player)) {
            MessageUtil.send(player, ConfigManager.getInstance().getMessage("already-in-arena"));
            return;
        }
        if (args.length < 1) {
            com.omniarena.gui.ArenaPickerMenu.open(player, com.omniarena.gui.ArenaPickerMenu.Mode.JOIN);
            return;
        }
        Optional<Arena> arenaOpt = SmartErrors.arena(player, args[0], "/oa join ");
        if (arenaOpt.isEmpty()) return;
        Arena arena = arenaOpt.get();
        if (!SmartErrors.explainNotJoinable(player, arena)) return;
        if (com.omniarena.queue.QueueManager.getInstance().isQueued(player)) {
            MessageUtil.send(player, "<red>You are in a queue, so you can't join an arena directly. <gray>Leave the queue first with <white>/oa queue leave</white>.");
            return;
        }
        boolean bypass = PermissionChecker.canBypassJoin(player);
        if (!bypass && !arena.isWhitelisted(player.getUniqueId())) {
            MessageUtil.send(player, "<red>This arena is whitelist-only and you are not on its whitelist. <gray>Ask an admin to add you, or join another arena (<white>/oa list</white>).");
            return;
        }
        int current = MatchManager.getInstance().getMatch(arena).map(com.omniarena.match.Match::getPlayerCount).orElse(0);
        if (!bypass && arena.getMaxPlayers() > 0 && current >= arena.getMaxPlayers()) {
            MessageUtil.send(player, "<red>Arena <yellow>" + arena.getName() + "</yellow> is full. <gray>Try another arena (<white>/oa list</white>) or spectate with <white>/oa spectate " + arena.getName() + "</white>.");
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
