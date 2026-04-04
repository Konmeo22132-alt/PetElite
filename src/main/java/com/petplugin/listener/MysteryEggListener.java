package com.petplugin.listener;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.data.PlayerData;
import com.petplugin.item.MysteryEggItem;
import com.petplugin.pet.PetType;
import com.petplugin.util.ChatUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Task 5 — Mystery Egg right-click animation + pet creation.
 *
 * Animation:
 *  Ticks 0-19  : switch pet name title every 2 ticks (fast spin)
 *  Ticks 20-39 : switch every 5 ticks (slow down)
 *  Tick 40     : stop on chosen pet, show result for 2s
 *  Tick 60     : create pet, remove egg, send message
 */
public class MysteryEggListener implements Listener {

    private static final long COOLDOWN_MS = 3000L;
    private static final Random RNG = new Random();

    private final PetPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public MysteryEggListener(PetPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!MysteryEggItem.isMysteryEgg(plugin, item)) return;

        event.setCancelled(true);

        // Cooldown check
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - last)) / 1000 + 1;
            player.sendMessage(ChatUtil.color("&cWait " + remaining + "s before using another egg!"));
            return;
        }
        cooldowns.put(player.getUniqueId(), now);

        // Check pet slot
        PlayerData pd = plugin.getDataManager().loadPlayer(player.getUniqueId());
        if (pd == null) return;
        var owned = plugin.getDataManager().loadPetsForPlayer(player.getUniqueId());
        if (owned.size() >= pd.getPetSlots()) {
            player.sendMessage(ChatUtil.color("&cNo pet slots available! Upgrade your rank first."));
            return;
        }

        // Pre-pick the result so animation can end on it
        PetType[] types = PetType.values();
        PetType chosen = types[RNG.nextInt(types.length)];

        player.closeInventory();
        startAnimation(player, chosen, item);
    }

    private void startAnimation(Player player, PetType chosen, ItemStack eggItem) {
        String[] names = {"Turtle", "Wolf", "Cat"};
        final int[] tick = {0};
        final int[] nameIdx = {0};

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }

                boolean done = tick[0] >= 60;

                if (tick[0] < 60) {
                    // Determine switch interval
                    int interval = tick[0] < 20 ? 2 : 5;

                    if (tick[0] % interval == 0) {
                        // On tick 40+ lock to chosen
                        String display = (tick[0] >= 40)
                                ? chosen.getDisplayName()
                                : names[nameIdx[0] % names.length];
                        nameIdx[0]++;

                        String colour = tick[0] >= 40 ? "§a" : "§e";
                        player.sendTitle(
                                colour + "🥚 Mystery Egg",
                                "§f" + display,
                                0, 7, 3
                        );
                    }
                } else {
                    // Animation complete — create pet
                    cancel();
                    finalisePet(player, chosen, eggItem);
                    return;
                }

                tick[0]++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finalisePet(Player player, PetType chosen, ItemStack eggItem) {
        if (!player.isOnline()) return;

        // Create randomized pet
        String name = chosen.getDisplayName() + " (Egg)";
        PetData newPet = new PetData(player.getUniqueId(), chosen, name);

        // Random starting level 1-3
        int startLevel = 1 + RNG.nextInt(3);
        for (int i = 1; i < startLevel; i++) newPet.addExp(newPet.requiredExpForNextLevel());

        plugin.getDataManager().savePet(newPet);

        // Remove 1 egg from hand
        eggItem.setAmount(Math.max(0, eggItem.getAmount() - 1));

        player.sendMessage(ChatUtil.color(
                "&aYou received a &e" + chosen.getDisplayName() + " &a(Lv." + newPet.getLevel() + ") &afrom the Mystery Egg!"));
        player.sendMessage(ChatUtil.color(
                "&7Use &e/pet &7to view your new pet in the Pet Selector."));
        player.sendTitle("§a✦ " + chosen.getDisplayName() + "!", "§7Added to your collection.", 10, 40, 20);
    }
}
