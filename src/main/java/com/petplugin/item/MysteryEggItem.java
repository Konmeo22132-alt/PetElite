package com.petplugin.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Factory for the Mystery Egg custom item (Task 4).
 *
 * Uses a PersistentDataContainer tag "petelite:mystery_egg" = 1
 * so it is never confused with vanilla DRAGON_EGG.
 * Custom model data = 1001 for resource pack compatibility.
 */
public final class MysteryEggItem {

    public static final String PDC_KEY = "mystery_egg";

    private MysteryEggItem() {}

    public static NamespacedKey key(Plugin plugin) {
        return new NamespacedKey(plugin, PDC_KEY);
    }

    /**
     * Create one Mystery Egg ItemStack tagged with PDC.
     */
    public static ItemStack create(Plugin plugin) {
        ItemStack item = new ItemStack(Material.DRAGON_EGG, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text(
                    net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE + "🥚 Mystery Egg"));
            meta.lore(List.of(
                    net.kyori.adventure.text.Component.text(
                            org.bukkit.ChatColor.GRAY + "Hatch to receive a random pet!"),
                    net.kyori.adventure.text.Component.text(
                            org.bukkit.ChatColor.GRAY + "Right-click to use."),
                    net.kyori.adventure.text.Component.text(""),
                    net.kyori.adventure.text.Component.text(
                            org.bukkit.ChatColor.GOLD + "✦ PetElite Exclusive")));
            meta.setCustomModelData(1001);
            // Mark with PDC so we can detect it on right-click
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.INTEGER, 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Returns true if the given item is a PetElite Mystery Egg.
     */
    public static boolean isMysteryEgg(Plugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.DRAGON_EGG) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key(plugin), PersistentDataType.INTEGER);
    }
}
