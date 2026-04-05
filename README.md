# PetElite

<div align="center">

![PetElite](https://img.shields.io/badge/Plugin-PetElite-6A0DAD?style=for-the-badge&logo=minecraft&logoColor=white)
![Paper](https://img.shields.io/badge/Paper-1.21.x-F7A800?style=for-the-badge&logo=papermc&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-22C55E?style=for-the-badge)
![Version](https://img.shields.io/badge/Version-1.0.3-6A0DAD?style=for-the-badge)
![Status](https://img.shields.io/badge/Status-Production-22C55E?style=for-the-badge)

### *A Pokémon-inspired pet system for Minecraft economy servers.*

Choose your starter · Level up · Master skills · Battle for rank.

</div>

---

## 🔄 Latest Update

### v1.0.3 — Arena System + Full Audit + 200+ Player Optimization
- **Battle Arena System**: `/petop set battle` registers arena, automatic teleportation, 3-second countdown with Blindness + Slowness IV, boundary enforcement, post-battle teleport-back.
- **Full Codebase Audit**: 8 classes migrated to ConcurrentHashMap, 5 memory leaks fixed (PlayerQuitEvent cleanup), all scheduled tasks properly cancelled.
- **GlobalTickRunnable**: Single centralized tick loop replaces per-entity scheduling — O(1) scheduler overhead for any number of players.
- **Critical Bug Fix**: `BattleSession.checkWin()` now correctly uses battle HP (`hpA`/`hpB`) instead of `petData.isFainted()`.
- **Performance**: O(1) pet entity lookups, async inventory snapshot saves, particle culling.
- **Folia Fixes**: All remaining non-Folia scheduler calls in GUI handlers migrated.

## 🌟 Overview

**PetElite** is a feature-rich Paper plugin that brings a Pokémon-style companion system to your economy server. Players choose a starter pet, grow it through an EXP & level system, unlock powerful skills across three strategic branches, complete daily and weekly quests, and compete in 1v1 ranked turn-based battles.

### ✨ Key Highlights

| Feature | Description |
|---------|-------------|
| 🐾 **3 Unique Pets** | Turtle (Tank), Wolf (DPS), Cat (Support) — each with distinct stats and growth |
| ⚔️ **Skill Tree System** | 3 branches (ATK / DEF / HEAL) × 5 skills = **15 unique skills per pet** (45 total) |
| 📋 **Quest System** | Daily & weekly quests tied to your pet progress, auto-resetting on schedule |
| 🏆 **Ranked Battles** | 1v1 turn-based PvP with ELO rating and 6 rank tiers from Coal to Netherite |
| ✨ **Particle Effects** | Every skill triggers unique, hand-crafted particle animations |
| 📈 **Full Progression** | EXP → Level → Branch Points → Skill Unlock → Evolution (Level 50) |
| 🥚 **Mystery Egg** | Hatch a randomized pet with bonus starting stats |
| 🔄 **Multi-Pet Slots** | Earn additional slots through rank — switch pets on the fly |

---

## 🐾 Pets

| Pet | Role | Movement | Base HP | Base ATK | Base DEF | Growth (ATK/DEF/HEAL per level) |
|-----|------|----------|---------|----------|----------|----------------------------------|
| 🐢 **Turtle** | Tank | Floating (real Turtle entity, AI off, gravity off) | 100 | 8 | 20 | `+0 / +2 / +1` |
| 🐺 **Wolf** | DPS | Ground (manual follow, AI off) | 80 | 20 | 10 | `+2 / +1 / +0` |
| 🐱 **Cat** | Support | Ground (manual follow, AI off) | 80 | 12 | 8 | `+1 / +0 / +2` |

> **Turtle** floats beside you at Y+1.2 using a real Turtle entity with AI disabled and gravity off, lerping smoothly to stay 1.5 blocks behind.  
> **Wolf** and **Cat** use manual position updates (no pathfinder AI) and stay within 2 blocks of the player at all times.

---

## ⚔️ Skill System

Each pet has **3 branches** of skills, each with **5 tiers** unlocked sequentially.  
Each branch uses its own separate point pool, earned automatically on level-up based on pet growth rate.

### Branch Types

| Branch | Resource | Focus |
|--------|----------|-------|
| ⚔️ **ATK Branch** | ATK Points | Damage, debuffs, multi-hit |
| 🛡️ **DEF Branch** | DEF Points | Mitigation, dodge, reflect, crowd control |
| 💚 **HEAL Branch** | HEAL Points | Recovery, lifesteal, cleanse, rebirth |

### Unlock Cost & PP

| Slot | Unlock Cost (branch pts) | Max PP |
|------|--------------------------|--------|
| 1 | 1 pt | 10 PP |
| 2 | 2 pts | 8 PP |
| 3 | 3 pts | 8 PP |
| 4 | 4 pts | 4 PP |
| 5 | 5 pts | 2 PP |

> ⚠️ **PP does NOT regenerate during battle.** Manage your resources wisely.

### Skill Highlights (Sample)

| Pet | Skill | Branch | Effect |
|-----|-------|--------|--------|
| 🐢 Turtle | **Titan Crash** | ATK Slot 5 | 200% ATK + Stun 2 turns · EXPLOSION_LARGE particle |
| 🐢 Turtle | **Unbreakable** | DEF Slot 5 | Invincible 2 turns · once per battle · TOTEM erupt |
| 🐢 Turtle | **Eternal Shell** | HEAL Slot 5 | +40% HP + DEF +25% 5 turns · TOTEM + WATER_BUBBLE orbit |
| 🐺 Wolf | **Alpha Rend** | ATK Slot 5 | 220% ATK + Bleed + Stun · EXPLOSION + CRIT 360° |
| 🐺 Wolf | **Iron Will** | DEF Slot 5 | Survive 1 lethal hit at 1 HP · once per battle |
| 🐺 Wolf | **Berserker Heal** | HEAL Slot 5 | Heal 50% of damage dealt in 5 hits |
| 🐱 Cat | **Nine Lives Slash** | ATK Slot 5 | 40% ATK × 9 hits · SWEEP_ATTACK ×9 sequential |
| 🐱 Cat | **Ghost Step** | DEF Slot 5 | Invisible + immune 3 turns · once per battle |
| 🐱 Cat | **Rebirth** | HEAL Slot 5 | Auto-heal +60% HP when HP drops below 20% · once per battle |

---

## 🏆 Battle System

PetElite features a **1v1 turn-based ranked battle system** driven entirely through in-game GUIs and inventories.

### How It Works

```
/petbattle challenge <player>
  → Target accepts with /petbattle accept
  → Both players enter battle state
  → BattleSession starts:
      • Inventories snapshotted and cleared
      • Skill reference book given
      • HP BossBar displayed for both players
  → Players take turns using skills (30s per turn)
      • Select skill from the chest GUI (book = reference)
      • Damage, heals, and effects applied immediately
      • PP consumed — no regeneration
  → First pet to reach 0 HP loses
  → Inventories restored, ELO calculated, rank updated
```

### Rank Tiers

| Rank | ELO Range | Reward |
|------|-----------|--------|
| ⬛ **Coal** | 0 – 999 | Starting rank |
| 🟫 **Copper** | 1,000 – 1,999 | +1 Pet Slot |
| ⬜ **Iron** | 2,000 – 2,999 | *(more rewards soon)* |
| 🟡 **Gold** | 3,000 – 3,999 | *(more rewards soon)* |
| 💎 **Diamond** | 4,000 – 4,999 | +1 Pet Slot |
| 🔷 **Netherite** | 5,000+ | Elite status |

> ELO uses the standard **K=32** formula with expected score calculation:  
> `newELO = currentELO + 32 × (result − expected)`

### In-Battle Status Effects

| Effect | Mechanic |
|--------|----------|
| ☠️ **Poison** | Damage every turn |
| 🔥 **Burn** | Damage + DEF −20% |
| ⚡ **Stun** | Skip next turn |
| 🔻 **ATK Down** | Attacker's ATK reduced by % for N turns |
| 🛡️ **DEF Up** | Temporary DEF bonus this turn |
| ✨ **Status Immune** | Blocks all status effects for N turns |

---

## 📋 Quest System

Quests are tied to your **active pet's progress data** and reset automatically on schedule.

### 📅 Daily Quests *(Reset at 00:00)*

| Quest | Goal | Trigger |
|-------|------|---------|
| ⛏️ Mine Ore | Mine 32 ores | `BlockBreakEvent` |
| 🚶 Walk Blocks | Walk 500 blocks | `PlayerMoveEvent` (accumulated) |
| ⚔️ Kill Players | Kill 3 players | `PlayerDeathEvent` (check killer) |

### 📆 Weekly Quests *(Reset Monday 00:00)*

| Quest | Goal | Trigger |
|-------|------|---------|
| 💀 Kill Boss | Defeat 2 bosses (Wither / Elder Guardian) | `EntityDeathEvent` |
| 🏆 Win Battles | Win 5 ranked battles | `BattleSession.end()` callback |
| 🗺️ Long Journey | Walk 5,000 blocks | `PlayerMoveEvent` (accumulated) |
| 🔨 Craft Rare | Craft 10 rare items | `CraftItemEvent` |
| 🎉 Join Events | Participate in 3 server events | Manual API call |

---

## 🚀 Installation

### Requirements

- **Paper** 1.21.x (Spigot is **not** supported — Paper API is required)
- **Java** 21 or higher

### Steps

1. Download the latest `PetElite-x.x.x.jar` from [Releases](https://github.com/Konmeo22132-alt/PetElite/releases)
2. Drop the JAR into your server's `plugins/` folder
3. Restart (or reload) your Paper server
4. PetElite will generate its default config on first run
5. No additional dependencies required

---

## 🎮 Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/pet` | Open your pet menu (or choose starter) | `petplugin.use` |
| `/pet recall` | Toggle your pet (summon / despawn) | `petplugin.use` |
| `/petbattle challenge <player>` | Challenge another player to a ranked battle | `petplugin.battle` |
| `/petbattle accept` | Accept a pending battle challenge | `petplugin.battle` |
| `/petbattle surrender` | Forfeit the current battle | `petplugin.battle` |
| `/petbattle rank` | Open the rank viewer GUI | `petplugin.battle` |
| `/petop set battle` | Register an arena at your location | `petplugin.admin` |
| `/petop arena list` | List all registered arenas | `petplugin.admin` |
| `/petop arena remove <id>` | Remove an arena | `petplugin.admin` |

### Examples

```bash
# Open your pet menu
/pet

# Summon or recall your pet
/pet recall

# Challenge player Steve to a battle
/petbattle challenge Steve

# Accept Steve's challenge
/petbattle accept

# Surrender if you're losing
/petbattle surrender

# Register an arena at your current location (OP only)
/petop set battle

# List all registered arenas
/petop arena list
```

### Battle Arena Setup

1. Stand at the center of your desired battle arena
2. Run `/petop set battle` — registers a 40-block radius arena
3. Repeat for multiple arenas if needed
4. When players accept a battle challenge, they will be teleported to the least-busy arena
5. After the battle ends, both players are teleported back to their original locations

> If no arenas are registered, battles still work — players simply fight in place.

---

## 🗺️ Roadmap

- [x] Core pet system (Turtle / Wolf / Cat)
- [x] Per-pet skill tree (ATK / DEF / HEAL, 5 skills each)
- [x] Daily & Weekly quest system with auto-reset
- [x] 1v1 turn-based ranked battle system with ELO
- [x] Particle effects on every skill
- [x] Mystery Egg — hatch a random pet
- [x] Multi-pet slot GUI with pet switching
- [x] Battle Arena System with teleportation
- [x] Production-grade audit (ConcurrentHashMap, memory leaks, null safety)
- [x] GlobalTickRunnable for 200+ player servers
- [ ] Pet evolution at level 50
- [ ] Pet items & accessories (food, held items)
- [ ] Weekly quest rewards (loot tables)
- [ ] Database support (SQLite / MySQL via DataManager swap)
- [ ] In-memory data cache with async flush

---

## 🏗️ For Developers

PetElite is built with clean architecture and a swappable data layer.

```
com.petplugin/
├── PetPlugin.java          Main class, command routing
├── pet/                    FloatPet, GroundPet, PetType, PetManager
├── skill/                  SkillTree, SkillBranch, Skill, StatusEffectManager, ParticleHandler
├── quest/                  QuestTracker, QuestResetScheduler, DailyQuest, WeeklyQuest
├── battle/                 BattleSession, TurnManager, EloManager, ArenaManager
├── data/                   DataManager (interface), YamlDataManager, PetData, PlayerData
├── gui/                    PetSelectGUI, PetMainGUI, SkillTreeGUI, QuestGUI, RankGUI, PetSelectorGUI
├── listener/               PlayerListener, PetInteractListener, BattleListener
└── util/                   ChatUtil (Adventure API), GuiUtil
```

> The `DataManager` interface is designed to be swapped: replace `YamlDataManager` with a `SQLiteDataManager` or `MySQLDataManager` without touching any other module.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

Made with ❤️ for Minecraft economy servers

[📦 Modrinth](https://modrinth.com) · [🐛 Issues](https://github.com/Konmeo22132-alt/PetElite/issues) · [⭐ Star this repo](https://github.com/Konmeo22132-alt/PetElite)

</div>
