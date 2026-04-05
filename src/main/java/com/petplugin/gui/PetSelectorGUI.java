package com.petplugin.gui;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.data.PlayerData;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.GuiUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.UUID;

/**
 * Sub-GUI that lists all pets owned by a player and lets them switch active pet.
 * Opened from PetMainGUI when petSlots > 1.
 */
public class PetSelectorGUI implements Listener {

    private static final String TITLE = "§b⇄ Chọn Pet";
    private final PetPlugin plugin;

    public PetSelectorGUI(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        PlayerData pd = plugin.getDataManager().loadPlayer(player.getUniqueId());
        if (pd == null) return;

        List<PetData> pets = plugin.getDataManager().loadPetsForPlayer(player.getUniqueId());
        int rows = Math.max(1, (int) Math.ceil(pets.size() / 9.0) + 1);
        rows = Math.min(rows, 6);
        Inventory inv = Bukkit.createInventory(null, rows * 9, ChatUtil.color(TITLE));
        for (int i = 0; i < rows * 9; i++) inv.setItem(i, GuiUtil.filler());

        UUID activePetId = pd.getActivePetId();
        for (int i = 0; i < pets.size() && i < (rows * 9 - 9); i++) {
            PetData pet = pets.get(i);
            boolean isActive = pet.getId().equals(activePetId);
            Material mat = petMaterial(pet);
            String nameColor = isActive ? "&a" : "&e";
            String activeTag = isActive ? " &a[ĐANG DÙNG]" : "";

            inv.setItem(i, GuiUtil.buildGlowItem(mat,
                    nameColor + pet.getName() + activeTag,
                    "&7Loại: &f" + pet.getType().getDisplayName(),
                    "&7Cấp: &e" + pet.getLevel(),
                    "&7HP: &f" + (pet.getType().getBaseHp() + pet.getLevel() * 2),
                    "&7ATK: &c" + (pet.getType().getBaseAtk() + pet.getLevel()),
                    "&7DEF: &a" + (pet.getType().getBaseDef() + pet.getLevel()),
                    "",
                    isActive ? "&aĐang được chọn." : "&eClick để chọn pet này!",
                    // Encode pet ID for retrieval
                    "&8PetId:" + pet.getId().toString()));
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(ChatUtil.color(TITLE))) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null || event.getCurrentItem().getType().isAir()) return;

        var meta = event.getCurrentItem().getItemMeta();
        if (meta == null || meta.lore() == null) return;

        // Extract pet ID from lore
        UUID petId = null;
        for (net.kyori.adventure.text.Component line : meta.lore()) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(line);
            if (plain.startsWith("PetId:")) {
                try { petId = UUID.fromString(plain.substring("PetId:".length()).trim()); }
                catch (Exception ignored) {}
            }
        }
        if (petId == null) return;

        UUID finalPetId = petId;
        player.closeInventory();

        // Recall current pet, switch, summon new one
        plugin.getPetManager().recall(player);

        PlayerData pd = plugin.getDataManager().loadPlayer(player.getUniqueId());
        if (pd == null) return;
        pd.setActivePetId(finalPetId);
        plugin.getDataManager().savePlayer(pd);

        // AUDIT FIX: Folia-safe scheduler call
        Runnable summonTask = () -> {
            plugin.getPetManager().summon(player);
            player.sendMessage(ChatUtil.color("&a✦ Đã chuyển pet!"));
        };
        if (com.petplugin.util.FoliaUtil.IS_FOLIA) {
            player.getScheduler().run(plugin, task -> summonTask.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, summonTask);
        }
    }

    private Material petMaterial(PetData pet) {
        return switch (pet.getType()) {
            case TURTLE -> Material.TURTLE_EGG;
            case WOLF   -> Material.BONE;
            case CAT    -> Material.STRING;
        };
    }
}
