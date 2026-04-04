package com.petplugin.battle;

import com.petplugin.PetPlugin;
import com.petplugin.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages per-turn timeouts.
 * If the active player doesn't act within 30 seconds, they forfeit.
 */
public class TurnManager {

    private final PetPlugin plugin;
    private final Map<UUID, BukkitRunnable> timers = new HashMap<>();

    public TurnManager(PetPlugin plugin) {
        this.plugin = plugin;
    }

    /** Start (or restart) the turn timer for the current active player. */
    public void startTurn(BattleSession session, Player activePlayer) {
        cancelTimer(activePlayer.getUniqueId());

        final int timeoutSeconds = plugin.getConfig().getInt("battle.turn-timeout-seconds", 30);
        final int[] remaining = {timeoutSeconds};

        BukkitRunnable timer = new BukkitRunnable() {
            @Override
            public void run() {
                if (!session.isActive()) { cancel(); return; }
                remaining[0]--;
                if (remaining[0] <= 0) {
                    cancel();
                    activePlayer.sendMessage(ChatUtil.color("&cHết giờ! Bạn tự động thua."));
                    session.forfeit(activePlayer.getUniqueId());
                } else if (remaining[0] <= 5) {
                    Component actionBar = ChatUtil.color("&c⚠ Còn &f" + remaining[0] + "s &cđể chọn skill!");
                    activePlayer.sendActionBar(actionBar);
                }
            }
        };

        timers.put(activePlayer.getUniqueId(), timer);
        timer.runTaskTimer(plugin, 20L, 20L);
    }

    public void cancelTimer(UUID uuid) {
        BukkitRunnable existing = timers.remove(uuid);
        if (existing != null) {
            try { existing.cancel(); } catch (Exception ignored) {}
        }
    }

    public void cancelAll(BattleSession session) {
        cancelTimer(session.getPlayerA().getUniqueId());
        cancelTimer(session.getPlayerB().getUniqueId());
    }
}
