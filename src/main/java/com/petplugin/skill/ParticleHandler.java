package com.petplugin.skill;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Centralized particle spawning for skills and status effects.
 * All spawns happen on the server thread (called from BattleSession / skill execute).
 */
public final class ParticleHandler {

    private ParticleHandler() {}

    // ------------------------------------------------------------------ //
    //  Status effect ambient particles  (called every tick by StatusEffectManager)
    // ------------------------------------------------------------------ //

    public static void spawnStatusParticle(LivingEntity entity, StatusEffectType type) {
        Location loc = entity.getLocation().add(0, 1, 0);
        switch (type) {
            case POISON -> entity.getWorld().spawnParticle(
                    Particle.ANGRY_VILLAGER, loc, 3, 0.3, 0.5, 0.3);
            case BURN   -> entity.getWorld().spawnParticle(
                    Particle.FLAME, loc, 5, 0.2, 0.4, 0.2, 0.02);
            case STUN   -> entity.getWorld().spawnParticle(
                    Particle.ENCHANTED_HIT, loc, 8, 0.4, 0.5, 0.4, 0.1);
        }
    }

    // ------------------------------------------------------------------ //
    //  Generic branch particles (fallback)
    // ------------------------------------------------------------------ //

    public static void spawnSkillParticle(LivingEntity entity, BranchType branch) {
        Location loc = entity.getLocation().add(0, 1, 0);
        switch (branch) {
            case ATK_BRANCH  -> entity.getWorld().spawnParticle(
                    Particle.CRIT, loc, 10, 0.4, 0.4, 0.4, 0.1);
            case DEF_BRANCH  -> entity.getWorld().spawnParticle(
                    Particle.ENCHANT, loc, 15, 0.5, 0.5, 0.5, 0.5);
            case HEAL_BRANCH -> entity.getWorld().spawnParticle(
                    Particle.HEART, loc, 5, 0.3, 0.3, 0.3);
        }
    }

