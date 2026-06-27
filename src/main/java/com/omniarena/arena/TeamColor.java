package com.omniarena.arena;

import org.bukkit.Color;
import org.bukkit.DyeColor;

public enum TeamColor {
    RED(Color.RED, DyeColor.RED, "<red>"),
    BLUE(Color.BLUE, DyeColor.BLUE, "<blue>"),
    GREEN(Color.GREEN, DyeColor.GREEN, "<green>"),
    YELLOW(Color.YELLOW, DyeColor.YELLOW, "<yellow>"),
    PURPLE(Color.PURPLE, DyeColor.PURPLE, "<dark_purple>"),
    ORANGE(Color.ORANGE, DyeColor.ORANGE, "<gold>"),
    WHITE(Color.WHITE, DyeColor.WHITE, "<white>"),
    AQUA(Color.AQUA, DyeColor.CYAN, "<aqua>");

    private final Color armorColor;
    private final DyeColor dyeColor;
    private final String miniMessageTag;

    TeamColor(Color armorColor, DyeColor dyeColor, String miniMessageTag) {
        this.armorColor = armorColor;
        this.dyeColor = dyeColor;
        this.miniMessageTag = miniMessageTag;
    }

    public Color getArmorColor() { return armorColor; }
    public DyeColor getDyeColor() { return dyeColor; }
    public String getMiniMessageTag() { return miniMessageTag; }

    public static TeamColor fromString(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return RED;
        }
    }
}
