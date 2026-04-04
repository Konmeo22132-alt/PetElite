package com.petplugin.skill;

import com.petplugin.battle.BattleSession;
import com.petplugin.data.PetData;
import com.petplugin.pet.PetType;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Map;

/**
 * Static registry of all skill trees per pet type.
 *
 * Growth points for ATK/DEF/HEAL per level: TURTLE(0/2/1), WOLF(2/1/0), CAT(1/0/2).
 * Points are granted automatically by PetData.grantLevelUpPoints().
 * Skills unlock sequentially; cost = slotIndex+1 from branch's own point pool.
 * PP (max): slot0=10, slot1=8, slot2=6, slot3=4, slot4=2. No regen in battle.
 */
public class SkillTree {

    private static final Map<PetType, Map<BranchType, SkillBranch>> TREES =
            new EnumMap<>(PetType.class);

    static {
        for (PetType type : PetType.values()) {
            TREES.put(type, buildTree(type));
        }
    }

    public static Map<BranchType, SkillBranch> getTree(PetType type) { return TREES.get(type); }
    public static SkillBranch getBranch(PetType type, BranchType branch) {
        return TREES.get(type).get(branch);
    }
    public static Skill getSkill(PetType type, BranchType branch, int slotIndex) {
        SkillBranch b = getBranch(type, branch);
        return b == null ? null : b.getSkill(slotIndex);
    }

    // ================================================================== //
    //  Tree builder
    // ================================================================== //

    private static Map<BranchType, SkillBranch> buildTree(PetType petType) {
        Map<BranchType, SkillBranch> tree = new EnumMap<>(BranchType.class);
        tree.put(BranchType.ATK_BRANCH,  buildAtkBranch(petType));
        tree.put(BranchType.DEF_BRANCH,  buildDefBranch(petType));
        tree.put(BranchType.HEAL_BRANCH, buildHealBranch(petType));
        return tree;
    }

    // ================================================================== //
    //  ████████ TURTLE ████████
    // ================================================================== //

    // ---- TURTLE ATK ----

    private static SkillBranch buildAtkBranch(PetType petType) {
        return switch (petType) {
            case TURTLE -> buildTurtleAtk();
            case WOLF   -> buildWolfAtk();
            case CAT    -> buildCatAtk();
        };
    }

