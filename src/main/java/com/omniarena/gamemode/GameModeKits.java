package com.omniarena.gamemode;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * Builds and equips the loadout for every {@link GameModeType}.
 * Kept separate from the enum so the item-building stays readable.
 */
public final class GameModeKits {

    private GameModeKits() {}

    public static void equip(Player p, GameModeType mode) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(null); inv.setChestplate(null); inv.setLeggings(null); inv.setBoots(null);

        switch (mode) {
            case DEATHMATCH -> {
                diamondArmor(inv, 1);
                inv.setItem(0, ench(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 1));
                inv.setItem(1, ench(new ItemStack(Material.BOW), Enchantment.POWER, 1));
                inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 2));
                inv.setItem(8, new ItemStack(Material.ARROW, 16));
                inv.setItem(7, new ItemStack(Material.COOKED_BEEF, 16));
            }
            case TEAM_DEATHMATCH -> {
                ironArmor(inv, 2);
                inv.setItem(0, ench(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 1));
                inv.setItem(1, ench(new ItemStack(Material.BOW), Enchantment.POWER, 2));
                inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 2));
                inv.setItem(8, new ItemStack(Material.ARROW, 32));
                inv.setItem(7, new ItemStack(Material.COOKED_BEEF, 16));
            }
            case LAST_ONE_STANDING -> {
                diamondArmor(inv, 2);
                inv.setItem(0, ench(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 2));
                inv.setItem(1, ench(new ItemStack(Material.BOW), Enchantment.POWER, 2));
                inv.setItem(8, new ItemStack(Material.ARROW, 16));
                inv.setItem(7, new ItemStack(Material.GOLDEN_APPLE, 3));
                inv.setItem(6, new ItemStack(Material.COOKED_BEEF, 16));
            }
            case SUMO -> {
                ItemStack stick = ench(new ItemStack(Material.STICK), Enchantment.KNOCKBACK, 2);
                inv.setItem(0, stick);
            }
            case CLASSIC_PVP -> {
                ironArmor(inv, 0);
                inv.setItem(0, new ItemStack(Material.IRON_SWORD));
                inv.setItem(1, new ItemStack(Material.BOW));
                inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 4));
                inv.setItem(8, new ItemStack(Material.ARROW, 32));
                inv.setItem(7, new ItemStack(Material.COOKED_BEEF, 32));
            }
            case CRYSTAL_PVP -> {
                netheriteArmor(inv, 2);
                inv.setItem(0, ench(new ItemStack(Material.NETHERITE_SWORD), Enchantment.SHARPNESS, 2));
                inv.setItem(1, new ItemStack(Material.END_CRYSTAL, 16));
                inv.setItem(2, new ItemStack(Material.OBSIDIAN, 32));
                inv.setItem(3, new ItemStack(Material.RESPAWN_ANCHOR, 4));
                inv.setItem(4, new ItemStack(Material.GLOWSTONE, 16));
                inv.setItem(8, new ItemStack(Material.TOTEM_OF_UNDYING, 2));
                inv.setItem(7, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4));
                inv.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            }
            case MACE -> {
                ItemStack mace = ench(new ItemStack(Material.MACE), Enchantment.DENSITY, 3);
                ench(mace, Enchantment.WIND_BURST, 2);
                inv.setItem(0, mace);
                inv.setItem(1, new ItemStack(Material.WIND_CHARGE, 16));
                inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 4));
                ItemStack chest = ench(new ItemStack(Material.ELYTRA), Enchantment.UNBREAKING, 3);
                inv.setChestplate(chest);
                inv.setHelmet(ench(new ItemStack(Material.DIAMOND_HELMET), Enchantment.PROTECTION, 2));
                inv.setItem(3, new ItemStack(Material.FIREWORK_ROCKET, 32));
            }
            case SWORD_DUEL -> {
                netheriteArmor(inv, 4);
                inv.setItem(0, ench(new ItemStack(Material.NETHERITE_SWORD), Enchantment.SHARPNESS, 4));
                inv.setItem(7, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4));
                inv.setItem(8, new ItemStack(Material.GOLDEN_APPLE, 8));
            }
            case AXE_SHIELD -> {
                netheriteArmor(inv, 3);
                inv.setItem(0, ench(new ItemStack(Material.NETHERITE_AXE), Enchantment.SHARPNESS, 3));
                inv.setItemInOffHand(new ItemStack(Material.SHIELD));
                inv.setItem(7, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3));
                inv.setItem(8, new ItemStack(Material.GOLDEN_APPLE, 8));
            }
            case UHC -> {
                diamondArmor(inv, 2);
                inv.setItem(0, ench(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 2));
                inv.setItem(1, ench(new ItemStack(Material.BOW), Enchantment.POWER, 2));
                inv.setItem(8, new ItemStack(Material.ARROW, 16));
                inv.setItem(7, new ItemStack(Material.GOLDEN_APPLE, 8));
                inv.setItem(6, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 2));
                inv.setItem(5, new ItemStack(Material.COOKED_BEEF, 16));
            }
            case POTION_PVP -> {
                diamondArmor(inv, 2);
                inv.setItem(0, ench(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 2));
                inv.setItem(2, splash(PotionType.STRONG_HEALING, 8));
                inv.setItem(3, splash(PotionType.STRONG_SWIFTNESS, 4));
                inv.setItem(4, splash(PotionType.FIRE_RESISTANCE, 2));
                inv.setItem(7, new ItemStack(Material.GOLDEN_APPLE, 4));
            }
            case NODEBUFF -> {
                diamondArmor(inv, 2);
                inv.setItem(0, ench(new ItemStack(Material.DIAMOND_SWORD), Enchantment.SHARPNESS, 2));
                for (int i = 0; i < 9; i++) inv.setItem(9 + i, splash(PotionType.STRONG_HEALING, 1));
                inv.setItem(18, splash(PotionType.STRONG_SWIFTNESS, 1));
                inv.setItem(19, splash(PotionType.STRONG_STRENGTH, 1));
                inv.setItem(7, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16));
                inv.setItem(8, new ItemStack(Material.GOLDEN_APPLE, 64));
            }
            case ARCHER -> {
                leatherArmor(inv, Color.LIME);
                ItemStack bow = ench(new ItemStack(Material.BOW), Enchantment.POWER, 3);
                ench(bow, Enchantment.INFINITY, 1);
                inv.setItem(0, bow);
                inv.setItem(1, new ItemStack(Material.IRON_SWORD));
                inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 4));
                inv.setItem(8, new ItemStack(Material.ARROW, 1));
                inv.setItem(7, new ItemStack(Material.COOKED_BEEF, 16));
            }
            case NETHERITE_OP -> {
                netheriteArmor(inv, 4);
                ItemStack sword = ench(new ItemStack(Material.NETHERITE_SWORD), Enchantment.SHARPNESS, 5);
                ench(sword, Enchantment.FIRE_ASPECT, 2);
                inv.setItem(0, sword);
                ItemStack bow = ench(new ItemStack(Material.BOW), Enchantment.POWER, 5);
                ench(bow, Enchantment.FLAME, 1);
                inv.setItem(1, bow);
                inv.setItem(2, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16));
                inv.setItem(8, new ItemStack(Material.ARROW, 64));
                inv.setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
            }
            case SPLEEF -> {
                ItemStack shovel = ench(new ItemStack(Material.DIAMOND_SHOVEL), Enchantment.EFFICIENCY, 5);
                inv.setItem(0, shovel);
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 1, false, false));
            }
            case GLADIATOR -> {
                inv.setItem(0, new ItemStack(Material.STONE_SWORD));
                inv.setItemInOffHand(new ItemStack(Material.SHIELD));
                inv.setItem(7, new ItemStack(Material.COOKED_BEEF, 8));
            }
        }
        p.updateInventory();
    }

    // ── Armor helpers ────────────────────────────────────────────────────────
    private static void diamondArmor(PlayerInventory inv, int prot) {
        inv.setHelmet(prot(new ItemStack(Material.DIAMOND_HELMET), prot));
        inv.setChestplate(prot(new ItemStack(Material.DIAMOND_CHESTPLATE), prot));
        inv.setLeggings(prot(new ItemStack(Material.DIAMOND_LEGGINGS), prot));
        inv.setBoots(prot(new ItemStack(Material.DIAMOND_BOOTS), prot));
    }
    private static void ironArmor(PlayerInventory inv, int prot) {
        inv.setHelmet(prot(new ItemStack(Material.IRON_HELMET), prot));
        inv.setChestplate(prot(new ItemStack(Material.IRON_CHESTPLATE), prot));
        inv.setLeggings(prot(new ItemStack(Material.IRON_LEGGINGS), prot));
        inv.setBoots(prot(new ItemStack(Material.IRON_BOOTS), prot));
    }
    private static void netheriteArmor(PlayerInventory inv, int prot) {
        inv.setHelmet(prot(new ItemStack(Material.NETHERITE_HELMET), prot));
        inv.setChestplate(prot(new ItemStack(Material.NETHERITE_CHESTPLATE), prot));
        inv.setLeggings(prot(new ItemStack(Material.NETHERITE_LEGGINGS), prot));
        inv.setBoots(prot(new ItemStack(Material.NETHERITE_BOOTS), prot));
    }
    private static void leatherArmor(PlayerInventory inv, Color color) {
        inv.setHelmet(dye(new ItemStack(Material.LEATHER_HELMET), color));
        inv.setChestplate(dye(new ItemStack(Material.LEATHER_CHESTPLATE), color));
        inv.setLeggings(dye(new ItemStack(Material.LEATHER_LEGGINGS), color));
        inv.setBoots(dye(new ItemStack(Material.LEATHER_BOOTS), color));
    }

    // ── Item helpers ─────────────────────────────────────────────────────────
    private static ItemStack prot(ItemStack item, int level) {
        if (level > 0) item.addUnsafeEnchantment(Enchantment.PROTECTION, level);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return item;
    }
    private static ItemStack ench(ItemStack item, Enchantment ench, int level) {
        item.addUnsafeEnchantment(ench, level);
        return item;
    }
    private static ItemStack dye(ItemStack item, Color color) {
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return prot(item, 1);
    }
    private static ItemStack splash(PotionType type, int amount) {
        ItemStack pot = new ItemStack(Material.SPLASH_POTION, amount);
        if (pot.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(type);
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            pot.setItemMeta(meta);
        }
        return pot;
    }
}
