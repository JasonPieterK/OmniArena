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
        super(PermissionChecker.ADMIN_ALL, "/oa web [kill]", "Open the web dashboard (kill = revoke all sessions)");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        WebDashboardServer web = plugin.getWebDashboardServer();
        if (args.length > 0 && args[0].equalsIgnoreCase("kill")) {
            if (web == null) { MessageUtil.send(sender, "<red>The web dashboard is not running, so there are no sessions to kill."); return; }
            int before = web.activeSessionCount();
            web.killAllSessions();
            MessageUtil.send(sender, "<green>Killed <yellow>" + before + "</yellow> active web session(s).");
            return;
        }
        if (web == null) {
            MessageUtil.send(sender, "<red>The web dashboard is disabled or failed to start. <gray>Set <white>web.enabled: true</white> in config.yml, run <white>/oa reload</white> (or restart), and check the console for port/bind errors.");
            return;
        }

        String url = web.createEditorUrl();
        if (url == null) {
            int active = web.activeSessionCount();
            int max = web.getSessions().getMaxSessions();
            MessageUtil.send(sender, "<red>Session limit reached (<yellow>" + active + "/" + max + "</yellow> active), so no new editor link can be created. <gray>Raise the limit in <white>web.max-sessions</white> in config.yml, or wait for sessions to expire.");
            MessageUtil.send(sender, "<gray>Kill existing sessions first: <yellow>/oa web kill");
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
        MessageUtil.send(sender, linkComponent);
        MessageUtil.send(sender, "<dark_gray>Revoke all sessions: <yellow>/oa web kill");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return com.omniarena.commands.TabUtil.filter(List.of("kill"), args[0]);
        return List.of();
    }
}
