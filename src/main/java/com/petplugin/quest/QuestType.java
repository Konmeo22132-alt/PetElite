package com.petplugin.quest;

/**
 * All quest types — daily and weekly.
 */
public enum QuestType {

    // Daily
    MINE_ORE    (true,  "Đào %d quặng",                   32,   200, 0, 0, 1),
    WALK_BLOCKS (true,  "Đi bộ %d block",                 500,  150, 0, 1, 0),
    KILL_PLAYERS(true,  "Giết %d người chơi",               3,   250, 1, 0, 0),

    // Weekly
    KILL_BOSS   (false, "Giết %d boss (Wither/Elder Guardian)", 2, 1000, 1, 1, 0),
    WIN_BATTLES (false, "Thắng %d trận battle",             5,  1500, 2, 0, 0),
    LONG_JOURNEY(false, "Đi bộ %d block trong tuần",     5000,   800, 0, 2, 0),
    CRAFT_RARE  (false, "Craft %d đồ hiếm",                10,  1200, 0, 0, 2),
    JOIN_EVENTS (false, "Tham gia %d sự kiện server",       3,   900, 0, 0, 2);

    private final boolean daily;
    private final String descriptionTemplate; // uses %d for target
    private final int target;
    private final int expReward;
    private final int atkPointReward;
    private final int defPointReward;
    private final int healPointReward;

    QuestType(boolean daily, String descriptionTemplate, int target,
              int expReward, int atkPointReward, int defPointReward, int healPointReward) {
        this.daily = daily;
        this.descriptionTemplate = descriptionTemplate;
        this.target = target;
        this.expReward = expReward;
        this.atkPointReward = atkPointReward;
        this.defPointReward = defPointReward;
        this.healPointReward = healPointReward;
    }

    public boolean isDaily()   { return daily; }
    public boolean isWeekly()  { return !daily; }

    public String getDescription() {
        return String.format(descriptionTemplate, target);
    }

    public int getTarget()          { return target; }
    public int getExpReward()       { return expReward; }
    public int getAtkPointReward()  { return atkPointReward; }
    public int getDefPointReward()  { return defPointReward; }
    public int getHealPointReward() { return healPointReward; }
}
