package com.petplugin.battle;

import com.petplugin.PetPlugin;
import com.petplugin.data.PetData;
import com.petplugin.quest.QuestType;
import com.petplugin.skill.Skill;
import com.petplugin.skill.StatusEffectType;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.FoliaUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Represents one running battle between two players.
 *
 * HP is tracked separately from the entity health.
 * A BossBar shows both pets' HP.
 *
 * AUDIT FIXES:
 * - checkWin() now uses hpA/hpB <= 0 instead of petData.isFainted()
 * - All scheduled tasks in activeScheduled cancelled on end()
 * - All Player references guarded with isOnline() before use
 * - inArena flag controls teleport-back on end
 */
public class BattleSession {

    public enum Phase { WAITING, ACTIVE, ENDED }
    public enum TurnSide { A, B }

    private final PetPlugin plugin;
    private final Player playerA, playerB;
    private final PetData petA,  petB;

    /** Whether this battle is inside a registered arena. */
    private final boolean inArena;

    // Current HP per side (starts at base HP + level bonus)
    private int hpA, hpB;
    private final int maxHpA, maxHpB;

    // PP tracking: slotIndex (0-4) -> remaining PP per player
    private final Map<Integer, Integer> ppA = new HashMap<>();
    private final Map<Integer, Integer> ppB = new HashMap<>();

    // Temp combat modifiers (cleared each turn)
    private int tempDefBonusA = 0, tempDefBonusB = 0;
    private double damageReductionA = 0.0, damageReductionB = 0.0;
    private int immunityTurnsA = 0, immunityTurnsB = 0;

    // Extended modifiers (NOT cleared per turn unless explicitly noted)
    private double atkReductionA = 0.0, atkReductionB = 0.0;
    private double reflectFractionA = 0.0, reflectFractionB = 0.0;
    private double dodgeChanceA = 0.0, dodgeChanceB = 0.0;
    private int dodgeTurnsA = 0, dodgeTurnsB = 0;
    private int atkDownTurnsA = 0, atkDownTurnsB = 0;
    private int dmgReductHitsA = 0, dmgReductHitsB = 0;
    private double dmgReductPerHitA = 0.0, dmgReductPerHitB = 0.0;
    private int statusImmuneA = 0, statusImmuneB = 0;
    private boolean lastStandA = false, lastStandB = false;
    private double rebirthFractionA = 0.0, rebirthFractionB = 0.0;
    private boolean rebirthFiredA = false, rebirthFiredB = false;
    private int berserkerHitsA = 0, berserkerHitsB = 0;
    private double berserkerFractionA = 0.0, berserkerFractionB = 0.0;
    private int berserkerDmgA = 0, berserkerDmgB = 0;

    // Scheduled regen tasks — AUDIT FIX: cancelled on end()
    private final List<Object> activeScheduled = new ArrayList<>();

    // Active status effects in battle (type -> turns remaining)
    private final Map<StatusEffectType, Integer> battleEffectsA = new EnumMap<>(StatusEffectType.class);
    private final Map<StatusEffectType, Integer> battleEffectsB = new EnumMap<>(StatusEffectType.class);

    private Phase phase = Phase.WAITING;
    private TurnSide currentTurn = TurnSide.A;

    // Snapshots
    private InventorySnapshot snapshotA, snapshotB;

    // BossBar
    private BossBar bossBar;

    // Input lock (prevent double-click)
    private final Set<UUID> inputLocked = new HashSet<>();

    // Random for dodge/reflect
    private static final Random RNG = new Random();

    public BattleSession(PetPlugin plugin,
                          Player playerA, Player playerB,
                          PetData petA,   PetData petB,
                          boolean inArena) {
        this.plugin = plugin;
        this.playerA = playerA;
        this.playerB = playerB;
        this.petA = petA;
        this.petB = petB;
        this.inArena = inArena;

        this.maxHpA = calcMaxHp(petA);
        this.maxHpB = calcMaxHp(petB);
        this.hpA = maxHpA;
        this.hpB = maxHpB;

        // Init PP for both players
        initPP(ppA, petA);
        initPP(ppB, petB);
    }

    private int calcMaxHp(PetData pet) {
        return pet.getType().getBaseHp() + pet.getLevel() * 2;
    }

