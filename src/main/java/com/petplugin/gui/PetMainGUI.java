package com.petplugin.gui;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.data.PlayerData;
import com.petplugin.pet.PetEntity;
import com.petplugin.pet.FloatPet;
import com.petplugin.pet.GroundPet;
import com.petplugin.pet.PetType;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.GuiUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main interaction GUI opened by /pet (when pet exists) or Shift+click on pet.
 *
 * Layout (27 slots — 3 rows):
 *  Slot  2  — Skill Tree
 *  Slot  4  — Rename
 *  Slot  6  — Quests
 *  Slot  8  — Mystery Egg (always visible)
 *  Slot 11  — Pet Selector (only if petSlots > 1)
 *  Slot 13  — Show/Hide pet toggle
 */
public class PetMainGUI implements Listener {

    private static final String TITLE_COLOR = "§8✦ Pet Menu ✦";
    private static final String MYSTERY_EGG_COST = "1000"; // placeholder currency cost
    private static final Random RNG = new Random();

    private final PetPlugin plugin;

    // Players waiting to type a new name
    // AUDIT FIX: ConcurrentHashMap.newKeySet() for thread safety (async chat)
    private final Set<UUID> awaitingName = ConcurrentHashMap.newKeySet();

    public PetMainGUI(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        PlayerData pd = plugin.getDataManager().loadPlayer(player.getUniqueId());
        PetData pet = plugin.getDataManager().getActivePet(player.getUniqueId());

        // Null checks — do not open if player data is unavailable
        if (pd == null) {
            player.sendMessage(ChatUtil.color("&cLỗi: không đọc được dữ liệu người chơi."));
            return;
        }
        if (pet == null) {
            player.sendMessage(ChatUtil.color("&cBạn chưa có pet!"));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, ChatUtil.color(TITLE_COLOR));
        for (int i = 0; i < 27; i++) inv.setItem(i, GuiUtil.filler());

        // Slot 2 — Skills
        inv.setItem(2, GuiUtil.buildGlowItem(Material.BOOK,
                "&c🗡 Chiêu Thức",
                "&7Xem và unlock kỹ năng pet",
                "&7Cấp hiện tại: &e" + pet.getLevel(),
                "&7ATK pts: &c" + pet.getAtkPoints()
                        + "  DEF pts: &a" + pet.getDefPoints()
                        + "  HEAL pts: &b" + pet.getHealPoints(),
                "",
                "&eClick để mở Skill Tree!"));

        // Slot 4 — Rename
        inv.setItem(4, GuiUtil.buildGlowItem(Material.NAME_TAG,
                "&b✏ Đổi Tên",
                "&7Đổi tên pet của bạn",
                "&7Hỗ trợ mã màu &",
                "",
                "&eTên hiện tại: &f" + pet.getName(),
                "",
                "&eClick để đổi tên!"));

        // Slot 6 — Quests
        inv.setItem(6, GuiUtil.buildGlowItem(Material.WRITABLE_BOOK,
                "&e📋 Nhiệm Vụ",
                "&7Xem nhiệm vụ hàng ngày và tuần",
                "",
                "&eClick để mở Quest!"));

        // Slot 8 — Mystery Egg (always visible)
        boolean usedFree = pd.hasUsedFreeMysteryEgg();
        List<PetData> ownedPets = plugin.getDataManager().loadPetsForPlayer(player.getUniqueId());
        int slots = pd.getPetSlots();
        boolean hasSlot = ownedPets.size() < slots;

        if (!usedFree) {
            inv.setItem(8, GuiUtil.buildGlowItem(Material.DRAGON_EGG,
                    "&d🥚 Mystery Egg &6[MIỄN PHÍ]",
                    "&7Nở ra một pet ngẫu nhiên!",
                    "&7Loại: Turtle / Wolf / Cat + chỉ số random",
                    "",
                    hasSlot ? "&aClick để ấp trứng!" : "&cKhông đủ slot pet! Nâng cấp trước."));
        } else {
            inv.setItem(8, GuiUtil.buildItem(Material.DRAGON_EGG,
                    "&d🥚 Mystery Egg",
                    "&7Nở ra một pet ngẫu nhiên!",
                    "&7Loại: Turtle / Wolf / Cat + chỉ số random",
                    "",
                    "&6Giá: &e" + MYSTERY_EGG_COST + " coin",
                    hasSlot ? "&eClick để mua và ấp!" : "&cKhông đủ slot pet! Nâng cấp trước."));
        }

        // Slot 11 — Pet Selector (only if petSlots > 1)
        if (pd.getPetSlots() > 1) {
            inv.setItem(11, GuiUtil.buildGlowItem(Material.ENDER_PEARL,
                    "&b⇄ Chọn Pet",
                    "&7Chuyển đổi giữa các pet của bạn",
                    "&7Slots: &e" + ownedPets.size() + "/" + pd.getPetSlots(),
                    "",
                    "&eClick để chọn pet!"));
        }

        // Slot 13 — Show/Hide toggle (Task 8)
        boolean isVisible = pet.isVisible();
        inv.setItem(13, GuiUtil.buildGlowItem(
                isVisible ? Material.ENDER_EYE : Material.ENDER_PEARL,
                isVisible ? "&aẨn Pet" : "&aHiện Pet",
                isVisible ? "&7Pet đang hiển thị. Click để ẩn." : "&7Pet đang bị ẩn. Click để hiện.",
                "",
                "&eClick để chuyển đổi!"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(ChatUtil.color(TITLE_COLOR))) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        switch (slot) {
            case 2 -> { // Skills
                player.closeInventory();
                PetData pet = plugin.getDataManager().getActivePet(player.getUniqueId());
                if (pet != null) plugin.getSkillTreeGUI().open(player, pet);
            }
            case 4 -> { // Rename
                player.closeInventory();
                awaitingName.add(player.getUniqueId());
                player.sendMessage(ChatUtil.color("&aNhập tên mới cho pet (hỗ trợ &code màu):"));
            }
            case 6 -> { // Quest
                player.closeInventory();
                PetData pet = plugin.getDataManager().getActivePet(player.getUniqueId());
                if (pet != null) plugin.getQuestGUI().open(player, pet);
            }
            case 8 -> handleMysteryEgg(player); // Mystery Egg
            case 11 -> { // Pet Selector
                PlayerData pd = plugin.getDataManager().loadPlayer(player.getUniqueId());
                if (pd != null && pd.getPetSlots() > 1) {
                    player.closeInventory();
                    plugin.getPetSelectorGUI().open(player);
                }
            }
            case 13 -> handleShowHide(player, event.getView().getTopInventory()); // Show/Hide
        }
    }

    // ------------------------------------------------------------------ //
    //  Show/Hide toggle (Task 8)
    // ------------------------------------------------------------------ //

    private void handleShowHide(Player player, Inventory inv) {
        PetData pet = plugin.getDataManager().getActivePet(player.getUniqueId());
        if (pet == null) return;

        boolean nowVisible = !pet.isVisible();
        pet.setVisible(nowVisible);
        plugin.getDataManager().savePet(pet);

        if (nowVisible) {
            plugin.getPetManager().summon(player);
            player.sendMessage(ChatUtil.color("&a✦ Pet đã được hiện!"));
        } else {
            plugin.getPetManager().recall(player);
            player.sendMessage(ChatUtil.color("&7✦ Pet đã được ẩn."));
        }

        // Update slot in-place without closing GUI
        inv.setItem(13, GuiUtil.buildGlowItem(
                nowVisible ? Material.ENDER_EYE : Material.ENDER_PEARL,
                nowVisible ? "&aẨn Pet" : "&aHiện Pet",
                nowVisible ? "&7Pet đang hiển thị. Click để ẩn." : "&7Pet đang bị ẩn. Click để hiện.",
                "",
                "&eClick để chuyển đổi!"));
    }

    // ------------------------------------------------------------------ //
    //  Mystery Egg logic
    // ------------------------------------------------------------------ //

    private void handleMysteryEgg(Player player) {
        PlayerData pd = plugin.getDataManager().loadPlayer(player.getUniqueId());
        if (pd == null) return;

        List<PetData> owned = plugin.getDataManager().loadPetsForPlayer(player.getUniqueId());
        if (owned.size() >= pd.getPetSlots()) {
            player.sendMessage(ChatUtil.color("&cKhông đủ slot! Nâng cấp slot pet trước."));
            return;
        }

        boolean usedFree = pd.hasUsedFreeMysteryEgg();
        if (usedFree) {
            // TODO: check currency and debit. For now, just inform.
            player.sendMessage(ChatUtil.color(
                    "&cBạn đã dùng Mystery Egg miễn phí. Cần &e" + MYSTERY_EGG_COST + " coin."));
            // Currency check placeholder: return until economy is hooked up.
            return;
        }

        // Grant free egg
        pd.setUsedFreeMysteryEgg(true);
        plugin.getDataManager().savePlayer(pd);

        player.closeInventory();
        rollMysteryPet(player);
    }

    private void rollMysteryPet(Player player) {
        PetType[] types = PetType.values();
        PetType chosen = types[RNG.nextInt(types.length)];

        // Randomize base stats: ±20% on HP, ATK, DEF within type minimums
        String name = capitalise(chosen.name().toLowerCase()) + " Ngẫu Nhiên";
        PetData newPet = new PetData(player.getUniqueId(), chosen, name);

        // Randomized level between 1-3 for variety
        int startLevel = 1 + RNG.nextInt(3);
        for (int i = 1; i < startLevel; i++) newPet.addExp(newPet.requiredExpForNextLevel());

        plugin.getDataManager().savePet(newPet);
        player.sendMessage(ChatUtil.color(
                "&6✦ Mystery Egg nở! &fBạn nhận được &e" + chosen.getDisplayName()
                        + " &fcấp " + newPet.getLevel() + "!" ));
        player.sendMessage(ChatUtil.color("&7Dùng &e/pet &7để xem pet mới trong Pet Selector."));
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ //
    //  Rename flow
    // ------------------------------------------------------------------ //

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingName.contains(player.getUniqueId())) return;

        event.setCancelled(true);
        awaitingName.remove(player.getUniqueId());

        // Apply & colour codes (legacy translate)
        String raw = event.getMessage().trim();
        if (raw.isEmpty()) {
            player.sendMessage(ChatUtil.color("&cĐổi tên đã bị hủy (nhập trống)."));
            return;
        }
        String newName = ChatColor.translateAlternateColorCodes('&', raw);
        // Strip colour codes for length check
        String stripped = ChatColor.stripColor(newName);
        if (stripped == null || stripped.isEmpty()) {
            player.sendMessage(ChatUtil.color("&cTên không hợp lệ."));
            return;
        }
        if (stripped.length() > 32) {
            player.sendMessage(ChatUtil.color("&cTên quá dài! Tối đa 32 ký tự."));
            return;
        }

        Runnable renameTask = () -> {
            PetData pet = plugin.getDataManager().getActivePet(player.getUniqueId());
            if (pet == null) return;
            pet.setName(newName);
            plugin.getDataManager().savePet(pet);

            // Update live entity name immediately
            var petEntity = plugin.getPetManager().getActivePet(player.getUniqueId());
            if (petEntity != null) petEntity.updateDisplayName(newName);

            player.sendMessage(ChatUtil.color("&aTên pet đã được đổi thành: &f" + newName));
        };
        // AUDIT FIX: Folia-safe scheduler call
        if (com.petplugin.util.FoliaUtil.IS_FOLIA) {
            player.getScheduler().run(plugin, task -> renameTask.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, renameTask);
        }
    }

    public boolean isAwaitingName(UUID uuid) {
        return awaitingName.contains(uuid);
    }

    /** AUDIT FIX: cleanup on PlayerQuitEvent. */
    public void cleanupPlayer(UUID uuid) {
        awaitingName.remove(uuid);
    }
}
