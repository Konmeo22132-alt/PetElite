package com.petplugin.pet;

import com.petplugin.data.PetData;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Abstract base for all pet entity wrappers.
 * Subclasses handle the actual entity spawning (FloatPet vs GroundPet).
 */
public abstract class PetEntity {

    protected final PetData petData;
    protected final Player owner;
    protected boolean spawned = false;

    public PetEntity(PetData petData, Player owner) {
        this.petData = petData;
        this.owner = owner;
    }

    /** Spawn the entity in the world near the owner. */
    public abstract void spawn();

    /** Remove the entity from the world. */
    public abstract void despawn();

    /** Called by PetManager every tick (for FloatPet lerp animation). */
    public abstract void tick();

    /** Called when a passive skill should trigger. */
    public abstract void onPassiveSkillTrigger(org.bukkit.entity.LivingEntity target);

    /**
     * Update the visible display name on the entity immediately (after rename).
     * @param newName colour-translated name string
     */
    public abstract void updateDisplayName(String newName);

    /** Is this pet currently visible in the world? */
    public boolean isSpawned() { return spawned; }

    public PetData getPetData() { return petData; }
    public Player getOwner()    { return owner; }
}
