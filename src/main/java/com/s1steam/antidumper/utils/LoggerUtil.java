package com.s1steam.antidumper.utils;

import com.s1steam.antidumper.AntiDumperPlugin;
import org.bukkit.Bukkit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class LoggerUtil {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private LoggerUtil() {}

    public static void info(AntiDumperPlugin plugin, String module, String message) {
        log(plugin, "[INFO]", module, message);
    }

    public static void warn(AntiDumperPlugin plugin, String module, String message) {
        log(plugin, "[WARN]", module, message);
    }

    public static void severe(AntiDumperPlugin plugin, String module, String message) {
        log(plugin, "[SEVERE]", module, message);
    }

    public static void debug(AntiDumperPlugin plugin, String module, String message) {
        if (!plugin.getConfigManager().isDebugEnabled()) return;
        plugin.getLogger().info("[DEBUG] [" + module + "] " + message);
    }

    public static void violation(AntiDumperPlugin plugin, String module, String playerName, String detail) {
        String msg = "§f[§cAntiDumper§f] §7[" + module + "] §c" + playerName + " §7- " + detail;
        Bukkit.getConsoleSender().sendMessage(msg);
        plugin.getLogger().info("VIOLATION [" + module + "] " + playerName + " - " + detail);
    }

    private static void log(AntiDumperPlugin plugin, String level, String module, String message) {
        plugin.getLogger().info(level + " [" + module + "] " + message);
    }
}
