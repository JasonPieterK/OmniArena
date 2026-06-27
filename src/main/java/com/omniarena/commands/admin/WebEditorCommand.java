package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import com.omniarena.web.WebDashboardServer;
import org.bukkit.command.CommandSender;

import java.util.List;

public class WebEditorCommand extends SubCommand {

    private final OmniArena plugin;

    public WebEditorCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ALL, "/oa editor", "Open the web config editor");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        WebDashboardServer web = plugin.getWebDashboardServer();
        if (web == null) {
            MessageUtil.send(sender, "<red>Web editor is disabled.");
            return;
        }

        String url = web.createEditorUrl();
        if (url == null) {
            int active = web.activeSessionCount();
            int max = web.getSessions().getMaxSessions();
            MessageUtil.send(sender, "<red>Session limit reached (<yellow>" + active + "/" + max + "</yellow> active).");
            MessageUtil.send(sender, "<gray>Kill existing sessions first: <yellow>/oa override killweb");
            return;
        }

        MessageUtil.send(sender, "<gold>Web editor URL: <yellow>" + url);
        MessageUtil.send(sender, "<gray>Open in browser, edit, click Apply.");
        MessageUtil.send(sender, "<dark_gray>Kill session: <yellow>/oa override killweb");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