    private void initPP(Map<Integer, Integer> ppMap, PetData pet) {
        for (int slot = 0; slot < 5; slot++) {
            ppMap.put(slot, 10 - slot * 2); // 10,8,6,4,2
        }
    }

    // ------------------------------------------------------------------ //
    //  Lifecycle
    // ------------------------------------------------------------------ //

    public void start() {
        phase = Phase.ACTIVE;

        // Snapshot inventories
        snapshotA = new InventorySnapshot(playerA);
        snapshotB = new InventorySnapshot(playerB);

        // Clear inventories
        playerA.getInventory().clear();
        playerB.getInventory().clear();

        // Give skill books
        BookSkillHandler.giveSkillBook(playerA, petA, ppA);
        BookSkillHandler.giveSkillBook(playerB, petB, ppB);

        // Freeze players (if not already frozen by arena countdown)
        ArenaManager.freeze(plugin, playerA, playerB);

        // BossBar
        bossBar = BossBar.bossBar(buildBossBarTitle(), 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        playerA.showBossBar(bossBar);
        playerB.showBossBar(bossBar);

        broadcast("&6⚔ Trận đấu bắt đầu! &e" + petA.getName() + " &6vs &e" + petB.getName());
        promptCurrentPlayer();
    }

    public void end(Player winner, boolean surrender) {
        if (phase == Phase.ENDED) return;
        phase = Phase.ENDED;

        // AUDIT FIX: cancel all scheduled regen/berserker tasks
        cancelAllScheduled();

        // winner may be null if both disconnect simultaneously — treat as draw/no winner
        Player loser = (winner == playerA) ? playerB : playerA;

        // Hide BossBar — guard against offline players
        if (playerA.isOnline()) playerA.hideBossBar(bossBar);
        if (playerB.isOnline()) playerB.hideBossBar(bossBar);

        // Clear status effects
        plugin.getStatusEffectManager().clearAll(playerA.getUniqueId());
        plugin.getStatusEffectManager().clearAll(playerB.getUniqueId());

        // Restore inventories — works even for offline players; items saved on rejoin
        snapshotA.restore(playerA);
        snapshotB.restore(playerB);

        // TASK 2: Arena cleanup — teleport back to pre-battle location, remove effects
        if (inArena) {
            ArenaManager.cleanupBattle(playerA, playerB);
        } else {
            // In-place battle — just unfreeze
            ArenaManager.unfreeze(playerA, playerB);
        }

        // Calculate ELO (only when there is a clear winner)
        if (winner != null) {
            plugin.getEloManager().calculate(winner, loser, surrender);
            plugin.getQuestTracker().increment(winner, QuestType.WIN_BATTLES, 1);
        }

        // Announce — only to online players
        String resultMsg = (winner != null)
                ? ("&6⚔ " + winner.getName() + " &ađã thắng trận! &c" + loser.getName() + " &cthua!")
                : "&6⚔ Trận đấu kết thúc (forfeit)!";
        if (playerA.isOnline()) playerA.sendMessage(ChatUtil.color(resultMsg));
        if (playerB.isOnline()) playerB.sendMessage(ChatUtil.color(resultMsg));

        // Unregister session
        plugin.getBattleManager().endSession(playerA.getUniqueId());
        plugin.getBattleManager().endSession(playerB.getUniqueId());
    }

    /** AUDIT FIX: cancel all scheduled regen/berserker tasks. */
    private void cancelAllScheduled() {
        for (Object task : activeScheduled) {
            try {
                if (FoliaUtil.IS_FOLIA) {
                    ((io.papermc.paper.threadedregions.scheduler.ScheduledTask) task).cancel();
                } else {
                    ((org.bukkit.scheduler.BukkitTask) task).cancel();
                }
            } catch (Exception ignored) {}
        }
        activeScheduled.clear();
    }

    public void forfeit(UUID uuid) {
        boolean isPlayerA = uuid.equals(playerA.getUniqueId());
        Player forfeiter = isPlayerA ? playerA : playerB;
        Player winner    = isPlayerA ? playerB : playerA;
        String msg = "&c" + forfeiter.getName() + " đã thất bại (forfeit)!";
        // Notify only online players
        if (playerA.isOnline()) playerA.sendMessage(ChatUtil.color(msg));
        if (playerB.isOnline()) playerB.sendMessage(ChatUtil.color(msg));
        // If the winner is also offline, there is no clear winner
        end(winner.isOnline() ? winner : null, true);
    }

    // ------------------------------------------------------------------ //
    //  Turn flow
    // ------------------------------------------------------------------ //

    public void applySkill(Player user, int slotIndex) {
        if (phase != Phase.ACTIVE) return;
        if (inputLocked.contains(user.getUniqueId())) return;

        boolean isA = user == playerA;
        if ((isA && currentTurn != TurnSide.A) || (!isA && currentTurn != TurnSide.B)) {
            user.sendMessage(ChatUtil.color("&cKhông phải lượt của bạn!"));
            return;
        }

        PetData myPet  = isA ? petA : petB;
        PetData hisPet = isA ? petB : petA;
        Player target  = isA ? playerB : playerA;
        Map<Integer, Integer> myPP = isA ? ppA : ppB;

        // Check STUN
        Map<StatusEffectType, Integer> myEffects = isA ? battleEffectsA : battleEffectsB;
        if (myEffects.containsKey(StatusEffectType.STUN)) {
            myEffects.remove(StatusEffectType.STUN);
            user.sendMessage(ChatUtil.color("&cBạn đang bị choáng! Bỏ lượt này."));
            swapTurn();
            return;
        }

        // Check PP
        int pp = myPP.getOrDefault(slotIndex, 0);
        if (pp <= 0) {
            user.sendMessage(ChatUtil.color("&cSkill này đã hết PP!"));
            return;
        }

        // Find skill
        Skill skill = findUnlockedSkill(myPet, slotIndex);
        if (skill == null) {
            user.sendMessage(ChatUtil.color("&cBạn chưa unlock skill này!"));
            return;
        }

        // Lock input
        inputLocked.add(user.getUniqueId());

        // Consume PP
        myPP.put(slotIndex, pp - 1);

        // Execute skill — apply ATK reduction to attacker if debuffed
        int petAtk = myPet.getType().getBaseAtk() + myPet.getLevel();
        double atkDown = isA ? atkReductionA : atkReductionB;
        if (atkDown > 0) petAtk = (int)(petAtk * (1.0 - atkDown));
        int petDef = hisPet.getType().getBaseDef() + hisPet.getLevel();
        skill.executeInBattle(user, target, this, petAtk, petDef);

        // Tick battle status effects
        tickBattleEffects();

        // AUDIT FIX: Check win using hpA/hpB instead of petData.isFainted()
        if (checkWin()) return;

        // Update book
        Runnable updateBookTask = () -> {
            if (playerA.isOnline()) BookSkillHandler.giveSkillBook(playerA, petA, ppA);
            if (playerB.isOnline()) BookSkillHandler.giveSkillBook(playerB, petB, ppB);
        };
        if (FoliaUtil.IS_FOLIA) {
            user.getScheduler().runDelayed(plugin, task -> updateBookTask.run(), null, 2L);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, updateBookTask, 2L);
        }

        // Clear the current player's temp modifiers
        clearTempModifiers(isA ? TurnSide.A : TurnSide.B);

        // Swap turn AFTER clearing modifiers so next player starts clean
        swapTurn();

        inputLocked.remove(user.getUniqueId());
        promptCurrentPlayer();
    }

