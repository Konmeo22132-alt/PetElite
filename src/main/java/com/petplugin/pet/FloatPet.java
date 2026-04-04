package com.petplugin.pet;

import com.petplugin.data.PetData;
import com.petplugin.skill.ParticleHandler;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Turtle — Float locomotion using a BlockDisplay entity.
 *
 * Pokemon-style follow:
 *  • Target position: player.loc + behind player by 1.5 blocks + Y +1.2
 *  • Lerp smoothly each tick (factor 0.08)
 *  • Teleport snap if > 10 blocks away (prevents getting stuck)
 *  • Sine-wave Y float animation (±0.1 block)
 */
public class FloatPet extends PetEntity {

    private BlockDisplay display;
    private double tickCounter = 0.0;

    // Smoothed position
    private double smoothX, smoothY, smoothZ;

    public FloatPet(PetData petData, Player owner) {
        super(petData, owner);
    }

    @Override
    public void spawn() {
        Location loc = behindPlayer(owner, 1.5).add(0, 1.2, 0);
        display = owner.getWorld().spawn(loc, BlockDisplay.class, entity -> {
            entity.setBlock(org.bukkit.Bukkit.createBlockData(org.bukkit.Material.TURTLE_EGG));
            entity.setCustomNameVisible(true);
            entity.customName(Component.text(petData.getName()));
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(2);
            entity.setTransformation(new Transformation(
                new Vector3f(-0.35f, 0f, -0.35f),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(0.7f, 0.7f, 0.7f),
                new AxisAngle4f(0, 0, 0, 1)
            ));
        });
        smoothX = loc.getX();
        smoothY = loc.getY();
        smoothZ = loc.getZ();
        spawned = true;
    }

    @Override
    public void despawn() {
        if (display != null && !display.isDead()) display.remove();
        display = null;
        spawned = false;
    }

    @Override
    public void tick() {
        if (!spawned || display == null || display.isDead()) return;
        if (!owner.isOnline()) { despawn(); return; }

        tickCounter += 0.08;
        double floatOffset = Math.sin(tickCounter) * 0.1;

        Location targetRaw = behindPlayer(owner, 1.5).add(0, 1.2 + floatOffset, 0);

        // Snap if too far away
        double distSq = display.getLocation().distanceSquared(targetRaw);
        if (distSq > 100.0) { // > 10 blocks
            smoothX = targetRaw.getX();
            smoothY = targetRaw.getY();
            smoothZ = targetRaw.getZ();
        } else {
            double lf = 0.10; // lerp factor — slightly tighter than before
            smoothX += (targetRaw.getX() - smoothX) * lf;
            smoothY += (targetRaw.getY() - smoothY) * lf;
            smoothZ += (targetRaw.getZ() - smoothZ) * lf;
        }

        // Clamp Y
        double ownerY = owner.getLocation().getY();
        smoothY = Math.min(smoothY, ownerY + 3.0);
        smoothY = Math.max(smoothY, ownerY - 0.5);

        display.teleport(new Location(owner.getWorld(), smoothX, smoothY, smoothZ,
                display.getLocation().getYaw(), display.getLocation().getPitch()));
    }

    @Override
    public void updateDisplayName(String newName) {
        if (display != null && !display.isDead()) {
            display.customName(Component.text(newName));
        }
    }

    @Override
    public void onPassiveSkillTrigger(LivingEntity target) {
        if (display != null) {
            ParticleHandler.spawnSkillParticle(owner, com.petplugin.skill.BranchType.DEF_BRANCH);
        }
    }

    public BlockDisplay getDisplay() { return display; }

    // ---- Helpers ----

    /**
     * Returns a location exactly {@code dist} blocks behind the player along their facing direction.
     */
    static Location behindPlayer(Player player, double dist) {
        Location loc = player.getLocation().clone();
        double yaw = Math.toRadians(loc.getYaw());
        loc.add(Math.sin(yaw) * dist, 0, -Math.cos(yaw) * dist);
        return loc;
    }
}
