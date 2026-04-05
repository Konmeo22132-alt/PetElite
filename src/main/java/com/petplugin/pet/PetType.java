package com.petplugin.pet;

/**
 * Pet type enum — holds base stats and per-level growth rates.
 */
public enum PetType {

    // HP ATK DEF atkGain defGain healGain locomotion
    TURTLE(100, 8, 20, 0, 2, 1, LocomotionType.FLOAT),
    WOLF(80, 20, 10, 2, 1, 0, LocomotionType.GROUND),
    CAT(80, 12, 8, 1, 0, 2, LocomotionType.GROUND);

    public enum LocomotionType {
        FLOAT, GROUND
    }

    private final int baseHp;
    private final int baseAtk;
    private final int baseDef;
    private final int atkGain;
    private final int defGain;
    private final int healGain;
    private final LocomotionType locomotion;

    PetType(int baseHp, int baseAtk, int baseDef,
            int atkGain, int defGain, int healGain,
            LocomotionType locomotion) {
        this.baseHp = baseHp;
        this.baseAtk = baseAtk;
        this.baseDef = baseDef;
        this.atkGain = atkGain;
        this.defGain = defGain;
        this.healGain = healGain;
        this.locomotion = locomotion;
    }

    public int getBaseHp() {
        return baseHp;
    }

    public int getBaseAtk() {
        return baseAtk;
    }

    public int getBaseDef() {
        return baseDef;
    }

    public int getAtkGain() {
        return atkGain;
    }

    public int getDefGain() {
        return defGain;
    }

    public int getHealGain() {
        return healGain;
    }

    public LocomotionType getLocomotion() {
        return locomotion;
    }

    public String getDisplayName() {
        return switch (this) {
            case TURTLE -> "&aRùa";
            case WOLF -> "&cSói";
            case CAT -> "&dMèo";
        };
    }

    public String getRoleDescription() {
        return switch (this) {
            case TURTLE -> "&7Role: &aTank";
            case WOLF -> "&7Role: &cDPS";
            case CAT -> "&7Role: &dSupport";
        };
    }
}
