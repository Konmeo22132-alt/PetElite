package com.petplugin.battle;

import com.petplugin.data.PetData;
import com.petplugin.skill.BranchType;
import com.petplugin.skill.Skill;
import com.petplugin.skill.SkillTree;
import com.petplugin.util.ChatUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Builds the skill book given to players at battle start.
 * Book layout:
 *   Pages 1-5  → ATK branch skills (slot 0-4)
 *   Pages 6-10 → DEF branch skills (slot 0-4)
 *   Pages 11-15→ HEAL branch skills (slot 0-4)
 *
 * Page number → global slot: globalSlot = (pageIndex - 1)
 * Branch: globalSlot / 5 → 0=ATK, 1=DEF, 2=HEAL
 * SlotIndex: globalSlot % 5
 *
 * Clicking a page fires PlayerInteractEvent which BookSkillHandler processes.
 */
public class BookSkillHandler {

    private BookSkillHandler() {}

    /** Give a freshly generated skill book to the player. */
    public static void giveSkillBook(Player player, PetData pet, Map<Integer, Integer> ppMap) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        meta.setTitle("Skills");
        meta.setAuthor("PetPlugin");

        List<Component> pages = new ArrayList<>();

        BranchType[] branches = BranchType.values();
        for (int b = 0; b < branches.length; b++) {
            BranchType branch = branches[b];
            for (int slot = 0; slot < 5; slot++) {
                int globalSlot = b * 5 + slot;
                Skill skill = SkillTree.getSkill(pet.getType(), branch, slot);
                int pp = ppMap.getOrDefault(globalSlot, 0);
                boolean unlocked = pet.isSkillUnlocked(branch, slot);
                pages.add(buildPage(skill, pet, branch, slot, globalSlot, pp, unlocked));
            }
        }

        meta.pages(pages);
        book.setItemMeta(meta);

        // Place in slot 0 (hotbar)
        player.getInventory().setItem(0, book);
        player.updateInventory();
    }

    private static Component buildPage(Skill skill, PetData pet,
                                        BranchType branch, int slot,
                                        int globalSlot, int pp, boolean unlocked) {
        if (!unlocked) {
            return ChatUtil.color(
                    "&8[Khóa]\n"
                    + branch.getDisplayName() + "\n"
                    + "Skill " + (slot + 1) + "\n\n"
                    + "&7Chưa unlock.\n"
                    + "Chi phí: &e" + (slot + 1) + " pt");
        }

        if (skill == null) {
            return ChatUtil.color("&cSkill không tồn tại.");
        }

        String ppColor = pp > 0 ? "&a" : "&c";
        return ChatUtil.color(
                branch.getDisplayName() + "\n"
                + "&f" + skill.getName() + "\n\n"
                + "&7" + skill.getDescription() + "\n\n"
                + "PP: " + ppColor + pp + "&7/" + skill.getMaxPP() + "\n"
                + (pp <= 0 ? "&cHết PP!" : "&eClick để dùng")
                + "\n&8[Trang " + (globalSlot + 1) + "]");
    }

    /**
     * Translate a book page number (0-indexed) → global slot.
     * Return -1 if out of range.
     */
    public static int pageToGlobalSlot(int page) {
        if (page < 0 || page >= 15) return -1;
        return page;
    }

    /**
     * Translate global slot → (branch, slotIndex) pair.
     */
    public static BranchType globalSlotToBranch(int globalSlot) {
        return BranchType.values()[globalSlot / 5];
    }

    public static int globalSlotToSlotIndex(int globalSlot) {
        return globalSlot % 5;
    }
}
