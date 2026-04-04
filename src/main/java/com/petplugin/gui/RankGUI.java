package com.petplugin.gui;

import com.petplugin.PetPlugin;
import com.petplugin.data.PlayerData;
import com.petplugin.data.PlayerData.RankTier;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.GuiUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Displays a player's ELO rank, progress bar to next rank, and rank tiers.
 * Task 3: Each rank tier shows its own material. Current rank glows.
 */
public class RankGUI implements Listener {

    private static final String TITLE = "§5⚔ Rank & ELO";
    private final PetPlugin plugin;

    public RankGUI(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        PlayerData data = plugin.getDataManager().loadPlayer(player.getUniqueId());
        if (data == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, ChatUtil.color(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, GuiUtil.filler());

        int elo    = data.getElo();
        RankTier rank = data.getRank();
        RankTier next = nextRank(rank);

        int minElo = rank.getMinElo();
        int maxElo = (next != null) ? next.getMinElo() : rank.getMaxElo();
        String bar = GuiUtil.progressBar(elo - minElo, maxElo - minElo, 15);

        // Top banner — current rank
        inv.setItem(4, GuiUtil.buildGlowItem(rankMaterial(rank),
                rank.getDisplayName() + " &f(" + elo + " ELO)",
                bar,
                next != null
                        ? ("&7Đến " + next.getDisplayName() + ": &f" + (next.getMinElo() - elo) + " ELO nữa")
                        : "&6Rank cao nhất!",
                "",
                "&7Slot pet: &f" + data.getPetSlots()));

        // Rank tier list
        RankTier[] tiers = RankTier.values();
        int[] tierSlots = {19, 21, 23, 25, 27, 29};

        for (int i = 0; i < tiers.length && i < tierSlots.length; i++) {
            RankTier tier = tiers[i];
            boolean current  = (tier == rank);
            boolean achieved = (elo >= tier.getMinElo());
            Material mat = rankMaterial(tier);

            String nameStr = (current  ? "&e★ " : (achieved ? "&a✔ " : "&8")) + tier.getDisplayName();

            ItemStack item;
            if (current) {
                // Glow for current rank
                item = GuiUtil.buildGlowItem(mat, nameStr,
                        "&7ELO: &f" + tier.getMinElo() + " - "
                                + (tier.getMaxElo() == Integer.MAX_VALUE ? "∞" : tier.getMaxElo()),
                        "&e← Rank hiện tại");
            } else {
                item = GuiUtil.buildItem(mat,
                        achieved ? nameStr : "&8" + tier.getDisplayName(),
                        "&7ELO: &f" + tier.getMinElo() + " - "
                                + (tier.getMaxElo() == Integer.MAX_VALUE ? "∞" : tier.getMaxElo()),
                        achieved ? "&a✔ Đã đạt" : "&8Chưa đạt");
                // Grey out name if not yet reached (replace display name)
                if (!achieved) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.displayName(ChatUtil.color("&8" + tier.getDisplayName()));
                        item.setItemMeta(meta);
                    }
                }
            }

            inv.setItem(tierSlots[i], item);
        }

        player.openInventory(inv);
    }

    // ---- Helpers ----

    /** Returns the correct Material for each rank tier. */
    private Material rankMaterial(RankTier tier) {
        return switch (tier) {
            case COAL      -> Material.COAL;
            case COPPER    -> Material.COPPER_INGOT;
            case IRON      -> Material.IRON_INGOT;
            case GOLD      -> Material.GOLD_INGOT;
            case DIAMOND   -> Material.DIAMOND;
            case NETHERITE -> Material.NETHERITE_INGOT;
        };
    }

    private RankTier nextRank(RankTier current) {
        RankTier[] tiers = RankTier.values();
        for (int i = 0; i < tiers.length - 1; i++) {
            if (tiers[i] == current) return tiers[i + 1];
        }
        return null;
    }
}
