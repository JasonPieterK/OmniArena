package com.omniarena.commands.player;

import com.omniarena.OmniArena;
import com.omniarena.commands.SmartErrors;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.guide.GuideBooks;
import com.omniarena.permission.PermissionChecker;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/** /oa guide [topic]: opens an in-game guide book (no topic opens the index with clickable topic links). */
public class GuideCommand extends SubCommand {

    public GuideCommand(OmniArena plugin) {
        super(PermissionChecker.PLAYER_USE, "/oa guide [topic]", "Open an in-game guide book");
        GuideBooks.install(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) GuideBooks.openIndex(p);
            else GuideBooks.sendIndexText(sender);
            return;
        }
        if (args.length > 1) {
            SmartErrors.wrongArgs(sender, getUsage(), "/oa guide play");
            return;
        }
        Optional<GuideBooks.Topic> topic = GuideBooks.find(sender, args[0]);
        if (topic.isEmpty()) {
            SmartErrors.unknownSubcommand(sender, "/oa guide", args[0], GuideBooks.visibleIds(sender));
            return;
        }
        if (sender instanceof Player p) GuideBooks.open(p, topic.get());
        else GuideBooks.sendText(sender, topic.get());
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return args.length == 1 ? TabUtil.filter(GuideBooks.visibleIds(sender), args[0]) : List.of();
    }
}
