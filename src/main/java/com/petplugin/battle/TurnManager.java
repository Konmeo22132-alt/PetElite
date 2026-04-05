package com.petplugin.battle;

import com.petplugin.PetPlugin;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.FoliaUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-turn timeouts.
 * If the active player doesn't act within 30 seconds, they forfeit.
 */
public class TurnManager {

    private final PetPlugin plugin;
    // Map of UUID -> the scheduled task object (ScheduledTask or BukkitTask)
    private final Map<UUID, Object> timers = new HashMap<>();

    public TurnManager(PetPlugin plugin) {
        this.plugin = plugin;
    }

    /** Start (or restart) the turn timer for the current active player. */
    public void startTurn(BattleSession session, Player activePlayer) {
        cancelTimer(activePlayer.getUniqueId());

        final int timeoutSeconds = plugin.getConfig().getInt("battle.turn-timeout-seconds", 30);
        final int[] remaining = {timeoutSeconds};

        Runnable timerTask = new Runnable() {
            @Override
            public void run() {
                if (!session.isActive()) { cancelTimer(activePlayer.getUniqueId()); return; }
                remaining[0]--;
                if (remaining[0] <= 0) {
                    cancelTimer(activePlayer.getUniqueId());
                    if (activePlayer.isOnline()) {
                        activePlayer.sendMessage(ChatUtil.color("&cHết giờ! Bạn tự động thua."));
                    }
                    session.forfeit(activePlayer.getUniqueId());
                } else if (remaining[0] <= 5 && activePlayer.isOnline()) {
                    Component actionBar = ChatUtil.color("&c⚠ Còn &f" + remaining[0] + "s &cđể chọn skill!");
                    activePlayer.sendActionBar(actionBar);
                }
            }
        };

        Object task;
        if (FoliaUtil.IS_FOLIA) {
            task = activePlayer.getScheduler().runAtFixedRate(plugin, t -> timerTask.run(), null, 20L, 20L);
        } else {
            task = Bukkit.getScheduler().runTaskTimer(plugin, timerTask, 20L, 20L);
        }
        
        timers.put(activePlayer.getUniqueId(), task);
    }

    public void cancelTimer(UUID uuid) {
        Object existing = timers.remove(uuid);
        if (existing != null) {
            try { 
                if (FoliaUtil.IS_FOLIA) {
                    ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) existing).cancel();
                } else {
                    ((org.bukkit.scheduler.BukkitTask) existing).cancel();
                }
            } catch (Exception ignored) {}
        }
    }

    public void cancelAll(BattleSession session) {
        cancelTimer(session.getPlayerA().getUniqueId());
        cancelTimer(session.getPlayerB().getUniqueId());
    }
}
