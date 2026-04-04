package com.petplugin.quest;

public class DailyQuest extends Quest {

    public DailyQuest(QuestType type) {
        super(type);
        if (!type.isDaily()) throw new IllegalArgumentException(type + " is not a daily quest");
    }

    /** All daily quest instances. */
    public static final DailyQuest MINE_ORE     = new DailyQuest(QuestType.MINE_ORE);
    public static final DailyQuest WALK_BLOCKS  = new DailyQuest(QuestType.WALK_BLOCKS);
    public static final DailyQuest KILL_PLAYERS = new DailyQuest(QuestType.KILL_PLAYERS);

    public static final DailyQuest[] ALL = { MINE_ORE, WALK_BLOCKS, KILL_PLAYERS };
}
