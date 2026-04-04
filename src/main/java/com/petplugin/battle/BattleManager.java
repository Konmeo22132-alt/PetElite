package com.petplugin.battle;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.skill.BranchType;
import com.petplugin.skill.Skill;
import com.petplugin.skill.SkillTree;
import com.petplugin.util.ChatUtil;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Central registry for all challenges and active battle sessions.
 */
public class BattleManager {

    private final PetPlugin plugin;

    // challenger UUID -> challenged UUID (pending challenges)
    private final Map<UUID, UUID> pendingChallenges = new HashMap<>();

    // player UUID -> their active session
    private final Map<UUID, BattleSession> activeSessions = new HashMap<>();

    public BattleManager(PetPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ //
    //  Challenge flow
    // ------------------------------------------------------------------ //

    public void challenge(Player challenger, Player target) {
        if (getSession(challenger.getUniqueId()) != null) {
            challenger.sendMessage(ChatUtil.color("&cBạn đang trong trận đấu!"));
            return;
        }
        if (getSession(target.getUniqueId()) != null) {
            challenger.sendMessage(ChatUtil.color("&c" + target.getName() + " đang trong trận đấu khác!"));
            return;
        }
        if (plugin.getDataManager().getActivePet(challenger.getUniqueId()) == null) {
            challenger.sendMessage(ChatUtil.color("&cBạn chưa có pet!"));
            return;
        }
        if (plugin.getDataManager().getActivePet(target.getUniqueId()) == null) {
            challenger.sendMessage(ChatUtil.color("&c" + target.getName() + " chưa có pet!"));
            return;
        }

        pendingChallenges.put(challenger.getUniqueId(), target.getUniqueId());
        challenger.sendMessage(ChatUtil.color("&aĐã gửi lời thách đấu đến &f" + target.getName() + "&a!"));
        target.sendMessage(ChatUtil.color("&e" + challenger.getName()
                + " &athách đấu bạn! Dùng &f/petbattle accept &aĐể chấp nhận."));
    }

    public void accept(Player accepter) {
        // Find who challenged accepter
        UUID challengerUuid = null;
        for (Map.Entry<UUID, UUID> entry : pendingChallenges.entrySet()) {
            if (entry.getValue().equals(accepter.getUniqueId())) {
                challengerUuid = entry.getKey();
                break;
            }
        }

        if (challengerUuid == null) {
            accepter.sendMessage(ChatUtil.color("&cKhông có lời thách đấu nào."));
            return;
        }

        Player challenger = plugin.getServer().getPlayer(challengerUuid);
        if (challenger == null || !challenger.isOnline()) {
            pendingChallenges.remove(challengerUuid);
            accepter.sendMessage(ChatUtil.color("&cNgười thách đấu đã offline."));
            return;
        }

        pendingChallenges.remove(challengerUuid);

        PetData petA = plugin.getDataManager().getActivePet(challenger.getUniqueId());
        PetData petB = plugin.getDataManager().getActivePet(accepter.getUniqueId());

        if (petA == null || petB == null) {
            accepter.sendMessage(ChatUtil.color("&cMột trong hai người chưa có pet!"));
            return;
        }

        BattleSession session = new BattleSession(plugin, challenger, accepter, petA, petB);
        activeSessions.put(challenger.getUniqueId(), session);
        activeSessions.put(accepter.getUniqueId(), session);
        session.start();
    }

    public void surrender(Player player) {
        BattleSession session = getSession(player.getUniqueId());
        if (session == null || !session.isActive()) {
            player.sendMessage(ChatUtil.color("&cBạn không trong trận đấu nào."));
            return;
        }
        player.sendMessage(ChatUtil.color("&cBạn đầu hàng!"));
        session.forfeit(player.getUniqueId());
    }

    // ------------------------------------------------------------------ //
    //  Session management
    // ------------------------------------------------------------------ //

    public BattleSession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    public void endSession(UUID uuid) {
        BattleSession session = activeSessions.remove(uuid);
        if (session != null) {
            plugin.getTurnManager().cancelAll(session);
        }
    }

    public void endAllSessions() {
        Set<UUID> keys = new HashSet<>(activeSessions.keySet());
        for (UUID key : keys) {
            BattleSession s = activeSessions.get(key);
            if (s != null && s.isActive()) {
                s.forfeit(key);
            }
        }
        activeSessions.clear();
    }

    /**
     * Resolve a Skill object from a pet's tree by global slot index (0-14).
     * Used by BattleSession to find which skill to execute.
     */
    public Skill resolveSkill(PetData pet, int globalSlot) {
        if (globalSlot < 0 || globalSlot >= 15) return null;
        BranchType branch = BookSkillHandler.globalSlotToBranch(globalSlot);
        int slotIndex = BookSkillHandler.globalSlotToSlotIndex(globalSlot);
        if (!pet.isSkillUnlocked(branch, slotIndex)) return null;
        return SkillTree.getSkill(pet.getType(), branch, slotIndex);
    }
}
