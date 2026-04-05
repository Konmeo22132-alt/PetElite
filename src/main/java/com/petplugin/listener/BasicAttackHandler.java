package com.petplugin.listener;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.pet.FloatPet;
import com.petplugin.pet.GroundPet;
import com.petplugin.pet.PetEntity;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Task 5 — Basic Attack
 *
 * When a pet owner deals melee damage to an entity:
 *  1. Cool-down check (2 seconds)
 *  2. Pet dashes to target (teleport to face of target, 0.5 blocks away)
 *  3. CRIT particle × 6 at target
 *  4. Bonus damage: 25% of pet base ATK
 *  5. Sound: ENTITY_PLAYER_ATTACK_STRONG pitch 1.2
 *  6. After 6 ticks: pet returns to follow position behind player
 *
 * Blocked when: pet is fainted, hidden, or owner is in battle.
 * FloatPet: dash position keeps Y+1.2 offset.
 * GroundPet: dash at ground level.
 */
public class BasicAttackHandler implements Listener {

    private static final long COOLDOWN_TICKS = 40L; // 2 seconds
    private static final int  RETURN_TICKS   = 6;   // 0.3 seconds

    private final PetPlugin plugin;

    /** player UUID → cooldown (server tick when attack was last fired) */
    private final Map<UUID, Long> lastAttack = new HashMap<>();

    public BasicAttackHandler(PetPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMeleeHit(EntityDamageByEntityEvent event) {
        // Only player-originated melee (not projectiles)
        if (!(event.getDamager() instanceof Player player)) return;

        // Skip if in battle
        if (plugin.getBattleManager().getSession(player.getUniqueId()) != null) return;

        // Must have an active, spawned pet
        PetEntity petEntity = plugin.getPetManager().getActivePet(player.getUniqueId());
        if (petEntity == null || !petEntity.isSpawned()) return;

        PetData petData = petEntity.getPetData();

        // Skip if pet is fainted or hidden
        if (petData.isFainted() || !petData.isVisible()) return;

        // Do not trigger pet's own basic attack on itself or another pet
        Entity damaged = event.getEntity();
        if (plugin.getPetManager().isPetEntity(damaged)) return;

        // Cooldown check
        long now = plugin.getServer().getCurrentTick();
        Long lastTick = lastAttack.get(player.getUniqueId());
        if (lastTick != null && (now - lastTick) < COOLDOWN_TICKS) return;
        lastAttack.put(player.getUniqueId(), now);

        // Target must be a LivingEntity
        if (!(damaged instanceof LivingEntity target)) return;

        // ---- Dash ----
        performBasicAttack(player, petEntity, petData, target);
    }

    private void performBasicAttack(Player player, PetEntity petEntity, PetData petData, LivingEntity target) {
        // Calculate dash position: face of target (0.5 blocks toward target from its centre)
        Location targetLoc = target.getLocation().clone().add(0, 0.5, 0);
        Location petCurrentLoc = getPetLocation(petEntity);
        if (petCurrentLoc == null) return;

        // Direction from pet to target
        org.bukkit.util.Vector dir = targetLoc.toVector().subtract(petCurrentLoc.toVector()).normalize();
        // Stand 0.5 blocks away from target centre
        Location dashLoc = targetLoc.clone().subtract(dir.clone().multiply(0.5));

        // For FloatPet: maintain Y+1.2 above target
        if (petEntity instanceof FloatPet) {
            dashLoc.setY(target.getLocation().getY() + 1.2);
        } else {
            // GroundPet: ground level
            dashLoc.setY(target.getLocation().getY());
        }
        dashLoc.setWorld(player.getWorld());

        // Teleport pet to dash position
        teleportPet(petEntity, dashLoc);

        // Particle: CRIT × 6 at target location
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0),
                6, 0.3, 0.3, 0.3, 0.1);

        // Sound at pet location
        target.getWorld().playSound(dashLoc, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.2f);

        // Bonus damage: 25% of pet base ATK (scaled by level)
        double baseAtk = petData.getType().getBaseAtk() + petData.getLevel();
        double bonusDmg = baseAtk * 0.25;
        if (bonusDmg > 0 && target.isValid() && !target.isDead()) {
            target.damage(bonusDmg, player);
        }

        // ---- Return after 6 ticks ----
        Runnable returnTask = () -> {
            if (!player.isOnline()) return;
            if (!petEntity.isSpawned()) return;
            // Return to follow position behind player
            Location returnLoc = FloatPet.behindPlayer(player, 1.5);
            if (petEntity instanceof FloatPet) {
                returnLoc = returnLoc.add(0, 1.2, 0);
            } else {
                returnLoc.setY(player.getLocation().getY());
            }
            returnLoc.setWorld(player.getWorld());
            teleportPet(petEntity, returnLoc);
        };

        Entity internalEntity = null;
        if (petEntity instanceof FloatPet fp) internalEntity = fp.getTurtleEntity();
        if (petEntity instanceof GroundPet gp) internalEntity = gp.getEntity();

        if (internalEntity != null && com.petplugin.util.FoliaUtil.IS_FOLIA) {
            internalEntity.getScheduler().runDelayed(plugin, task -> returnTask.run(), null, RETURN_TICKS);
        } else {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, returnTask, RETURN_TICKS);
        }
    }

    // ---- Helpers ----

    private Location getPetLocation(PetEntity petEntity) {
        if (petEntity instanceof FloatPet fp && fp.getTurtleEntity() != null) {
            return fp.getTurtleEntity().getLocation();
        }
        if (petEntity instanceof GroundPet gp && gp.getEntity() != null) {
            return gp.getEntity().getLocation();
        }
        return null;
    }

    private void teleportPet(PetEntity petEntity, Location loc) {
        if (petEntity instanceof FloatPet fp && fp.getTurtleEntity() != null
                && fp.getTurtleEntity().isValid()) {
            fp.getTurtleEntity().teleport(loc);
        }
        if (petEntity instanceof GroundPet gp && gp.getEntity() != null
                && gp.getEntity().isValid()) {
            gp.getEntity().teleport(loc);
        }
    }
}
