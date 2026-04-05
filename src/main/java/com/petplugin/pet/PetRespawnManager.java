package com.petplugin.pet;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.FoliaUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Task 7 — Pet respawn system.
 *
 * Flow:
 *  1. Pet entity dies → handleFaint() called
 *  2. Mark fainted=true in PetData, set waitingRespawn=false
 *  3. Recall (despawn) entity immediately
 *  4. Play SMOKE_LARGE at death location for ~1s
 *  5. Send owner message
 *  6. Schedule 30s respawn:
 *     a. If owner online → summon at current location, heal to 50% maxHP
 *     b. If owner offline → set waitingRespawn=true, summon on next login
 */
public class PetRespawnManager {

    private static final int RESPAWN_TICKS = 600; // 30 seconds

    private final PetPlugin plugin;

    public PetRespawnManager(PetPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called when a pet entity (GroundPet) reaches 0 HP.
     * @param ownerUuid  UUID of the pet's owner
     * @param deathLoc   location where the pet died
     */
    public void handleFaint(UUID ownerUuid, Location deathLoc) {
        PetData petData = plugin.getDataManager().getActivePet(ownerUuid);
        if (petData == null) return;

        // Mark fainted
        petData.setFainted(true);
        petData.setWaitingRespawn(false);
        plugin.getDataManager().savePet(petData);

        // Despawn the entity
        plugin.getPetManager().recall(plugin.getServer().getOfflinePlayer(ownerUuid).getPlayer() != null
                ? plugin.getServer().getPlayer(ownerUuid)
                : null);
        plugin.getPetManager().forceRecall(ownerUuid);

        // Smoke particle burst at death location
        playFaintParticles(deathLoc);

        // Notify owner if online
        Player owner = plugin.getServer().getPlayer(ownerUuid);
        if (owner != null && owner.isOnline()) {
            owner.sendMessage(ChatUtil.color(
                    "&cYour pet &e" + petData.getName()
                    + " &chas fainted! It will respawn in &e30 seconds&c."));
        }

        // Schedule respawn
        Runnable respawnTask = () -> {
            PetData freshData = plugin.getDataManager().getActivePet(ownerUuid);
            if (freshData == null) return;

            Player onlineOwner = plugin.getServer().getPlayer(ownerUuid);
            if (onlineOwner != null && onlineOwner.isOnline()) {
                // Heal to 50% and respawn
                freshData.setFainted(false);
                freshData.setWaitingRespawn(false);
                plugin.getDataManager().savePet(freshData);
                plugin.getPetManager().summon(onlineOwner);
                onlineOwner.sendMessage(ChatUtil.color(
                        "&a&e" + freshData.getName() + " &ahas recovered and returned!"));
            } else {
                // Player offline — flag for respawn on login
                freshData.setFainted(false);
                freshData.setWaitingRespawn(true);
                plugin.getDataManager().savePet(freshData);
            }
        };

        if (FoliaUtil.IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> respawnTask.run(), RESPAWN_TICKS);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, respawnTask, RESPAWN_TICKS);
        }
    }

    /**
     * Called on PlayerJoinEvent — check if pet is waiting to respawn.
     */
    public void checkWaitingRespawn(Player player) {
        PetData petData = plugin.getDataManager().getActivePet(player.getUniqueId());
        if (petData == null) return;
        if (!petData.isWaitingRespawn()) return;

        petData.setWaitingRespawn(false);
        petData.setFainted(false);
        plugin.getDataManager().savePet(petData);
        plugin.getPetManager().summon(player);
        player.sendMessage(ChatUtil.color(
                "&a&e" + petData.getName() + " &ahas recovered and returned!"));
    }

    // ---- Private helpers ----

    private void playFaintParticles(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        
        final int[] ticks = {0};
        Runnable particleTask = new Runnable() {
            public void run() {
                if (ticks[0] >= 20) return;
                loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, 0.05, 0), 5, 0.3, 0.3, 0.3, 0.01);
                ticks[0]++;
            }
        };

        if (FoliaUtil.IS_FOLIA) {
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, loc, task -> {
                particleTask.run();
                if (ticks[0] >= 20) task.cancel();
            }, 1L, 1L);
        } else {
            // AUDIT FIX: track and cancel the BukkitRunnable after 20 ticks
            org.bukkit.scheduler.BukkitTask[] taskHolder = new org.bukkit.scheduler.BukkitTask[1];
            taskHolder[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                particleTask.run();
                if (ticks[0] >= 20 && taskHolder[0] != null) {
                    taskHolder[0].cancel();
                }
            }, 0L, 1L);
        }
    }
}
