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

### v1.0.2 Task Status
[x] TASK 1 — Folia Support: Created FoliaUtil, migrated all BukkitRunnable usages to Folia-compatible schedulers across all managers and entities.
[x] TASK 2 — Turtle Rendering: Implemented fallback to direct position lerp on Turtle entity. Added global respawn loop to PetManager.run() for unloaded entities.
[x] TASK 3 — Rank-up Pet Slot Unlock: Validate slots on join, GUI sync/logs.
[x] TASK 4 — Rank GUI Error Handling: Global fail-safes and specific inventory try-catch block implemented.
[x] TASK 5 — Multi-GUI Bug Exploits: Unified BaseGUI class, single GUIListener intercepting all inventory clicks.
[x] TASK 6 — Basic Attack: Updated scheduler for Folia compatibility.
[x] TASK 7 — Pet Follow Rework: GroundPet/FloatPet Folia-safe self-ticking, 2-block lerp per tick follow logic.
[x] TASK 8 — Production Hardening: Atomic YAML saves via Bukkit AsyncScheduler, chunk entity cap > 100 check in PetManager.summon(), added /pet reload command.
[x] TASK 9 — Versioning: Updated pom.xml to 1.0.2, plugin.yml with folia-supported: true.

**Last completed:** 2026-04-05 v1.0.2 Production Hardening.
**Next task:** Post-release monitoring, Pet evolution (placeholder), SQL DB transition.

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

### ✅ Fixed in 1.0.1 (2026-04-04 v1.0.1 patch)
- **FloatPet (Turtle) not rendering:** Added explicit `setInvisible(false)` in spawn consumer lambda. Added debug logs on spawn and first tick. Turtle spawning confirmed via console log `[FloatPet] Spawned FloatPet for <player>`.
- **Rank slot not unlocking on rank-up:** `EloManager.grantRankReward()` now uses `slotGranted` boolean flag, saves PlayerData AFTER incrementing slots, and sends explicit `★ Slot pet mới được mở khoá!` message with new slot count.
- **Rank GUI crash:** Wrapped `rankGUI.open(player)` in `PetPlugin.handleBattleCommand()` with try-catch, logs full stack trace to console.
- **Items can be taken from QuestGUI:** Added `@EventHandler onInventoryClick` and `onInventoryDrag` to `QuestGUI` class cancelling all events when title matches. Same applied to `RankGUI`.
- **FloatPet follow improved:** lerp factor now 0.3 per tick when dist > 2 (was flat 0.12). Snap >10 blocks. Faces player yaw.
- **GroundPet follow reworked:** AI fully disabled (`setAI(false)`) in spawn(). `tick()` uses manual lerp 0.3/tick (dist > 2) or hard snap (dist > 10). No pathfinder calls.
- **BasicAttackHandler added:** New `listener/BasicAttackHandler.java`. Triggers on `EntityDamageByEntityEvent` when pet owner does melee damage. Pet dashes to target offset (0.5 blocks from face), CRIT × 6 particles, 25% base ATK bonus damage, `ENTITY_PLAYER_ATTACK_STRONG` sound (pitch 1.2). Returns after 6 ticks. 2s cooldown. Skips: in battle, fainted, hidden, target is pet.

### ⚠️ New edge cases to watch (v1.0.1)
- **BasicAttackHandler cooldown map leak:** `lastAttack` map in `BasicAttackHandler` is never cleaned up when player quits. Minor memory leak; entries expire naturally after 2s. Consider clearing on PlayerQuitEvent for cleanliness.
- **GroundPet AI-off during respawn:** If GroundPet auto-respawns (tick finds entity invalid), the new entity also gets `setAI(false)` via `spawn()`. Confirmed correct.
- **FloatPet `setInvisible(false)` note:** The Paper 1.21 `Turtle` entity requires the flag to be set INSIDE the spawn consumer lambda (before entity is added to world). Already done.
- **GroundPet teleport-every-tick:** Teleporting ground mobs every tick may cause visual jitter on high-ping clients. Consider throttling to every 2-3 ticks if reported.

- **FloatPet entity change:** No longer uses BlockDisplay. Now spawns a real `Turtle` entity (`EntityType.TURTLE`) with `setAI(false)`, `setSilent(true)`, `setInvulnerable(true)`, `setPersistent(true)`, `setGravity(false)`, `setAdult()`. Position lerped + float animation applied by calling `teleport()` each tick exactly like before.
- **FloatPet.getTurtleEntity():** Renamed from `getDisplay()`. PetManager updated to call `getTurtleEntity()` in `isPetEntity()` and `getOwnerOf()`.
- **GroundPet safety flags:** Wolf and Cat now also get `setInvulnerable(true)`, `setPersistent(true)`, `setSilent(true)` on spawn.
- **Paper API note:** `Ageable.setBaby(boolean)` does NOT exist — use `setAdult()` to force adult state or `setBaby()` (no-arg) to force baby. This is a Paper/Bukkit quirk.
- **MysteryEggListener rewrite:** 5-phase slot-machine animation (intervals: 1/2/4/6/10 ticks per switch), total 90 ticks. Spin shown via `sendActionBar()`. Final reveal via `sendTitle()` with type colour. Sound: `UI_BUTTON_CLICK` (pitch 1.5 fast → descending 1.4/1.2/1.0/0.8 slow), `ENTITY_PLAYER_LEVELUP` on reveal.
- **`/pet set rank` command:** Sets both `rank` and `elo` to `tier.getMinElo()`. OP-only. Saves via `dataManager.savePlayer()`.
- **`/pet set level` command:** Sets `pet.level` directly. If new level > old level, grants `(diff * type.getXxxGain())` extra points to each branch. Does NOT reset already-unlocked skills.
- **`/pet set exp` command:** Sets `currentExp=0` then calls `pet.addExp(amount)` which triggers the full level-up loop.
- **Tab completion:** `onTabComplete()` override in PetPlugin. `/pet` → recall/egg (+ set if OP). `/pet set` → rank/level/exp. `/pet set rank/level/exp <player>` → online players. `/pet set rank <player>` → tier names. `/pet set level <player>` → preset levels. `/petbattle` → challenge/accept/surrender/rank. `/petbattle challenge` → online players (excluding self).



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