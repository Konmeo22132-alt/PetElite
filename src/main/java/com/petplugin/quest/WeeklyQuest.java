package com.petplugin.quest;

public class WeeklyQuest extends Quest {

    public WeeklyQuest(QuestType type) {
        super(type);
        if (!type.isWeekly()) throw new IllegalArgumentException(type + " is not a weekly quest");
    }

    /** All weekly quest instances. */
    public static final WeeklyQuest KILL_BOSS    = new WeeklyQuest(QuestType.KILL_BOSS);
    public static final WeeklyQuest WIN_BATTLES  = new WeeklyQuest(QuestType.WIN_BATTLES);
    public static final WeeklyQuest LONG_JOURNEY = new WeeklyQuest(QuestType.LONG_JOURNEY);
    public static final WeeklyQuest CRAFT_RARE   = new WeeklyQuest(QuestType.CRAFT_RARE);
    public static final WeeklyQuest JOIN_EVENTS  = new WeeklyQuest(QuestType.JOIN_EVENTS);

    public static final WeeklyQuest[] ALL = {
        KILL_BOSS, WIN_BATTLES, LONG_JOURNEY, CRAFT_RARE, JOIN_EVENTS
    };
}
