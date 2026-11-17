package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import com.omniarena.visual.HologramManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class HologramCommand extends SubCommand {

    private static final String USAGE = "/oa hologram <create|remove|list|move> ...";
    private final OmniArena plugin;

    public HologramCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_HOLOGRAM, USAGE, "Manage leaderboard holograms");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        HologramManager hm = plugin.getVisual() == null ? null : plugin.getVisual().getHologramManager();
        if (hm == null) { err(sender, "Holograms are not available right now (visual system not started)."); return; }
        if (args.length < 1) {
            err(sender, "Missing action. Usage: " + USAGE);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (!(sender instanceof Player p)) { err(sender, "Run this in-game: the hologram is placed at your location."); return; }
                if (args.length < 3) { err(sender, "Usage: /oa hologram create <id> <kills|wins|kd|elo> [size 1-" + HologramManager.MAX_SIZE + "]"); return; }
                HologramManager.Type type = HologramManager.Type.parse(args[2]);
                if (type == null) { err(sender, "Unknown leaderboard type '" + args[2] + "'. Choose one of: kills, wins, kd, elo."); return; }
                int size = HologramManager.DEFAULT_SIZE;
                if (args.length >= 4) {
                    try { size = Integer.parseInt(args[3]); }
                    catch (NumberFormatException e) { err(sender, "Size '" + args[3] + "' is not a number. Use 1-" + HologramManager.MAX_SIZE + "."); return; }
                    if (size < 1 || size > HologramManager.MAX_SIZE) { err(sender, "Size " + size + " is out of range. Use 1-" + HologramManager.MAX_SIZE + "."); return; }
                }
                String error = hm.create(args[1], type, size, p.getLocation());
                if (error != null) { err(sender, error); return; }
                MessageUtil.send(sender, "<green>Created " + type.name().toLowerCase(Locale.ROOT) + " hologram <yellow>" + args[1].toLowerCase(Locale.ROOT) + "</yellow> <gray>(top " + size + ") at your location.");
            }
            case "remove", "delete" -> {
                if (args.length < 2) { err(sender, "Usage: /oa hologram remove <id>"); return; }
                String error = hm.remove(args[1]);
                if (error != null) { err(sender, error); return; }
                MessageUtil.send(sender, "<green>Removed hologram <yellow>" + args[1].toLowerCase(Locale.ROOT) + "</yellow>.");
            }
            case "move" -> {
                if (!(sender instanceof Player p)) { err(sender, "Run this in-game: the hologram moves to your location."); return; }
                if (args.length < 2) { err(sender, "Usage: /oa hologram move <id>"); return; }
                String error = hm.move(args[1], p.getLocation());
                if (error != null) { err(sender, error); return; }
                MessageUtil.send(sender, "<green>Moved hologram <yellow>" + args[1].toLowerCase(Locale.ROOT) + "</yellow> to your location.");
            }
            case "list" -> {
                List<String> lines = hm.describe();
                if (lines.isEmpty()) { MessageUtil.send(sender, "<gray>No holograms yet. Create one with <white>/oa hologram create <id> <kills|wins|kd|elo></white>."); return; }
                MessageUtil.send(sender, "<gold>Holograms (" + lines.size() + "):");
                for (String l : lines) MessageUtil.send(sender, " <gray>- " + l);
            }
            default -> err(sender, "Unknown action '" + args[0] + "'. Usage: " + USAGE);
        }
    }

    private void err(CommandSender sender, String msg) {
        MessageUtil.send(sender, "<red>" + msg.replace("<", "\\<"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        HologramManager hm = plugin.getVisual() == null ? null : plugin.getVisual().getHologramManager();
        if (args.length == 1) return TabUtil.filter(args[0], "create", "remove", "list", "move");
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if ((sub.equals("remove") || sub.equals("move")) && hm != null) return TabUtil.filter(hm.ids(), args[1]);
            if (sub.equals("create")) return TabUtil.hint("<id>", args[1]);
        }
        if (args.length == 3 && sub.equals("create")) return TabUtil.filter(args[2], "kills", "wins", "kd", "elo");
        if (args.length == 4 && sub.equals("create")) return TabUtil.filter(args[3], "5", "10", "15");
        return List.of();
    }
}