    private Skill findUnlockedSkill(PetData pet, int slotIndex) {
        return plugin.getBattleManager().resolveSkill(pet, slotIndex);
    }

    private void swapTurn() {
        currentTurn = (currentTurn == TurnSide.A) ? TurnSide.B : TurnSide.A;
    }

    private void promptCurrentPlayer() {
        Player current = (currentTurn == TurnSide.A) ? playerA : playerB;
        Player waiting = (currentTurn == TurnSide.A) ? playerB : playerA;
        if (current.isOnline()) current.sendMessage(ChatUtil.color("&a⚔ Đến lượt bạn! Chọn skill trong cuốn sách."));
        if (waiting.isOnline()) waiting.sendMessage(ChatUtil.color("&7⌛ Đợi đối thủ chọn skill..."));
        updateBossBar();
        plugin.getTurnManager().startTurn(this, current);
    }

    /**
     * AUDIT FIX: Check win using battle HP (hpA/hpB) instead of petData.isFainted().
     * petData.isFainted() is a world-entity flag and is not set during battle.
     */
    private boolean checkWin() {
        if (hpA <= 0 && hpB <= 0) {
            end(null, false); // Draw
            return true;
        }

        if (hpA <= 0) {
            end(playerB, false);
            return true;
        } else if (hpB <= 0) {
            end(playerA, false);
            return true;
        }
        return false;
    }

