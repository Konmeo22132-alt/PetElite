package com.petplugin.pet;

import com.petplugin.data.PetData;
import com.petplugin.skill.ParticleHandler;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;

/**
 * Wolf/Cat — Ground locomotion.
 *
 * Pokemon-style follow (Task 2):
 *  • Target: 2 blocks behind player
 *  • Distance > 2 → use Pathfinder.moveTo() at speed 1.0
 *  • Distance > 10 → teleport to behind player (prevents getting stuck)
 *  • Pet faces same direction as player every tick
 */
public class GroundPet extends PetEntity {

    private Tameable petEntity;

    public GroundPet(PetData petData, Player owner) {
        super(petData, owner);
    }

    @Override
    public void spawn() {
        Location loc = FloatPet.behindPlayer(owner, 2.0);
        EntityType entityType = petData.getType() == PetType.WOLF
                ? EntityType.WOLF : EntityType.CAT;

        petEntity = (Tameable) owner.getWorld().spawnEntity(loc, entityType);
        petEntity.setOwner(owner);
        petEntity.setCustomNameVisible(true);
        petEntity.customName(Component.text(petData.getName()));

        // --- Safety flags (same as FloatPet Turtle) ---
        petEntity.setInvulnerable(true);
        petEntity.setPersistent(false); // DO NOT save to disk
        petEntity.setSilent(true);

        int maxHp = petData.getType().getBaseHp() + petData.getLevel() * 2;
        if (petEntity instanceof Creature creature) {
            var attr = creature.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) attr.setBaseValue(maxHp);
            creature.setHealth(maxHp);
        }

        if (petEntity instanceof Mob mob) {
            mob.setRemoveWhenFarAway(false);
            if (petEntity instanceof Sittable sit) sit.setSitting(false);
        }

        spawned = true;
    }

    @Override
    public void despawn() {
        if (petEntity != null && !petEntity.isDead()) petEntity.remove();
        petEntity = null;
        spawned = false;
    }

    @Override
    public void tick() {
        if (!spawned) return;
        if (!owner.isOnline()) { despawn(); return; }

        if (petEntity == null || !petEntity.isValid() || petEntity.isDead()) {
            // Auto-respawn if chunk unloaded or entity somehow removed
            if (petData.isVisible() && !petData.isFainted()) {
                despawn();
                spawn();
            } else {
                despawn();
            }
            return;
        }

        Location targetLoc = FloatPet.behindPlayer(owner, 2.0);
        double dist = petEntity.getLocation().distance(owner.getLocation());

        if (dist > 10.0) {
            // Hard teleport snap
            petEntity.teleport(targetLoc);
        } else if (dist > 2.0) {
            // Use pathfinder to move toward target point
            petEntity.getPathfinder().moveTo(targetLoc, 1.0);
        }

        // Face same direction as owner (yaw only)
        Location petLoc = petEntity.getLocation();
        petLoc.setYaw(owner.getLocation().getYaw());
        petEntity.teleport(petLoc);
    }

    @Override
    public void updateDisplayName(String newName) {
        if (petEntity != null && !petEntity.isDead()) {
            petEntity.customName(Component.text(newName));
        }
    }

    @Override
    public void onPassiveSkillTrigger(LivingEntity target) {
        if (petEntity != null) {
            ParticleHandler.spawnSkillParticle(owner, com.petplugin.skill.BranchType.ATK_BRANCH);
        }
    }

    public Tameable getEntity() { return petEntity; }
}
