package com.s1steam.antidumper.stats;

import com.s1steam.antidumper.AntiDumperPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StatsTracker {
    private final AntiDumperPlugin plugin;
    private final File statsFile;
    private FileConfiguration stats;
    private final Map<UUID, ViolationStats> violations = new ConcurrentHashMap<>();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public StatsTracker(AntiDumperPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (statsFile.exists()) {
            stats = YamlConfiguration.loadConfiguration(statsFile);
        } else {
            stats = new YamlConfiguration();
        }
    }

    public void save() {
        try {
            stats.save(statsFile);
        } catch (Exception ignored) {}
    }

    public void logViolation(UUID playerId, String playerName, String module, String detail) {
        String path = playerId.toString() + "." + module;
        List<Map<String, String>> list = (List<Map<String, String>>) stats.getList(path);
        if (list == null) list = new ArrayList<>();

        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("time", LocalDateTime.now().format(FMT));
        entry.put("player", playerName);
        entry.put("detail", detail);

        list.add(entry);
        stats.set(path, list);
        save();

        ViolationStats vs = violations.computeIfAbsent(playerId, k -> new ViolationStats());
        vs.total++;
        vs.lastViolation = System.currentTimeMillis();
        vs.lastModule = module;
    }

    public Map<String, Integer> getModuleStats() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String key : stats.getKeys(false)) {
            for (String module : stats.getConfigurationSection(key).getKeys(false)) {
                List<?> list = stats.getList(key + "." + module);
                result.merge(module, list != null ? list.size() : 0, Integer::sum);
            }
        }
        return result;
    }

    public int getTotalViolations(UUID playerId) {
        ViolationStats vs = violations.get(playerId);
        return vs != null ? vs.total : 0;
    }

    public int getTotalViolations() {
        int total = 0;
        for (String key : stats.getKeys(false)) {
            for (String module : stats.getConfigurationSection(key).getKeys(false)) {
                List<?> list = stats.getList(key + "." + module);
                if (list != null) total += list.size();
            }
        }
        return total;
    }

    public String getFormattedStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== AntiDumper Statistics ===\n");
        sb.append("§7Total violations: §f").append(getTotalViolations()).append("\n");
        sb.append("§7Modules:\n");
        for (Map.Entry<String, Integer> e : getModuleStats().entrySet()) {
            sb.append("  §8- §f").append(e.getKey()).append(": §e").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }

    private static class ViolationStats {
        int total = 0;
        long lastViolation = 0;
        String lastModule = "";
    }
}
