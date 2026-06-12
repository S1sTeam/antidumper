package com.s1steam.antidumper.utils;

import com.s1steam.antidumper.AntiDumperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class StaffAlertManager {
    private final AntiDumperPlugin plugin;

    public StaffAlertManager(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    public void alert(String playerName, String action, String module) {
        if (!plugin.getConfigManager().isStaffAlertsEnabled()) return;
        if (!plugin.getConfigManager().isStaffAlertEvent(module)) return;

        String format = plugin.getConfigManager().getStaffAlertFormat();
        String message = format
            .replace("%player%", playerName)
            .replace("%action%", action)
            .replace("%module%", module);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(plugin.getConfigManager().getStaffAlertPermission())) {
                p.sendMessage(message);
                playAlertSound(p);
            }
        }

        plugin.getLogger().info("[STAFF ALERT] " + playerName + " - " + action + " (" + module + ")");
    }

    private void playAlertSound(Player player) {
        try {
            if (!plugin.getConfigManager().isAlertSoundEnabled()) return;
            String soundName = plugin.getConfigManager().getAlertSoundName();
            float volume = plugin.getConfigManager().getAlertSoundVolume();
            float pitch = plugin.getConfigManager().getAlertSoundPitch();
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception ignored) {
        }
    }

    public void alert(String playerName, String action) {
        alert(playerName, action, "unknown");
    }
}
