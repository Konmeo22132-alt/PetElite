package com.petplugin.pet;

import com.petplugin.data.PetData;
import com.petplugin.skill.ParticleHandler;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Turtle — Float locomotion using a BlockDisplay entity.
 * Lerps Y toward player.y + 1.2, adds ±0.1 sine-wave float animation every tick.
 */
public class FloatPet extends PetEntity {

    private BlockDisplay display;
    private double floatOffset = 0.0;   // current sine offset
    private double tickCounter = 0.0;

    // Target smoothed position
    private double smoothX, smoothY, smoothZ;

    public FloatPet(PetData petData, Player owner) {
        super(petData, owner);
    }

    @Override
    public void spawn() {
        Location loc = owner.getLocation().add(1.5, 1.2, 0);
        display = owner.getWorld().spawn(loc, BlockDisplay.class, entity -> {
            entity.setBlock(org.bukkit.Bukkit.createBlockData(org.bukkit.Material.TURTLE_EGG));
            entity.setCustomNameVisible(true);
            entity.customName(net.kyori.adventure.text.Component.text(petData.getName()));
            entity.setInterpolationDuration(2);
            entity.setTeleportDuration(2);
            // Scale up to 0.7 so it's visible but not huge
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
        if (display != null && !display.isDead()) {
            display.remove();
        }
        display = null;
        spawned = false;
    }

    @Override
    public void tick() {
        if (!spawned || display == null || display.isDead()) return;
        if (!owner.isOnline()) { despawn(); return; }

        tickCounter += 0.08;
        floatOffset = Math.sin(tickCounter) * 0.1;

        Location target = owner.getLocation().add(1.5, 1.2 + floatOffset, 0);

        // Lerp position toward target
        double lerpFactor = 0.08;
        smoothX += (target.getX() - smoothX) * lerpFactor;
        smoothY += (target.getY() - smoothY) * lerpFactor;
        smoothZ += (target.getZ() - smoothZ) * lerpFactor;

        // Clamp Y: don't fly more than 3 blocks above player
        smoothY = Math.min(smoothY, owner.getLocation().getY() + 3.0);
        // Don't go below ground (approx)
        smoothY = Math.max(smoothY, owner.getLocation().getY() - 0.5);

        display.teleport(new Location(owner.getWorld(), smoothX, smoothY, smoothZ,
                display.getLocation().getYaw(), display.getLocation().getPitch()));
    }

    @Override
    public void onPassiveSkillTrigger(LivingEntity target) {
        if (display != null) {
            ParticleHandler.spawnSkillParticle(owner, com.petplugin.skill.BranchType.DEF_BRANCH);
        }
    }

    public BlockDisplay getDisplay() { return display; }
}
