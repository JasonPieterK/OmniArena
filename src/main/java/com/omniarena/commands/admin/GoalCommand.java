package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.match.GoalType;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class GoalCommand extends SubCommand {

    public GoalCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa goal set <arena> <type> [value]", "Set win condition");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(args[1]);
        if (arenaOpt.isEmpty()) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", args[1]));
            return;
        }
        Arena arena = arenaOpt.get();
        if (!args[0].equalsIgnoreCase("set")) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        GoalType goal;
        try { goal = GoalType.valueOf(args[2].toUpperCase().replace("-", "_").replace(" ", "_")); }
        catch (IllegalArgumentException e) {
            MessageUtil.send(sender, "<red>Unknown goal <yellow>" + args[2] + "</yellow>. Valid: "
                    + Arrays.stream(GoalType.values()).map(GoalType::name).collect(java.util.stream.Collectors.joining(", ")));
            return;
        }
        int value = -1;
        if (args.length >= 4) {
            try { value = Integer.parseInt(args[3]); if (value < 1) throw new NumberFormatException(); }
            catch (NumberFormatException e) {
                MessageUtil.send(sender, ConfigManager.getInstance().getMessage("invalid-number").replace("{input}", args[3]));
                return;
            }
        }
        arena.setGoalType(goal);
        if (value > 0) arena.setGoalValue(value);
        ArenaManager.getInstance().save(arena);
        MessageUtil.send(sender, ConfigManager.getInstance().getMessage("goal-set")
                .replace("{goal}", goal.name()).replace("{arena}", arena.getName()));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("set");
        if (args.length == 2) return ArenaManager.getInstance().getAll().stream().map(Arena::getName).filter(n -> n.startsWith(args[1])).toList();
        if (args.length == 3) return Arrays.stream(GoalType.values()).map(GoalType::name).filter(n -> n.startsWith(args[2].toUpperCase())).toList();
        return List.of();
    }
}