    private void tickBattleEffects() {
        tickEffectMap(battleEffectsA, playerA, true);
        tickEffectMap(battleEffectsB, playerB, false);
    }

    private void tickEffectMap(Map<StatusEffectType, Integer> effectMap,
                                Player player, boolean isA) {
        effectMap.entrySet().removeIf(entry -> {
            StatusEffectType type = entry.getKey();
            int turnsLeft = entry.getValue() - 1;
            if (type.getDamagePerTick() > 0) {
                int dmg = type.getDamagePerTick();
                if (isA) { hpA = Math.max(0, hpA - dmg); }
                else     { hpB = Math.max(0, hpB - dmg); }
                if (player.isOnline()) {
                    player.sendMessage(ChatUtil.color("&c" + type.getDisplayName()
                            + " gây " + dmg + " sát thương!"));
                }
            }
            if (turnsLeft <= 0) {
                entry.setValue(0);
                return true;
            }
            entry.setValue(turnsLeft);
            return false;
        });
    }

    // ------------------------------------------------------------------ //
    //  Combat API (called by skills)
    // ------------------------------------------------------------------ //

    public void dealDamage(Player attacker, int rawDamage) {
        boolean attackerIsA = (attacker == playerA);
        boolean targetIsA   = !attackerIsA;

        int targetDef = (targetIsA ? petA : petB).getType().getBaseDef()
                + (targetIsA ? petA : petB).getLevel()
                + (targetIsA ? tempDefBonusA : tempDefBonusB);

        double defReduct = plugin.getStatusEffectManager().getDefReduction(
                (targetIsA ? playerA : playerB).getUniqueId());
        int effectiveDef = (int)(targetDef * (1.0 - defReduct));

        if ((targetIsA ? battleEffectsA : battleEffectsB).containsKey(StatusEffectType.BURN)) {
            effectiveDef = (int)(effectiveDef * 0.80);
        }

        int finalDamage = Math.max(1, rawDamage - effectiveDef);

        if (targetIsA && immunityTurnsA > 0) { finalDamage = 0; immunityTurnsA--; }
        if (!targetIsA && immunityTurnsB > 0) { finalDamage = 0; immunityTurnsB--; }

        finalDamage = (int)(finalDamage * (1.0 - (targetIsA ? damageReductionA : damageReductionB)));

        applyFinalDamage(targetIsA, finalDamage, attacker,
                (targetIsA ? playerA : playerB));
    }

    public void dealDamageRaw(Player attacker, int damage) {
        boolean targetIsA = (attacker != playerA);
        applyFinalDamage(targetIsA, damage, attacker, targetIsA ? playerA : playerB);
    }

