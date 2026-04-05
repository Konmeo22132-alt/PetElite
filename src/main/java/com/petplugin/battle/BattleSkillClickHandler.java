package com.petplugin.battle;

import com.petplugin.PetPlugin;
import com.petplugin.util.ChatUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles one-shot inventory clicks for the battle skill selection GUI.
 * When BattleListener opens the 27-slot skill picker, it registers the player here.
 * The next inventory click for that player is intercepted and routed to BattleSession.
 */
public class BattleSkillClickHandler implements Listener {

    private final PetPlugin plugin;
    // player UUID -> their active BattleSession (registered just before GUI opens)
    // AUDIT FIX: ConcurrentHashMap for thread safety
    private final Map<UUID, BattleSession> pending = new ConcurrentHashMap<>();

    public BattleSkillClickHandler(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(UUID uuid, BattleSession session) {
        pending.put(uuid, session);
    }

    public void unregister(UUID uuid) {
        pending.remove(uuid);
    }

    /** AUDIT FIX: cleanup on PlayerQuitEvent. */
    public void cleanupPlayer(UUID uuid) {
        pending.remove(uuid);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        BattleSession session = pending.get(uuid);
        if (session == null) return;

        // Check title matches our skill picker
        if (!event.getView().title().equals(ChatUtil.color("&8⚔ Chọn Skill (click để dùng)"))) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null
                || event.getCurrentItem().getType().isAir()) return;

        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) return;

        // Extract global slot from lore "&8GlobalSlot:X"
        if (meta.lore() == null) return;
        int globalSlot = -1;
        for (net.kyori.adventure.text.Component loreLine : meta.lore()) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(loreLine);
            if (plain.startsWith("GlobalSlot:")) {
                try {
                    globalSlot = Integer.parseInt(plain.substring("GlobalSlot:".length()).trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        if (globalSlot < 0) return;

        pending.remove(uuid);
        player.closeInventory();

        // AUDIT FIX: Folia-safe scheduler call
        final int finalGlobalSlot = globalSlot;
        if (com.petplugin.util.FoliaUtil.IS_FOLIA) {
            player.getScheduler().run(plugin, task ->
                    session.applySkill(player, finalGlobalSlot), null);
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    session.applySkill(player, finalGlobalSlot));
        }
    }
}