    private static SkillBranch buildTurtleAtk() {
        // 1. Shell Slam — 80% ATK | no effect | CD 5s | PP10
        Skill s0 = new Skill("TURTLE_ATK_0", "Shell Slam",
                "&7Gây 80% ATK. Không hiệu ứng.",
                BranchType.ATK_BRANCH, 0, 100) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 0.80));
                // BLOCK_CRACK (stone) burst at impact, 8 particles
                Location impact = t.getLocation().add(0, 1, 0);
                ParticleHandler.spawnBlockCrack(impact, 8);
            }
        };

        // 2. Spike Shot — 100% ATK | Slow 2s | CD 8s | PP8
        Skill s1 = new Skill("TURTLE_ATK_1", "Spike Shot",
                "&780% ATK + &9Slow 2s.",
                BranchType.ATK_BRANCH, 1, 160) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, atk); // 100% ATK
                s.applyStatusEffect(t, StatusEffectType.STUN, 2); // STUN = closest to Slow in battle
                // 3 CRIT particles straight line from pet to target
                ParticleHandler.spawnCritLine(a, t, 3);
            }
        };

        // 3. Crush — 120% ATK | Slow 3s + DEF -15% 3s | CD 10s | PP6
        Skill s2 = new Skill("TURTLE_ATK_2", "Crush",
                "&7120% ATK + DEF &c-15%&7 3s + Slow 3s.",
                BranchType.ATK_BRANCH, 2, 200) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 1.20));
                s.applyAtkDownOnTarget(t, 0.15, 3); // DEF -15% re-uses atkDown slot as def debuff
                s.applyStatusEffect(t, StatusEffectType.BURN, 3); // BURN = def reduction proxy
                // FALLING_DUST (brown) around target radius 1
                ParticleHandler.spawnFallingDust(t.getLocation().add(0, 1, 0), 12);
            }
        };

        // 4. Tidal Strike — 150% ATK | strong knockback | CD 14s | PP4
        Skill s3 = new Skill("TURTLE_ATK_3", "Tidal Strike",
                "&7150% ATK + knockback mạnh.",
                BranchType.ATK_BRANCH, 3, 280) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 1.50));
                // Knockback = apply stun for 1 turn in battle
                s.applyStatusEffect(t, StatusEffectType.STUN, 3);
                Plugin plugin = s.getPlugin();
                // WATER_SPLASH spirals around pet then shoots toward target
                ParticleHandler.spawnWaterSplashSpiral(a, t, plugin);
            }
        };

        // 5. Titan Crash — 200% ATK | Stun 2s | CD 20s | PP2
        Skill s4 = new Skill("TURTLE_ATK_4", "Titan Crash",
                "&7200% ATK + Stun 2s.",
                BranchType.ATK_BRANCH, 4, 400) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 2.00));
                s.applyStatusEffect(t, StatusEffectType.STUN, 2);
                // EXPLOSION_LARGE at impact + SMOKE_LARGE trails
                ParticleHandler.spawnExplosionLarge(t.getLocation().add(0, 1, 0));
            }
        };

        return new SkillBranch(BranchType.ATK_BRANCH, java.util.List.of(s0, s1, s2, s3, s4));
    }

    // ---- TURTLE DEF ----

    private static SkillBranch buildDefBranch(PetType petType) {
        return switch (petType) {
            case TURTLE -> buildTurtleDef();
            case WOLF   -> buildWolfDef();
            case CAT    -> buildCatDef();
        };
    }

    private static SkillBranch buildTurtleDef() {
        // 1. Hard Shell — DEF +20% 5s | PP10
        Skill s0 = new Skill("TURTLE_DEF_0", "Hard Shell",
                "&7DEF +20% trong 5 lượt.",
                BranchType.DEF_BRANCH, 0, 120) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                int bonus = (int)(s.getMaxDefOf(a) * 0.20);
                s.grantTempDefBonus(a, bonus);
                // ENCHANTMENT_TABLE orbit for 5 ticks
                ParticleHandler.spawnEnchantOrbit(a, 100, s.getPlugin());
            }
        };

        // 2. Thorn Armor — Reflect 15% damage received | PP8
        Skill s1 = new Skill("TURTLE_DEF_1", "Thorn Armor",
                "&7Phản lại 15% sát thương nhận.",
                BranchType.DEF_BRANCH, 1, 200) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.grantReflect(a, 0.15);
                // CRIT shoots from pet toward attacker on each hit — shown at activation
                ParticleHandler.spawnCritToward(a, t);
            }
        };

        // 3. Stone Skin — Cleanse + Status immune 3s | PP6
        Skill s2 = new Skill("TURTLE_DEF_2", "Stone Skin",
                "&7Xóa debuff + miễn trạng thái 3 lượt.",
                BranchType.DEF_BRANCH, 2, 240) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.clearAllDebuffs(a);
                s.grantStatusImmunity(a, 3);
                // SMOKE_NORMAL dense around pet body for 3s
                ParticleHandler.spawnSmokeDense(a, 60, s.getPlugin());
            }
        };

        // 4. Iron Fortress — DMG received -30% 5s | PP4
        Skill s3 = new Skill("TURTLE_DEF_3", "Iron Fortress",
                "&7Giảm sát thương nhận -30% trong 5 lượt.",
                BranchType.DEF_BRANCH, 3, 320) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.grantDamageReduction(a, 0.30);
                // VILLAGER_HAPPY orbit
                ParticleHandler.spawnHappyOrbit(a, 100, s.getPlugin());
            }
        };

        // 5. Unbreakable — Invincible 2s, once per battle | PP2
        Skill s4 = new Skill("TURTLE_DEF_4", "Unbreakable",
                "&7Bất tử 2 lượt (1 lần/trận).",
                BranchType.DEF_BRANCH, 4, 900) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.grantImmunity(a, 2);
                // TOTEM erupts upward
                ParticleHandler.spawnTotemErupt(a);
            }
        };

        return new SkillBranch(BranchType.DEF_BRANCH, java.util.List.of(s0, s1, s2, s3, s4));
    }

    // ---- TURTLE HEAL ----

    private static SkillBranch buildHealBranch(PetType petType) {
        return switch (petType) {
            case TURTLE -> buildTurtleHeal();
            case WOLF   -> buildWolfHeal();
            case CAT    -> buildCatHeal();
        };
    }

    private static SkillBranch buildTurtleHeal() {
        // 1. Tide Pulse — +15% max HP | PP10
        Skill s0 = new Skill("TURTLE_HEAL_0", "Tide Pulse",
                "&7Hồi +15% HP tối đa.",
                BranchType.HEAL_BRANCH, 0, 160) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                int heal = (int)(s.getMaxHpOf(a) * 0.15);
                s.healPet(a, heal);
                ParticleHandler.spawnWaterBubbleRing(a);
            }
        };

        // 2. Shell Mend — heal = DEF × 0.5 | PP8
        Skill s1 = new Skill("TURTLE_HEAL_1", "Shell Mend",
                "&7Hồi = DEF × 0.5.",
                BranchType.HEAL_BRANCH, 1, 200) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                // Use defender's DEF stat as healer's DEF here
                int heal = (int)(s.getCurrentDefOf(a) * 0.5);
                s.healPet(a, Math.max(1, heal));
                ParticleHandler.spawnEnchantedHitInflow(a, a, 10, s.getPlugin());
            }
        };

        // 3. Ocean Breath — +8% max HP every 3s for 9s | PP6
        Skill s2 = new Skill("TURTLE_HEAL_2", "Ocean Breath",
                "&7Hồi +8% HP mỗi 3 lượt trong 9 lượt.",
                BranchType.HEAL_BRANCH, 2, 280) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.scheduleRegen(a, (int)(s.getMaxHpOf(a) * 0.08), 3, 3); // 3 ticks, every 3 turns
                ParticleHandler.spawnDripWater(a, 180, s.getPlugin());
            }
        };

        // 4. Ancient Tide — +35% max HP | PP4
        Skill s3 = new Skill("TURTLE_HEAL_3", "Ancient Tide",
                "&7Hồi +35% HP tối đa.",
                BranchType.HEAL_BRANCH, 3, 360) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.healPet(a, (int)(s.getMaxHpOf(a) * 0.35));
                ParticleHandler.spawnWaterColumn(a);
            }
        };

        // 5. Eternal Shell — +40% HP + DEF +25% 5s | PP2
        Skill s4 = new Skill("TURTLE_HEAL_4", "Eternal Shell",
                "&7+40% HP + DEF +25% trong 5 lượt.",
                BranchType.HEAL_BRANCH, 4, 500) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.healPet(a, (int)(s.getMaxHpOf(a) * 0.40));
                s.grantTempDefBonus(a, (int)(s.getMaxDefOf(a) * 0.25));
                ParticleHandler.spawnEternalShellAura(a, 40, s.getPlugin());
            }
        };

        return new SkillBranch(BranchType.HEAL_BRANCH, java.util.List.of(s0, s1, s2, s3, s4));
    }

    // ================================================================== //
    //  ████████ WOLF ████████
    // ================================================================== //

    private static SkillBranch buildWolfAtk() {
        // 1. Bite — 90% ATK | no effect | CD 4s | PP10
        Skill s0 = new Skill("WOLF_ATK_0", "Bite",
                "&790% ATK. Không hiệu ứng.",
                BranchType.ATK_BRANCH, 0, 80) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 0.90));
                // CRIT (red) from wolf toward target, 5 particles
                ParticleHandler.spawnCritLine(a, t, 5);
            }
        };

        // 2. Feral Slash — 70% ATK × 2 hits | CD 6s | PP8
        Skill s1 = new Skill("WOLF_ATK_1", "Feral Slash",
                "&770% ATK × 2 đòn.",
                BranchType.ATK_BRANCH, 1, 120) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 0.70));
                s.dealDamage(a, (int)(atk * 0.70));
                // SWEEP_ATTACK twice 0.3s apart
                ParticleHandler.spawnSweepDouble(t.getLocation().add(0, 1, 0), s.getPlugin());
            }
        };

        // 3. Blood Fang — 110% ATK | Poison 3s | CD 8s | PP6
        Skill s2 = new Skill("WOLF_ATK_2", "Blood Fang",
                "&7110% ATK + Poison 3s.",
                BranchType.ATK_BRANCH, 2, 160) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 1.10));
                s.applyStatusEffect(t, StatusEffectType.POISON, 3);
                // VILLAGER_ANGRY (purple) at bite point radius 0.5
                ParticleHandler.spawnAngryCloud(t.getLocation().add(0, 1, 0), 0.5);
            }
        };

        // 4. Pack Strike — 160% ATK | target DEF -20% 4s | CD 12s | PP4
        Skill s3 = new Skill("WOLF_ATK_3", "Pack Strike",
                "&7160% ATK + DEF đối thủ &c-20%&7 4s.",
                BranchType.ATK_BRANCH, 3, 240) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 1.60));
                s.applyAtkDownOnTarget(t, 0.20, 4);
                // CRIT_MAGIC (gold) converges from surroundings into target
                ParticleHandler.spawnEnchantedConverge(t);
            }
        };

        // 5. Alpha Rend — 220% ATK | Bleed 3s + Stun 1.5s | CD 20s | PP2
        Skill s4 = new Skill("WOLF_ATK_4", "Alpha Rend",
                "&7220% ATK + Bleed(Poison) 3s + Stun 2s.",
                BranchType.ATK_BRANCH, 4, 400) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 2.20));
                s.applyStatusEffect(t, StatusEffectType.POISON, 3); // Bleed = Poison
                s.applyStatusEffect(t, StatusEffectType.STUN, 2);
                // EXPLOSION_NORMAL + CRIT (red) 360°
                ParticleHandler.spawnExplosionCrit360(t.getLocation().add(0, 1, 0));
            }
        };

        return new SkillBranch(BranchType.ATK_BRANCH, java.util.List.of(s0, s1, s2, s3, s4));
    }

    private static SkillBranch buildWolfDef() {
        // 1. Fur Guard — DMG received -15% 4s | PP10
        Skill s0 = new Skill("WOLF_DEF_0", "Fur Guard",
                "&7Giảm sát thương nhận -15% trong 4 lượt.",
                BranchType.DEF_BRANCH, 0, 120) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.grantDamageReduction(a, 0.15);
                // SMOKE_NORMAL thin around wolf body
                ParticleHandler.spawnSmokeDense(a, 80, s.getPlugin());
            }
        };

        // 2. Dodge — Evade next hit completely | PP8
        Skill s1 = new Skill("WOLF_DEF_1", "Dodge",
                "&7Né tránh hoàn toàn đòn tiếp theo.",
                BranchType.DEF_BRANCH, 1, 200) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.grantImmunity(a, 1); // immunity for 1 hit = full evade
                // CLOUD burst from wolf feet
                ParticleHandler.spawnCloudBurst(a);
            }
        };

        // 3. Intimidate — target ATK -25% 5s | PP6
        Skill s2 = new Skill("WOLF_DEF_2", "Intimidate",
                "&7ATK đối thủ &c-25%&7 5 lượt.",
                BranchType.DEF_BRANCH, 2, 240) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.applyAtkDownOnTarget(t, 0.25, 5);
                // SMOKE_LARGE cone from wolf mouth toward target
                ParticleHandler.spawnSmokeCone(a, t);
            }
        };

        // 4. Wild Stance — DEF +40% when HP below 40% | PP4
        Skill s3 = new Skill("WOLF_DEF_3", "Wild Stance",
                "&7DEF +40% (chỉ khi HP < 40%).",
                BranchType.DEF_BRANCH, 3, 300) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                if (s.getHpOf(a) <= s.getMaxHpOf(a) * 0.40) {
                    s.grantTempDefBonus(a, (int)(s.getMaxDefOf(a) * 0.40));
                    ParticleHandler.spawnHeartPulse(a, 2, s.getPlugin());
                } else {
                    a.sendMessage(com.petplugin.util.ChatUtil.color("&cHP phải dưới 40% để dùng Wild Stance!"));
                    // Refund PP — done by caller if needed; for now, skill still fires but does nothing
                }
            }
        };

        // 5. Iron Will — Survive 1 lethal hit at 1 HP, once per battle | PP2
        Skill s4 = new Skill("WOLF_DEF_4", "Iron Will",
                "&7Sống sót 1 đòn chí mạng với 1 HP (1 lần/trận).",
                BranchType.DEF_BRANCH, 4, 900) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.grantLastStand(a); // survive next lethal hit at 1 HP
                // TOTEM (gold) + CRIT (white) 360°
                ParticleHandler.spawnTotemErupt(a);
                ParticleHandler.spawnExplosionCrit360(a.getLocation().add(0, 1, 0));
            }
        };

        return new SkillBranch(BranchType.DEF_BRANCH, java.util.List.of(s0, s1, s2, s3, s4));
    }

    private static SkillBranch buildWolfHeal() {
        // 1. Lick Wound — +12% max HP | PP10
        Skill s0 = new Skill("WOLF_HEAL_0", "Lick Wound",
                "&7Hồi +12% HP tối đa.",
                BranchType.HEAL_BRANCH, 0, 140) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.healPet(a, (int)(s.getMaxHpOf(a) * 0.12));
                ParticleHandler.spawnHeartFloat(a, 4);
            }
        };

        // 2. Primal Hunger — Lifesteal 20% of damage dealt | PP8
        Skill s1 = new Skill("WOLF_HEAL_1", "Primal Hunger",
                "&7Lifesteal 20% sát thương gây ra.",
                BranchType.HEAL_BRANCH, 1, 200) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                int dmg = atk; // 100% ATK base strike
                s.dealDamage(a, dmg);
                int steal = (int)(dmg * 0.20);
                s.healPet(a, steal);
                // CRIT_MAGIC (red) flows from target back to wolf
                ParticleHandler.spawnEnchantedHitInflow(t, a, 10, s.getPlugin());
            }
        };

        // 3. Pack Bond — +10% HP every 3 ticks, max 3 stacks | PP6
        Skill s2 = new Skill("WOLF_HEAL_2", "Pack Bond",
                "&7Hồi +10% HP mỗi 3 lượt, tối đa 3 lần.",
                BranchType.HEAL_BRANCH, 2, 280) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.scheduleRegen(a, (int)(s.getMaxHpOf(a) * 0.10), 3, 3);
                // VILLAGER_HAPPY travels between wolf and player
                ParticleHandler.spawnHappyTether(a, a, 180, s.getPlugin());
            }
        };

        // 4. Howl Heal — +30% max HP | PP4
        Skill s3 = new Skill("WOLF_HEAL_3", "Howl Heal",
                "&7Hồi +30% HP tối đa.",
                BranchType.HEAL_BRANCH, 3, 360) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.healPet(a, (int)(s.getMaxHpOf(a) * 0.30));
                // NOTE (gold) burst + CLOUD white
                ParticleHandler.spawnNoteCloud(a);
                ParticleHandler.spawnCloudCloud(a, 20, 1.0, s.getPlugin());
            }
        };

        // 5. Berserker Heal — heal = 50% of total dmg dealt in 5 attack hits | PP2
        Skill s4 = new Skill("WOLF_HEAL_4", "Berserker Heal",
                "&7Hồi 50% tổng sát thương gây ra trong 5 đòn tiếp.",
                BranchType.HEAL_BRANCH, 4, 440) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.startBerserkerHeal(a, 0.50, 5);
                // CRIT (red) on every hit during window — shown at activation
                ParticleHandler.spawnCritLine(a, t, 5);
            }
        };

        return new SkillBranch(BranchType.HEAL_BRANCH, java.util.List.of(s0, s1, s2, s3, s4));
    }

    // ================================================================== //
    //  ████████ CAT ████████
    // ================================================================== //

    private static SkillBranch buildCatAtk() {
        // 1. Scratch — 85% ATK | no effect | CD 4s | PP10
        Skill s0 = new Skill("CAT_ATK_0", "Scratch",
                "&785% ATK. Không hiệu ứng.",
                BranchType.ATK_BRANCH, 0, 80) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 0.85));
                // SWEEP_ATTACK small at target
                ParticleHandler.spawnHitParticle(t);
            }
        };

        // 2. Pounce — 105% ATK | Slow 2s | CD 7s | PP8
        Skill s1 = new Skill("CAT_ATK_1", "Pounce",
                "&7105% ATK + Stun 2s.",
                BranchType.ATK_BRANCH, 1, 140) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 1.05));
                s.applyStatusEffect(t, StatusEffectType.STUN, 2);
                // CLOUD at launch + CRIT at landing
                ParticleHandler.spawnCloudBurst(a);
                ParticleHandler.spawnCritLine(a, t, 3);
            }
        };

        // 3. Venom Claw — 95% ATK | Poison 4s | CD 9s | PP6
        Skill s2 = new Skill("CAT_ATK_2", "Venom Claw",
                "&795% ATK + Poison 4s.",
                BranchType.ATK_BRANCH, 2, 180) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 0.95));
                s.applyStatusEffect(t, StatusEffectType.POISON, 4);
                // VILLAGER_ANGRY (green) trails
                ParticleHandler.spawnAngryCloud(t.getLocation().add(0, 1, 0), 0.5);
            }
        };

        // 4. Shadow Strike — 140% ATK | Blind 2s | CD 13s | PP4
        Skill s3 = new Skill("CAT_ATK_3", "Shadow Strike",
                "&7140% ATK + STUN (Blind) 2s.",
                BranchType.ATK_BRANCH, 3, 260) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.dealDamage(a, (int)(atk * 1.40));
                s.applyStatusEffect(t, StatusEffectType.STUN, 2); // Blind = STUN proxy
                // CRIT_MAGIC (purple) burst at appear point
                ParticleHandler.spawnEnchantedConverge(t);
            }
        };

        // 5. Nine Lives Slash — 40% ATK × 9 hits | CD 20s | PP2
        Skill s4 = new Skill("CAT_ATK_4", "Nine Lives Slash",
                "&740% ATK × 9 đòn liên tiếp.",
                BranchType.ATK_BRANCH, 4, 400) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                for (int i = 0; i < 9; i++) {
                    s.dealDamage(a, (int)(atk * 0.40));
                }
                // SWEEP_ATTACK × 9 sequential 0.1s apart
                ParticleHandler.spawnSweepSequential(t.getLocation().add(0, 1, 0), 9, s.getPlugin());
            }
        };

        return new SkillBranch(BranchType.ATK_BRANCH, java.util.List.of(s0, s1, s2, s3, s4));
    }

    private static SkillBranch buildCatDef() {
        // 1. Nimble — dodge chance +15% 4s | PP10
        Skill s0 = new Skill("CAT_DEF_0", "Nimble",
                "&7Tăng khả năng né tránh +15% 4 lượt.",
                BranchType.DEF_BRANCH, 0, 120) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.grantDodgeChance(a, 0.15, 4);
                // CRIT_MAGIC white fast orbit
                ParticleHandler.spawnFastOrbit(a, 80, s.getPlugin());
            }
        };

        // 2. Hiss — target ATK -20% 4s | PP8
        Skill s1 = new Skill("CAT_DEF_1", "Hiss",
                "&7ATK đối thủ &c-20%&7 4 lượt.",
                BranchType.DEF_BRANCH, 1, 180) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.applyAtkDownOnTarget(t, 0.20, 4);
                // SMOKE_LARGE small cone from cat face toward target
                ParticleHandler.spawnSmokeCone(a, t);
            }
        };

        // 3. Smoke Veil — target miss chance +30% 3s | PP6
        Skill s2 = new Skill("CAT_DEF_2", "Smoke Veil",
                "&7Miss chance +30% 3 lượt (né tránh cao).",
                BranchType.DEF_BRANCH, 2, 240) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.grantDodgeChance(a, 0.30, 3);
                // CLOUD white dense radius 2
                ParticleHandler.spawnCloudCloud(a, 60, 2.0, s.getPlugin());
            }
        };

        // 4. Reflex Guard — next 2 hits reduced by 50% each | PP4
        Skill s3 = new Skill("CAT_DEF_3", "Reflex Guard",
                "&72 đòn tiếp theo giảm 50% sát thương.",
                BranchType.DEF_BRANCH, 3, 300) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.grantDamageReductionHits(a, 0.50, 2);
                // ENCHANTMENT_TABLE (light blue) flash
                ParticleHandler.spawnEnchantFlash(a);
            }
        };

        // 5. Ghost Step — Invisible 3s + immune to all skills, once per battle | PP2
        Skill s4 = new Skill("CAT_DEF_4", "Ghost Step",
                "&7Tàng hình + miễn sát thương 3 lượt (1 lần/trận).",
                BranchType.DEF_BRANCH, 4, 900) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.grantImmunity(a, 3);
                s.grantStatusImmunity(a, 3);
                // Faint CRIT_MAGIC pulses every 0.5s during invisible period
                ParticleHandler.spawnFastOrbit(a, 60, s.getPlugin());
            }
        };

        return new SkillBranch(BranchType.DEF_BRANCH, java.util.List.of(s0, s1, s2, s3, s4));
    }

    private static SkillBranch buildCatHeal() {
        // 1. Purr Heal — +12% max HP | PP10
        Skill s0 = new Skill("CAT_HEAL_0", "Purr Heal",
                "&7Hồi +12% HP tối đa.",
                BranchType.HEAL_BRANCH, 0, 140) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.healPet(a, (int)(s.getMaxHpOf(a) * 0.12));
                ParticleHandler.spawnHeartFloat(a, 5);
            }
        };

        // 2. Catnap — +20% max HP, cat immobile 1s | PP8
        Skill s1 = new Skill("CAT_HEAL_1", "Catnap",
                "&7Hồi +20% HP (bất động 1 lượt).",
                BranchType.HEAL_BRANCH, 1, 240) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.healPet(a, (int)(s.getMaxHpOf(a) * 0.20));
                // "immobile" — skip next ability interaction for 1 tick (handled via stun on self, but
                // we do NOT apply stun to self — instead already used their own turn for this skill)
                ParticleHandler.spawnCloudCloud(a, 20, 1.0, s.getPlugin());
                ParticleHandler.spawnHeartFloat(a, 5);
            }
        };

        // 3. Cleanse — remove all debuffs | PP6
        Skill s2 = new Skill("CAT_HEAL_2", "Cleanse",
                "&7Xóa toàn bộ debuff.",
                BranchType.HEAL_BRANCH, 2, 260) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                s.clearAllDebuffs(a);
                // VILLAGER_HAPPY (green) burst 360°
                ParticleHandler.spawnHappyOrbit(a, 20, s.getPlugin());
            }
        };

        // 4. Life Drain — steal 25% of target current HP | PP4
        Skill s3 = new Skill("CAT_HEAL_3", "Life Drain",
                "&7Hút 25% HP hiện tại của đối thủ.",
                BranchType.HEAL_BRANCH, 3, 360) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                int stolen = (int)(s.getHpOf(t) * 0.25);
                s.dealDamageRaw(a, stolen); // deals to target
                s.healPet(a, stolen);        // heals self
                // CRIT_MAGIC (red) slow stream from target to cat
                ParticleHandler.spawnLifeDrainStream(t, a, 20, s.getPlugin());
            }
        };

        // 5. Rebirth — auto-heal +60% HP when HP drops below 20%, once per battle | PP2
        Skill s4 = new Skill("CAT_HEAL_4", "Rebirth",
                "&7Tự động hồi +60% HP khi HP < 20% (1 lần/trận).",
                BranchType.HEAL_BRANCH, 4, 900) {
            @Override public void executeInBattle(Player a, Player t, BattleSession s, int atk, int def) {
                // Register a rebirth trigger — when HP drops below 20%, auto-heal fires
                s.registerRebirth(a, 0.60);
                // TOTEM white + HEART (red) 360° burst on activation
                ParticleHandler.spawnTotemErupt(a);
                ParticleHandler.spawnHeartFloat(a, 12);
            }
        };

        return new SkillBranch(BranchType.HEAL_BRANCH, java.util.List.of(s0, s1, s2, s3, s4));
    }
}
