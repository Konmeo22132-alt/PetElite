package com.petplugin.listener;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityTargetEvent;

/**
 * Detects Shift+click on an active pet entity → open PetMainGUI.
 */
public class PetInteractListener implements Listener {

    private final PetPlugin plugin;

    public PetInteractListener(PetPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        // Check if clicked entity is this player's pet
        java.util.UUID ownerUuid = plugin.getPetManager().getOwnerOf(event.getRightClicked());
        if (ownerUuid == null) return;
        if (!ownerUuid.equals(player.getUniqueId())) {
            // Prevent opening other players' pet menus
            return;
        }

        event.setCancelled(true);

        PetData pet = plugin.getDataManager().getActivePet(player.getUniqueId());
        if (pet == null) return;

        plugin.getPetMainGUI().open(player);
    }

    @EventHandler
    public void onEntityInteract(EntityInteractEvent event) {
        if (plugin.getPetManager().isPetEntity(event.getEntity())) {
            // Prevent pets from triggering pressure plates, tripwires, etc.
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && plugin.getPetManager().isPetEntity(event.getTarget())) {
            // Prevent hostile mobs from targeting pets
            event.setCancelled(true);
        }
    }
}
