package com.petplugin.battle;

import com.petplugin.data.PlayerData;
import com.petplugin.PetPlugin;
import com.petplugin.util.ChatUtil;
import org.bukkit.entity.Player;

/**
 * Standard K=32 Elo rating system.
 * Calculates new ELO for both players and updates their PlayerData.
 */
public class EloManager {

    private static final int K = 32;

    private final PetPlugin plugin;

    public EloManager(PetPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Update ELO for both players. Call after a battle ends.
     *
     * @param winner the winning player
     * @param loser  the losing player
     * @param surrender true if loser surrendered (only half K applied)
     */
    public void calculate(Player winner, Player loser, boolean surrender) {
        PlayerData winnerData = plugin.getDataManager().loadPlayer(winner.getUniqueId());
        PlayerData loserData  = plugin.getDataManager().loadPlayer(loser.getUniqueId());

        int winnerElo = winnerData.getElo();
        int loserElo  = loserData.getElo();

        double winnerExpected = expectedScore(winnerElo, loserElo);
        double loserExpected  = 1.0 - winnerExpected;

        int effectiveK = surrender ? K / 2 : K;

        int winnerDelta = (int)(effectiveK * (1.0 - winnerExpected));
        int loserDelta  = (int)(effectiveK * (0.0 - loserExpected));

        winnerData.setElo(winnerElo + winnerDelta);
        loserData.setElo(Math.max(0, loserElo + loserDelta));

        boolean winnerRankedUp = winnerData.updateRank();
        loserData.updateRank();

        plugin.getDataManager().savePlayer(winnerData);
        plugin.getDataManager().savePlayer(loserData);

        // Inform players
        winner.sendMessage(ChatUtil.color(
                "&6[ELO] &a+" + winnerDelta + " ELO &7(" + winnerData.getElo() + ") "
                + "&7Rank: " + winnerData.getRank().getDisplayName()));
        loser.sendMessage(ChatUtil.color(
                "&6[ELO] &c" + loserDelta + " ELO &7(" + loserData.getElo() + ") "
                + "&7Rank: " + loserData.getRank().getDisplayName()));

        if (winnerRankedUp) {
            winner.sendMessage(ChatUtil.color(
                    "&6[ELO] &d★ Bạn đã lên hạng &f" + winnerData.getRank().getDisplayName() + "&d! ★"));
            grantRankReward(winner, winnerData);
        }
    }

    private void grantRankReward(Player player, PlayerData data) {
        switch (data.getRank()) {
            case COPPER   -> data.setPetSlots(data.getPetSlots() + 1); // first real promotion
            case DIAMOND  -> data.setPetSlots(data.getPetSlots() + 1);
            // Other rewards (items, skins) — placeholder
            default -> {}
        }
        plugin.getDataManager().savePlayer(data);
        player.sendMessage(ChatUtil.color("&6[ELO] &eBạn nhận được phần thưởng hạng mới!"));
    }

    private double expectedScore(int playerElo, int opponentElo) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentElo - playerElo) / 400.0));
    }
}
