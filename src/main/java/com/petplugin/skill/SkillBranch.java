package com.petplugin.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single branch (ATK/DEF/HEAL) containing up to 5 skills in order.
 * Skills must be unlocked sequentially.
 */
public class SkillBranch {

    private final BranchType type;
    private final List<Skill> skills; // index 0-4

    public SkillBranch(BranchType type, List<Skill> skills) {
        if (skills.size() > 5) throw new IllegalArgumentException("Branch may have max 5 skills");
        this.type = type;
        this.skills = new ArrayList<>(skills);
    }

    public BranchType getType() { return type; }

    public List<Skill> getSkills() { return Collections.unmodifiableList(skills); }

    public Skill getSkill(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= skills.size()) return null;
        return skills.get(slotIndex);
    }

    public int size() { return skills.size(); }
}
