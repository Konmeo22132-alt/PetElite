package com.petplugin.gui;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.data.PlayerData;
import com.petplugin.pet.PetType;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.GuiUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Main interaction GUI opened by /pet (when pet exists) or Shift+click on pet.
 *
 * Layout (18 slots — 2 rows):
 *  Slot  2  — Skill Tree
 *  Slot  4  — Rename
 *  Slot  6  — Quests
 *  Slot  8  — Mystery Egg (always visible)
 *  Slot 11  — Pet Selector (only if petSlots > 1)
 */
public class PetMainGUI implements Listener {

    private static final String TITLE_COLOR = "§8✦ Pet Menu ✦";
    private static final String MYSTERY_EGG_COST = "1000"; // placeholder currency cost
    private static final Random RNG = new Random();

    private final PetPlugin plugin;

    // Players waiting to type a new name
    private final Set<UUID> awaitingName = new HashSet<>();

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

        Inventory inv = Bukkit.createInventory(null, 18, ChatUtil.color(TITLE_COLOR));
        for (int i = 0; i < 18; i++) inv.setItem(i, GuiUtil.filler());

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
            // First use — FREE and glowing
            inv.setItem(8, GuiUtil.buildGlowItem(Material.DRAGON_EGG,
                    "&d🥚 Mystery Egg &6[MIỄN PHÍ]",
                    "&7Nở ra một pet ngẫu nhiên!",
                    "&7Loại: Turtle / Wolf / Cat + chỉ số random",
                    "",
                    hasSlot ? "&aClick để ấp trứng!" : "&cKhông đủ slot pet! Nâng cấp trước."));
        } else {
            // Already used — show cost
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

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(ChatUtil.color(TITLE_COLOR))) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 18) return;

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
        }
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

        String newName = ChatUtil.colorLegacy(event.getMessage().trim());
        if (newName.isEmpty() || newName.length() > 32) {
            player.sendMessage(ChatUtil.color("&cTên không hợp lệ (1-32 ký tự)."));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            PetData pet = plugin.getDataManager().getActivePet(player.getUniqueId());
            if (pet == null) return;
            pet.setName(newName);
            plugin.getDataManager().savePet(pet);
            player.sendMessage(ChatUtil.color("&aTên pet đã được đổi thành: &f" + newName));
        });
    }

    public boolean isAwaitingName(UUID uuid) {
        return awaitingName.contains(uuid);
    }
}
