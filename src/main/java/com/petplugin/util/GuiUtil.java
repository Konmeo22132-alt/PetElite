package com.petplugin.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GuiUtil {

    private GuiUtil() {}

    /**
     * Build an ItemStack with display name and lore using &-color codes.
     */
    public static ItemStack buildItem(Material material, String displayName, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(ChatUtil.color(displayName));

        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(ChatUtil.color(line));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Build a glowing item (has enchant glow, hides all flags).
     */
    public static ItemStack buildGlowItem(Material material, String displayName, String... loreLines) {
        ItemStack item = buildItem(material, displayName, loreLines);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Build a filler/spacer item (glass pane).
     */
    public static ItemStack filler() {
        return buildItem(Material.GRAY_STAINED_GLASS_PANE, "&8 ");
    }

    /**
     * Create a simple progress bar string.
     * @param current current value
     * @param max     maximum value
     * @param length  number of characters
     */
    public static String progressBar(int current, int max, int length) {
        if (max <= 0) return "&8[" + "&a".repeat(0) + "&7" + "▪".repeat(length) + "&8]";
        int filled = (int) Math.round((double) current / max * length);
        filled = Math.min(filled, length);
        return "&8[&a" + "▪".repeat(filled) + "&7" + "▪".repeat(length - filled) + "&8]";
    }
}
