package com.omniarena.commands.standalone;

import com.omniarena.OmniArena;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StandaloneLeaveCommand implements CommandExecutor {

    private final OmniArena plugin;

    public StandaloneLeaveCommand(OmniArena plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }
        
        // Call the wrapped command directly
        new com.omniarena.commands.player.LeaveCommand(plugin).execute(sender, args);
        return true;
    }
}
