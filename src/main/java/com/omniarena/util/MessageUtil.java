package com.omniarena.util;

import com.omniarena.platform.Platform;
import net.kyori.adventure.bossbar.BossBar;
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
        Platform.get().send(sender, message);
    }

    public static void send(CommandSender sender, String miniMessage) {
        Platform.get().send(sender, ColorUtil.parse(miniMessage));
    }

    /** Server-wide broadcast (all players + console). */
    public static void broadcast(Component message) {
        Platform.get().broadcast(message);
    }

    public static void broadcast(String miniMessage) {
        Platform.get().broadcast(ColorUtil.parse(miniMessage));
    }

    public static void showBossBar(Player player, BossBar bar) {
        Platform.get().showBossBar(player, bar);
    }

    public static void hideBossBar(Player player, BossBar bar) {
        Platform.get().hideBossBar(player, bar);
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Platform.get().title(player, net.kyori.adventure.title.Title.title(
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
        Platform.get().actionBar(player, ColorUtil.parse(miniMessage));
    }
}
