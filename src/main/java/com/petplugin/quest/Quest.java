package com.petplugin.quest;

/**
 * Represents a single quest definition (daily or weekly).
 */
public class Quest {

    private final QuestType type;
    private final int target;
    private final int expReward;
    private final int atkPointReward;
    private final int defPointReward;
    private final int healPointReward;

    public Quest(QuestType type) {
        this.type = type;
        this.target = type.getTarget();
        this.expReward = type.getExpReward();
        this.atkPointReward = type.getAtkPointReward();
        this.defPointReward = type.getDefPointReward();
        this.healPointReward = type.getHealPointReward();
    }

    public QuestType getType()      { return type; }
    public int getTarget()          { return target; }
    public int getExpReward()       { return expReward; }
    public int getAtkPointReward()  { return atkPointReward; }
    public int getDefPointReward()  { return defPointReward; }
    public int getHealPointReward() { return healPointReward; }

    public String getDescription()  { return type.getDescription(); }
    public String getId()           { return type.name(); }
    public boolean isDaily()        { return type.isDaily(); }
    public boolean isWeekly()       { return type.isWeekly(); }
}
