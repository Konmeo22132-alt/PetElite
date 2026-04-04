package com.petplugin.listener;

import com.petplugin.PetPlugin;
import com.petplugin.battle.ArenaManager;
import com.petplugin.battle.BattleSession;
import com.petplugin.battle.BookSkillHandler;
import com.petplugin.util.ChatUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Handles all battle-related events:
 * - Book page click → skill use
 * - Player move → freeze enforcement
 * - Player quit → forfeit active battle
 */
public class BattleListener implements Listener {

    private final PetPlugin plugin;

    public BattleListener(PetPlugin plugin) {
        this.plugin = plugin;
    }

    /** Handle WRITTEN_BOOK right-click → parse page and apply skill. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBookInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (player.getInventory().getItemInMainHand().getType() != Material.WRITTEN_BOOK) return;

        BattleSession session = plugin.getBattleManager().getSession(player.getUniqueId());
        if (session == null || !session.isActive()) return;

        // Prevent the book from opening
        event.setCancelled(true);

        // We cannot directly read which page was clicked from this event alone.
        // We use PlayerEditBookEvent or a workaround: the player opens the book and we
        // intercept InventoryClickEvent on the book GUI — however for WRITTEN_BOOK
        // Paper provides no "page turn" event directly.
        //
        // Workaround: Player types /petbattle use <slot> (0-14) to select their skill.
        // The book serves as a reference card. This is noted in Known Issues.
        //
        // Alternative: We detect click action and open a 9-slot hotbar GUI for skill selection.
        openSkillSelectMenu(player, session);
    }

    /** Hotbar GUI (9 slots) as skill selector — simpler than book page detection. */
    private void openSkillSelectMenu(Player player, BattleSession session) {
        com.petplugin.data.PetData pet =
                (session.getPlayerA() == player) ? session.getPetA() : session.getPetB();
        java.util.Map<Integer, Integer> ppMap =
                (session.getPlayerA() == player) ? session.getPpA() : session.getPpB();

        org.bukkit.inventory.Inventory inv =
                org.bukkit.Bukkit.createInventory(null, 27,
                        ChatUtil.color("&8⚔ Chọn Skill (click để dùng)"));

        com.petplugin.util.GuiUtil filler = null; // unused, use static method
        for (int i = 0; i < 27; i++) inv.setItem(i, com.petplugin.util.GuiUtil.filler());

        com.petplugin.skill.BranchType[] branches = com.petplugin.skill.BranchType.values();
        // Slot layout: row 0 = ATK, row 1 = DEF, row 2 = HEAL, cols 0-4 = skill 1-5
        int[] rowStarts = {0, 9, 18};

        for (int b = 0; b < branches.length; b++) {
            for (int s = 0; s < 5; s++) {
                int globalSlot = b * 5 + s;
                int invSlot    = rowStarts[b] + s;
                com.petplugin.skill.Skill skill =
                        com.petplugin.skill.SkillTree.getSkill(pet.getType(), branches[b], s);
                boolean unlocked = pet.isSkillUnlocked(branches[b], s);
                int pp = ppMap.getOrDefault(globalSlot, 0);

                if (!unlocked) {
                    inv.setItem(invSlot, com.petplugin.util.GuiUtil.buildItem(
                            org.bukkit.Material.GRAY_STAINED_GLASS_PANE,
                            "&8[Khóa] " + branches[b].getDisplayName() + " " + (s + 1)));
                } else if (skill == null || pp <= 0) {
                    inv.setItem(invSlot, com.petplugin.util.GuiUtil.buildItem(
                            org.bukkit.Material.RED_STAINED_GLASS_PANE,
                            "&c" + (skill != null ? skill.getName() : "?"),
                            "&cHết PP!"));
                } else {
                    inv.setItem(invSlot, com.petplugin.util.GuiUtil.buildGlowItem(
                            org.bukkit.Material.ENCHANTED_BOOK,
                            branches[b].getDisplayName() + " &f" + skill.getName(),
                            "&7" + skill.getDescription(),
                            "&aClick để dùng!",
                            "PP: &a" + pp + "/" + skill.getMaxPP(),
                            "&8GlobalSlot:" + globalSlot)); // encoded for retrieval
                }
            }
        }

        // Register a one-shot InventoryClickEvent for this player
        player.openInventory(inv);
        // Register per-open listener via custom holder pattern
        plugin.getBattleSkillClickHandler().register(player.getUniqueId(), session);
    }

    /** Enforce player freeze during battle. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!ArenaManager.isFrozen(player.getUniqueId())) return;

        // Allow looking around (head rotation only)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        event.setCancelled(true);
    }

    /** Auto-forfeit on disconnect. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BattleSession session = plugin.getBattleManager().getSession(player.getUniqueId());
        if (session != null && session.isActive()) {
            session.forfeit(player.getUniqueId());
        }
    }

    /** Lockdown commands during battle. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!ArenaManager.isFrozen(player.getUniqueId())) return;
        
        String msg = event.getMessage().toLowerCase();
        if (!msg.startsWith("/petbattle surrender") && !msg.startsWith("/petbattle forfeit")) {
            event.setCancelled(true);
            player.sendMessage(ChatUtil.color("&cBạn không thể dùng lệnh trong trân đấu ngoại trừ /petbattle surrender."));
        }
    }

    /** Prevent opening chests/crafting during battle. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!ArenaManager.isFrozen(player.getUniqueId())) return;
        
        // Allow ONLY our own custom menus. Our custom menus have titles or are CHEST type without a block hook.
        // Easiest hook: ArenaManager.isFrozen + they try to open something. 
        // Battle GUI itself is handled via player.openInventory but wait, that triggers InventoryOpenEvent!
        // We can check if the title starts with "⚔ Chọn Skill"
        String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.contains("Chọn Skill")) {
            event.setCancelled(true);
        }
    }
}
