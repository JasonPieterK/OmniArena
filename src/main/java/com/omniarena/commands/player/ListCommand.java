package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaState;
import com.omniarena.commands.SmartErrors;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import com.omniarena.util.Suggest;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** /oa list lists arenas (click one for details); /oa list &lt;arena&gt; shows that arena's details (the old /oa info). */
public class ListCommand extends SubCommand {

    public ListCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_USE, "/oa list [arena]", "List arenas, or show one arena's details");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            Optional<Arena> found = SmartErrors.arena(sender, args[0], "/oa list ");
            if (found.isPresent()) details(sender, found.get());
            return;
        }
        Collection<Arena> arenas = ArenaManager.getInstance().getAll();
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-list-header")
                .replace("{count}", String.valueOf(arenas.size())));
        if (arenas.isEmpty()) {
            MessageUtil.send(sender, "<gray>No arenas yet. Admins can create one with " + Suggest.click("/oa setup <name>") + "<gray>.");
            return;
        }
        for (Arena arena : arenas) {
            Optional<Match> match = MatchManager.getInstance().getMatch(arena);
            int players = match.map(Match::getPlayerCount).orElse(0);
            String line = ConfigManager.getInstance().getRawMessage("arena-list-entry")
                    .replace("{arena}", "<click:run_command:'/oa list " + arena.getName() + "'>" + arena.getName() + "</click>")
                    .replace("{mode}", arena.getGameMode().name())
                    .replace("{status}", formatStatus(arena.getState()))
                    .replace("{players}", String.valueOf(players))
                    .replace("{max}", String.valueOf(arena.getMaxPlayers()));
            MessageUtil.send(sender, line);
        }
    }

    private void details(CommandSender sender, Arena arena) {
        Optional<Match> match = MatchManager.getInstance().getMatch(arena);
        int players = match.map(Match::getPlayerCount).orElse(0);
        ConfigManager cm = ConfigManager.getInstance();
        MessageUtil.send(sender, cm.getMessage("arena-info-header").replace("{arena}", arena.getName()));
        MessageUtil.send(sender, cm.getMessage("arena-info-mode").replace("{mode}", arena.getGameMode().name()));
        MessageUtil.send(sender, cm.getMessage("arena-info-status").replace("{status}", formatStatus(arena.getState())));
        MessageUtil.send(sender, cm.getMessage("arena-info-players")
                .replace("{players}", String.valueOf(players)).replace("{max}", String.valueOf(arena.getMaxPlayers())));
        MessageUtil.send(sender, cm.getMessage("arena-info-region")
                .replace("{region}", arena.getRegion().isDefined() ? arena.getRegion().getDisplayString() : "not set"));
        MessageUtil.send(sender, cm.getMessage("arena-info-spawns")
                .replace("{spawns}", String.valueOf(arena.getBattleSpawns().size())));
        if (arena.isJoinable()) {
            MessageUtil.send(sender, "<gray>Join with " + Suggest.click("/oa join " + arena.getName()) + "<gray> or watch with "
                    + Suggest.click("/oa spectate " + arena.getName()) + "<gray>.");
        } else if (PermissionChecker.hasAdmin(sender, PermissionChecker.ADMIN_ARENA)) {
            for (String line : SmartErrors.setupChecklist(arena)) MessageUtil.send(sender, line);
        }
    }

    private String formatStatus(ArenaState state) {
        return switch (state) {
            case WAITING    -> "<green>Waiting";
            case STARTING   -> "<yellow>Starting";
            case RUNNING    -> "<gold>Running";
            case ENDING     -> "<red>Ending";
            case RESETTING  -> "<gray>Resetting";
            case DISABLED   -> "<red>Disabled";
            case MAINTENANCE -> "<dark_red>Maintenance";
        };
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return TabUtil.arenas(args[0]);
        return List.of();
    }
}
