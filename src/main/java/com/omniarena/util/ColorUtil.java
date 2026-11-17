package com.omniarena.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ColorUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final String[] CODES = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f", "k", "l", "m", "n", "o", "r"};
    private static final String[] TAGS = {"<black>", "<dark_blue>", "<dark_green>", "<dark_aqua>", "<dark_red>", "<dark_purple>", "<gold>", "<gray>", "<dark_gray>", "<blue>", "<green>", "<aqua>", "<red>", "<light_purple>", "<yellow>", "<white>", "<obfuscated>", "<bold>", "<strikethrough>", "<underlined>", "<italic>", "<reset>"};

    private ColorUtil() {}

    /** Parsed Components are immutable, so repeated static texts (scoreboard/actionbar lines, messages) are cached. Bounded. */
    private static final java.util.concurrent.ConcurrentHashMap<String, Component> PARSE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int PARSE_CACHE_MAX = 1024;

    public static Component parse(String miniMessage) {
        if (miniMessage == null || miniMessage.isBlank()) return Component.empty();
        Component cached = PARSE_CACHE.get(miniMessage);
        if (cached != null) return cached;
        Component result;
        // If string contains legacy formatting codes (§), parse as legacy
        if (miniMessage.contains("§")) {
            result = parseLegacy(miniMessage);
        } else {
            result = MM.deserialize(translateAmpersandsToMiniMessage(miniMessage));
        }
        if (miniMessage.length() <= 256) {
            if (PARSE_CACHE.size() >= PARSE_CACHE_MAX) PARSE_CACHE.clear();
            PARSE_CACHE.put(miniMessage, result);
        }
        return result;
    }

    private static String translateAmpersandsToMiniMessage(String text) {
        if (text == null || !text.contains("&")) {
            return text;
        }
        String result = text;
        for (int i = 0; i < CODES.length; i++) {
            result = result.replace("&" + CODES[i], TAGS[i]);
            result = result.replace("&" + CODES[i].toUpperCase(java.util.Locale.ROOT), TAGS[i]);
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
