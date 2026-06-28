package com.omniarena.commands.standalone;

import com.omniarena.OmniArena;
import com.omniarena.commands.CommandManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Forwards standalone player commands to CommandManager.
 * Allows /spectate, /stats, etc. to work same as /oa spectate, /oa stats
 */
public class PlayerCommandForwarder implements CommandExecutor, TabCompleter {

    private final OmniArena plugin;
    private final String subcommandName;

    public PlayerCommandForwarder(OmniArena plugin, String subcommandName) {
        this.plugin = plugin;
        this.subcommandName = subcommandName;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        return plugin.getCommandManager().onCommand(sender, command, "oa",
            mergeArgs(subcommandName, args));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        return plugin.getCommandManager().onTabComplete(sender, command, "oa",
            mergeArgs(subcommandName, args));
    }

    private String[] mergeArgs(String subcommand, String[] args) {
        String[] merged = new String[args.length + 1];
        merged[0] = subcommand;
        System.arraycopy(args, 0, merged, 1, args.length);
        return merged;
    }
}