    private void applyFinalDamage(boolean targetIsA, int damage, Player attacker, Player target) {
        double dodge = targetIsA ? dodgeChanceA : dodgeChanceB;
        int dodgeTurns = targetIsA ? dodgeTurnsA : dodgeTurnsB;
        if (dodgeTurns > 0 && dodge > 0 && RNG.nextDouble() < dodge) {
            if (attacker.isOnline()) attacker.sendMessage(ChatUtil.color("&eBạn trúng... nhưng bị né!"));
            if (target.isOnline()) target.sendMessage(ChatUtil.color("&aBạn đã né đòn!"));
            return;
        }

        int reductHits = targetIsA ? dmgReductHitsA : dmgReductHitsB;
        if (reductHits > 0) {
            double perHit = targetIsA ? dmgReductPerHitA : dmgReductPerHitB;
            damage = (int)(damage * (1.0 - perHit));
            if (targetIsA) dmgReductHitsA--; else dmgReductHitsB--;
        }

        boolean lastStand = targetIsA ? lastStandA : lastStandB;
        if (lastStand) {
            int currentHp = targetIsA ? hpA : hpB;
            if (currentHp - damage <= 0) {
                damage = currentHp - 1;
                if (targetIsA) lastStandA = false; else lastStandB = false;
                if (target.isOnline()) target.sendMessage(ChatUtil.color("&6✦ Iron Will! Sống sót với 1 HP!"));
            }
        }

        if ((targetIsA ? berserkerHitsB : berserkerHitsA) > 0) {
            if (targetIsA) berserkerDmgB += damage; else berserkerDmgA += damage;
        }

        if (targetIsA) {
            hpA = Math.max(0, hpA - damage);
        } else {
            hpB = Math.max(0, hpB - damage);
        }
        if (attacker.isOnline()) attacker.sendMessage(ChatUtil.color("&cBạn gây &f" + damage + " &csát thương!"));
        if (target.isOnline()) target.sendMessage(ChatUtil.color("&cBạn nhận &f" + damage + " &csát thương! &7HP: "
                + (targetIsA ? hpA : hpB)));
        updateBossBar();

        double reflect = targetIsA ? reflectFractionA : reflectFractionB;
        if (reflect > 0 && damage > 0) {
            int reflectDmg = Math.max(1, (int)(damage * reflect));
            boolean attackerIsA = !targetIsA;
            if (attackerIsA) { hpA = Math.max(0, hpA - reflectDmg); }
            else             { hpB = Math.max(0, hpB - reflectDmg); }
            if (attacker.isOnline()) attacker.sendMessage(ChatUtil.color("&c✦ Phản đòn! Nhận " + reflectDmg + " sát thương!"));
            updateBossBar();
        }

        checkRebirth(targetIsA, target);
    }

    private void checkRebirth(boolean isA, Player player) {
        double frac = isA ? rebirthFractionA : rebirthFractionB;
        boolean fired = isA ? rebirthFiredA : rebirthFiredB;
        int maxHp = isA ? maxHpA : maxHpB;
        int hp = isA ? hpA : hpB;
        if (frac > 0 && !fired && hp > 0 && hp <= maxHp * 0.20) {
            int heal = (int)(maxHp * frac);
            if (isA) { hpA = Math.min(maxHpA, hpA + heal); rebirthFiredA = true; }
            else     { hpB = Math.min(maxHpB, hpB + heal); rebirthFiredB = true; }
            if (player.isOnline()) player.sendMessage(ChatUtil.color("&6✦ Rebirth! Hồi " + heal + " HP!"));
            updateBossBar();
        }
    }

    public void healPet(Player owner, int amount) {
        boolean isA = (owner == playerA);
        if (isA) { hpA = Math.min(maxHpA, hpA + amount); }
        else     { hpB = Math.min(maxHpB, hpB + amount); }
        if (owner.isOnline()) owner.sendMessage(ChatUtil.color("&aHồi phục &f" + amount + " &aHP! HP: "
                + (isA ? hpA : hpB)));
        updateBossBar();
    }

    public void applyStatusEffect(Player target, StatusEffectType type, int durationSeconds) {
        boolean isA = (target == playerA);
        if (isA && statusImmuneA > 0) {
            if (target.isOnline()) target.sendMessage(ChatUtil.color("&a✦ Miễn trạng thái!"));
            return;
        }
        if (!isA && statusImmuneB > 0) {
            if (target.isOnline()) target.sendMessage(ChatUtil.color("&a✦ Miễn trạng thái!"));
            return;
        }
        Map<StatusEffectType, Integer> map = isA ? battleEffectsA : battleEffectsB;
        int turns = Math.max(1, durationSeconds);
        map.put(type, turns);
        if (target.isOnline()) target.sendMessage(ChatUtil.color("&c✦ Bạn bị &f" + type.getDisplayName()
                + " &ctrong &f" + turns + " &clượt!"));
    }

    public void grantTempDefBonus(Player owner, int bonus) {
        if (owner == playerA) tempDefBonusA += bonus;
        else tempDefBonusB += bonus;
    }

    public void grantDamageReduction(Player owner, double fraction) {
        if (owner == playerA) damageReductionA = Math.min(0.90, damageReductionA + fraction);
        else damageReductionB = Math.min(0.90, damageReductionB + fraction);
    }

    public void grantImmunity(Player owner, int turns) {
        if (owner == playerA) immunityTurnsA += turns;
        else immunityTurnsB += turns;
    }

    public void clearOneDebuff(Player owner) {
        Map<StatusEffectType, Integer> map = (owner == playerA) ? battleEffectsA : battleEffectsB;
        if (!map.isEmpty()) {
            map.entrySet().iterator().remove();
        }
    }

