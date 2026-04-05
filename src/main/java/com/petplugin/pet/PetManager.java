package com.petplugin.pet;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.util.FoliaUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of all active pet entities.
 * Runs a task every tick to call PetEntity.tick() for FloatPet lerp (on non-Folia).
 */
public class PetManager implements Runnable {

    private final PetPlugin plugin;
    // owner UUID -> active PetEntity
    private final Map<UUID, PetEntity> activePets = new ConcurrentHashMap<>();

    public PetManager(PetPlugin plugin) {
        this.plugin = plugin;
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
        // Use iterator so we can remove stale entries in the same pass.
        // FloatPet.tick() calls despawn() when owner goes offline, but does NOT
        // remove itself from this map — we clean those up here.
        java.util.Iterator<java.util.Map.Entry<UUID, PetEntity>> it =
                activePets.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<UUID, PetEntity> entry = it.next();
            PetEntity pet = entry.getValue();

            if (!pet.isSpawned()) {
                // Pet is despawned; only keep it if the owner is online
                // (they may recall/re-summon). Remove if owner is gone.
                Player owner = org.bukkit.Bukkit.getPlayer(entry.getKey());
                if (owner == null || !owner.isOnline()) {
                    it.remove();
                }
            } else {
                // Task 2: Global respawn loop - catch entities removed by Paper's Activation Range
                // or if their Folia entity scheduler stopped
                org.bukkit.entity.Entity internalEntity = null;
                if (pet instanceof FloatPet fp) internalEntity = fp.getTurtleEntity();
                if (pet instanceof GroundPet gp) internalEntity = gp.getEntity();

                if (internalEntity == null || !internalEntity.isValid() || internalEntity.isDead()) {
                    Player owner = org.bukkit.Bukkit.getPlayer(entry.getKey());
                    if (owner != null && owner.isOnline() && pet.getPetData().isVisible() && !pet.getPetData().isFainted()) {
                        // Reschedule spawn on owner's region to avoid Folia thread issues
                        if (FoliaUtil.IS_FOLIA) {
                            owner.getScheduler().run(plugin, task -> {
                                pet.despawn();
                                pet.spawn();
                            }, null);
                        } else {
                            pet.despawn();
                            pet.spawn();
                        }
                    } else if (owner == null || !owner.isOnline()) {
                        pet.despawn();
                        it.remove();
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    public void summon(Player player) {
        if (activePets.containsKey(player.getUniqueId())) return; // already out

        // Task 8: Entity cap check (skip if too many entities to prevent lag/crash)
        if (player.getLocation().getChunk().getEntities().length > 100) {
            player.sendMessage(net.kyori.adventure.text.Component.text("§cKhu vực này có quá nhiều thực thể! Không thể triệu hồi Pet."));
            return;
        }

        PetData petData = plugin.getDataManager().getActivePet(player.getUniqueId());
        if (petData == null) {
            player.sendMessage(net.kyori.adventure.text.Component.text("Bạn chưa có pet!"));
            return;
        }

        PetEntity pet = createPetEntity(petData, player);
        pet.spawn();
        activePets.put(player.getUniqueId(), pet);
    }

    /** Recall (despawn) the pet. */
    public void recall(Player player) {
        if (player == null) return;
        PetEntity pet = activePets.remove(player.getUniqueId());
        if (pet != null) pet.despawn();
    }

    /** Force-recall by UUID (works even without a Player object). */
    public void forceRecall(java.util.UUID uuid) {
        PetEntity pet = activePets.remove(uuid);
        if (pet != null) pet.despawn();
    }

    /** Toggle between summon and recall. */
    public void toggle(Player player) {
        if (activePets.containsKey(player.getUniqueId())) {
            recall(player);
        } else {
            summon(player);
        }
    }

    public PetEntity getActivePet(UUID ownerUuid) {
        return activePets.get(ownerUuid);
    }

    public boolean hasPet(UUID ownerUuid) {
        return activePets.containsKey(ownerUuid);
    }

    /** Despawn all pets — called on plugin disable. */
    public void despawnAll() {
        for (PetEntity pet : activePets.values()) {
            pet.despawn();
        }
        activePets.clear();
    }

    /** Check if an entity is an active pet entity. */
    public boolean isPetEntity(org.bukkit.entity.Entity entity) {
        for (PetEntity pet : activePets.values()) {
            if (pet instanceof GroundPet gp && gp.getEntity() != null
                    && gp.getEntity().getEntityId() == entity.getEntityId()) return true;
            if (pet instanceof FloatPet fp && fp.getTurtleEntity() != null
                    && fp.getTurtleEntity().getEntityId() == entity.getEntityId()) return true;
        }
        return false;
    }

    /** Get the owner UUID of a pet entity. */
    public UUID getOwnerOf(org.bukkit.entity.Entity entity) {
        for (Map.Entry<UUID, PetEntity> entry : activePets.entrySet()) {
            PetEntity pet = entry.getValue();
            if (pet instanceof GroundPet gp && gp.getEntity() != null
                    && gp.getEntity().getEntityId() == entity.getEntityId()) return entry.getKey();
            if (pet instanceof FloatPet fp && fp.getTurtleEntity() != null
                    && fp.getTurtleEntity().getEntityId() == entity.getEntityId()) return entry.getKey();
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Factory
    // ------------------------------------------------------------------ //

    private PetEntity createPetEntity(PetData data, Player owner) {
        return switch (data.getType().getLocomotion()) {
            case FLOAT  -> new FloatPet(data, owner);
            case GROUND -> new GroundPet(data, owner);
        };
    }
}
