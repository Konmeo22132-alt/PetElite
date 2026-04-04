package com.petplugin.battle;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Captures and restores a player's entire inventory state.
 */
public class InventorySnapshot {

    private final ItemStack[] contents;
    private final ItemStack[] armorContents;
    private final ItemStack offHand;

    public InventorySnapshot(Player player) {
        this.contents = cloneArray(player.getInventory().getContents());
        this.armorContents = cloneArray(player.getInventory().getArmorContents());
        this.offHand = player.getInventory().getItemInOffHand().clone();
    }

    public void restore(Player player) {
        player.getInventory().clear();
        player.getInventory().setContents(cloneArray(contents));
        player.getInventory().setArmorContents(cloneArray(armorContents));
        player.getInventory().setItemInOffHand(offHand != null ? offHand.clone() : null);
        player.updateInventory();
    }

    private ItemStack[] cloneArray(ItemStack[] arr) {
        if (arr == null) return new ItemStack[0];
        ItemStack[] copy = new ItemStack[arr.length];
        for (int i = 0; i < arr.length; i++) {
            copy[i] = arr[i] != null ? arr[i].clone() : null;
        }
        return copy;
    }
}
