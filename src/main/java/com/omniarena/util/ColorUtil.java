package com.omniarena.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ColorUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ColorUtil() {}

    public static Component parse(String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) return Component.empty();
        // If string contains legacy formatting codes (§), parse as legacy
        if (miniMessage.contains("§")) {
            return parseLegacy(miniMessage);
        }
        return MM.deserialize(translateAmpersandsToMiniMessage(miniMessage));
    }

    private static String translateAmpersandsToMiniMessage(String text) {
        if (text == null || !text.contains("&")) {
            return text;
        }
        String result = text;
        String[] codes = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f", "k", "l", "m", "n", "o", "r"};
        String[] tags = {"<black>", "<dark_blue>", "<dark_green>", "<dark_aqua>", "<dark_red>", "<dark_purple>", "<gold>", "<gray>", "<dark_gray>", "<blue>", "<green>", "<aqua>", "<red>", "<light_purple>", "<yellow>", "<white>", "<obfuscated>", "<bold>", "<strikethrough>", "<underlined>", "<italic>", "<reset>"};
        for (int i = 0; i < codes.length; i++) {
            result = result.replace("&" + codes[i], tags[i]);
            result = result.replace("&" + codes[i].toUpperCase(), tags[i]);
        }
        return result;
    }

    public static String strip(String miniMessage) {
        return MM.stripTags(translateAmpersandsToMiniMessage(miniMessage));
    }

    public static String toLegacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public static Component parseLegacy(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy);
    }
}
