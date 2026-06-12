package com.s1steam.antidumper.protection.bot;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class UUIDWhitelist implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private final Set<UUID> whitelisted = new LinkedHashSet<>();
    private final File whitelistFile;
    private boolean enabled = false;

    public UUIDWhitelist(AntiDumperPlugin plugin) {
        this.plugin = plugin;
        this.whitelistFile = new File(plugin.getDataFolder(), "uuid-whitelist.yml");
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        if (!enabled) return;
        HandlerList.unregisterAll(this);
        whitelisted.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "UUIDWhitelist";
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if (!enabled) return;
        if (!plugin.getConfigManager().isUUIDWhitelistEnabled()) return;

        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.whitelist.bypass") || player.hasPermission("antidumper.bypass.uuid-whitelist")) return;

        if (!whitelisted.contains(player.getUniqueId())) {
            
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST,
                plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().get("protection.uuid-whitelist.kick"));
            LoggerUtil.violation(plugin, getName(), player.getName(),
                "UUID not whitelisted: " + player.getUniqueId());
            plugin.getStaffAlert().alert(player.getName(),
                "UUID not whitelisted", getName());
        }
    }

    public boolean isWhitelisted(UUID uuid) {
        return whitelisted.contains(uuid);
    }

    public boolean addWhitelist(UUID uuid) {
        boolean added = whitelisted.add(uuid);
        if (added) save();
        return added;
    }

    public boolean removeWhitelist(UUID uuid) {
        boolean removed = whitelisted.remove(uuid);
        if (removed) save();
        return removed;
    }

    public Set<UUID> getWhitelisted() {
        return new LinkedHashSet<>(whitelisted);
    }

    public void load() {
        whitelisted.clear();
        if (!whitelistFile.exists()) {
            try {
                whitelistFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create uuid-whitelist.yml: " + e.getMessage());
            }
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(whitelistFile);
        for (String key : config.getStringList("uuids")) {
            try {
                whitelisted.add(UUID.fromString(key.trim()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in whitelist: " + key);
            }
        }
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        config.set("uuids", whitelisted.stream().map(UUID::toString).toList());
        try {
            config.save(whitelistFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save uuid-whitelist.yml: " + e.getMessage());
        }
    }

    private void handleDetection(Player player) {
        
        String action = plugin.getConfigManager().getModuleAction("protection.uuid-whitelist");
        switch (action) {
            case "KICK":
                plugin.getScheduler().runKickTask(player, () ->
                    player.kickPlayer(plugin.getConfigManager().getPrefix() + " " +
                        plugin.getConfigManager().get("protection.exploit.kick").replace("%module%", getName())));
                break;
            case "WARN":
                player.sendMessage(plugin.getConfigManager().getPrefixed("protection.exploit.warn")
                    .replace("%module%", getName()));
                break;
        }
    }
}
