package com.petplugin.quest;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.util.ChatUtil;
import org.bukkit.entity.Player;

/**
 * Handles incrementing quest progress and granting rewards when quests complete.
 */
public class QuestTracker {

    private final PetPlugin plugin;

    public QuestTracker(PetPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Increment progress for a quest. Grants rewards if the quest is newly completed.
     *
     * @param player    the player whose pet progresses
     * @param questType the quest to increment
     * @param amount    amount to add
     */
    public void increment(Player player, QuestType questType, int amount) {
        PetData pet = plugin.getDataManager().getActivePet(player.getUniqueId());
        if (pet == null) return;

        String questId = questType.name();
        int prev = pet.getQuestProgress(questId);
        int target = questType.getTarget();

        if (prev >= target) return; // already completed

        pet.incrementQuestProgress(questId, amount);
        int current = pet.getQuestProgress(questId);

        // Check completion
        if (prev < target && current >= target) {
            grantReward(player, pet, questType);
        }

        plugin.getDataManager().savePet(pet);
    }

    private void grantReward(Player player, PetData pet, QuestType questType) {
        boolean leveled = pet.addExp(questType.getExpReward());
        pet.setAtkPoints(pet.getAtkPoints() + questType.getAtkPointReward());
        pet.setDefPoints(pet.getDefPoints() + questType.getDefPointReward());
        pet.setHealPoints(pet.getHealPoints() + questType.getHealPointReward());

        plugin.getDataManager().savePet(pet);

        player.sendMessage(ChatUtil.color(
                "&6[Pet] &eNhiệm vụ &f" + questType.getDescription() + " &ehoàn thành!"));
        player.sendMessage(ChatUtil.color(
                "&6[Pet] &aPhần thưởng: &f+" + questType.getExpReward() + " EXP"
                + (questType.getAtkPointReward() > 0 ? " &c+" + questType.getAtkPointReward() + " ATK" : "")
                + (questType.getDefPointReward() > 0 ? " &a+" + questType.getDefPointReward() + " DEF" : "")
                + (questType.getHealPointReward() > 0 ? " &b+" + questType.getHealPointReward() + " HEAL" : "")));

        if (leveled) {
            player.sendMessage(ChatUtil.color(
                    "&6[Pet] &dPet của bạn &f" + pet.getName()
                    + " &dlên cấp &e" + pet.getLevel() + "&d!"));
        }
    }
}
