package com.omniarena.commands;

import com.omniarena.permission.PermissionChecker;
import org.bukkit.command.CommandSender;

import java.util.List;

public abstract class SubCommand {

    private final String requiredPermission;
    private final String usage;
    private final String description;

    protected SubCommand(String requiredPermission, String usage, String description) {
        this.requiredPermission = requiredPermission;
        this.usage = usage;
        this.description = description;
    }

    public abstract void execute(CommandSender sender, String[] args);

    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }

    public String getRequiredPermission() { return requiredPermission; }
    public String getUsage()              { return usage; }
    public String getDescription()        { return description; }
}
