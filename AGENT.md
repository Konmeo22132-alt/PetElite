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

```
[x] PetPlugin.java (main)  — PetSelectorGUI wired in
[x] pet/ module   — FloatPet (BlockDisplay lerp), GroundPet (Tameable AI), PetManager (tick scheduler)
[x] skill/ module — SkillTree (45 skills, 3 pets × 3 branches × 5), StatusEffectManager, ParticleHandler (full)
[x] quest/ module — QuestTracker, QuestResetScheduler, DailyQuest/WeeklyQuest
[x] battle/ module — BattleSession (extended modifiers), TurnManager, BookSkillHandler, BattleSkillClickHandler, EloManager, ArenaManager
[x] data/ module  — DataManager interface, YamlDataManager (hasUsedFreeMysteryEgg persisted)
[x] gui/ module   — PetSelectGUI, PetMainGUI (Mystery Egg + Pet Selector), SkillTreeGUI, QuestGUI, RankGUI, PetSelectorGUI [NEW]
[x] listener/ module — PlayerListener, PetInteractListener, BattleListener
[x] util/ module  — ChatUtil (Adventure API), GuiUtil
```

**Last completed:** 2026-04-04 — Tasks 1/2/3: skill tree rework + full 45 skills + /pet GUI flow. `mvn clean package` → **BUILD SUCCESS** → `target/PetPlugin-1.0.0.jar`  
**Next task:** Server-side testing and balance tuning.

---

## Architecture Summary

### Package Structure
```
com.petplugin/
├── PetPlugin.java
├── pet/        FloatPet (Display Entity), GroundPet (Pathfinding), PetType enum
├── skill/      SkillTree, SkillBranch (ATK/DEF/HEAL), Skill, StatusEffect
├── quest/      DailyQuest, WeeklyQuest, QuestTracker, QuestResetScheduler
├── battle/     BattleSession, TurnManager, BookSkillHandler, EloManager
├── data/       PlayerData, PetData, DataManager (interface), YamlDataManager
├── gui/        PetSelectGUI, PetMainGUI, SkillTreeGUI, QuestGUI, RankGUI
├── listener/   PlayerListener, PetInteractListener, BattleListener
└── util/       ChatUtil, GuiUtil
```

### Key Design Decisions
1. **DataManager là interface** — impl hiện tại là YamlDataManager, sau swap sang SQLite/MySQL không cần sửa code khác
2. **FloatPet dùng Display Entity + lerp** — không dùng ArmorStand, không dùng Shulker
3. **Battle book = skill interface** — mỗi page 1 skill, click page = dùng skill, detect qua `PlayerInteractEvent` với item type WRITTEN_BOOK
4. **PP không hồi trong trận** — resource management hoàn toàn
5. **Status effect hoạt động cả trong lẫn ngoài battle** — StatusEffectManager tick độc lập

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
- **FloatPet:** Display Entity, lerp Y mỗi tick, target Y = player.y + 1.2, float animation ±0.1 block
- **GroundPet:** Spawn actual mob entity với custom AI, follow player trong radius 10

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
- `MINE_ORE` target=32 → listen BlockBreakEvent, check ore tag
- `WALK_BLOCKS` target=500 → listen PlayerMoveEvent, accumulate distance
- `KILL_PLAYERS` target=3 → listen PlayerDeathEvent, check killer

### Weekly (reset Monday 00:00)
- `KILL_BOSS` target=2 → EntityDeathEvent, check WITHER / ELDER_GUARDIAN
- `WIN_BATTLES` target=5 → BattleSession end callback
- `LONG_JOURNEY` target=5000 → accumulate từ PlayerMoveEvent
- `CRAFT_RARE` target=10 → CraftItemEvent, check rare item list
- `JOIN_EVENTS` target=3 → manual call từ event system server

### Reset Logic
QuestResetScheduler chạy BukkitRunnable mỗi phút, check epoch time so với lastReset.

---

## Battle System

