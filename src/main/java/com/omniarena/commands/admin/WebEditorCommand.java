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
        super(PermissionChecker.ADMIN_ALL, "/oa website", "Open the web dashboard");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        WebDashboardServer web = plugin.getWebDashboardServer();
        if (web == null) {
            MessageUtil.send(sender, "<red>The web dashboard is disabled or failed to start. <gray>Set <white>web.enabled: true</white> in config.yml, run <white>/oa reload</white> (or restart), and check the console for port/bind errors.");
            return;
        }

        String url = web.createEditorUrl();
        if (url == null) {
            int active = web.activeSessionCount();
            int max = web.getSessions().getMaxSessions();
            MessageUtil.send(sender, "<red>Session limit reached (<yellow>" + active + "/" + max + "</yellow> active), so no new editor link can be created. <gray>Raise the limit in <white>web.max-sessions</white> in config.yml, or wait for sessions to expire.");
            MessageUtil.send(sender, "<gray>Kill existing sessions first: <yellow>/oa override killweb");
            return;
        }

        MessageUtil.send(sender, "<gold>Web dashboard ready!");
        // Send clickable link using Adventure text with click event
        net.kyori.adventure.text.Component linkComponent =
            net.kyori.adventure.text.Component.text(url)
                .color(net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                    net.kyori.adventure.text.Component.text("Click to open in browser")));
        sender.sendMessage(linkComponent);
        MessageUtil.send(sender, "<dark_gray>Kill session: <yellow>/oa override killweb");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return List.of();
    }
}
