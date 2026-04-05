package com.petplugin.pet;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.skill.ParticleHandler;
import com.petplugin.util.FoliaUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;

/**
 * Wolf/Cat — Ground locomotion.
 *
 * Task 6 / 7 rework: manual position update, NO pathfinder AI.
 *  • Target: 1.5 blocks behind player at ground level (same Y as player)
 *  • Distance > 10 → hard teleport snap
 *  • Distance > 2  → lerp 0.35 per tick toward target
 *  • Distance ≤ 2  → do nothing
 *  • Pet faces same direction as player (copyYaw) every tick
 *  • Folia Self-ticking via EntityScheduler
 */
public class GroundPet extends PetEntity {

    private Tameable petEntity;

    // Smoothed position for GroundPet
    private double smoothX, smoothY, smoothZ;
    private boolean smoothInit = false;

    // Scheduler reference
    private Object tickTask;

    public GroundPet(PetData petData, Player owner) {
        super(petData, owner);
    }

    @Override
    public void spawn() {
        Location loc = FloatPet.behindPlayer(owner, 1.5);
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

        // Task 6: Disable AI completely — use manual position update only
        if (petEntity instanceof Mob mob) {
            mob.setAI(false);
            mob.setRemoveWhenFarAway(false);
            if (petEntity instanceof Sittable sit) sit.setSitting(false);
        }

        int maxHp = petData.getType().getBaseHp() + petData.getLevel() * 2;
        if (petEntity instanceof Creature creature) {
            var attr = creature.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) attr.setBaseValue(maxHp);
            creature.setHealth(maxHp);
        }

        // Initialise smooth position
        smoothX = loc.getX();
        smoothY = loc.getY();
        smoothZ = loc.getZ();
        smoothInit = true;
        spawned = true;

        startTickLoop();
    }

    private void startTickLoop() {
        PetPlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(PetPlugin.class);
        if (FoliaUtil.IS_FOLIA) {
            tickTask = petEntity.getScheduler().runAtFixedRate(plugin, task -> {
                tick();
            }, null, 1L, 1L);
        } else {
            tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    private void cancelTickLoop() {
        if (tickTask != null) {
            if (FoliaUtil.IS_FOLIA) {
                ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) tickTask).cancel();
            } else {
                ((org.bukkit.scheduler.BukkitTask) tickTask).cancel();
            }
            tickTask = null;
        }
    }

    @Override
    public void despawn() {
        cancelTickLoop();
        if (petEntity != null && !petEntity.isDead()) petEntity.remove();
        petEntity = null;
        spawned = false;
        smoothInit = false;
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

        // Task 6: Manual position update — NO pathfinder
        Location targetLoc = FloatPet.behindPlayer(owner, 1.5);
        // Keep Y same as player (ground level)
        targetLoc.setY(owner.getLocation().getY());

        Location petLoc = petEntity.getLocation();
        double dist = petLoc.distance(targetLoc);

        // Initialise smooth if not done
        if (!smoothInit) {
            smoothX = petLoc.getX();
            smoothY = petLoc.getY();
            smoothZ = petLoc.getZ();
            smoothInit = true;
        }

        if (dist > 10.0) {
            // Hard snap
            smoothX = targetLoc.getX();
            smoothY = targetLoc.getY();
            smoothZ = targetLoc.getZ();
        } else if (dist > 2.0) { // Task 7: 2-block tight follow
            // Lerp 0.35 per tick
            double lf = 0.35;
            smoothX += (targetLoc.getX() - smoothX) * lf;
            smoothY += (targetLoc.getY() - smoothY) * lf;
            smoothZ += (targetLoc.getZ() - smoothZ) * lf;
        }
        // else: within 2 blocks — just update yaw, no position change

        // Apply: face player direction, move to smoothed position
        float playerYaw = owner.getLocation().getYaw();
        petEntity.teleport(new Location(owner.getWorld(), smoothX, smoothY, smoothZ,
                playerYaw, petLoc.getPitch()));
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
