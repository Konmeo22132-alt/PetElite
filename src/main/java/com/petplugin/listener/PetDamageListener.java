package com.petplugin.listener;

import com.petplugin.PetPlugin;
import com.petplugin.pet.GroundPet;
import com.petplugin.pet.PetEntity;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

/**
 * Task 6 — Prevent players from damaging their own pet.
 * Also triggers pet faint/respawn flow (Task 7) when the pet entity dies.
 */
public class PetDamageListener implements Listener {

    private final PetPlugin plugin;

    public PetDamageListener(PetPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();

        // Find which player (if any) is the pet owner of the damaged entity
        UUID ownerUuid = plugin.getPetManager().getOwnerOf(damaged);
        if (ownerUuid == null) return; // not a pet

        // Determine the actual damager (may be projectile)
        UUID damagerUuid = null;
        if (event.getDamager() instanceof Player p) {
            damagerUuid = p.getUniqueId();
        } else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player shooter) {
            damagerUuid = shooter.getUniqueId();
        }

        if (damagerUuid == null) return;

        // Silently cancel if the damager is the pet's own owner
        if (damagerUuid.equals(ownerUuid)) {
            event.setCancelled(true);
        }
    }

    /**
     * Task 7 — When a GroundPet entity dies naturally (e.g. mob attack), trigger faint flow.
     * FloatPet (BlockDisplay) is indestructible — no need to handle here.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        UUID ownerUuid = plugin.getPetManager().getOwnerOf(event.getEntity());
        if (ownerUuid == null) return;

        // Suppress drops
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Trigger faint
        plugin.getPetRespawnManager().handleFaint(ownerUuid, event.getEntity().getLocation());
    }
}
