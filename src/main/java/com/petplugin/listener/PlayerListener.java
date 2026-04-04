package com.petplugin.listener;

import com.petplugin.PetPlugin;
import com.petplugin.quest.QuestType;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Handles quest-tracking events:
 * - BlockBreakEvent    → MINE_ORE, and future ore checks
 * - PlayerMoveEvent   → WALK_BLOCKS, LONG_JOURNEY
 * - PlayerDeathEvent  → KILL_PLAYERS (check killer)
 * - EntityDeathEvent  → KILL_BOSS (Wither / Elder Guardian)
 * - CraftItemEvent    → CRAFT_RARE
 * Also fires passive skill triggers on EntityDamageByEntityEvent.
 */
public class PlayerListener implements Listener {

    private final PetPlugin plugin;

    // Materials considered "ore" for quest purposes
    private static final Set<Material> ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE,
            Material.ANCIENT_DEBRIS
    );

    private static final List<String> RARE_MATERIALS;

    static {
        RARE_MATERIALS = List.of(
                "ELYTRA", "ENCHANTED_GOLDEN_APPLE", "BEACON", "END_CRYSTAL",
                "SHULKER_BOX", "NETHERITE_INGOT"
        );
    }

    public PlayerListener(PetPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Material type = event.getBlock().getType();
        if (ORES.contains(type)) {
            plugin.getQuestTracker().increment(player, QuestType.MINE_ORE, 1);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Only count distinct block movement (not pitch/yaw only)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        // Calculate distance (integer blocks for performance)
        double dist = event.getFrom().distance(event.getTo());
        int blocksMoved = (int) Math.ceil(dist);

        plugin.getQuestTracker().increment(player, QuestType.WALK_BLOCKS, blocksMoved);
        plugin.getQuestTracker().increment(player, QuestType.LONG_JOURNEY, blocksMoved);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getPlayer().getKiller();
        if (killer == null) return;
        plugin.getQuestTracker().increment(killer, QuestType.KILL_PLAYERS, 1);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        Player killer = event.getEntity().getKiller();

        EntityType type = event.getEntityType();
        if (type == EntityType.WITHER || type == EntityType.ELDER_GUARDIAN) {
            plugin.getQuestTracker().increment(killer, QuestType.KILL_BOSS, 1);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getRecipe().getResult();
        if (result == null) return;

        String matName = result.getType().name();
        if (RARE_MATERIALS.contains(matName)) {
            plugin.getQuestTracker().increment(player, QuestType.CRAFT_RARE, 1);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        // Trigger pet passive skill on combat hit
        com.petplugin.pet.PetEntity pet = plugin.getPetManager().getActivePet(player.getUniqueId());
        if (pet != null && pet.isSpawned()) {
            if (event.getEntity() instanceof org.bukkit.entity.LivingEntity target) {
                pet.onPassiveSkillTrigger(target);
            }
        }
    }
}
