package com.s1steam.antidumper.punish;

import com.s1steam.antidumper.AntiDumperPlugin;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentManager {
    private final AntiDumperPlugin plugin;
    private final Map<UUID, PlayerPunishData> punishData = new ConcurrentHashMap<>();

    public PunishmentManager(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    public void handleViolation(Player player, String module) {
        if (!plugin.getConfigManager().isAutoPunishEnabled()) return;
        if (player.hasPermission(plugin.getConfigManager().getPunishExemptPermission())) return;

        PlayerPunishData data = punishData.computeIfAbsent(player.getUniqueId(), k -> new PlayerPunishData());
        data.violationCount++;
        data.lastViolation = System.currentTimeMillis();

        int count = data.violationCount;
        int warn = plugin.getConfigManager().getPunishThresholdWarn();
        int kick = plugin.getConfigManager().getPunishThresholdKick();
        int ban = plugin.getConfigManager().getPunishThresholdTempban();

        if (count >= ban) {
            int hours = plugin.getConfigManager().getPunishTempbanDurationHours();
            String reason = plugin.getConfigManager().get("punish.tempban-reason")
                .replace("%hours%", String.valueOf(hours))
                .replace("%module%", module);
            plugin.getScheduler().sync(() -> {
                String cmd = "tempban " + player.getName() + " " + hours + "h " + reason;
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd);
            });
            data.violationCount = 0;
        } else if (count >= kick) {
            String reason = plugin.getConfigManager().get("punish.kick-reason")
                .replace("%module%", module);
            plugin.getScheduler().runKickTask(player, () ->
                player.kickPlayer(plugin.getConfigManager().getPrefix() + " " + reason)
            );
        } else if (count >= warn) {
            String msg = plugin.getConfigManager().get("punish.warn-message")
                .replace("%module%", module);
            player.sendMessage(plugin.getConfigManager().getPrefix() + " " + msg);
        }
    }

    public void resetPlayer(UUID playerId) {
        punishData.remove(playerId);
    }

    public int getViolationCount(UUID playerId) {
        PlayerPunishData data = punishData.get(playerId);
        return data != null ? data.violationCount : 0;
    }

    public String getFormattedData(UUID playerId) {
        PlayerPunishData data = punishData.get(playerId);
        if (data == null) return "§7No violations recorded.";
        long diff = System.currentTimeMillis() - data.lastViolation;
        String ago = (diff < 60000) ? "just now" : (diff / 60000) + "m ago";
        return "§7Violations: §f" + data.violationCount
            + "\n§7Last: §f" + ago;
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        long resetMs = plugin.getConfigManager().getPunishResetAfterDays() * 86400L * 1000L;
        punishData.values().removeIf(d -> now - d.lastViolation > resetMs);
    }

    public void punish(UUID playerUuid, String playerName, String action, String reason) {
        Player player = org.bukkit.Bukkit.getPlayer(playerUuid);
        if (player == null) return;
        switch (action.toUpperCase()) {
            case "KICK":
                plugin.getScheduler().runKickTask(player, () -> player.kickPlayer(reason));
                break;
            case "BAN":
            case "TEMP BAN":
                plugin.getScheduler().sync(() -> {
                    String cmd = "tempban " + playerName + " 24h " + reason;
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd);
                });
                break;
        }
    }

    public boolean hasBypass(UUID playerUuid) {
        Player player = org.bukkit.Bukkit.getPlayer(playerUuid);
        return player != null && player.hasPermission(plugin.getConfigManager().getPunishExemptPermission());
    }

    private static class PlayerPunishData {
        int violationCount = 0;
        long lastViolation = 0;
    }
}
