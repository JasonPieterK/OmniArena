package com.omniarena.gui;

import org.bukkit.entity.Player;

/** STUB: replaced by the real implementation from the GUI package at merge time. */
public final class ArenaPickerMenu {
    public enum Mode { JOIN, SPECTATE }

    private ArenaPickerMenu() {}

    public static void open(Player player, Mode mode) {
        com.omniarena.util.MessageUtil.send(player, "<yellow>This menu is coming soon.");
    }
}
