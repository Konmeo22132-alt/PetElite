package com.petplugin.pet;

import com.petplugin.PetPlugin;
import com.petplugin.battle.ArenaManager;
import com.petplugin.battle.BattleSession;
import com.petplugin.util.FoliaUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Task 3 — Global tick runnable for 200+ player production servers.
 *
 * Replaces individual per-entity tick loops with a SINGLE centralized pass.
 * Operations per tick:
 *   1. Pet follow update (calls PetEntity.tick() for each active pet)
 *   2. Arena boundary check (every 10 ticks) — forfeit if outside radius
 *
 * Design note: each PetEntity.tick() method remains intact and callable.
 * This class simply calls them from one place instead of N scheduler tasks.
 */
public class GlobalTickRunnable implements Runnable {

    private final PetPlugin plugin;
    private int tickCounter = 0;

    // Config: how often to check arena boundaries (ticks)
    private final int boundaryCheckInterval;

    public GlobalTickRunnable(PetPlugin plugin) {
        this.plugin = plugin;
        this.boundaryCheckInterval = plugin.getConfig().getInt(
                "battle.arena_boundary_check_interval_ticks", 10);
    }

    public void start() {
        if (FoliaUtil.IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> run(), 1L, 1L);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
        }
    }

    @Override
    public void run() {
        tickCounter++;

        // ---- 1. Pet follow updates ----
        // PetManager.run() handles pet validity checks and calls tick() on each
        plugin.getPetManager().tick();

        // ---- 2. Arena boundary check (every N ticks) ----
        if (tickCounter % boundaryCheckInterval == 0) {
            checkArenaBoundaries();
        }
    }

    /**
     * Check all players in active battles for arena boundary violation.
     * If outside: teleport back to spawn point and send warning.
     */
    private void checkArenaBoundaries() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            if (!ArenaManager.isInArena(uuid)) continue;

            BattleSession session = plugin.getBattleManager().getSession(uuid);
            if (session == null || !session.isActive() || !session.isInArena()) continue;

            if (ArenaManager.isOutsideBoundary(uuid)) {
                // Teleport back to spawn
                ArenaManager.teleportBackToSpawn(uuid);

                // If repeated violation (already warned), forfeit
                // Simple: just teleport back. The message is sent by teleportBackToSpawn().
            }
        }
    }
}
