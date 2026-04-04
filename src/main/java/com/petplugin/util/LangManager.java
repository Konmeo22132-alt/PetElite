package com.petplugin.util;

import com.petplugin.PetPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class LangManager {

    private final PetPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();
    private final String prefix = "&8[&bPetElite&8] &r";
    private String currentLang = "en_US";

    public LangManager(PetPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        currentLang = plugin.getConfig().getString("language", "en_US");
        
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        // Save defaults if they don't exist
        String[] languages = {"en_US.yml", "vi_VN.yml", "zh_CN.yml", "es_ES.yml"};
        for (String langFile : languages) {
            File destFile = new File(langDir, langFile);
            if (!destFile.exists()) {
                plugin.saveResource("lang/" + langFile, false);
            }
        }

        File file = new File(langDir, currentLang + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("Language file " + currentLang + " not found, falling back to en_US.");
            file = new File(langDir, "en_US.yml");
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // Use default embedded as fallback for missing keys
        InputStream defStream = plugin.getResource("lang/en_US.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            config.setDefaults(defConfig);
        }

        messages.clear();
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                messages.put(key, config.getString(key));
            }
        }
    }

    public String get(String key, String... placeholders) {
        String msg = messages.getOrDefault(key, key);
        if (msg.startsWith("!")) {
            msg = msg.substring(1); // Exclude prefix bypass
        } else {
            msg = prefix + msg;
        }

        for (int i = 0; i < placeholders.length - 1; i += 2) {
            msg = msg.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}
