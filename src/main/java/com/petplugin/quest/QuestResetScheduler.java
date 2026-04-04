package com.petplugin.quest;

import com.petplugin.PetPlugin;
import com.petplugin.data.DataManager;
import com.petplugin.data.PetData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Runs every 60 seconds. Resets daily/weekly quest progress when appropriate.
 */
public class QuestResetScheduler extends BukkitRunnable {

    private final PetPlugin plugin;

    public QuestResetScheduler(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        runTaskTimer(plugin, 0L, 20L * 60); // every 60 seconds
    }

    @Override
    public void run() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        long nowMillis = System.currentTimeMillis();

        DataManager dm = plugin.getDataManager();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PetData pet = dm.getActivePet(player.getUniqueId());
            if (pet == null) continue;

            // Daily reset
            ZonedDateTime lastDailyTime = Instant.ofEpochMilli(pet.getLastDailyReset())
                    .atZone(ZoneId.systemDefault());
            if (!lastDailyTime.toLocalDate().equals(now.toLocalDate())) {
                resetDaily(pet);
                pet.setLastDailyReset(nowMillis);
                dm.savePet(pet);
            }

            // Weekly reset — Monday
            ZonedDateTime lastWeeklyTime = Instant.ofEpochMilli(pet.getLastWeeklyReset())
                    .atZone(ZoneId.systemDefault());
            boolean sameWeek = lastWeeklyTime.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .equals(now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            if (!sameWeek) {
                resetWeekly(pet);
                pet.setLastWeeklyReset(nowMillis);
                dm.savePet(pet);
            }
        }
    }

    private void resetDaily(PetData pet) {
        for (DailyQuest q : DailyQuest.ALL) {
            pet.resetQuestProgress(q.getId());
        }
    }

    private void resetWeekly(PetData pet) {
        for (WeeklyQuest q : WeeklyQuest.ALL) {
            pet.resetQuestProgress(q.getId());
        }
    }
}
