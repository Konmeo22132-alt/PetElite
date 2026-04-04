package com.petplugin.skill;

/**
 * The three skill branches. Each branch uses a different point type.
 */
public enum BranchType {

    ATK_BRANCH("&cTấn Công", "&7Tốn ATK point"),
    DEF_BRANCH("&aPòng Thủ", "&7Tốn DEF point"),
    HEAL_BRANCH("&bHồi Phục", "&7Tốn HEAL point");

    private final String displayName;
    private final String description;

    BranchType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
