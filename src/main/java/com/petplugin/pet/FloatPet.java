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
 * Turtle — Float locomotion using a real TURTLE entity.
 *
 * Task 1 fix: added Folia-compatible tick loop scheduling inside spawn().
 * Task 2 fix: Fallback to direct position lerp on the Turtle entity itself to avoid passenger alignment issues.
 * Task 7 fix: 2-block tight follow implemented.
 */
public class FloatPet extends PetEntity {

    private Turtle turtleEntity;
    private double tickCounter = 0.0;

    // Smoothed position
    private double smoothX, smoothY, smoothZ;
    private boolean firstTick = true;

    // Scheduler reference
    private Object tickTask;

    public FloatPet(PetData petData, Player owner) {
        super(petData, owner);
    }

    @Override
    public void spawn() {
        Location loc = behindPlayer(owner, 1.5).add(0, 1.2, 0);

        turtleEntity = owner.getWorld().spawn(loc, Turtle.class, entity -> {
            // --- Appearance ---
            entity.setCustomNameVisible(true);
            entity.customName(Component.text(petData.getName()));
            entity.setAdult(); // Force adult turtle — more visible
            entity.setInvisible(false);

            // --- Behaviour flags ---
            entity.setAI(false);
            entity.setSilent(true);
            entity.setInvulnerable(true);
            entity.setPersistent(false); // DO NOT save to disk
            entity.setRemoveWhenFarAway(false);
            entity.setGravity(false); // Float — don't fall

            // --- Max HP (cosmetic, no battle impact) ---
            var attr = entity.getAttribute(Attribute.MAX_HEALTH);
            if (attr != null) attr.setBaseValue(100);
            entity.setHealth(100);
        });

        Bukkit.getLogger().info("[PetPlugin] Spawned FloatPet for " + owner.getName() + " at " + loc);

        smoothX = loc.getX();
        smoothY = loc.getY();
        smoothZ = loc.getZ();
        spawned = true;
        firstTick = true;

        startTickLoop();
    }

    private void startTickLoop() {
        PetPlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(PetPlugin.class);
        if (FoliaUtil.IS_FOLIA) {
            tickTask = turtleEntity.getScheduler().runAtFixedRate(plugin, task -> {
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
        if (turtleEntity != null && !turtleEntity.isDead()) turtleEntity.remove();
        turtleEntity = null;
        spawned = false;
    }

    @Override
    public void tick() {
        if (!spawned) return;
        if (!owner.isOnline()) { despawn(); return; }

        if (turtleEntity == null || !turtleEntity.isValid() || turtleEntity.isDead()) {
            // Auto-respawn if chunk unloaded or entity somehow removed
            if (petData.isVisible() && !petData.isFainted()) {
                despawn();
                spawn();
            } else {
                despawn();
            }
            return;
        }

        if (firstTick) {
            Bukkit.getLogger().info("[PetPlugin] First tick for FloatPet: " + turtleEntity.getUniqueId());
            firstTick = false;
        }

        // TPS-independent float animation using system time
        tickCounter = (System.currentTimeMillis() % 10000L) / 1000.0 * Math.PI;
        double floatOffset = Math.sin(tickCounter) * 0.1;

        Location targetRaw = behindPlayer(owner, 1.5).add(0, 1.2 + floatOffset, 0);
        double distSq = turtleEntity.getLocation().distanceSquared(targetRaw);

        // Snap if too far away (> 10 blocks)
        if (distSq > 100.0) {
            smoothX = targetRaw.getX();
            smoothY = targetRaw.getY();
            smoothZ = targetRaw.getZ();
            turtleEntity.teleport(new Location(owner.getWorld(), smoothX, smoothY, smoothZ,
                    owner.getLocation().getYaw(), turtleEntity.getLocation().getPitch()));
        } else if (distSq > 4.0) { // > 2 blocks
            double lf = 0.35; // lerp factor
            smoothX += (targetRaw.getX() - smoothX) * lf;
            smoothY += (targetRaw.getY() - smoothY) * lf;
            smoothZ += (targetRaw.getZ() - smoothZ) * lf;
            
            // Clamp Y
            double ownerY = owner.getLocation().getY();
            smoothY = Math.min(smoothY, ownerY + 3.5);
            smoothY = Math.max(smoothY, ownerY - 0.5);

            turtleEntity.teleport(new Location(owner.getWorld(), smoothX, smoothY, smoothZ,
                    owner.getLocation().getYaw(), turtleEntity.getLocation().getPitch()));
        } else {
            // Idle float animation only
            smoothY += (targetRaw.getY() - smoothY) * 0.12;
            turtleEntity.teleport(new Location(owner.getWorld(), smoothX, smoothY, smoothZ,
                    owner.getLocation().getYaw(), turtleEntity.getLocation().getPitch()));
        }
    }

    @Override
    public void updateDisplayName(String newName) {
        if (turtleEntity != null && !turtleEntity.isDead()) {
            turtleEntity.customName(Component.text(newName));
        }
    }

    @Override
    public void onPassiveSkillTrigger(LivingEntity target) {
        if (turtleEntity != null) {
            ParticleHandler.spawnSkillParticle(owner, com.petplugin.skill.BranchType.DEF_BRANCH);
        }
    }

    public Turtle getTurtleEntity() { return turtleEntity; }

    // ---- Helpers ----

    /**
     * Returns a location exactly {@code dist} blocks behind the player along their facing direction.
     */
    public static Location behindPlayer(Player player, double dist) {
        Location loc = player.getLocation().clone();
        double yaw = Math.toRadians(loc.getYaw());
        loc.add(Math.sin(yaw) * dist, 0, -Math.cos(yaw) * dist);
        return loc;
    }
}
