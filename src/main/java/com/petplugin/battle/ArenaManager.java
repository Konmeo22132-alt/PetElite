package com.petplugin.battle;

import com.petplugin.PetPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Freezes / unfreezes players during battle.
 * Currently implemented by cancelling movement events (no arena teleport).
 * Placeholder: later replace with arena teleport logic.
 */
public class ArenaManager {

    /** Set of frozen player UUIDs — checked in BattleListener. */
    private static final Set<UUID> frozen = new HashSet<>();
    private static PetPlugin plugin;

    public static void init(PetPlugin p) {
        plugin = p;
    }

    public static void freeze(PetPlugin plugin, Player... players) {
        for (Player player : players) {
            frozen.add(player.getUniqueId());
            player.setVelocity(new Vector(0, 0, 0));
        }
    }

    public static void unfreeze(Player... players) {
        for (Player player : players) {
            frozen.remove(player.getUniqueId());
        }
    }

    public static boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    /** Called to completely clean up all frozen players (e.g. on plugin disable). */
    public static void unfreezeAll() {
        frozen.clear();
    }
}
