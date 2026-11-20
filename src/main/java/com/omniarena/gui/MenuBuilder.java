package com.omniarena.gui;

import com.omniarena.util.ColorUtil;
import com.omniarena.util.ItemUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Builds an {@link OmniMenu}: centered button rows, gray glass on every empty slot, and the standard footer
 * (Back bottom-left, Help bottom-center, Close bottom-right). All text goes through {@link ItemUtil} /
 * the platform adapter.
 */
public final class MenuBuilder {

    /** An item plus its click handler (handler may be null for display-only items). */
    public record Button(ItemStack item, OmniMenu.Handler handler) {}

    private final OmniMenu menu;
    private final int rows;
    private final ItemStack[] items;

    public MenuBuilder(String miniMessageTitle, int rows) {
        this.rows = Math.max(1, Math.min(6, rows));
        this.menu = new OmniMenu(this.rows, ColorUtil.parse(miniMessageTitle));
        this.items = new ItemStack[this.rows * MenuLayout.WIDTH];
    }

    public int rows() { return rows; }

    public MenuBuilder set(int slot, ItemStack item, OmniMenu.Handler handler) {
        if (slot < 0 || slot >= items.length) return this;
        items[slot] = item;
        menu.bind(slot, handler);
        return this;
    }

    public MenuBuilder set(int slot, Button b) {
        return set(slot, b.item(), b.handler());
    }

    /** Places the buttons centered in the given row. */
    public MenuBuilder row(int row, List<Button> buttons) {
        int[] slots = MenuLayout.centeredSlots(row, buttons.size());
        for (int i = 0; i < slots.length; i++) set(slots[i], buttons.get(i));
        return this;
    }

    /** Places items in a flowing grid from row 0 (last partial row centered). */
    public MenuBuilder flow(List<Button> buttons) {
        int[] slots = MenuLayout.flowSlots(buttons.size(), rows);
        for (int i = 0; i < slots.length; i++) set(slots[i], buttons.get(i));
        return this;
    }

    /**
     * Standard footer on the last row. {@code back} may be null (no Back button, glass instead).
     * {@code helpLines} are MiniMessage lines shown on the Help book.
     */
    public MenuBuilder footer(OmniMenu.Handler back, String... helpLines) {
        if (back != null) {
            set(MenuLayout.footerBack(rows), item(Material.ARROW, "<yellow><bold>Back</bold>", "<gray>Click to go back."), back);
        }
        set(MenuLayout.footerHelp(rows), item(Material.BOOK, "<aqua><bold>Help</bold>", helpLines), null);
        set(MenuLayout.footerClose(rows), item(Material.BARRIER, "<red><bold>Close</bold>", "<gray>Click to close this menu."),
                (p, t) -> p.closeInventory());
        return this;
    }

    public OmniMenu build() {
        ItemStack glass = ItemUtil.buildFillerItem(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < items.length; i++) {
            menu.getInventory().setItem(i, items[i] != null ? items[i] : glass);
        }
        return menu;
    }

    public void open(Player player) {
        build().open(player);
    }

    // ── item helpers ────────────────────────────────────────────────────────

    public static ItemStack item(Material material, String name, String... lore) {
        return ItemUtil.buildGuiItem(material, name, lore);
    }

    /** LIME_CONCRETE when on, RED_CONCRETE when off. The state is appended to the lore. */
    public static ItemStack toggle(boolean on, String name, String... lore) {
        String[] full = new String[lore.length + 2];
        System.arraycopy(lore, 0, full, 0, lore.length);
        full[lore.length] = on ? "<green>Currently: ON" : "<red>Currently: OFF";
        full[lore.length + 1] = "<yellow>Click <gray>to switch " + (on ? "<red>off" : "<green>on");
        return item(on ? Material.LIME_CONCRETE : Material.RED_CONCRETE, name, full);
    }

    public static Button button(ItemStack item, OmniMenu.Handler handler) {
        return new Button(item, handler);
    }

    /** Escapes MiniMessage tags in untrusted text (names, descriptions). */
    public static String esc(String s) {
        return s == null ? "" : MiniMessage.miniMessage().escapeTags(s);
    }
}
