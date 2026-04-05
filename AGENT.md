# AGENT.md — PetPlugin Handoff Document

> **Đọc file này trước khi làm bất cứ điều gì.** File này được cập nhật liên tục sau mỗi session để agent tiếp theo không cần đọc lại toàn bộ code.

---

## Project Overview
Paper 1.21.x plugin. Hệ thống pet cho server economy: chọn pet → progression (level/exp/skill) → quest → battle turn-based rank mode.

**Main class:** `com.petplugin.PetPlugin`  
**Build:** Maven, Java 21  
**Storage:** YAML (tạm thời), DataManager interface sẵn sàng swap sang DB

---

## Current Status
> ⚠️ **Cập nhật phần này sau mỗi session**

[x] PetPlugin.java (main)
[x] pet/ module
[x] skill/ module
[x] quest/ module
[x] battle/ module
[x] data/ module
[x] gui/ module
[x] listener/ module
[x] item/ module
[x] pet/PetRespawnManager.java
[x] util/ module

### v1.0.3 Task Status (2026-04-05)
[x] TASK 1 — Full Codebase Audit:
  - Thread safety: ConcurrentHashMap migrated for BattleManager, ArenaManager, TurnManager, BattleSkillClickHandler, BasicAttackHandler, MysteryEggListener, PetMainGUI
  - Null safety: EloManager.calculate() null guard, all sendMessage guarded with isOnline()
  - Memory leaks: All per-player maps (lastAttack, cooldowns, awaitingName, pending, statusEffects) cleaned on PlayerQuitEvent
  - Performance: O(1) entityId→owner reverse lookup in PetManager, async InventorySnapshot.saveToDisk()
  - Battle fix: checkWin() now uses hpA/hpB<=0, all scheduled tasks cancelled on end()
  - Folia safety: BattleSkillClickHandler, PetSelectorGUI, PetMainGUI rename fixed
[x] TASK 2 — Battle Arena System:
  - ArenaManager complete rewrite: registration, persistence, load balancing, teleportation
  - /petop set battle, /petop arena list, /petop arena remove commands
  - 3-second countdown with Blindness + Slowness IV
  - Boundary enforcement via GlobalTickRunnable (10 tick interval)
  - Post-battle cleanup: teleport-back, potion clear, arena count decrement
[x] TASK 3 — Production Readiness (200+ Players):
  - GlobalTickRunnable: single centralized tick replaces FloatPet/GroundPet self-ticking
  - PetManager: tick() called by GlobalTickRunnable, O(1) scheduler overhead
  - config.yml: arena_max_concurrent_battles, particle_cull_radius, data_flush_interval_seconds  
  - PetRespawnManager: non-Folia particle runnable now properly cancelled after 20 ticks
[x] TASK 4 — Release v1.0.3:
  - pom.xml, plugin.yml updated
  - AGENT.md, README.md updated
  - Git commit + tag + push

### v1.0.2 Task Status
[x] TASK 1 — Folia Support
[x] TASK 2 — Turtle Rendering
[x] TASK 3 — Rank-up Pet Slot Unlock
[x] TASK 4 — Rank GUI Error Handling
[x] TASK 5 — Multi-GUI Bug Exploits
[x] TASK 6 — Basic Attack
[x] TASK 7 — Pet Follow Rework
[x] TASK 8 — Production Hardening
[x] TASK 9 — Versioning

**Last completed:** 2026-04-05 v1.0.3 Arena System + Full Audit + 200+ Player Optimization.
**Next task:** Pet evolution (placeholder), SQL DB transition, in-memory PlayerData cache with async flush.

---

## Architecture Summary

### Package Structure
```
com.petplugin/
├── PetPlugin.java
├── pet/        FloatPet (Turtle Entity), GroundPet (Pathfinding), GlobalTickRunnable, PetManager
├── skill/      SkillTree, SkillBranch (ATK/DEF/HEAL), Skill, StatusEffect
├── quest/      DailyQuest, WeeklyQuest, QuestTracker, QuestResetScheduler
├── battle/     BattleSession, TurnManager, BookSkillHandler, EloManager, ArenaManager
├── data/       PlayerData, PetData, DataManager (interface), YamlDataManager
├── gui/        PetSelectGUI, PetMainGUI, SkillTreeGUI, QuestGUI, RankGUI, PetSelectorGUI
├── listener/   PlayerListener, PetInteractListener, BattleListener, BasicAttackHandler, MysteryEggListener
└── util/       ChatUtil, GuiUtil, FoliaUtil, LangManager
```