    public void clearAllDebuffs(Player owner) {
        ((owner == playerA) ? battleEffectsA : battleEffectsB).clear();
        if (owner == playerA) { atkReductionA = 0; atkDownTurnsA = 0; }
        else                  { atkReductionB = 0; atkDownTurnsB = 0; }
    }

    private void clearTempModifiers(TurnSide side) {
        if (side == TurnSide.A) { tempDefBonusA = 0; damageReductionA = 0; }
        else                    { tempDefBonusB = 0; damageReductionB = 0; }
        tickExtendedModifiers(side);
    }

    private void tickExtendedModifiers(TurnSide side) {
        if (side == TurnSide.A) {
            if (dodgeTurnsA > 0) { dodgeTurnsA--; if (dodgeTurnsA == 0) dodgeChanceA = 0; }
            if (atkDownTurnsA > 0) { atkDownTurnsA--; if (atkDownTurnsA == 0) atkReductionA = 0; }
            if (statusImmuneA > 0) statusImmuneA--;
        } else {
            if (dodgeTurnsB > 0) { dodgeTurnsB--; if (dodgeTurnsB == 0) dodgeChanceB = 0; }
            if (atkDownTurnsB > 0) { atkDownTurnsB--; if (atkDownTurnsB == 0) atkReductionB = 0; }
            if (statusImmuneB > 0) statusImmuneB--;
        }
    }

    // ---- Extended combat API ----

    public void applyAtkDownOnTarget(Player target, double fraction, int turns) {
        boolean isA = (target == playerA);
        if (isA) { atkReductionA = Math.min(0.80, atkReductionA + fraction); atkDownTurnsA = turns; }
        else     { atkReductionB = Math.min(0.80, atkReductionB + fraction); atkDownTurnsB = turns; }
        if (target.isOnline()) target.sendMessage(ChatUtil.color("&c✦ ATK bị giảm &f" + (int)(fraction*100) + "%&c trong &f" + turns + "&c lượt!"));
    }

    public void grantReflect(Player owner, double fraction) {
        if (owner == playerA) reflectFractionA = Math.min(0.50, reflectFractionA + fraction);
        else                  reflectFractionB = Math.min(0.50, reflectFractionB + fraction);
    }

    public void grantDodgeChance(Player owner, double chance, int turns) {
        if (owner == playerA) { dodgeChanceA = Math.min(0.95, dodgeChanceA + chance); dodgeTurnsA = turns; }
        else                  { dodgeChanceB = Math.min(0.95, dodgeChanceB + chance); dodgeTurnsB = turns; }
    }

    public void grantDamageReductionHits(Player owner, double fraction, int hits) {
        if (owner == playerA) { dmgReductPerHitA = fraction; dmgReductHitsA += hits; }
        else                  { dmgReductPerHitB = fraction; dmgReductHitsB += hits; }
    }

    public void grantStatusImmunity(Player owner, int turns) {
        if (owner == playerA) statusImmuneA = Math.max(statusImmuneA, turns);
        else                  statusImmuneB = Math.max(statusImmuneB, turns);
    }

    public void grantLastStand(Player owner) {
        if (owner == playerA) lastStandA = true;
        else                  lastStandB = true;
        if (owner.isOnline()) owner.sendMessage(ChatUtil.color("&6✦ Iron Will sẵn sàng!"));
    }

    public void registerRebirth(Player owner, double healFraction) {
        if (owner == playerA) { rebirthFractionA = healFraction; rebirthFiredA = false; }
        else                  { rebirthFractionB = healFraction; rebirthFiredB = false; }
        if (owner.isOnline()) owner.sendMessage(ChatUtil.color("&6✦ Rebirth đã đăng ký!"));
    }

