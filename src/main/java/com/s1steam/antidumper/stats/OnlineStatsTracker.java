package com.s1steam.antidumper.stats;

import com.s1steam.antidumper.AntiDumperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class OnlineStatsTracker {
    private final AntiDumperPlugin plugin;
    private final File statsFile;
    private FileConfiguration stats;
    private Object taskRef = null;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public OnlineStatsTracker(AntiDumperPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "onlinestats.yml");
        load();
    }

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

    public void startTracking() {
        if (!plugin.getConfigManager().isOnlineStatsEnabled()) return;
        stopTracking();
        int interval = plugin.getConfigManager().getOnlineStatsTrackInterval();
        long ticks = interval * 60L * 20L;
        taskRef = plugin.getScheduler().asyncTimer(this::recordSnapshot, ticks, ticks);
    }

    public void stopTracking() {
        if (taskRef != null) {
            plugin.getScheduler().cancelTask(taskRef);
            taskRef = null;
        }
    }

    private void recordSnapshot() {
        int online = Bukkit.getOnlinePlayers().size();
        String today = LocalDate.now().format(FMT);
        String path = today + ".snapshots";
        List<Integer> snapshots = stats.getIntegerList(path);
        snapshots.add(online);
        stats.set(path, snapshots);
        save();
    }

    public int getCurrentOnline() {
        return Bukkit.getOnlinePlayers().size();
    }

    public int getPeakOnline() {
        int peak = 0;
        for (String key : stats.getKeys(false)) {
            for (int val : stats.getIntegerList(key + ".snapshots")) {
                if (val > peak) peak = val;
            }
        }
        return peak;
    }

    public double getAverageOnline() {
        long total = 0;
        int count = 0;
        for (String key : stats.getKeys(false)) {
            for (int val : stats.getIntegerList(key + ".snapshots")) {
                total += val;
                count++;
            }
        }
        return count > 0 ? (double) total / count : 0.0;
    }

    public String getFormattedStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("§6=== Online Statistics ===\n");
        sb.append("§7Current online: §f").append(getCurrentOnline()).append("\n");
        sb.append("§7Peak online: §f").append(getPeakOnline()).append("\n");
        sb.append("§7Average online: §f").append(String.format("%.1f", getAverageOnline())).append("\n");
        sb.append("§7Days tracked: §f").append(stats.getKeys(false).size()).append("\n");
        sb.append("§7By day:\n");
        List<String> days = new ArrayList<>(stats.getKeys(false));
        Collections.sort(days, Collections.reverseOrder());
        for (String day : days) {
            List<Integer> snapshots = stats.getIntegerList(day + ".snapshots");
            int dayPeak = snapshots.stream().mapToInt(Integer::intValue).max().orElse(0);
            double dayAvg = snapshots.stream().mapToInt(Integer::intValue).average().orElse(0);
            sb.append("  §8- §f").append(day).append(" §7peak: §e").append(dayPeak)
                .append(" §7avg: §e").append(String.format("%.1f", dayAvg)).append("\n");
        }
        return sb.toString();
    }

    public void cleanup() {
        int storeDays = plugin.getConfigManager().getOnlineStatsStoreDays();
        String cutoff = LocalDate.now().minusDays(storeDays).format(FMT);
        for (String key : stats.getKeys(false)) {
            if (key.compareTo(cutoff) < 0) {
                stats.set(key, null);
            }
        }
        save();
    }
}
