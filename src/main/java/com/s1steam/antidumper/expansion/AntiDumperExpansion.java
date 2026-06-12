package com.s1steam.antidumper.expansion;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;

public class AntiDumperExpansion {

    private final AntiDumperPlugin plugin;

    public AntiDumperExpansion(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    public String onRequest(OfflinePlayer player, String identifier) {
        if (identifier == null || identifier.isEmpty()) return null;

        if (identifier.equals("version"))
            return plugin.getDescription().getVersion();

        if (identifier.equals("modules_total"))
            return String.valueOf(plugin.getModules().size());

        if (identifier.equals("modules_active"))
            return String.valueOf(plugin.getModules().size());

        if (identifier.equals("violations_total"))
            return String.valueOf(plugin.getStats().getTotalViolations());

        if (identifier.equals("violations_global"))
            return String.valueOf(plugin.getStats().getTotalViolations());

        if (identifier.equals("online_peak"))
            return String.valueOf(plugin.getOnlineStats().getPeakOnline());

        if (identifier.equals("online_average"))
            return String.format("%.1f", plugin.getOnlineStats().getAverageOnline());

        if (identifier.equals("online_current"))
            return String.valueOf(plugin.getOnlineStats().getCurrentOnline());

        if (identifier.equals("lang"))
            return plugin.getConfigManager().getCurrentLang();

        if (identifier.equals("server_name"))
            return plugin.getConfigManager().getServerName();

        if (identifier.equals("platform"))
            return com.s1steam.antidumper.platform.Platform.getName();

        if (identifier.equals("database"))
            return plugin.getDatabase().getType().toUpperCase();

        if (identifier.equals("redis"))
            return plugin.getRedis().isEnabled() ? "ON" : "OFF";

        if (identifier.startsWith("player_") && player != null) {
            String sub = identifier.substring(7);

            if (sub.equals("violations"))
                return String.valueOf(plugin.getStats().getTotalViolations(player.getUniqueId()));

            if (sub.equals("punish_level"))
                return String.valueOf(plugin.getPunishment().getViolationCount(player.getUniqueId()));

            if (sub.equals("punish_reset_days"))
                return String.valueOf(plugin.getConfigManager().getPunishResetAfterDays());

            if (sub.equals("name"))
                return player.getName();

            if (sub.equals("uuid"))
                return player.getUniqueId().toString();

            if (sub.equals("bypass"))
                return String.valueOf(plugin.getPunishment().hasBypass(player.getUniqueId()));
        }

        if (identifier.startsWith("geo_") && player != null) {
            String[] parts = identifier.split("_", 3);
            if (parts.length >= 3) {
                String playerName = parts[1];
                String type = parts[2];
                Player onlinePlayer = Bukkit.getPlayerExact(playerName);
                if (onlinePlayer != null) return getGeoValue(onlinePlayer, type);
            }
        }

        if (identifier.startsWith("module_")) {
            String searchName = identifier.substring(7).toLowerCase();
            for (ProtectionModule mod : plugin.getModules()) {
                String modKey = mod.getName().toLowerCase()
                    .replace("anti", "")
                    .replace(" ", "-")
                    .replace("_", "");
                if (modKey.equals(searchName)) return "ON";
            }
            return "OFF";
        }

        if (identifier.startsWith("check_") && player != null) {
            String sub = identifier.substring(6);
            if (sub.equals("suspicious")) {
                Player online = Bukkit.getPlayer(player.getUniqueId());
                if (online != null)
                    return String.valueOf(online.getListeningPluginChannels().size());
            }
        }

        return null;
    }

    private String getGeoValue(Player player, String type) {
        com.s1steam.antidumper.geo.GeoIPManager.GeoEntry geo =
            plugin.getGeoIP().getGeo(player.getUniqueId());
        if (geo == null) return "?";
        if ("country".equals(type)) return geo.getCountry();
        if ("city".equals(type)) return geo.getCity();
        return "?";
    }
}
