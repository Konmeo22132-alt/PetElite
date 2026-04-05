package com.petplugin.battle;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;
import com.petplugin.PetPlugin;

/**
 * Captures and restores a player's entire inventory state.
 */
public class InventorySnapshot {

    private final ItemStack[] contents;
    private final ItemStack[] armorContents;
    private final ItemStack offHand;
    private final UUID playerUuid;

    public InventorySnapshot(Player player) {
        this.playerUuid = player.getUniqueId();
        this.contents = cloneArray(player.getInventory().getContents());
        this.armorContents = cloneArray(player.getInventory().getArmorContents());
        this.offHand = player.getInventory().getItemInOffHand().clone();
        saveToDisk();
    }

    public void restore(Player player) {
        player.getInventory().clear();
        player.getInventory().setContents(cloneArray(contents));
        player.getInventory().setArmorContents(cloneArray(armorContents));
        player.getInventory().setItemInOffHand(offHand != null ? offHand.clone() : null);
        player.updateInventory();
        deleteFromDisk();
    }

    private void saveToDisk() {
        File dir = new File(JavaPlugin.getPlugin(PetPlugin.class).getDataFolder(), "snapshots");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, playerUuid.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("contents", contents);
        config.set("armor", armorContents);
        config.set("offHand", offHand);
        // AUDIT FIX: async save + log errors
        PetPlugin plugin = JavaPlugin.getPlugin(PetPlugin.class);
        Runnable saveTask = () -> {
            try { config.save(file); }
            catch (IOException e) {
                plugin.getLogger().severe("[PetElite] Failed to save inventory snapshot for " + playerUuid + ": " + e.getMessage());
            }
        };
        if (com.petplugin.util.FoliaUtil.IS_FOLIA) {
            org.bukkit.Bukkit.getAsyncScheduler().runNow(plugin, task -> saveTask.run());
        } else {
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, saveTask);
        }
    }

    private void deleteFromDisk() {
        File file = new File(JavaPlugin.getPlugin(PetPlugin.class).getDataFolder(), "snapshots/" + playerUuid.toString() + ".yml");
        if (file.exists()) file.delete();
    }

    public static void restoreIfPresent(Player player) {
        File file = new File(JavaPlugin.getPlugin(PetPlugin.class).getDataFolder(), "snapshots/" + player.getUniqueId() + ".yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        List<?> cList = config.getList("contents");
        List<?> aList = config.getList("armor");
        ItemStack oItem = config.getItemStack("offHand");

        player.getInventory().clear();
        if (cList != null) player.getInventory().setContents(cList.toArray(new ItemStack[0]));
        if (aList != null) player.getInventory().setArmorContents(aList.toArray(new ItemStack[0]));
        if (oItem != null) player.getInventory().setItemInOffHand(oItem);
        player.updateInventory();
        
        file.delete();
        player.sendMessage("§a[PetElite] Hành trang của bạn đã được phục hồi sau sự cố.");
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
