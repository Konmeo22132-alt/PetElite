package com.petplugin;

import com.petplugin.battle.*;
import com.petplugin.data.DataManager;
import com.petplugin.data.YamlDataManager;
import com.petplugin.gui.*;
import com.petplugin.gui.PetSelectorGUI;
import com.petplugin.item.MysteryEggItem;
import com.petplugin.listener.*;
import com.petplugin.pet.PetManager;
import com.petplugin.pet.PetRespawnManager;
import com.petplugin.quest.QuestResetScheduler;
import com.petplugin.quest.QuestTracker;
import com.petplugin.skill.StatusEffectManager;
import com.petplugin.util.ChatUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

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

        // Data layer
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

        // GUIs
        petSelectGUI = new PetSelectGUI(this);
        petMainGUI   = new PetMainGUI(this);
        skillTreeGUI = new SkillTreeGUI(this);
        questGUI     = new QuestGUI(this);
        rankGUI      = new RankGUI(this);
        petSelectorGUI = new PetSelectorGUI(this);

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
        getServer().getPluginManager().registerEvents(new MysteryEggListener(this),   this);
        // PlayerJoin hook for waitingRespawn
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
                petRespawnManager.checkWaitingRespawn(e.getPlayer());
            }
        }, this);

        // Start schedulers
        petManager.start();
        statusEffectManager.start();
        questResetScheduler.start();

        getLogger().info("PetPlugin enabled!");
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

        getLogger().info("PetPlugin disabled.");
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
            default          -> false;
        };
    }

    private boolean handlePetCommand(Player player, String[] args) {
        // /pet egg — give mystery egg item
        if (args.length > 0 && args[0].equalsIgnoreCase("egg")) {
            player.getInventory().addItem(MysteryEggItem.create(this));
            player.sendMessage(ChatUtil.color("&a🥚 Mystery Egg added to your inventory!"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("recall")) {
            petManager.toggle(player);
            return true;
        }

        // No active pet → show starter GUI
        var activePet = dataManager.getActivePet(player.getUniqueId());
        if (activePet == null) {
            petSelectGUI.open(player);
        } else {
            petMainGUI.open(player);
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
                rankGUI.open(player);
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
}