    public void scheduleRegen(Player owner, int healAmount, int intervalTurns, int maxTicks) {
        final int[] turnsElapsed = {0};
        final int[] healsLeft = {maxTicks};
        Runnable regenTask = new Runnable() {
            @Override public void run() {
                if (phase == Phase.ENDED || healsLeft[0] <= 0) return;
                turnsElapsed[0]++;
                if (turnsElapsed[0] % intervalTurns == 0) {
                    healPet(owner, healAmount);
                    healsLeft[0]--;
                }
            }
        };
        Object task;
        if (FoliaUtil.IS_FOLIA) {
            task = owner.getScheduler().runAtFixedRate(plugin, t -> {
                regenTask.run();
                if (phase == Phase.ENDED || healsLeft[0] <= 0) t.cancel();
            }, null, 20L, 20L);
        } else {
            org.bukkit.scheduler.BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override public void run() {
                    regenTask.run();
                }
            }, 20L, 20L);
            task = bukkitTask;
        }
        activeScheduled.add(task);
    }

    public void startBerserkerHeal(Player owner, double fraction, int hits) {
        if (owner == playerA) {
            berserkerHitsA = hits; berserkerFractionA = fraction; berserkerDmgA = 0;
        } else {
            berserkerHitsB = hits; berserkerFractionB = fraction; berserkerDmgB = 0;
        }
        Runnable berserkTask = () -> {
            if (phase == Phase.ENDED) return;
            boolean isA = (owner == playerA);
            int accumulated = isA ? berserkerDmgA : berserkerDmgB;
            double frac = isA ? berserkerFractionA : berserkerFractionB;
            int heal = (int)(accumulated * frac);
            if (heal > 0) {
                healPet(owner, heal);
                if (owner.isOnline()) owner.sendMessage(ChatUtil.color("&6✦ Berserker Heal! Hồi &f" + heal + " &6HP!"));
            }
            if (isA) { berserkerHitsA = 0; berserkerDmgA = 0; }
            else     { berserkerHitsB = 0; berserkerDmgB = 0; }
        };
        Object task;
        if (FoliaUtil.IS_FOLIA) {
            task = owner.getScheduler().runDelayed(plugin, t -> berserkTask.run(), null, 100L);
        } else {
            task = Bukkit.getScheduler().runTaskLater(plugin, berserkTask, 100L);
        }
        activeScheduled.add(task);
    }

    // ---- Getters for skill access ----

    public PetPlugin getPlugin()   { return plugin; }

    public int getHpOf(Player p)      { return p == playerA ? hpA : hpB; }
    public int getMaxHpOf(Player p)   { return p == playerA ? maxHpA : maxHpB; }

    public int getCurrentDefOf(Player p) {
        boolean isA = (p == playerA);
        PetData pet = isA ? petA : petB;
        return pet.getType().getBaseDef() + pet.getLevel()
                + (isA ? tempDefBonusA : tempDefBonusB);
    }

    public int getMaxDefOf(Player p) {
        PetData pet = (p == playerA) ? petA : petB;
        return pet.getType().getBaseDef() + pet.getLevel();
    }

    // ------------------------------------------------------------------ //
    //  BossBar
    // ------------------------------------------------------------------ //

    private Component buildBossBarTitle() {
        return ChatUtil.color(
                "&c" + petA.getName() + " " + hpA + "/" + maxHpA + " HP"
                + " &8| "
                + "&a" + petB.getName() + " " + hpB + "/" + maxHpB + " HP");
    }

    private void updateBossBar() {
        if (bossBar == null) return;
        float progress = (float) Math.max(0, Math.min(1.0, (double)(hpA + hpB) / (maxHpA + maxHpB)));
        bossBar.progress(progress);
        bossBar.name(buildBossBarTitle());
    }

    // ------------------------------------------------------------------ //
    //  Helpers
    // ------------------------------------------------------------------ //

    private void broadcast(String msg) {
        Component component = ChatUtil.color(msg);
        if (playerA.isOnline()) playerA.sendMessage(component);
        if (playerB.isOnline()) playerB.sendMessage(component);
    }

    public boolean isActive()         { return phase == Phase.ACTIVE; }
    public boolean isInArena()        { return inArena; }
    public Player getPlayerA()        { return playerA; }
    public Player getPlayerB()        { return playerB; }
    public PetData getPetA()          { return petA; }
    public PetData getPetB()          { return petB; }
    public TurnSide getCurrentTurn()  { return currentTurn; }
    public Map<Integer, Integer> getPpA() { return ppA; }
    public Map<Integer, Integer> getPpB() { return ppB; }
    public int getHpA()               { return hpA; }
    public int getHpB()               { return hpB; }
    public int getMaxHpA()            { return maxHpA; }
    public int getMaxHpB()            { return maxHpB; }
}