### Key Design Decisions
1. **DataManager là interface** — impl hiện tại là YamlDataManager, sau swap sang SQLite/MySQL không cần sửa code khác
2. **FloatPet dùng Turtle Entity + lerp** — không dùng ArmorStand, không dùng Shulker
3. **Battle uses 27-slot chest GUI** — book is reference card only, skills selected via inventory click
4. **PP không hồi trong trận** — resource management hoàn toàn
5. **Status effect hoạt động cả trong lẫn ngoài battle** — StatusEffectManager tick độc lập
6. **GlobalTickRunnable (v1.0.3)** — single centralized tick loop replaces per-entity scheduling. O(1) scheduler overhead for n players. Each PetEntity.tick() method remains callable for fallback.
7. **ConcurrentHashMap everywhere** — all shared-state maps are thread-safe for Folia region thread access
8. **Arena system** — ArenaManager handles registration, load balancing, teleportation, countdown, boundary enforcement. Falls back to in-place battle if no arena registered.

---

## Data Models

### PetData
```java
UUID id
UUID ownerUuid
PetType type          // TURTLE, WOLF, CAT
String name
int level             // 1-50
long currentExp
int atkPoints
int defPoints
int healPoints
Map<BranchType, List<Integer>> unlockedSkills  // branch -> list of unlocked slot index
Map<String, Integer> questProgress             // questId -> current count
long lastDailyReset   // epoch millis
long lastWeeklyReset  // epoch millis
```

### PlayerData
```java
UUID uuid
int elo               // default 500
RankTier rank         // COAL, COPPER, IRON, GOLD, DIAMOND, NETHERITE
int petSlots          // default 1
UUID activePetId
```

---

## Pet System

### Types & Stats
| Pet | Type | HP | ATK | DEF | Growth |
|-----|------|----|-----|-----|--------|
| TURTLE | Float | 100 | 8 | 20 | +0/+2/+1 per level |
| WOLF | Ground | 80 | 20 | 10 | +2/+1/+0 per level |
| CAT | Ground | 80 | 12 | 8 | +1/+0/+2 per level |

### Locomotion
- **FloatPet:** Turtle entity, lerp Y mỗi tick, target Y = player.y + 1.2, float animation ±0.1 block
- **GroundPet:** Spawn actual mob entity với custom AI off, follow player via manual lerp

### GlobalTickRunnable (v1.0.3)
- Single runnable registered on plugin enable
- Calls `PetManager.tick()` which iterates all active pets and calls `PetEntity.tick()` on each
- Also checks arena boundaries every 10 ticks
- FloatPet/GroundPet no longer self-tick — removed `startTickLoop()` and `cancelTickLoop()`

---

## Skill System

### Branches
- `ATK_BRANCH` — tốn atkPoints
- `DEF_BRANCH` — tốn defPoints
- `HEAL_BRANCH` — tốn healPoints

### Unlock Cost (per slot index 0-4)
`cost = slotIndex + 1` (1, 2, 3, 4, 5)

### PP (per slot index 0-4)
`pp = 10 - (slotIndex * 2)` (10, 8, 6, 4, 2)

### Status Effects
```
POISON → VILLAGER_ANGRY particle (green) → damage every 20 ticks
BURN   → FLAME particle → damage + DEF -20%
STUN   → CRIT_MAGIC particle → battle: skip turn | outside: Slowness III 3s
```

---

## Quest System

### Daily (reset 00:00)
- `MINE_ORE` target=32
- `WALK_BLOCKS` target=500
- `KILL_PLAYERS` target=3

### Weekly (reset Monday 00:00)
- `KILL_BOSS` target=2
- `WIN_BATTLES` target=5
- `LONG_JOURNEY` target=5000
- `CRAFT_RARE` target=10
- `JOIN_EVENTS` target=3

---

## Battle System

