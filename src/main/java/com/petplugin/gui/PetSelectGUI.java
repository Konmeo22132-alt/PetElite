package com.petplugin.gui;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.data.PlayerData;
import com.petplugin.pet.PetType;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.GuiUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * GUI shown to new players to choose their starter pet.
 * Slot layout: [Turtle=2] [Wolf=4] [Cat=6] with filler around them.
 */
public class PetSelectGUI implements Listener {

    private static final String TITLE = "§6Chọn Pet Starter";
    private final PetPlugin plugin;

    public PetSelectGUI(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, ChatUtil.color("&6✦ Chọn Pet Starter ✦"));

        // Fillers
        for (int i = 0; i < 9; i++) inv.setItem(i, GuiUtil.filler());

        // Turtle - slot 2
        inv.setItem(2, GuiUtil.buildGlowItem(Material.TURTLE_EGG,
                "&a🐢 Rùa &7(Tank)",
                "&7Role: &aTank",
                "&7HP: &f100 &7| ATK: &f8 &7| DEF: &f20",
                "",
                "&7Growth per level:",
                "&7ATK: &c+0 &7| DEF: &a+2 &7| HEAL: &b+1",
                "",
                "&eClick để chọn!"));

        // Wolf - slot 4
        inv.setItem(4, GuiUtil.buildGlowItem(Material.BONE,
                "&cSói &7(DPS)",
                "&7Role: &cDPS",
                "&7HP: &f80 &7| ATK: &f20 &7| DEF: &f10",
                "",
                "&7Growth per level:",
                "&7ATK: &c+2 &7| DEF: &a+1 &7| HEAL: &b+0",
                "",
                "&eClick để chọn!"));

        // Cat - slot 6
        inv.setItem(6, GuiUtil.buildGlowItem(Material.STRING,
                "&dMèo &7(Support)",
                "&7Role: &dSupport",
                "&7HP: &f80 &7| ATK: &f12 &7| DEF: &f8",
                "",
                "&7Growth per level:",
                "&7ATK: &c+1 &7| DEF: &a+0 &7| HEAL: &b+2",
                "",
                "&eClick để chọn!"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(ChatUtil.color("&6✦ Chọn Pet Starter ✦"))) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();

        PetType chosen = switch (slot) {
            case 2 -> PetType.TURTLE;
            case 4 -> PetType.WOLF;
            case 6 -> PetType.CAT;
            default -> null;
        };

        if (chosen == null) return;
        player.closeInventory();

        // Create pet data
        PetData petData = new PetData(player.getUniqueId(), chosen, chosen.name().charAt(0)
                + chosen.name().substring(1).toLowerCase());
        plugin.getDataManager().savePet(petData);

        // Set as active pet
        PlayerData playerData = plugin.getDataManager().loadPlayer(player.getUniqueId());
        playerData.setActivePetId(petData.getId());
        plugin.getDataManager().savePlayer(playerData);

        // Summon
        plugin.getPetManager().summon(player);

        player.sendMessage(ChatUtil.color(
                "&a✦ Bạn đã chọn pet &f" + chosen.getDisplayName() + "&a! Sử dụng &f/pet recall &ađể cất."));
    }
}