### Flow
```
/petbattle challenge <player>
  → target /petbattle accept
  → cả 2 chọn pet (GUI)
  → BattleSession.start()
    → snapshot inventory
    → clear inventory
    → give skill book
    → show BossBar (HP cả 2 pet)
  → TurnManager.nextTurn()
    → wait PlayerInteractEvent với WRITTEN_BOOK
    → BookSkillHandler.parseSkill(page)
    → apply damage/effect
    → check win condition
    → swap turn
  → BattleSession.end(winner)
    → restore inventory
    → EloManager.calculate()
    → give rank reward nếu lên rank
    → cleanup
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

## GUI Notes
- Tất cả GUI dùng `Bukkit.createInventory()` với custom title
- GuiUtil.buildItem() helper để tạo ItemStack nhanh với display name + lore
- Shift+click vào pet entity detect qua `PlayerInteractEntityEvent` + check sneaking

---

## Known Issues / Watch Out

### ✅ Fixed in 2026-04-04 bug-fix pass
- **BattleSession.end() NPE on null winner:** `winner.getName()` was called unconditionally even though `winner` can be null (both-offline edge case). Now guarded with null check; ELO only calculated when winner != null.
- **BattleSession disconnect crash:** `playerA.hideBossBar()` / `sendMessage()` crashed on offline player. All calls now guarded with `isOnline()` checks.
- **BattleSession.forfeit() offline winner:** If the opposing player was also offline, `end()` was passed an offline Player as winner. Now passes `null` when winner is offline, resulting in a draw.
- **BattleSession.broadcast() crash after disconnect:** All `sendMessage()` calls in `broadcast()` are now guarded with `isOnline()`.
- **PetData.addExp() single level-up:** Large EXP rewards (e.g. quest completion) only triggered one level-up even if the total crossed multiple thresholds. Fixed to loop until EXP is exhausted or level cap reached.
- **EloManager.grantRankReward() COAL case:** COAL is the default starting rank — players can never "rank up" to it, so awarding a bonus was unreachable dead code. Changed to COPPER (first real promotion).
- **StatusEffectManager.findEntity() O(n²) per tick:** Iterated all worlds × all living entities every tick to find an entity by UUID. Replaced with Paper API `Bukkit.getEntity(UUID)` which is O(1).
- **PetManager stale entry leak:** FloatPet.tick() calls `despawn()` on owner logout but does NOT remove itself from `PetManager.activePets`, causing permanent stale entries. PetManager.run() now uses an iterator to clean up despawned pets whose owner is offline.
- **QuestResetScheduler unused variables:** Dead `midnight` and `monday` ZonedDateTime locals were computed but never used. Removed.

### ⚠️ Still active / not yet tested
- **Float pet despawn:** Display Entity UUID is not persisted in PetData (no restart recovery). If server restarts while pet is out, the entity is gone but PetData has no record of it — normal summon flow re-creates it on next `/petsummon`.
- **Quest progress persist:** Quest progress is saved to YAML on every `increment()` call — no batching. High-frequency events like `WALK_BLOCKS` trigger a disk write every block moved. Consider debouncing saves.
- **Book input spam:** BattleSkillClickHandler `pending` map prevents double-triggers within a session but the map is never cleaned up if a player closes the skill GUI without clicking. Entry stays until the battle ends and the session is GC'd — no functional bug, just a minor leak within the session lifetime.
- **Particle names (Paper 1.21):** `CRIT_MAGIC` → `ENCHANTED_HIT`, không có `SEA_TURTLE_EGG` dùng `TURTLE_EGG`
- **PlayerDeathEvent package:** nằm ở `org.bukkit.event.entity`, KHÔNG phải `org.bukkit.event.player`
- **Battle skill selection:** Book page click không expose page number trực tiếp qua API → dùng 27-slot chest GUI thay thế. Book chỉ là reference card. `BattleSkillClickHandler` encode globalSlot vào lore dòng `§8GlobalSlot:X` để parse.
- **FloatPet material:** Dùng `TURTLE_EGG` BlockDisplay (0.7x scale). Có thể đổi sang `BlockDisplay` khác sau.
- **GroundPet:** Spawn thực thể Wolf/Cat, tame cho owner → dùng vanilla follow AI. Có thể bị tấn công bởi mob khác — cần thêm invulnerable flag hoặc heal logic sau.

### ✅ New decisions (2026-04-04 task session)
- **Skill point pools:** ATK/DEF/HEAL have SEPARATE point pools. `grantLevelUpPoints()` uses `PetType.getAtkGain/defGain/healGain` per level. Turtle: +0/+2/+1; Wolf: +2/+1/+0; Cat: +1/+0/+2.
- **Skills per pet:** Each of 3 pets has 15 unique skills (3 branches × 5) defined as anonymous inner classes in `SkillTree`. Skills reference new `BattleSession` API methods.
- **BattleSession extended modifiers:** Added `atkReduction` (ATK debuff), `reflectFraction` (Thorn Armor), `dodgeChance + dodgeTurns` (probabilistic evasion), `dmgReductHits` (Reflex Guard), `statusImmune` (Stone Skin / Ghost Step), `lastStand` (Iron Will, once-per-battle), `rebirth` (Rebirth trigger at <20% HP, once-per-battle), `scheduleRegen` (periodic HP ticks), `startBerserkerHeal` (Berserker Heal 5-hit window).
- **applyStatusEffect now respects statusImmuneA/B:** Status immunity granted by Stone Skin / Ghost Step blocks all status effects for N turns.
- **ATK deduction with atkReductionA/B:** When the attacker has an ATK debuff, petAtk is multiplied by `(1 - atkReduction)` before `skill.executeInBattle()` is called.
- **Mystery Egg (PetMainGUI slot 8):** Always visible. First use is free (glowing Dragon Egg). Subsequent uses show MYSTERY_EGG_COST (placeholder: 1000 coin). Economy hook is a TODO. `hasUsedFreeMysteryEgg` stored in PlayerData + YAML.
- **PetSelectorGUI (PetMainGUI slot 11):** Only shown when `petSlots > 1`. Lists all owned pets with inline stats. Encodes petId in lore for safe retrieval. On click: recalls current pet, changes activePetId, summons new pet.
- **Wild Stance condition check:** Wild Stance (Wolf DEF 4) checks HP at time of use. If HP ≥ 40%, the skill fires but grants no bonus — player still consumes PP. This is intentional to prevent trivial save-for-low-hp logic.
- **Berserker Heal timing:** Uses a 5-second BukkitRunnable delay (`100L`). Runs `runTaskLaterAsynchronously` — this is intentional and safe because it only modifies BattleSession state through `healPet()` which is main-thread safe (called back via the scheduler).
- **Life Drain (Cat HEAL 4):** Uses `dealDamageRaw` (bypasses DEF) + `healPet`. This is intentional — draining is a direct life-steal, not a blocked strike.


---

## Open Items (chưa implement, không được tự ý implement)
- [ ] Pet evolution (level 50) — placeholder method `onEvolve()` thôi
- [ ] Multiple pet slot mechanics
- [ ] Pet items (food, accessory)
- [ ] Weekly quest rewards cụ thể
- [ ] Swap YAML → DB
- [ ] Arena teleport (tạm freeze tại chỗ trước)

---

## How to Continue
1. Đọc file này
2. Check `Current Status` section để biết đã làm đến đâu
3. Đọc class liên quan đến task tiếp theo
4. Sau khi xong 1 task, **cập nhật `Current Status`** trong file này
5. Ghi rõ bất kỳ design decision mới nào vào `Known Issues / Watch Out`