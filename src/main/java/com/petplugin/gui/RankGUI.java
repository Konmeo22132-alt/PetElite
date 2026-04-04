package com.petplugin.gui;

import com.petplugin.PetPlugin;
import com.petplugin.data.PlayerData;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.GuiUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;

/**
 * Displays a player's ELO rank, progress bar to next rank, and rank tiers.
 */
public class RankGUI implements Listener {

    private static final String TITLE = "&5⚔ Rank & ELO";
    private final PetPlugin plugin;

    public RankGUI(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        PlayerData data = plugin.getDataManager().loadPlayer(player.getUniqueId());
        Inventory inv = Bukkit.createInventory(null, 54, ChatUtil.color(TITLE));
        for (int i = 0; i < 54; i++) inv.setItem(i, GuiUtil.filler());

        // Current rank display
        int elo    = data.getElo();
        PlayerData.RankTier rank = data.getRank();
        PlayerData.RankTier next = nextRank(rank);

        int minElo  = rank.getMinElo();
        int maxElo  = (next != null) ? next.getMinElo() : rank.getMaxElo();
        String bar  = GuiUtil.progressBar(elo - minElo, maxElo - minElo, 15);

        inv.setItem(4, GuiUtil.buildGlowItem(Material.NETHER_STAR,
                rank.getDisplayName() + " &f(" + elo + " ELO)",
                bar,
                next != null ? ("&7Đến " + next.getDisplayName() + ": &f" + (next.getMinElo() - elo) + " ELO nữa") : "&6Rank cao nhất!",
                "",
                "&7Slot pet: &f" + data.getPetSlots()));

        // Rank tier list
        PlayerData.RankTier[] tiers = PlayerData.RankTier.values();
        int[] tierSlots = {19, 21, 23, 25, 27, 29};

        for (int i = 0; i < tiers.length && i < tierSlots.length; i++) {
            PlayerData.RankTier tier = tiers[i];
            boolean current = tier == rank;
            boolean achieved = elo >= tier.getMinElo();
            Material mat = achieved ? Material.GOLD_INGOT : Material.IRON_INGOT;
            String nameStr = (current ? "&e★ " : (achieved ? "&a✔ " : "&8")) + tier.getDisplayName();

            inv.setItem(tierSlots[i], GuiUtil.buildItem(mat,
                    nameStr,
                    "&7ELO: &f" + tier.getMinElo() + " - " + (tier.getMaxElo() == Integer.MAX_VALUE ? "∞" : tier.getMaxElo()),
                    current ? "&e← Rank hiện tại" : ""));
        }

        player.openInventory(inv);
    }

    private PlayerData.RankTier nextRank(PlayerData.RankTier current) {
        PlayerData.RankTier[] tiers = PlayerData.RankTier.values();
        for (int i = 0; i < tiers.length - 1; i++) {
            if (tiers[i] == current) return tiers[i + 1];
        }
        return null;
    }
}
