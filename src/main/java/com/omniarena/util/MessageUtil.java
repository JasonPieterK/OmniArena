package com.omniarena.util;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class MessageUtil {

    private MessageUtil() {}

    public static Component replace(Component component, Map<String, String> placeholders) {
        String serialized = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(component);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            serialized = serialized.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ColorUtil.parse(serialized);
    }

    public static String replace(String template, Map<String, String> placeholders) {
        String result = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public static void send(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }

    public static void send(CommandSender sender, String miniMessage) {
        sender.sendMessage(ColorUtil.parse(miniMessage));
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.showTitle(net.kyori.adventure.title.Title.title(
                ColorUtil.parse(title),
                ColorUtil.parse(subtitle),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(fadeIn * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(fadeOut * 50L)
                )
        ));
    }

    public static void sendActionBar(Player player, String miniMessage) {
        player.sendActionBar(ColorUtil.parse(miniMessage));
    }
}
