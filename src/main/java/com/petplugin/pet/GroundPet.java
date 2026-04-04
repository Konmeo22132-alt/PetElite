package com.petplugin.pet;

import com.petplugin.data.PetData;
import com.petplugin.skill.ParticleHandler;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;

/**
 * Wolf/Cat — Ground locomotion via Pathfinding AI.
 * Spawns the actual Wolf or Cat entity, tames it to the owner,
 * and lets Paper's built-in AI handle follow behavior.
 */
public class GroundPet extends PetEntity {

    private Tameable petEntity;

    public GroundPet(PetData petData, Player owner) {
        super(petData, owner);
    }

    @Override
    public void spawn() {
        Location loc = owner.getLocation().add(1.5, 0, 0);
        EntityType entityType = petData.getType() == PetType.WOLF
                ? EntityType.WOLF : EntityType.CAT;

        petEntity = (Tameable) owner.getWorld().spawnEntity(loc, entityType);

        // Tame to owner for follow AI
        petEntity.setOwner(owner);

        // Set custom name
        petEntity.setCustomNameVisible(true);
        petEntity.customName(net.kyori.adventure.text.Component.text(petData.getName()));

        // Set max health based on pet stats
        int maxHp = petData.getType().getBaseHp() + petData.getLevel() * 2;
        if (petEntity instanceof Creature creature) {
            var attr = creature.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) attr.setBaseValue(maxHp);
            creature.setHealth(maxHp);
        }

        // Disable natural harm from environment
        if (petEntity instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
        }

        spawned = true;
    }

    @Override
    public void despawn() {
        if (petEntity != null && !petEntity.isDead()) {
            petEntity.remove();
        }
        petEntity = null;
        spawned = false;
    }

    @Override
    public void tick() {
        if (!spawned || petEntity == null || petEntity.isDead()) return;
        if (!owner.isOnline()) { despawn(); return; }
        // AI handles following; tick is minimal for ground pets
    }

    @Override
    public void onPassiveSkillTrigger(LivingEntity target) {
        if (petEntity != null) {
            ParticleHandler.spawnSkillParticle(owner, com.petplugin.skill.BranchType.ATK_BRANCH);
        }
    }

    public Tameable getEntity() { return petEntity; }
}
