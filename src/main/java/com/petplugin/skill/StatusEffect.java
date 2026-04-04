package com.petplugin.skill;

/**
 * A single active instance of a status effect on an entity.
 */
public class StatusEffect {

    private final StatusEffectType type;
    private int remainingTicks;   // countdown in server ticks (20/s)
    private int tickCounter;      // internal counter for periodic damage

    public StatusEffect(StatusEffectType type, int durationSeconds) {
        this.type = type;
        this.remainingTicks = durationSeconds * 20;
        this.tickCounter = 0;
    }

    /**
     * Tick this effect once. Returns true if the effect should deal periodic damage this tick.
     */
    public boolean tick() {
        remainingTicks--;
        tickCounter++;
        return (tickCounter % 20 == 0) && type.getDamagePerTick() > 0;
    }

    public boolean isExpired() {
        return remainingTicks <= 0;
    }

    /** Effective DEF reduction factor (0.0 = none, 0.2 = -20%). */
    public double getDefReduction() {
        return type.getDefReductionFactor();
    }

    public StatusEffectType getType() { return type; }
    public int getRemainingTicks()    { return remainingTicks; }
    public int getDamagePerTick()     { return type.getDamagePerTick(); }
}