### Flow (v1.0.3)
```
/petbattle challenge <player>
  → target /petbattle accept
  → ArenaManager.findAvailableArena()
    → if arena available: teleport + 3s countdown (Blindness + Slowness IV)
    → if no arena: in-place battle (freeze only)
  → BattleSession.start()
    → snapshot inventory, clear, give skill book
    → show BossBar
  → Turn-based combat via BattleSkillClickHandler
    → checkWin() uses hpA/hpB <= 0 (NOT petData.isFainted())
  → BattleSession.end(winner)
    → restore inventory, ELO calc, rank reward
    → if in arena: ArenaManager.cleanupBattle() (teleport back, remove effects)
    → cancel all scheduled regen/berserker tasks
```

### Arena System (v1.0.3)
```
/petop set battle       — register arena at current location (40 block radius)
/petop arena list       — list all registered arenas with active battle counts
/petop arena remove <id> — remove arena

Arena selection: find arena with lowest active battle count that has capacity
Capacity: min(radius/10, config arena_max_concurrent_battles)
```

### ELO Formula
```java
double expected = 1.0 / (1 + Math.pow(10, (opponentElo - playerElo) / 400.0));
int newElo = (int)(currentElo + 32 * (won ? 1 : 0 - expected));
```

### Rank Thresholds
```
COAL:      0 – 999
COPPER:    1000 – 1999
IRON:      2000 – 2999
GOLD:      3000 – 3999
DIAMOND:   4000 – 4999
NETHERITE: 5000+
```

---

## Known Issues / Watch Out

### ✅ Fixed in v1.0.3
- **BattleSession.checkWin() used petData.isFainted():** This was wrong — battle HP is tracked separately in hpA/hpB, not in PetData. petData.isFainted() is for world faint state only. Fixed to check `hpA <= 0` / `hpB <= 0`.
- **Scheduled regen/berserker tasks not cancelled:** `BattleSession.activeScheduled` list was never cleaned. Now explicitly cancelled in `end()`.
- **8 classes using non-thread-safe HashMap/HashSet:** All migrated to ConcurrentHashMap.
- **5 per-player maps never cleaned on quit:** All cleaned via `cleanupPlayer(UUID)` methods on PlayerQuitEvent.
- **PetRespawnManager particle BukkitRunnable:** Non-Folia path never cancelled after 20 ticks. Now tracked and auto-cancelled.
- **InventorySnapshot.saveToDisk() synchronous:** Moved to async, errors logged instead of swallowed.
- **PetManager.isPetEntity()/getOwnerOf() O(n):** Added reverse lookup map `entityId→ownerUUID` for O(1) checks.
- **BattleSkillClickHandler/PetSelectorGUI/PetMainGUI not Folia-safe:** All migrated to FoliaUtil branching.

### ⚠️ Still active / deferred to v1.1
- **BattleSession stores direct Player references:** Should be UUID-based. Deferred to v1.1 due to massive blast radius (~50 lines). All Player accesses now guarded with `isOnline()`.
- **Quest progress YAML writes:** QuestTracker.increment() → savePet() on every single quest event. WALK_BLOCKS fires every block. Need in-memory cache with periodic flush. (partially addressed by SaveAll pattern).
- **Float pet entity not persisted:** If server restarts while pet is out, entity lost. Normal summon flow re-creates.
- **GlobalTickRunnable fallback:** If global tick fails (exception), all pets stop. Monitor for any tick() exceptions in production. If regression detected, revert to self-ticking and note in AGENT.md.

---

## Open Items (chưa implement, không được tự ý implement)
- [ ] Pet evolution (level 50) — placeholder method `onEvolve()` thôi
- [ ] Pet items (food, accessory)
- [ ] Weekly quest rewards cụ thể
- [ ] Swap YAML → SQL DB (DataManager interface ready)
- [ ] In-memory PlayerData/PetData cache with async flush (partially planned in v1.0.3)
- [x] ~~Arena teleport~~ — Implemented in v1.0.3
- [x] ~~Multiple pet slot mechanics~~ — Implemented (PetSelectorGUI)

---

## How to Continue
1. Đọc file này
2. Check `Current Status` section để biết đã làm đến đâu
3. Đọc class liên quan đến task tiếp theo
4. Sau khi xong 1 task, **cập nhật `Current Status`** trong file này
5. Ghi rõ bất kỳ design decision mới nào vào `Known Issues / Watch Out`