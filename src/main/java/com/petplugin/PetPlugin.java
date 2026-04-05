package com.petplugin;

import com.petplugin.battle.*;
import com.petplugin.data.DataManager;
import com.petplugin.data.PlayerData;
import com.petplugin.data.PetData;
import com.petplugin.data.YamlDataManager;
import com.petplugin.gui.*;
import com.petplugin.gui.PetSelectorGUI;
import com.petplugin.item.MysteryEggItem;
import com.petplugin.listener.*;
import com.petplugin.pet.GlobalTickRunnable;
import com.petplugin.pet.PetManager;
import com.petplugin.pet.PetRespawnManager;
import com.petplugin.quest.QuestResetScheduler;
import com.petplugin.quest.QuestTracker;
import com.petplugin.skill.StatusEffectManager;
import com.petplugin.util.ChatUtil;
import com.petplugin.util.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class PetPlugin extends JavaPlugin {

    // ---- Singletons ----
    private DataManager dataManager;
    private PetManager petManager;
    private BattleManager battleManager;
    private EloManager eloManager;
    private TurnManager turnManager;
    private BattleSkillClickHandler battleSkillClickHandler;
    private StatusEffectManager statusEffectManager;
    private QuestTracker questTracker;
    private QuestResetScheduler questResetScheduler;
    private PetRespawnManager petRespawnManager;
    private LangManager langManager;
    private GlobalTickRunnable globalTickRunnable;

    // ---- Listeners (stored for cleanup) ----
    private BasicAttackHandler basicAttackHandler;
    private MysteryEggListener mysteryEggListener;

    // ---- GUI ----
    private PetSelectGUI petSelectGUI;
    private PetMainGUI petMainGUI;
    private SkillTreeGUI skillTreeGUI;
    private QuestGUI questGUI;
    private RankGUI rankGUI;
    private PetSelectorGUI petSelectorGUI;

    @Override
    public void onEnable() {
        // Config
        saveDefaultConfig();

        // Data layer & Lang
        langManager = new LangManager(this);
        langManager.load();
        
        dataManager = new YamlDataManager(this);

        // Core systems
        petManager     = new PetManager(this);
        battleManager  = new BattleManager(this);
        eloManager     = new EloManager(this);
        turnManager    = new TurnManager(this);
        battleSkillClickHandler = new BattleSkillClickHandler(this);
        statusEffectManager = new StatusEffectManager(this);
        questTracker   = new QuestTracker(this);
        questResetScheduler = new QuestResetScheduler(this);
        petRespawnManager = new PetRespawnManager(this);
        globalTickRunnable = new GlobalTickRunnable(this);

        // GUIs
        petSelectGUI = new PetSelectGUI(this);
        petMainGUI   = new PetMainGUI(this);
        skillTreeGUI = new SkillTreeGUI(this);
        questGUI     = new QuestGUI(this);
        rankGUI      = new RankGUI(this);
        petSelectorGUI = new PetSelectorGUI(this);

        // Create listener instances (stored for cleanup)
        basicAttackHandler = new BasicAttackHandler(this);
        mysteryEggListener = new MysteryEggListener(this);

        // Arena
        ArenaManager.init(this);

        // Register all listeners
        getServer().getPluginManager().registerEvents(petSelectGUI,          this);
        getServer().getPluginManager().registerEvents(petMainGUI,            this);
        getServer().getPluginManager().registerEvents(skillTreeGUI,          this);
        getServer().getPluginManager().registerEvents(questGUI,              this);
        getServer().getPluginManager().registerEvents(rankGUI,               this);
        getServer().getPluginManager().registerEvents(petSelectorGUI,        this);
        getServer().getPluginManager().registerEvents(battleSkillClickHandler, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this),       this);
        getServer().getPluginManager().registerEvents(new PetInteractListener(this),  this);
        getServer().getPluginManager().registerEvents(new BattleListener(this),       this);
        getServer().getPluginManager().registerEvents(new PetDamageListener(this),    this);
        getServer().getPluginManager().registerEvents(mysteryEggListener,             this);
        getServer().getPluginManager().registerEvents(basicAttackHandler,             this);

        // AUDIT FIX: comprehensive PlayerQuit + PlayerJoin cleanup handler
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                petRespawnManager.checkWaitingRespawn(e.getPlayer());
                InventorySnapshot.restoreIfPresent(e.getPlayer());
            }

            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
                UUID uuid = e.getPlayer().getUniqueId();
                // AUDIT FIX: clean up ALL per-player state to prevent memory leaks
                battleManager.cleanupPlayer(uuid);
                battleSkillClickHandler.cleanupPlayer(uuid);
                basicAttackHandler.cleanupPlayer(uuid);
                mysteryEggListener.cleanupPlayer(uuid);
                petMainGUI.cleanupPlayer(uuid);
                statusEffectManager.clearAll(uuid);

                // Save data
                var pd = dataManager.loadPlayer(uuid);
                var pet = dataManager.getActivePet(uuid);
                dataManager.saveAll(pd, pet);

                // Despawn pet entity
                petManager.recall(e.getPlayer());
            }
        }, this);

        // TASK 3: Start global tick runnable (replaces per-entity ticks)
        globalTickRunnable.start();

        // Start remaining schedulers
        statusEffectManager.start();
        questResetScheduler.start();

        getLogger().info("[PetElite] Plugin enabled! v" + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        // End all battles gracefully
        if (battleManager != null) battleManager.endAllSessions();

        // Despawn all pets
        if (petManager != null) petManager.despawnAll();

        // Unfreeze all players
        ArenaManager.unfreezeAll();

        // Save all online players' data
        for (Player player : getServer().getOnlinePlayers()) {
            var pd = dataManager.loadPlayer(player.getUniqueId());
            var pet = dataManager.getActivePet(player.getUniqueId());
            dataManager.saveAll(pd, pet);
        }

        getLogger().info("[PetElite] Plugin disabled.");
    }

    // ------------------------------------------------------------------ //
    //  Commands
    // ------------------------------------------------------------------ //

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        return switch (command.getName().toLowerCase()) {
            case "pet"       -> handlePetCommand(player, args);
            case "petbattle" -> handleBattleCommand(player, args);
            case "petop"     -> handlePetOpCommand(player, args);
            default          -> false;
        };
    }

    private boolean handlePetCommand(Player player, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("egg")) {
            player.getInventory().addItem(MysteryEggItem.create(this));
            player.sendMessage(ChatUtil.color("&a🥚 Mystery Egg added to your inventory!"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("recall")) {
            petManager.toggle(player);
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!player.isOp()) {
                player.sendMessage(ChatUtil.color("&cYou don't have permission to use this command."));
                return true;
            }
            reloadConfig();
            langManager.load();
            player.sendMessage(ChatUtil.color("&aPetPlugin configuration and language files reloaded."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            return handleSetCommand(player, args);
        }

        var activePet = dataManager.getActivePet(player.getUniqueId());
        if (activePet == null) {
            petSelectGUI.open(player);
        } else {
            petMainGUI.open(player);
        }
        return true;
    }

    // ------------------------------------------------------------------ //
    //  /petop command (TASK 2 — arena management)
    // ------------------------------------------------------------------ //

    private boolean handlePetOpCommand(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(ChatUtil.color("&cYou don't have permission."));
            return true;
        }

        if (args.length == 0) {
            sendPetOpHelp(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "set" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatUtil.color("&cUsage: /petop set battle"));
                    yield true;
                }
                if (args[1].equalsIgnoreCase("battle")) {
                    String arenaId = ArenaManager.registerArena(player);
                    player.sendMessage(ChatUtil.color("&a✦ Arena registered: &f" + arenaId
                            + " &aat your current location (40 block radius)."));
                    yield true;
                }
                player.sendMessage(ChatUtil.color("&cUsage: /petop set battle"));
                yield true;
            }
            case "arena" -> {
                if (args.length < 2) {
                    sendPetOpHelp(player);
                    yield true;
                }
                if (args[1].equalsIgnoreCase("list")) {
                    var arenas = ArenaManager.getAllArenas();
                    if (arenas.isEmpty()) {
                        player.sendMessage(ChatUtil.color("&7No arenas registered."));
                    } else {
                        player.sendMessage(ChatUtil.color("&6=== Registered Arenas ==="));
                        for (var arena : arenas) {
                            int count = ArenaManager.getActiveBattleCount(arena.getId());
                            player.sendMessage(ChatUtil.color("&e" + arena.getId()
                                    + " &7- " + arena.getWorldName()
                                    + " (" + (int) arena.getX() + "," + (int) arena.getY() + "," + (int) arena.getZ() + ")"
                                    + " r=" + arena.getRadius()
                                    + " &a[" + count + "/" + arena.getMaxConcurrentBattles() + " battles]"));
                        }
                    }
                    yield true;
                }
                if (args[1].equalsIgnoreCase("remove") && args.length >= 3) {
                    if (ArenaManager.removeArena(args[2])) {
                        player.sendMessage(ChatUtil.color("&aArena removed: &f" + args[2]));
                    } else {
                        player.sendMessage(ChatUtil.color("&cArena not found: " + args[2]));
                    }
                    yield true;
                }
                sendPetOpHelp(player);
                yield true;
            }
            default -> {
                sendPetOpHelp(player);
                yield true;
            }
        };
    }

    private void sendPetOpHelp(Player player) {
        player.sendMessage(ChatUtil.color(
                "&6=== PetOp Commands ===\n"
                + "&e/petop set battle &7- Register arena at current location\n"
                + "&e/petop arena list &7- List all arenas\n"
                + "&e/petop arena remove <id> &7- Remove an arena"));
    }

    // ------------------------------------------------------------------ //
    //  /pet set command (Task 3)
    // ------------------------------------------------------------------ //

    private boolean handleSetCommand(Player sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatUtil.color("&cYou don't have permission to use this command."));
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage(ChatUtil.color(
                "&cUsage: /pet set &e<rank|level|exp> <player> <value>"));
            return true;
        }

        String type   = args[1].toLowerCase();
        String playerName = args[2];
        String valueStr   = args[3];

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(ChatUtil.color("&cPlayer '" + playerName + "' not found or offline."));
            return true;
        }

        switch (type) {
            case "rank" -> {
                PlayerData.RankTier tier;
                try {
                    tier = PlayerData.RankTier.valueOf(valueStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatUtil.color(
                        "&cInvalid rank. Use: COAL, COPPER, IRON, GOLD, DIAMOND, NETHERITE"));
                    return true;
                }
                PlayerData pd = dataManager.loadPlayer(target.getUniqueId());
                pd.setRank(tier);
                pd.setElo(tier.getMinElo());
                dataManager.savePlayer(pd);
                sender.sendMessage(ChatUtil.color(
                    "&aSet rank of &e" + target.getName() + "&a to &e" + tier.name() + "&a successfully."));
                target.sendMessage(ChatUtil.color("&eYour pet stats have been updated by an admin."));
            }
            case "level" -> {
                int level;
                try { level = Integer.parseInt(valueStr); } catch (NumberFormatException e) {
                    sender.sendMessage(ChatUtil.color("&cInvalid level value."));
                    return true;
                }
                if (level < 1 || level > 50) {
                    sender.sendMessage(ChatUtil.color("&cLevel must be between 1 and 50."));
                    return true;
                }
                PetData pet = dataManager.getActivePet(target.getUniqueId());
                if (pet == null) {
                    sender.sendMessage(ChatUtil.color("&c" + target.getName() + " has no active pet."));
                    return true;
                }
                int currentLevel = pet.getLevel();
                pet.setLevel(level);
                pet.setCurrentExp(0);
                if (level > currentLevel) {
                    int diff = level - currentLevel;
                    pet.setAtkPoints(pet.getAtkPoints() + pet.getType().getAtkGain() * diff);
                    pet.setDefPoints(pet.getDefPoints() + pet.getType().getDefGain() * diff);
                    pet.setHealPoints(pet.getHealPoints() + pet.getType().getHealGain() * diff);
                }
                dataManager.savePet(pet);
                sender.sendMessage(ChatUtil.color(
                    "&aSet level of &e" + target.getName() + "'s pet&a to &e" + level + "&a successfully."));
                target.sendMessage(ChatUtil.color("&eYour pet stats have been updated by an admin."));
            }
            case "exp" -> {
                long exp;
                try { exp = Long.parseLong(valueStr); } catch (NumberFormatException e) {
                    sender.sendMessage(ChatUtil.color("&cInvalid EXP value."));
                    return true;
                }
                PetData pet = dataManager.getActivePet(target.getUniqueId());
                if (pet == null) {
                    sender.sendMessage(ChatUtil.color("&c" + target.getName() + " has no active pet."));
                    return true;
                }
                pet.setCurrentExp(0);
                pet.addExp(exp);
                dataManager.savePet(pet);
                sender.sendMessage(ChatUtil.color(
                    "&aSet EXP of &e" + target.getName() + "'s pet&a to &e" + exp + "&a (may have leveled up)."));
                target.sendMessage(ChatUtil.color("&eYour pet stats have been updated by an admin."));
            }
            default -> sender.sendMessage(ChatUtil.color(
                "&cUnknown type. Use: rank, level, exp"));
        }
        return true;
    }

    private boolean handleBattleCommand(Player player, String[] args) {
        if (args.length == 0) {
            sendBattleHelp(player);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "challenge" -> {
                if (args.length < 2) { player.sendMessage(ChatUtil.color("&cUsage: /petbattle challenge <player>")); yield true; }
                Player target = getServer().getPlayer(args[1]);
                if (target == null) { player.sendMessage(ChatUtil.color("&cPlayer không tìm thấy!")); yield true; }
                if (target == player) { player.sendMessage(ChatUtil.color("&cKhông thể thách đấu bản thân!")); yield true; }
                battleManager.challenge(player, target);
                yield true;
            }
            case "accept" -> {
                battleManager.accept(player);
                yield true;
            }
            case "surrender", "forfeit" -> {
                battleManager.surrender(player);
                yield true;
            }
            case "rank" -> {
                try {
                    rankGUI.open(player);
                } catch (Exception ex) {
                    getLogger().severe("[PetElite] RankGUI.open() threw an exception for " + player.getName() + ":");
                    ex.printStackTrace();
                    player.sendMessage(ChatUtil.color("&cLỗi khi mở Rank GUI. Vui lòng báo cáo admin."));
                }
                yield true;
            }
            default -> {
                sendBattleHelp(player);
                yield true;
            }
        };
    }

    private void sendBattleHelp(Player player) {
        player.sendMessage(ChatUtil.color(
                "&6=== PetBattle Commands ===\n"
                + "&e/petbattle challenge <player> &7- Thách đấu\n"
                + "&e/petbattle accept &7- Chấp nhận lời thách\n"
                + "&e/petbattle surrender &7- Đầu hàng\n"
                + "&e/petbattle rank &7- Xem rank"));
    }

    // ------------------------------------------------------------------ //
    //  Tab Completion
    // ------------------------------------------------------------------ //

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("pet")) {
            if (args.length == 1) {
                List<String> base = new ArrayList<>(Arrays.asList("recall", "egg"));
                if (sender.isOp()) {
                    base.add("set");
                    base.add("reload");
                }
                filterAndAdd(suggestions, base, args[0]);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("set") && sender.isOp()) {
                filterAndAdd(suggestions, Arrays.asList("rank", "level", "exp"), args[1]);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("set") && sender.isOp()) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[2].toLowerCase()))
                        suggestions.add(p.getName());
                }
            } else if (args.length == 4 && args[0].equalsIgnoreCase("set") && sender.isOp()) {
                switch (args[1].toLowerCase()) {
                    case "rank"  -> filterAndAdd(suggestions,
                        Arrays.asList("COAL", "COPPER", "IRON", "GOLD", "DIAMOND", "NETHERITE"), args[3]);
                    case "level" -> filterAndAdd(suggestions,
                        Arrays.asList("1", "10", "20", "30", "40", "50"), args[3]);
                }
            }
        }

        if (cmd.equals("petbattle")) {
            if (args.length == 1) {
                filterAndAdd(suggestions,
                    Arrays.asList("challenge", "accept", "surrender", "rank"), args[0]);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("challenge")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (sender instanceof Player self && p == self) continue;
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase()))
                        suggestions.add(p.getName());
                }
            }
        }

        if (cmd.equals("petop")) {
            if (args.length == 1) {
                filterAndAdd(suggestions, Arrays.asList("set", "arena"), args[0]);
            } else if (args.length == 2) {
                if (args[0].equalsIgnoreCase("set")) {
                    filterAndAdd(suggestions, List.of("battle"), args[1]);
                } else if (args[0].equalsIgnoreCase("arena")) {
                    filterAndAdd(suggestions, Arrays.asList("list", "remove"), args[1]);
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("arena") && args[1].equalsIgnoreCase("remove")) {
                for (var arena : ArenaManager.getAllArenas()) {
                    if (arena.getId().toLowerCase().startsWith(args[2].toLowerCase()))
                        suggestions.add(arena.getId());
                }
            }
        }

        return suggestions;
    }

    private void filterAndAdd(List<String> out, List<String> candidates, String prefix) {
        for (String c : candidates) {
            if (c.toLowerCase().startsWith(prefix.toLowerCase())) out.add(c);
        }
    }

    // ------------------------------------------------------------------ //
    //  Getters
    // ------------------------------------------------------------------ //

    public DataManager getDataManager()                 { return dataManager; }
    public PetManager getPetManager()                   { return petManager; }
    public BattleManager getBattleManager()             { return battleManager; }
    public EloManager getEloManager()                   { return eloManager; }
    public TurnManager getTurnManager()                 { return turnManager; }
    public BattleSkillClickHandler getBattleSkillClickHandler() { return battleSkillClickHandler; }
    public StatusEffectManager getStatusEffectManager() { return statusEffectManager; }
    public QuestTracker getQuestTracker()               { return questTracker; }
    public PetSelectGUI getPetSelectGUI()               { return petSelectGUI; }
    public PetMainGUI getPetMainGUI()                   { return petMainGUI; }
    public SkillTreeGUI getSkillTreeGUI()               { return skillTreeGUI; }
    public QuestGUI getQuestGUI()                       { return questGUI; }
    public RankGUI getRankGUI()                         { return rankGUI; }
    public PetSelectorGUI getPetSelectorGUI()           { return petSelectorGUI; }
    public PetRespawnManager getPetRespawnManager()     { return petRespawnManager; }
    public LangManager getLang()                        { return langManager; }
}
