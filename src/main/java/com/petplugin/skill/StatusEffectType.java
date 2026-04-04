package com.petplugin.skill;

import org.bukkit.Particle;

/**
 * Status effect types with their visual and mechanical properties.
 */
public enum StatusEffectType {

    POISON(
        "Độc",
        Particle.ANGRY_VILLAGER,
        3,       // damage per 20 ticks
        10,      // default duration seconds
        0.0      // no def reduction
    ),
    BURN(
        "Bỏng",
        Particle.FLAME,
        4,       // damage per 20 ticks
        8,       // default duration seconds
        0.20     // DEF -20%
    ),
    STUN(
        "Chóang",
        Particle.ENCHANTED_HIT,
        0,       // no periodic damage
        3,       // duration seconds (outside battle = Slowness III 3s)
        0.0
    );

    private final String displayName;
    private final Particle particle;
    private final int damagePerTick;   // applied every 20 server ticks
    private final int defaultDurationSeconds;
    private final double defReductionFactor;

    StatusEffectType(String displayName, Particle particle,
                     int damagePerTick, int defaultDurationSeconds,
                     double defReductionFactor) {
        this.displayName = displayName;
        this.particle = particle;
        this.damagePerTick = damagePerTick;
        this.defaultDurationSeconds = defaultDurationSeconds;
        this.defReductionFactor = defReductionFactor;
    }

    public String getDisplayName()          { return displayName; }
    public Particle getParticle()           { return particle; }
    public int getDamagePerTick()           { return damagePerTick; }
    public int getDefaultDurationSeconds()  { return defaultDurationSeconds; }
    public double getDefReductionFactor()   { return defReductionFactor; }
}
