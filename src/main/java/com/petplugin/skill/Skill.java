package com.petplugin.skill;

import com.petplugin.battle.BattleSession;
import com.petplugin.data.PetData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Base class for all pet skills.
 */
public abstract class Skill {

    private final String id;
    private final String name;
    private final String description;
    private final BranchType branch;
    private final int slotIndex;         // 0-4
    private final int cooldownTicks;     // passive cooldown

    public Skill(String id, String name, String description,
                 BranchType branch, int slotIndex, int cooldownTicks) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.branch = branch;
        this.slotIndex = slotIndex;
        this.cooldownTicks = cooldownTicks;
    }

    /** Unlock cost in branch points: slotIndex + 1 */
    public int getUnlockCost() { return slotIndex + 1; }

    /** Max PP this skill has: 10 - slotIndex * 2 */
    public int getMaxPP() { return 10 - slotIndex * 2; }

    /**
     * Execute this skill in battle context.
     * @param attacker  the attacking player
     * @param target    the defending player
     * @param session   current battle session
     * @param petAtk    attacker's effective ATK stat
     * @param petDef    defender's effective DEF stat
     */
    public abstract void executeInBattle(Player attacker, Player target,
                                         BattleSession session,
                                         int petAtk, int petDef);

    /**
     * Passive trigger outside battle.
     * @param owner     pet owner
     * @param target    entity being attacked (or null)
     * @param petData   owner's pet data
     * @return true if the skill fired
     */
    public boolean triggerPassive(Player owner, LivingEntity target, PetData petData) {
        return false; // default: no passive
    }

    // ---- Getters ----

    public String getId()          { return id; }
    public String getName()        { return name; }
    public String getDescription() { return description; }
    public BranchType getBranch()  { return branch; }
    public int getSlotIndex()      { return slotIndex; }
    public int getCooldownTicks()  { return cooldownTicks; }
}