    public static void spawnHitParticle(LivingEntity target) {
        Location loc = target.getLocation().add(0, 1, 0);
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 3, 0.2, 0.2, 0.2);
    }

    // ------------------------------------------------------------------ //
    //  Named per-skill particles
    // ------------------------------------------------------------------ //

    /** BLOCK_CRACK (stone) burst at impact — Shell Slam, etc. */
    public static void spawnBlockCrack(Location loc, int count) {
        loc.getWorld().spawnParticle(Particle.BLOCK, loc, count,
                0.4, 0.1, 0.4, 0.05,
                org.bukkit.Bukkit.createBlockData(org.bukkit.Material.STONE));
    }

    /** 3 CRIT particles travelling from 'from' toward 'to' — Spike Shot, etc. */
    public static void spawnCritLine(LivingEntity from, LivingEntity to, int count) {
        Location start = from.getLocation().add(0, 1, 0);
        Location end   = to.getLocation().add(0, 1, 0);
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len == 0) return;
        double step = len / count;
        for (int i = 1; i <= count; i++) {
            double t = step * i / len;
            Location p = start.clone().add(dx * t, dy * t, dz * t);
            p.getWorld().spawnParticle(Particle.CRIT, p, 1, 0, 0, 0, 0);
        }
    }

    /** FALLING_DUST (brown) around target — Crush. */
    public static void spawnFallingDust(Location loc, int count) {
        loc.getWorld().spawnParticle(Particle.FALLING_DUST, loc, count,
                0.8, 0.1, 0.8, 0.1,
                org.bukkit.Bukkit.createBlockData(org.bukkit.Material.BROWN_TERRACOTTA));
    }

    /** WATER_SPLASH spirals around 'from' then shoots toward 'to' — Tidal Strike. */
    public static void spawnWaterSplashSpiral(LivingEntity from, LivingEntity to, Plugin plugin) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= 6) { cancel(); return; }
                double angle = tick * Math.PI / 3;
                Location loc = from.getLocation().add(
                        Math.cos(angle) * 0.7, 1.0, Math.sin(angle) * 0.7);
                loc.getWorld().spawnParticle(Particle.SPLASH, loc, 4, 0.1, 0.1, 0.1, 0.1);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        // Shoot toward target
        spawnCritLine(from, to, 5);
    }

    /** EXPLOSION_LARGE at impact + SMOKE_LARGE trail — Titan Crash, Alpha Rend. */
    public static void spawnExplosionLarge(Location loc) {
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 3, 0.3, 0.3, 0.3, 0);
        loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 6, 0.4, 0.4, 0.4, 0.05);
    }

    /** ENCHANT orbit around pet for duration — Hard Shell. */
    public static void spawnEnchantOrbit(LivingEntity entity, int durationTicks, Plugin plugin) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || entity.isDead()) { cancel(); return; }
                double angle = tick * Math.PI / 5;
                Location loc = entity.getLocation().add(
                        Math.cos(angle) * 0.8, 1.0, Math.sin(angle) * 0.8);
                loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 3, 0.1, 0.1, 0.1, 0.5);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** CRIT from pet toward attacker — Thorn Armor reflect. */
    public static void spawnCritToward(LivingEntity from, LivingEntity toward) {
        spawnCritLine(from, toward, 4);
    }

    /** SMOKE_NORMAL dense cloud on self — Stone Skin. */
    public static void spawnSmokeDense(LivingEntity entity, int durationTicks, Plugin plugin) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || entity.isDead()) { cancel(); return; }
                entity.getWorld().spawnParticle(Particle.SMOKE,
                        entity.getLocation().add(0, 0.8, 0), 6, 0.4, 0.5, 0.4, 0.02);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** VILLAGER_HAPPY orbit — Iron Fortress. */
    public static void spawnHappyOrbit(LivingEntity entity, int durationTicks, Plugin plugin) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || entity.isDead()) { cancel(); return; }
                double angle = tick * Math.PI / 4;
                Location loc = entity.getLocation().add(
                        Math.cos(angle), 1.0, Math.sin(angle));
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 2, 0.1, 0.1, 0.1);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** TOTEM erupt upward — Unbreakable, Iron Will, Ghost Step, Rebirth. */
    public static void spawnTotemErupt(LivingEntity entity) {
        Location base = entity.getLocation().add(0, 0.5, 0);
        for (int i = 0; i < 3; i++) {
            Location loc = base.clone().add(0, i * 0.5, 0);
            loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 10,
                    0.3, 0.1, 0.3, 0.2);
        }
    }

    /** WATER_BUBBLE expands outward — Tide Pulse. */
    public static void spawnWaterBubbleRing(LivingEntity entity) {
        Location center = entity.getLocation().add(0, 0.3, 0);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            Location loc = center.clone().add(Math.cos(angle), 0, Math.sin(angle));
            loc.getWorld().spawnParticle(Particle.BUBBLE, loc, 2, 0.05, 0.05, 0.05, 0.05);
        }
    }

    /** CRIT_MAGIC (green tinted) flows inward — Shell Mend, Primal Hunger. */
    public static void spawnEnchantedHitInflow(LivingEntity source, LivingEntity sink,
                                                int count, Plugin plugin) {
        new BukkitRunnable() {
            int emitted = 0;
            @Override public void run() {
                if (emitted >= count) { cancel(); return; }
                // spawn near source, drifts toward sink (visually)
                Location loc = source.getLocation().clone().add(0, 1, 0);
                loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 2,
                        0.3, 0.3, 0.3, 0.05);
                emitted++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** DRIP_WATER continuously around entity — Ocean Breath. */
    public static void spawnDripWater(LivingEntity entity, int durationTicks, Plugin plugin) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || entity.isDead()) { cancel(); return; }
                entity.getWorld().spawnParticle(Particle.DRIPPING_WATER,
                        entity.getLocation().add(0, 1.5, 0), 3, 0.4, 0.1, 0.4, 0);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** WATER_SPLASH column rising from feet — Ancient Tide. */
    public static void spawnWaterColumn(LivingEntity entity) {
        Location base = entity.getLocation();
        for (int i = 0; i < 4; i++) {
            Location loc = base.clone().add(0, i * 0.5, 0);
            loc.getWorld().spawnParticle(Particle.SPLASH, loc, 5, 0.3, 0.05, 0.3, 0.1);
        }
    }

    /** HEART float upward — Lick Wound, Purr Heal, Catnap. */
    public static void spawnHeartFloat(LivingEntity entity, int count) {
        Location loc = entity.getLocation().add(0, 1.5, 0);
        loc.getWorld().spawnParticle(Particle.HEART, loc, count, 0.4, 0.2, 0.4, 0.0);
    }

    /** SWEEP_ATTACK twice 0.3s apart — Feral Slash. */
    public static void spawnSweepDouble(Location loc, Plugin plugin) {
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0, 0, 0, 0);
        new BukkitRunnable() {
            @Override public void run() {
                loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0, 0, 0, 0);
            }
        }.runTaskLater(plugin, 6L); // ~0.3s
    }

    /** VILLAGER_ANGRY (purple simulated by extra-spread angry) — Blood Fang. */
    public static void spawnAngryCloud(Location loc, double radius) {
        loc.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, loc, 5,
                radius, radius, radius, 0);
    }

    /** CRIT_MAGIC (gold) converges from surroundings — Pack Strike. */
    public static void spawnEnchantedConverge(LivingEntity target) {
        Location center = target.getLocation().add(0, 1, 0);
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            Location src = center.clone().add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);
            src.getWorld().spawnParticle(Particle.ENCHANTED_HIT, src, 2, 0.1, 0.1, 0.1, 0.05);
        }
    }

    /** EXPLOSION_NORMAL + CRIT 360° — Alpha Rend. */
    public static void spawnExplosionCrit360(Location loc) {
        loc.getWorld().spawnParticle(Particle.POOF, loc, 6, 0.5, 0.5, 0.5, 0.05);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            Location p = loc.clone().add(Math.cos(angle) * 0.8, 0.5, Math.sin(angle) * 0.8);
            p.getWorld().spawnParticle(Particle.CRIT, p, 2, 0.05, 0.05, 0.05, 0.1);
        }
    }

    /** CLOUD burst from feet — Dodge. */
    public static void spawnCloudBurst(LivingEntity entity) {
        Location loc = entity.getLocation().add(0, 0.2, 0);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 12, 0.5, 0.1, 0.5, 0.05);
    }

    /** SMOKE_LARGE cone from entity toward target — Intimidate, Hiss. */
    public static void spawnSmokeCone(LivingEntity from, LivingEntity to) {
        Location start = from.getLocation().add(0, 1.2, 0);
        Location end   = to.getLocation().add(0, 1.2, 0);
        double dx = end.getX() - start.getX();
        double dz = end.getZ() - start.getZ();
        double len = Math.sqrt(dx*dx + dz*dz);
        if (len == 0) return;
        for (int i = 1; i <= 5; i++) {
            double t = (double) i / 5;
            Location loc = start.clone().add(dx * t, 0, dz * t);
            double spread = t * 0.4;
            loc.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc, 2,
                    spread, 0.15, spread, 0.01);
        }
    }

    /** HEART (dark) pulses — Wild Stance. */
    public static void spawnHeartPulse(LivingEntity entity, int pulses, Plugin plugin) {
        new BukkitRunnable() {
            int done = 0;
            @Override public void run() {
                if (done >= pulses || entity.isDead()) { cancel(); return; }
                spawnHeartFloat(entity, 6);
                done++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    /** SWEEP_ATTACK × N sequential — Nine Lives Slash. */
    public static void spawnSweepSequential(Location loc, int count, Plugin plugin) {
        for (int i = 0; i < count; i++) {
            final int idx = i;
            new BukkitRunnable() {
                @Override public void run() {
                    loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc, 1, 0.1, 0.1, 0.1, 0);
                }
            }.runTaskLater(plugin, (long)(idx * 2)); // 0.1s apart (2 ticks)
        }
    }

    /** CRIT_MAGIC white orbits fast — Nimble. */
    public static void spawnFastOrbit(LivingEntity entity, int durationTicks, Plugin plugin) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || entity.isDead()) { cancel(); return; }
                double angle = tick * Math.PI / 3;
                Location loc = entity.getLocation().add(
                        Math.cos(angle) * 0.6, 1.0, Math.sin(angle) * 0.6);
                loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 1, 0, 0, 0, 0);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** CLOUD dense around entity — Smoke Veil / Catnap. */
    public static void spawnCloudCloud(LivingEntity entity, int durationTicks,
                                       double radius, Plugin plugin) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || entity.isDead()) { cancel(); return; }
                entity.getWorld().spawnParticle(Particle.CLOUD,
                        entity.getLocation().add(0, 0.8, 0), 3, radius, 0.5, radius, 0.01);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** NOTE burst — Howl Heal. */
    public static void spawnNoteCloud(LivingEntity entity) {
        Location loc = entity.getLocation().add(0, 1.5, 0);
        loc.getWorld().spawnParticle(Particle.NOTE, loc, 8, 0.5, 0.3, 0.5, 1.0);
    }

    /** ENCHANTED_HIT flash on block — Reflex Guard. */
    public static void spawnEnchantFlash(LivingEntity entity) {
        entity.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
                entity.getLocation().add(0, 1, 0), 6, 0.4, 0.4, 0.4, 0.1);
    }

    /** CRIT_MAGIC slow stream from source to sink — Life Drain / Ocean Breath tether. */
    public static void spawnLifeDrainStream(LivingEntity source, LivingEntity sink,
                                             int durationTicks, Plugin plugin) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || source.isDead()) { cancel(); return; }
                Location from = source.getLocation().add(0, 1, 0);
                Location to   = sink.getLocation().add(0, 1, 0);
                double dx = to.getX() - from.getX();
                double dy = to.getY() - from.getY();
                double dz = to.getZ() - from.getZ();
                double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (len > 0) {
                    double t = (double) tick / durationTicks;
                    Location p = from.clone().add(dx*t, dy*t, dz*t);
                    p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, p, 2, 0.1, 0.1, 0.1, 0.02);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** VILLAGER_HAPPY travels back/forth between two entities — Pack Bond. */
    public static void spawnHappyTether(LivingEntity a, LivingEntity b, int durationTicks,
                                         Plugin plugin) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || a.isDead()) { cancel(); return; }
                boolean forward = (tick % 20) < 10;
                LivingEntity from = forward ? a : b;
                LivingEntity to   = forward ? b : a;
                spawnCritLine(from, to, 3); // reuse line, different particle below
                Location fa = from.getLocation().add(0, 0.8, 0);
                Location ta = to.getLocation().add(0, 0.8, 0);
                Location mid = fa.clone().add(
                        (ta.getX() - fa.getX()) / 2,
                        (ta.getY() - fa.getY()) / 2,
                        (ta.getZ() - fa.getZ()) / 2);
                mid.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, mid, 1, 0, 0, 0, 0);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Generic TOTEM + WATER_BUBBLE alternating orbit — Eternal Shell. */
    public static void spawnEternalShellAura(LivingEntity entity, int durationTicks, Plugin plugin) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (tick >= durationTicks || entity.isDead()) { cancel(); return; }
                double angle = tick * Math.PI / 4;
                Location loc = entity.getLocation().add(
                        Math.cos(angle) * 0.7, 1.0, Math.sin(angle) * 0.7);
                Particle p = (tick % 2 == 0) ? Particle.TOTEM_OF_UNDYING : Particle.BUBBLE;
                loc.getWorld().spawnParticle(p, loc, 2, 0.05, 0.05, 0.05, 0.05);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
