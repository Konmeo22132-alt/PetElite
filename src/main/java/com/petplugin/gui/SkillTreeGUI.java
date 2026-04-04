package com.petplugin.gui;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.skill.BranchType;
import com.petplugin.skill.Skill;
import com.petplugin.skill.SkillBranch;
import com.petplugin.skill.SkillTree;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.GuiUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/**
 * 6-row (54 slot) chest GUI showing the skill tree.
 *
 * Layout (slot indices):
 *   Row 0 (0-8):   Header / labels
 *   Row 1 (9-17):  ATK skill 1, DEF skill 1, HEAL skill 1
 *   Row 2 (18-26): ATK skill 2, DEF skill 2, HEAL skill 2
 *   ...
 *   Each branch in columns: ATK=col1(slot+1), DEF=col4(slot+4), HEAL=col7(slot+7)
 *
 * Simplified column mapping:
 *   ATK  branch: column 1 → row offset 1,2,3,4,5 → slots 10,19,28,37,46
 *   DEF  branch: column 4 → slots 13,22,31,40,49
 *   HEAL branch: column 7 → slots 16,25,34,43,52
 */
public class SkillTreeGUI implements Listener {

    private static final String TITLE = "✦ Skill Tree ✦";
    private final PetPlugin plugin;

    // Column slots per branch (5 rows)
    private static final int[] ATK_SLOTS  = {10, 19, 28, 37, 46};
    private static final int[] DEF_SLOTS  = {13, 22, 31, 40, 49};
    private static final int[] HEAL_SLOTS = {16, 25, 34, 43, 52};

    public SkillTreeGUI(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, PetData pet) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatUtil.color("&8" + TITLE));

        // Fill all with gray glass
        for (int i = 0; i < 54; i++) inv.setItem(i, GuiUtil.filler());

        // Column headers
        inv.setItem(1,  GuiUtil.buildGlowItem(Material.RED_STAINED_GLASS_PANE,   "&c⚔ ATK Branch",
                "&7ATK point: &c" + pet.getAtkPoints(), "&7Mỗi skill tốn ATK pt"));
        inv.setItem(4,  GuiUtil.buildGlowItem(Material.GREEN_STAINED_GLASS_PANE, "&a🛡 DEF Branch",
                "&7DEF point: &a" + pet.getDefPoints(), "&7Mỗi skill tốn DEF pt"));
        inv.setItem(7,  GuiUtil.buildGlowItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&b❤ HEAL Branch",
                "&7HEAL point: &b" + pet.getHealPoints(), "&7Mỗi skill tốn HEAL pt"));

        // Info
        inv.setItem(0, GuiUtil.buildItem(Material.PAPER, "&ePet: &f" + pet.getName(),
                "&7Level: &e" + pet.getLevel(),
                "&7ATK pt: &c" + pet.getAtkPoints(),
                "&7DEF pt: &a" + pet.getDefPoints(),
                "&7HEAL pt: &b" + pet.getHealPoints()));

        // Build skill slots
        buildBranchColumn(inv, pet, BranchType.ATK_BRANCH,  ATK_SLOTS,
                Material.RED_STAINED_GLASS,    Material.RED_WOOL,    Material.GOLD_NUGGET);
        buildBranchColumn(inv, pet, BranchType.DEF_BRANCH,  DEF_SLOTS,
                Material.GREEN_STAINED_GLASS,  Material.GREEN_WOOL,  Material.IRON_NUGGET);
        buildBranchColumn(inv, pet, BranchType.HEAL_BRANCH, HEAL_SLOTS,
                Material.CYAN_STAINED_GLASS,   Material.CYAN_WOOL,   Material.EMERALD);

        player.openInventory(inv);
    }

    private void buildBranchColumn(Inventory inv, PetData pet, BranchType branch,
                                    int[] slots, Material locked, Material affordable, Material unlocked) {
        SkillBranch skillBranch = SkillTree.getBranch(pet.getType(), branch);

        for (int i = 0; i < 5; i++) {
            Skill skill = skillBranch.getSkill(i);
            boolean isUnlocked = pet.isSkillUnlocked(branch, i);
            boolean canAfford = canAfford(pet, branch, i);
            boolean prereqMet = i == 0 || pet.isSkillUnlocked(branch, i - 1);
            int cost = i + 1;
            int pp   = 10 - i * 2;

            if (isUnlocked) {
                inv.setItem(slots[i], GuiUtil.buildGlowItem(unlocked,
                        "&a✔ " + skill.getName(),
                        "&7" + skill.getDescription(),
                        "",
                        "&7PP: &a" + pp + " &7| Chi phí: &a" + cost + " pt",
                        "&aĐã unlock!"));
            } else if (!prereqMet) {
                inv.setItem(slots[i], GuiUtil.buildItem(locked,
                        "&8🔒 " + skill.getName(),
                        "&7" + skill.getDescription(),
                        "",
                        "&cCần unlock skill trước.",
                        "&7Chi phí: &7" + cost + " pt"));
            } else if (canAfford) {
                inv.setItem(slots[i], GuiUtil.buildGlowItem(affordable,
                        "&e" + skill.getName(),
                        "&7" + skill.getDescription(),
                        "",
                        "&7PP: &a" + pp,
                        "&eClick để unlock! Chi phí: &c" + cost + " pt"));
            } else {
                inv.setItem(slots[i], GuiUtil.buildItem(locked,
                        "&c" + skill.getName(),
                        "&7" + skill.getDescription(),
                        "",
                        "&cKhông đủ điểm. Chi phí: &c" + cost + " pt"));
            }
        }
    }

    private boolean canAfford(PetData pet, BranchType branch, int slotIndex) {
        int cost = slotIndex + 1;
        return switch (branch) {
            case ATK_BRANCH  -> pet.getAtkPoints() >= cost;
            case DEF_BRANCH  -> pet.getDefPoints() >= cost;
            case HEAL_BRANCH -> pet.getHealPoints() >= cost;
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(ChatUtil.color("&8" + TITLE))) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();

        PetData pet = plugin.getDataManager().getActivePet(player.getUniqueId());
        if (pet == null) return;

        // Determine branch and slot index from click
        BranchType branch = null;
        int slotIndex = -1;

        for (int i = 0; i < 5; i++) {
            if (slot == ATK_SLOTS[i])  { branch = BranchType.ATK_BRANCH;  slotIndex = i; break; }
            if (slot == DEF_SLOTS[i])  { branch = BranchType.DEF_BRANCH;  slotIndex = i; break; }
            if (slot == HEAL_SLOTS[i]) { branch = BranchType.HEAL_BRANCH; slotIndex = i; break; }
        }

        if (branch == null || slotIndex < 0) return;

        boolean success = pet.unlockSkill(branch, slotIndex);
        if (success) {
            plugin.getDataManager().savePet(pet);
            Skill skill = SkillTree.getSkill(pet.getType(), branch, slotIndex);
            player.sendMessage(ChatUtil.color("&a✔ Đã unlock skill: &f"
                    + (skill != null ? skill.getName() : "?")));
            // Refresh GUI
            open(player, pet);
        } else {
            player.sendMessage(ChatUtil.color("&cKhông thể unlock skill này (không đủ điểm hoặc chưa đủ điều kiện)."));
        }
    }
}
