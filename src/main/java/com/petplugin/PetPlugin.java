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
import com.petplugin.pet.PetManager;
import com.petplugin.pet.PetRespawnManager;
import com.petplugin.quest.QuestResetScheduler;
import com.petplugin.quest.QuestTracker;
import com.petplugin.skill.StatusEffectManager;
import com.petplugin.util.ChatUtil;
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

        // /pet recall
        if (args.length > 0 && args[0].equalsIgnoreCase("recall")) {
            petManager.toggle(player);
            return true;
        }

        // /pet set {rank|level|exp} <player> <value> — OP only (Task 3)
        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            return handleSetCommand(player, args);
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

    // ------------------------------------------------------------------ //
    //  /pet set command (Task 3)
    // ------------------------------------------------------------------ //

    private boolean handleSetCommand(Player sender, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage(ChatUtil.color("&cYou don't have permission to use this command."));
            return true;
        }
        // args: ["set", type, player, value]
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
                // Grant cumulative skill points for all levels up to target
                // without resetting already-unlocked skills
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
                pet.addExp(exp); // triggers level-up loop
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
    //  Tab Completion (Task 4 — context-aware)
    // ------------------------------------------------------------------ //

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        String cmd = command.getName().toLowerCase();

        if (cmd.equals("pet")) {
            if (args.length == 1) {
                // Base subcommands
                List<String> base = new ArrayList<>(Arrays.asList("recall", "egg"));
                if (sender.isOp()) base.add("set");
                filterAndAdd(suggestions, base, args[0]);

            } else if (args.length == 2 && args[0].equalsIgnoreCase("set") && sender.isOp()) {
                filterAndAdd(suggestions, Arrays.asList("rank", "level", "exp"), args[1]);

            } else if (args.length == 3 && args[0].equalsIgnoreCase("set") && sender.isOp()) {
                // Player argument — online players only
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[2].toLowerCase()))
                        suggestions.add(p.getName());
                }

            } else if (args.length == 4 && args[0].equalsIgnoreCase("set") && sender.isOp()) {
                // Value argument
                switch (args[1].toLowerCase()) {
                    case "rank"  -> filterAndAdd(suggestions,
                        Arrays.asList("COAL", "COPPER", "IRON", "GOLD", "DIAMOND", "NETHERITE"), args[3]);
                    case "level" -> filterAndAdd(suggestions,
                        Arrays.asList("1", "10", "20", "30", "40", "50"), args[3]);
                    // exp — no suggestion
                }
            }
        }

        if (cmd.equals("petbattle")) {
            if (args.length == 1) {
                filterAndAdd(suggestions,
                    Arrays.asList("challenge", "accept", "surrender", "rank"), args[0]);

            } else if (args.length == 2 && args[0].equalsIgnoreCase("challenge")) {
                // Suggest online players (excluding self)
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (sender instanceof Player self && p == self) continue;
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase()))
                        suggestions.add(p.getName());
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
}
