package com.petplugin.gui;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.quest.DailyQuest;
import com.petplugin.quest.Quest;
import com.petplugin.quest.WeeklyQuest;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.GuiUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

/**
 * Shows daily and weekly quest progress for the player's active pet.
 *
 * Layout (54 slots):
 *   Row 0: Daily quest header
 *   Rows 1-2: Daily quests (3 quests, slots 10,11,12)
 *   Row 3: Weekly header
 *   Rows 4-5: Weekly quests (5 quests, slots 28-32)
 */
public class QuestGUI implements Listener {

    private static final String TITLE = "&e📋 Nhiệm Vụ Pet";
    private final PetPlugin plugin;

    public QuestGUI(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, PetData pet) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatUtil.color(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, GuiUtil.filler());

        // Header: daily
        inv.setItem(4, GuiUtil.buildGlowItem(Material.SUNFLOWER,
                "&e☀ Nhiệm Vụ Hàng Ngày",
                "&7Reset lúc 00:00 mỗi ngày"));

        // Header: weekly
        inv.setItem(40, GuiUtil.buildGlowItem(Material.CLOCK,
                "&b📅 Nhiệm Vụ Hàng Tuần",
                "&7Reset thứ Hai mỗi tuần"));

        // Daily quests at slots 10, 12, 14
        int[] dailySlots = {10, 12, 14};
        for (int i = 0; i < DailyQuest.ALL.length; i++) {
            Quest q = DailyQuest.ALL[i];
            inv.setItem(dailySlots[i], buildQuestItem(q, pet));
        }

        // Weekly quests at slots 28,30,32,34,36
        int[] weeklySlots = {28, 30, 32, 34, 36};
        for (int i = 0; i < WeeklyQuest.ALL.length; i++) {
            Quest q = WeeklyQuest.ALL[i];
            inv.setItem(weeklySlots[i], buildQuestItem(q, pet));
        }

        player.openInventory(inv);
    }

    private org.bukkit.inventory.ItemStack buildQuestItem(Quest quest, PetData pet) {
        int progress = pet.getQuestProgress(quest.getId());
        int target   = quest.getTarget();
        boolean done = progress >= target;

        String bar = GuiUtil.progressBar(Math.min(progress, target), target, 10);
        Material material = done ? Material.LIME_DYE : Material.GRAY_DYE;
        String nameColor  = done ? "&a" : "&e";

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(bar + " &f" + Math.min(progress, target) + "/" + target);
        lore.add("");
        lore.add("&7Thưởng:");
        lore.add("&e  EXP: +" + quest.getExpReward());
        if (quest.getAtkPointReward()  > 0) lore.add("&c  ATK pt: +" + quest.getAtkPointReward());
        if (quest.getDefPointReward()  > 0) lore.add("&a  DEF pt: +" + quest.getDefPointReward());
        if (quest.getHealPointReward() > 0) lore.add("&b  HEAL pt: +" + quest.getHealPointReward());
        lore.add("");
        lore.add(done ? "&aHoàn thành!" : "&7Đang tiến hành...");

        return GuiUtil.buildItem(material, nameColor + quest.getDescription(),
                lore.toArray(new String[0]));
    }

    // ---- Task 4: Cancel ALL inventory interactions in Quest GUI ----

    private boolean isQuestGUI(net.kyori.adventure.text.Component title) {
        return title.equals(ChatUtil.color(TITLE));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isQuestGUI(event.getView().title())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isQuestGUI(event.getView().title())) return;
        event.setCancelled(true);
    }
}
