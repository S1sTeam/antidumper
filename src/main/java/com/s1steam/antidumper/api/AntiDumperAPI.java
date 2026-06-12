package com.s1steam.antidumper.api;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;

import java.util.List;
import java.util.UUID;

public final class AntiDumperAPI {

    private static AntiDumperPlugin plugin;

    public static void init(AntiDumperPlugin instance) {
        plugin = instance;
    }

    public static AntiDumperPlugin getPlugin() {
        return plugin;
    }

    public static boolean isModuleEnabled(String moduleName) {
        return plugin.getConfigManager().isModuleEnabled(moduleName);
    }

    public static void toggleModule(String moduleName, boolean enabled) {
        plugin.getConfigManager().setModuleEnabled(moduleName, enabled);
        plugin.getConfigManager().saveConfig();
        plugin.reload();
    }

    public static List<ProtectionModule> getActiveModules() {
        return plugin.getModules();
    }

    public static int getPlayerViolations(UUID playerUuid) {
        return plugin.getDatabase().getViolationsTotal(playerUuid);
    }

    public static void logViolation(UUID playerUuid, String playerName, String module) {
        plugin.getDatabase().logViolation(playerUuid, playerName, module);
    }

    public static void punishPlayer(UUID playerUuid, String playerName, String action, String reason) {
        plugin.getDatabase().logPunishment(playerUuid, playerName, action, reason);
        plugin.getPunishment().punish(playerUuid, playerName, action, reason);
    }

    public static boolean isRedisEnabled() {
        return plugin.getRedis().isEnabled();
    }

    public static void sendRedisMessage(String type, java.util.Map<String, Object> data) {
        plugin.getRedis().publish(type, data);
    }

    public static String getServerName() {
        return plugin.getConfigManager().getServerName();
    }

    public static boolean hasBypass(UUID playerUuid) {
        return plugin.getPunishment().hasBypass(playerUuid);
    }

    private AntiDumperAPI() {}
}
