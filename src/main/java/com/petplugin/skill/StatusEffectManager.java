package com.petplugin.skill;

import com.petplugin.PetPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ticks all active status effects on Living entities.
 * Runs every tick; damage is applied every 20 ticks per effect.
 */
public class StatusEffectManager extends BukkitRunnable {

    private final PetPlugin plugin;

    // entityUUID -> (effectType -> StatusEffect instance)
    private final Map<UUID, Map<StatusEffectType, StatusEffect>> effects =
            new ConcurrentHashMap<>();

    public StatusEffectManager(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void run() {
        Iterator<Map.Entry<UUID, Map<StatusEffectType, StatusEffect>>> outerIt =
                effects.entrySet().iterator();

        while (outerIt.hasNext()) {
            Map.Entry<UUID, Map<StatusEffectType, StatusEffect>> outerEntry = outerIt.next();
            UUID entityUuid = outerEntry.getKey();
            LivingEntity entity = findEntity(entityUuid);

            if (entity == null || entity.isDead()) {
                outerIt.remove();
                continue;
            }

            Map<StatusEffectType, StatusEffect> activeEffects = outerEntry.getValue();
            Iterator<Map.Entry<StatusEffectType, StatusEffect>> it = activeEffects.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<StatusEffectType, StatusEffect> entry = it.next();
                StatusEffect effect = entry.getValue();

                // Spawn particle
                ParticleHandler.spawnStatusParticle(entity, effect.getType());

                // Tick and apply damage
                boolean dealDamage = effect.tick();
                if (dealDamage && effect.getDamagePerTick() > 0) {
                    double damage = effect.getDamagePerTick();
                    entity.damage(damage);
                }

                // STUN outside battle — apply Slowness III if entity is player
                if (effect.getType() == StatusEffectType.STUN && entity instanceof Player player) {
                    if (!effect.isExpired()) {
                        player.addPotionEffect(new PotionEffect(
                                PotionEffectType.SLOWNESS, 40, 2, true, false));
                    }
                }

                if (effect.isExpired()) {
                    it.remove();
                    onEffectExpired(entity, entry.getKey());
                }
            }

            if (activeEffects.isEmpty()) outerIt.remove();
        }
    }

    public void applyEffect(LivingEntity entity, StatusEffectType type, int durationSeconds) {
        effects.computeIfAbsent(entity.getUniqueId(), k -> new EnumMap<>(StatusEffectType.class))
               .put(type, new StatusEffect(type, durationSeconds));
    }

    public void removeEffect(UUID entityUuid, StatusEffectType type) {
        Map<StatusEffectType, StatusEffect> map = effects.get(entityUuid);
        if (map != null) {
            map.remove(type);
            if (map.isEmpty()) effects.remove(entityUuid);
        }
    }

    public boolean hasEffect(UUID entityUuid, StatusEffectType type) {
        Map<StatusEffectType, StatusEffect> map = effects.get(entityUuid);
        return map != null && map.containsKey(type);
    }

    /** Returns effective DEF reduction from all active BURN effects. */
    public double getDefReduction(UUID entityUuid) {
        Map<StatusEffectType, StatusEffect> map = effects.get(entityUuid);
        if (map == null) return 0.0;
        double reduction = 0.0;
        for (StatusEffect e : map.values()) {
            reduction += e.getDefReduction();
        }
        return Math.min(reduction, 0.50); // cap at 50%
    }

    public void clearAll(UUID entityUuid) {
        effects.remove(entityUuid);
    }

    private void onEffectExpired(LivingEntity entity, StatusEffectType type) {
        // Could send message to player here
    }

    private LivingEntity findEntity(UUID uuid) {
        // Paper provides O(1) UUID lookup — avoids iterating all worlds every tick
        org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(uuid);
        if (entity instanceof LivingEntity living) return living;
        return null;
    }
}
