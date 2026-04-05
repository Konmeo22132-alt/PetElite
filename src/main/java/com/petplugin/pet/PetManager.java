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
 *
 * TASK 3: PetManager no longer runs its own tick loop.
 * Instead, GlobalTickRunnable calls tick() once per server tick.
 * This reduces scheduler overhead from O(n) to O(1).
 */
public class PetManager {

    private final PetPlugin plugin;
    // owner UUID -> active PetEntity
    private final Map<UUID, PetEntity> activePets = new ConcurrentHashMap<>();

    // AUDIT FIX: reverse entity lookup for O(1) isPetEntity / getOwnerOf
    private final Map<Integer, UUID> entityIdToOwner = new ConcurrentHashMap<>();

    public PetManager(PetPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * TASK 3: Called by GlobalTickRunnable once per server tick.
     * Replaces the old per-PetManager scheduled loop.
     */
    public void tick() {
        java.util.Iterator<java.util.Map.Entry<UUID, PetEntity>> it =
                activePets.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<UUID, PetEntity> entry = it.next();
            PetEntity pet = entry.getValue();

            if (!pet.isSpawned()) {
                Player owner = org.bukkit.Bukkit.getPlayer(entry.getKey());
                if (owner == null || !owner.isOnline()) {
                    it.remove();
                    removeEntityLookup(pet);
                }
            } else {
                // Check if internal entity is still valid
                org.bukkit.entity.Entity internalEntity = getInternalEntity(pet);

                if (internalEntity == null || !internalEntity.isValid() || internalEntity.isDead()) {
                    Player owner = org.bukkit.Bukkit.getPlayer(entry.getKey());
                    if (owner != null && owner.isOnline() && pet.getPetData().isVisible() && !pet.getPetData().isFainted()) {
                        removeEntityLookup(pet);
                        if (FoliaUtil.IS_FOLIA) {
                            owner.getScheduler().run(plugin, task -> {
                                pet.despawn();
                                pet.spawn();
                                registerEntityLookup(pet, entry.getKey());
                            }, null);
                        } else {
                            pet.despawn();
                            pet.spawn();
                            registerEntityLookup(pet, entry.getKey());
                        }
                    } else if (owner == null || !owner.isOnline()) {
                        pet.despawn();
                        it.remove();
                        removeEntityLookup(pet);
                    }
                } else {
                    // Normal tick — call PetEntity.tick() for follow/animation
                    pet.tick();
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    //  Entity lookup helpers (AUDIT FIX: O(1) lookups)
    // ------------------------------------------------------------------ //

    private void registerEntityLookup(PetEntity pet, UUID ownerUuid) {
        org.bukkit.entity.Entity entity = getInternalEntity(pet);
        if (entity != null) {
            entityIdToOwner.put(entity.getEntityId(), ownerUuid);
        }
    }

    private void removeEntityLookup(PetEntity pet) {
        org.bukkit.entity.Entity entity = getInternalEntity(pet);
        if (entity != null) {
            entityIdToOwner.remove(entity.getEntityId());
        }
    }

    private org.bukkit.entity.Entity getInternalEntity(PetEntity pet) {
        if (pet instanceof FloatPet fp) return fp.getTurtleEntity();
        if (pet instanceof GroundPet gp) return gp.getEntity();
        return null;
    }

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    public void summon(Player player) {
        if (activePets.containsKey(player.getUniqueId())) return;

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
        registerEntityLookup(pet, player.getUniqueId());
    }

    public void recall(Player player) {
        if (player == null) return;
        PetEntity pet = activePets.remove(player.getUniqueId());
        if (pet != null) {
            removeEntityLookup(pet);
            pet.despawn();
        }
    }

    public void forceRecall(java.util.UUID uuid) {
        PetEntity pet = activePets.remove(uuid);
        if (pet != null) {
            removeEntityLookup(pet);
            pet.despawn();
        }
    }

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

    public void despawnAll() {
        for (PetEntity pet : activePets.values()) {
            pet.despawn();
        }
        activePets.clear();
        entityIdToOwner.clear();
    }

    /** AUDIT FIX: O(1) check using reverse lookup map. */
    public boolean isPetEntity(org.bukkit.entity.Entity entity) {
        return entityIdToOwner.containsKey(entity.getEntityId());
    }

    /** AUDIT FIX: O(1) lookup using reverse lookup map. */
    public UUID getOwnerOf(org.bukkit.entity.Entity entity) {
        return entityIdToOwner.get(entity.getEntityId());
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
