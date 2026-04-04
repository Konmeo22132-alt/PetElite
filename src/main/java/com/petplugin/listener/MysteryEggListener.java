package com.petplugin.listener;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.data.PlayerData;
import com.petplugin.item.MysteryEggItem;
import com.petplugin.pet.PetType;
import com.petplugin.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
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
 * Task 2 — Mystery Egg right-click: slot-machine animation + sounds + actionbar spin.
 *
 * Timing (slot-machine feel):
 *  Ticks  0-10 : switch every 1 tick  (very fast)
 *  Ticks 11-20 : switch every 2 ticks (fast)
 *  Ticks 21-30 : switch every 4 ticks (medium)
 *  Ticks 31-40 : switch every 6 ticks (slowing)
 *  Ticks 41-50 : switch every 10 ticks (very slow, tension)
 *  Tick  50+   : hold result for 40 ticks (2 seconds)
 *  Tick  90    : finalise pet, remove egg
 *
 * Sound per switch:
 *  Fast (ticks 0-20)    : UI_BUTTON_CLICK, pitch 1.5
 *  Slowing (ticks 21-40): UI_BUTTON_CLICK, pitch 1.4 → 1.2 → 1.0 → 0.8 (step each phase)
 *  Final reveal         : ENTITY_PLAYER_LEVELUP, pitch 1.0, vol 1.0
 *
 * Display:
 *  Spin phase  → sendActionBar (less intrusive)
 *  Final reveal → sendTitle with type-colour + "is yours!" subtitle
 */
public class MysteryEggListener implements Listener {

    private static final long COOLDOWN_MS = 3000L;
    private static final Random RNG = new Random();

    // Slot-machine phase intervals (inclusive tick ranges + switch interval)
    // Each entry: {startTick, endTick, switchEvery}
    private static final int[][] PHASES = {
        {  0, 10,  1 },  // very fast
        { 11, 20,  2 },  // fast
        { 21, 30,  4 },  // medium
        { 31, 40,  6 },  // slowing
        { 41, 50, 10 },  // very slow
    };
    private static final int SPIN_END   = 50; // last spin tick
    private static final int TOTAL_TICKS = 90; // spin(50) + hold(40)

    // Pitches for slowing phases (phases 2-5, index 0-3)
    private static final float[] SLOW_PITCHES = { 1.4f, 1.2f, 1.0f, 0.8f };

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

        // Cooldown
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - last)) / 1000 + 1;
            player.sendMessage(ChatUtil.color("&cWait " + remaining + "s before using another egg!"));
            return;
        }
        cooldowns.put(player.getUniqueId(), now);

        // Check slot
        PlayerData pd = plugin.getDataManager().loadPlayer(player.getUniqueId());
        if (pd == null) return;
        var owned = plugin.getDataManager().loadPetsForPlayer(player.getUniqueId());
        if (owned.size() >= pd.getPetSlots()) {
            player.sendMessage(ChatUtil.color("&cNo pet slots available! Upgrade your rank first."));
            return;
        }

        // Pre-pick result
        PetType[] types = PetType.values();
        PetType chosen = types[RNG.nextInt(types.length)];

        player.closeInventory();
        startAnimation(player, chosen, item);
    }

    private void startAnimation(Player player, PetType chosen, ItemStack eggItem) {
        String[] names = { "🐢 Turtle", "🐺 Wolf", "🐱 Cat" };
        final int[] tick   = { 0 };
        final int[] nameIdx = { 0 };

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }

                int t = tick[0];

                if (t >= TOTAL_TICKS) {
                    // Done
                    cancel();
                    finalisePet(player, chosen, eggItem);
                    return;
                }

                if (t < SPIN_END) {
                    // Determine phase details
                    int phaseIdx = getPhaseIdx(t);
                    int interval = PHASES[phaseIdx][2];

                    if (t % interval == 0) {
                        String display = names[nameIdx[0] % names.length];
                        nameIdx[0]++;

                        // Actionbar spin
                        player.sendActionBar(Component.text("§6🥚  §e" + display + "  §6🥚"));

                        // Sound
                        if (phaseIdx <= 1) {
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                        } else {
                            float pitch = SLOW_PITCHES[Math.min(phaseIdx - 2, SLOW_PITCHES.length - 1)];
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, pitch);
                        }
                    }
                } else {
                    // Hold phase — show final result via title every 5 ticks to keep it visible
                    if (t == SPIN_END) {
                        // First reveal
                        player.sendTitle(
                            typeColour(chosen) + "✦ " + chosen.getDisplayName() + " ✦",
                            "§ais yours!",
                            5, 50, 20
                        );
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        player.sendActionBar(Component.text(typeColour(chosen) + "✦ " + chosen.getDisplayName()));
                    }
                }

                tick[0]++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finalisePet(Player player, PetType chosen, ItemStack eggItem) {
        if (!player.isOnline()) return;

        String name = chosen.getDisplayName() + " (Egg)";
        PetData newPet = new PetData(player.getUniqueId(), chosen, name);

        // Random level 1–3
        int startLevel = 1 + RNG.nextInt(3);
        for (int i = 1; i < startLevel; i++) newPet.addExp(newPet.requiredExpForNextLevel());

        plugin.getDataManager().savePet(newPet);

        // Remove 1 egg from hand
        eggItem.setAmount(Math.max(0, eggItem.getAmount() - 1));

        player.sendMessage(ChatUtil.color(
            "&aYou received a &e" + chosen.getDisplayName()
            + " &a(Lv." + newPet.getLevel() + ") &afrom the Mystery Egg!"));
        player.sendMessage(ChatUtil.color(
            "&7Use &e/pet &7to view your new pet in the Pet Selector."));
    }

    // ---- Helpers ----

    private int getPhaseIdx(int tick) {
        for (int i = 0; i < PHASES.length; i++) {
            if (tick >= PHASES[i][0] && tick <= PHASES[i][1]) return i;
        }
        return PHASES.length - 1;
    }

    private String typeColour(PetType type) {
        return switch (type) {
            case TURTLE -> "§a"; // green
            case WOLF   -> "§7"; // gray
            case CAT    -> "§d"; // light purple
        };
    }
}
