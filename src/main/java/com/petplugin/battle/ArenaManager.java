package com.petplugin.battle;

import com.petplugin.PetPlugin;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.FoliaUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Full arena system for PetElite battles.
 *
 * - Arena registration via /petop set battle
 * - Load balancing across multiple arenas
 * - Player teleportation with 3-second countdown
 * - Boundary enforcement during battle
 * - Post-battle cleanup and teleport-back
 *
 * AUDIT FIX: frozen set now uses ConcurrentHashMap.newKeySet() for thread safety.
 */
public class ArenaManager {

    // ---- Arena data ----
    public static class ArenaData {
        private final String id;
        private final String world;
        private final double x, y, z;
        private final int radius;
        private final UUID registeredBy;
        private final long registeredAt;

        public ArenaData(String id, String world, double x, double y, double z,
                         int radius, UUID registeredBy, long registeredAt) {
            this.id = id;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.registeredBy = registeredBy;
            this.registeredAt = registeredAt;
        }

        public String getId() { return id; }
        public String getWorldName() { return world; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public int getRadius() { return radius; }
        public UUID getRegisteredBy() { return registeredBy; }
        public long getRegisteredAt() { return registeredAt; }

        public Location getCenter() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z);
        }

        public int getMaxConcurrentBattles() {
            return Math.max(1, radius / 10);
        }
    }

    // ---- Static state ----
    private static PetPlugin plugin;

    /** Set of frozen player UUIDs — checked in BattleListener. */
    // AUDIT FIX: thread-safe set
    private static final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    /** All registered arenas. */
    private static final Map<String, ArenaData> arenas = new ConcurrentHashMap<>();

    /** Arena ID -> count of active battles in that arena. */
    private static final Map<String, Integer> activeBattleCounts = new ConcurrentHashMap<>();

    /** Player UUID -> pre-battle location (saved before teleport). */
    private static final Map<UUID, Location> preBattleLocations = new ConcurrentHashMap<>();

    /** Player UUID -> arena spawn point (for boundary enforcement return). */
    private static final Map<UUID, Location> arenaSpawnPoints = new ConcurrentHashMap<>();

    /** Player UUID -> arena ID they're battling in. */
    private static final Map<UUID, String> playerArenas = new ConcurrentHashMap<>();

    /** Battle queue: list of pending battle runnables waiting for arena. */
    private static final List<QueuedBattle> battleQueue = Collections.synchronizedList(new ArrayList<>());

    // ---- Queue entry ----
    public static class QueuedBattle {
        public final Player playerA, playerB;
        public final long queuedAt;
        public QueuedBattle(Player a, Player b) {
            this.playerA = a;
            this.playerB = b;
            this.queuedAt = System.currentTimeMillis();
        }
    }

    // ---- Init ----

    public static void init(PetPlugin p) {
        plugin = p;
        loadArenas();
    }

    // ---- Arena registration ----

    public static String registerArena(Player sender) {
        int nextId = arenas.size() + 1;
        String id = "arena_" + nextId;
        // Avoid collisions
        while (arenas.containsKey(id)) {
            nextId++;
            id = "arena_" + nextId;
        }

        Location loc = sender.getLocation();
        ArenaData data = new ArenaData(id, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), 40,
                sender.getUniqueId(), System.currentTimeMillis());
        arenas.put(id, data);
        activeBattleCounts.put(id, 0);
        saveArenas();

        Bukkit.getLogger().info("[PetElite] Arena registered: " + id
                + " at " + loc.getWorld().getName() + " " + (int) loc.getX()
                + "," + (int) loc.getY() + "," + (int) loc.getZ()
                + " by " + sender.getName());
        return id;
    }

    public static boolean removeArena(String id) {
        if (arenas.remove(id) != null) {
            activeBattleCounts.remove(id);
            saveArenas();
            Bukkit.getLogger().info("[PetElite] Arena removed: " + id);
            return true;
        }
        return false;
    }

    // ---- Arena selection ----

    /**
     * Find the arena with the lowest active battle count that has capacity.
     * Returns null if no arena is available.
     */
    public static ArenaData findAvailableArena() {
        ArenaData best = null;
        int bestCount = Integer.MAX_VALUE;

        for (Map.Entry<String, ArenaData> entry : arenas.entrySet()) {
            ArenaData arena = entry.getValue();
            int count = activeBattleCounts.getOrDefault(arena.getId(), 0);
            int maxConcurrent = arena.getMaxConcurrentBattles();

            // Check config override
            int configMax = plugin.getConfig().getInt("performance.arena_max_concurrent_battles", 4);
            maxConcurrent = Math.min(maxConcurrent, configMax);

            if (count < maxConcurrent && count < bestCount) {
                bestCount = count;
                best = arena;
            }
        }
        return best;
    }

    public static boolean hasArenas() {
        return !arenas.isEmpty();
    }

    public static Collection<ArenaData> getAllArenas() {
        return arenas.values();
    }

    public static ArenaData getArena(String id) {
        return arenas.get(id);
    }

    public static int getActiveBattleCount(String arenaId) {
        return activeBattleCounts.getOrDefault(arenaId, 0);
    }

    // ---- Battle lifecycle ----

    /**
     * Teleport both players to the arena and start the countdown.
     * Called by BattleManager after arena selection.
     */
    public static void teleportAndCountdown(PetPlugin plugin, Player playerA, Player playerB,
                                             ArenaData arena, Runnable onReady) {
        // Save pre-battle locations
        preBattleLocations.put(playerA.getUniqueId(), playerA.getLocation().clone());
        preBattleLocations.put(playerB.getUniqueId(), playerB.getLocation().clone());

        Location center = arena.getCenter();
        if (center == null) {
            playerA.sendMessage(ChatUtil.color("&cArena world not loaded!"));
            playerB.sendMessage(ChatUtil.color("&cArena world not loaded!"));
            return;
        }

        // Calculate spawn points facing each other
        Location spawnA = center.clone().add(3, 0, 0);
        Location spawnB = center.clone().add(-3, 0, 0);

        // Calculate yaw to face each other
        float yawA = getYawFacing(spawnA, spawnB);
        float yawB = getYawFacing(spawnB, spawnA);
        spawnA.setYaw(yawA);
        spawnA.setPitch(0);
        spawnB.setYaw(yawB);
        spawnB.setPitch(0);

        arenaSpawnPoints.put(playerA.getUniqueId(), spawnA.clone());
        arenaSpawnPoints.put(playerB.getUniqueId(), spawnB.clone());
        playerArenas.put(playerA.getUniqueId(), arena.getId());
        playerArenas.put(playerB.getUniqueId(), arena.getId());

        // Increment active battle count
        activeBattleCounts.merge(arena.getId(), 1, Integer::sum);

        // Teleport
        Runnable afterTeleport = () -> {
            // Apply blindness + slowness during countdown
            int countdownSeconds = plugin.getConfig().getInt("battle.countdown_seconds", 3);
            int effectDuration = (countdownSeconds + 1) * 20;
            playerA.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, effectDuration, 0, true, false));
            playerA.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, effectDuration, 3, true, false));
            playerB.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, effectDuration, 0, true, false));
            playerB.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, effectDuration, 3, true, false));

            // Freeze both during countdown
            frozen.add(playerA.getUniqueId());
            frozen.add(playerB.getUniqueId());

            // Countdown titles
            startCountdown(plugin, playerA, playerB, countdownSeconds, onReady);
        };

        if (FoliaUtil.IS_FOLIA) {
            playerA.teleportAsync(spawnA).thenRun(() ->
                playerB.teleportAsync(spawnB).thenRun(afterTeleport));
        } else {
            playerA.teleport(spawnA);
            playerB.teleport(spawnB);
            afterTeleport.run();
        }

        Bukkit.getLogger().info("[PetElite] Battle teleport: " + playerA.getName()
                + " vs " + playerB.getName() + " in " + arena.getId());
    }

    private static void startCountdown(PetPlugin plugin, Player a, Player b,
                                        int seconds, Runnable onReady) {
        final int[] remaining = {seconds};

        Runnable countdownTick = new Runnable() {
            @Override
            public void run() {
                if (remaining[0] > 0) {
                    String num = "&e&l" + remaining[0];
                    if (a.isOnline()) a.sendTitle(ChatUtil.legacyColor(num), "", 0, 25, 5);
                    if (b.isOnline()) b.sendTitle(ChatUtil.legacyColor(num), "", 0, 25, 5);
                    remaining[0]--;

                    // Schedule next tick
                    if (FoliaUtil.IS_FOLIA) {
                        Bukkit.getGlobalRegionScheduler().runDelayed(plugin,
                                task -> this.run(), 20L);
                    } else {
                        Bukkit.getScheduler().runTaskLater(plugin, this, 20L);
                    }
                } else {
                    // FIGHT!
                    String fight = "&c&lFIGHT!";
                    if (a.isOnline()) {
                        a.sendTitle(ChatUtil.legacyColor(fight), "", 0, 20, 10);
                        a.removePotionEffect(PotionEffectType.BLINDNESS);
                        a.removePotionEffect(PotionEffectType.SLOWNESS);
                    }
                    if (b.isOnline()) {
                        b.sendTitle(ChatUtil.legacyColor(fight), "", 0, 20, 10);
                        b.removePotionEffect(PotionEffectType.BLINDNESS);
                        b.removePotionEffect(PotionEffectType.SLOWNESS);
                    }
                    onReady.run();
                }
            }
        };

        // Start first countdown tick
        if (FoliaUtil.IS_FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin,
                    task -> countdownTick.run(), 20L);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, countdownTick, 20L);
        }
    }

    private static float getYawFacing(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return yaw;
    }

    /**
     * Check if a player is outside their arena boundary.
     * Returns true if outside (needs teleport back).
     */
    public static boolean isOutsideBoundary(UUID playerUuid) {
        String arenaId = playerArenas.get(playerUuid);
        if (arenaId == null) return false;
        ArenaData arena = arenas.get(arenaId);
        if (arena == null) return false;

        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return false;

        Location center = arena.getCenter();
        if (center == null) return false;

        double distSq = player.getLocation().distanceSquared(center);
        return distSq > (arena.getRadius() * arena.getRadius());
    }

    /**
     * Teleport a player back to their arena spawn point.
     */
    public static void teleportBackToSpawn(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        Location spawn = arenaSpawnPoints.get(playerUuid);
        if (player != null && player.isOnline() && spawn != null) {
            if (FoliaUtil.IS_FOLIA) {
                player.teleportAsync(spawn);
            } else {
                player.teleport(spawn);
            }
            player.sendMessage(ChatUtil.color("&cStay inside the arena!"));
        }
    }

    /**
     * Post-battle cleanup: teleport both players back, remove effects, decrement count.
     */
    public static void cleanupBattle(Player playerA, Player playerB) {
        // Teleport back to pre-battle locations
        teleportBack(playerA);
        teleportBack(playerB);

        // Remove potion effects
        removeBattleEffects(playerA);
        removeBattleEffects(playerB);

        // Unfreeze
        unfreeze(playerA, playerB);

        // Decrement arena battle count
        String arenaIdA = playerArenas.remove(playerA.getUniqueId());
        playerArenas.remove(playerB.getUniqueId());
        arenaSpawnPoints.remove(playerA.getUniqueId());
        arenaSpawnPoints.remove(playerB.getUniqueId());

        if (arenaIdA != null) {
            activeBattleCounts.merge(arenaIdA, -1, (a, b) -> Math.max(0, a + b));
        }
    }

    private static void teleportBack(Player player) {
        if (player == null || !player.isOnline()) return;
        Location preBattle = preBattleLocations.remove(player.getUniqueId());
        if (preBattle != null) {
            if (FoliaUtil.IS_FOLIA) {
                player.teleportAsync(preBattle);
            } else {
                player.teleport(preBattle);
            }
        }
    }

    private static void removeBattleEffects(Player player) {
        if (player == null || !player.isOnline()) return;
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    // ---- Freeze/Unfreeze (preserved API) ----

    public static void freeze(PetPlugin plugin, Player... players) {
        for (Player player : players) {
            frozen.add(player.getUniqueId());
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

    public static void unfreezeAll() {
        frozen.clear();
        preBattleLocations.clear();
        arenaSpawnPoints.clear();
        playerArenas.clear();
        // Reset battle counts
        for (String key : activeBattleCounts.keySet()) {
            activeBattleCounts.put(key, 0);
        }
    }

    public static boolean isInArena(UUID uuid) {
        return playerArenas.containsKey(uuid);
    }

    // ---- Persistence ----

    private static void loadArenas() {
        File file = new File(plugin.getDataFolder(), "arenas.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (!config.contains("arenas")) return;

        var section = config.getConfigurationSection("arenas");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String path = "arenas." + id + ".";
            String world = config.getString(path + "world", "world");
            double x = config.getDouble(path + "x");
            double y = config.getDouble(path + "y");
            double z = config.getDouble(path + "z");
            int radius = config.getInt(path + "radius", 40);
            String regBy = config.getString(path + "registered_by", "");
            long regAt = config.getLong(path + "registered_at", 0);

            UUID regUuid;
            try { regUuid = UUID.fromString(regBy); }
            catch (Exception e) { regUuid = UUID.randomUUID(); }

            ArenaData data = new ArenaData(id, world, x, y, z, radius, regUuid, regAt);
            arenas.put(id, data);
            activeBattleCounts.put(id, 0);
        }
        Bukkit.getLogger().info("[PetElite] Loaded " + arenas.size() + " arena(s).");
    }

    private static void saveArenas() {
        File file = new File(plugin.getDataFolder(), "arenas.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (ArenaData arena : arenas.values()) {
            String path = "arenas." + arena.getId() + ".";
            config.set(path + "world", arena.getWorldName());
            config.set(path + "x", arena.getX());
            config.set(path + "y", arena.getY());
            config.set(path + "z", arena.getZ());
            config.set(path + "radius", arena.getRadius());
            config.set(path + "registered_by", arena.getRegisteredBy().toString());
            config.set(path + "registered_at", arena.getRegisteredAt());
        }

        // AUDIT FIX: async save
        Runnable saveTask = () -> {
            try {
                config.save(file);
            } catch (IOException e) {
                Bukkit.getLogger().log(Level.SEVERE, "[PetElite] Failed to save arenas.yml", e);
            }
        };
        if (FoliaUtil.IS_FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> saveTask.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, saveTask);
        }
    }
}
