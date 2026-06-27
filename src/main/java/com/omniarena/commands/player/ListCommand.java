package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaState;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class ListCommand extends SubCommand {

    public ListCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_USE, "/oa list", "List all arenas");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Collection<Arena> arenas = ArenaManager.getInstance().getAll();
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-list-header")
                .replace("{count}", String.valueOf(arenas.size())));

        for (Arena arena : arenas) {
            Optional<Match> match = MatchManager.getInstance().getMatch(arena);
            int players = match.map(Match::getPlayerCount).orElse(0);
            String status = formatStatus(arena.getState());

            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-list-entry")
                    .replace("{arena}", arena.getName())
                    .replace("{mode}", arena.getGameMode().name())
                    .replace("{status}", status)
                    .replace("{players}", String.valueOf(players))
                    .replace("{max}", String.valueOf(arena.getMaxPlayers())));
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
    public List<String> tabComplete(CommandSender sender, String[] args) { return List.of(); }
}
