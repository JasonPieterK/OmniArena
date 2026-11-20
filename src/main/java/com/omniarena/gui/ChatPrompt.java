package com.omniarena.gui;

import com.omniarena.util.ColorUtil;
import com.omniarena.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;

/** Text input for menus: closes the menu and sends a clickable chat line that pre-fills a command. */
public final class ChatPrompt {

    private ChatPrompt() {}

    public static void suggest(Player player, String what, String command, String hover) {
        player.closeInventory();
        Component msg = ColorUtil.parse("<gray>" + MenuBuilder.esc(what) + ": <green>[click to type]")
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(ColorUtil.parse("<gray>" + MenuBuilder.esc(hover == null ? command : hover))));
        MessageUtil.send(player, msg);
    }
}
