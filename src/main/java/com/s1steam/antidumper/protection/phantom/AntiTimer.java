package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiTimer implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, Deque<Long>> moveTimes = new ConcurrentHashMap<>();
    private static final int MAX_MOVES_PER_SECOND = 22;
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int VIOLATION_THRESHOLD = 3;
    private final Map<UUID, Integer> violationCounts = new ConcurrentHashMap<>();

    public AntiTimer(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startChecker();
    }

    @Override
    public void disable() {
        if (!enabled) return;
        HandlerList.unregisterAll(this);
        moveTimes.clear();
        violationCounts.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiTimer";
    }

    private void startChecker() {
        plugin.getScheduler().syncTimer(() -> {
            if (!enabled) return;
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Deque<Long>> entry : moveTimes.entrySet()) {
                Deque<Long> times = entry.getValue();
                while (!times.isEmpty() && times.peekFirst() < now - 1000) {
                    times.pollFirst();
                }
                int count = times.size();
                if (count > MAX_MOVES_PER_SECOND) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null) continue;

                    int violations = violationCounts.merge(entry.getKey(), 1, Integer::sum);
                    if (violations >= VIOLATION_THRESHOLD) {
                        times.clear();
                        violationCounts.remove(entry.getKey());
                        String detail = "Timer speed: " + count + " moves/s (max " + MAX_MOVES_PER_SECOND + ")";
                        LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                        plugin.getStaffAlert().alert(player.getName(), detail, getName());
                        plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                        handleDetection(player);
                    }
                }
            }
        }, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-timer")) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockY() == event.getTo().getBlockY() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        long now = System.currentTimeMillis();
        Deque<Long> times = moveTimes.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        times.addLast(now);

        while (!times.isEmpty() && times.peekFirst() < now - 1000) {
            times.pollFirst();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        moveTimes.remove(event.getPlayer().getUniqueId());
        violationCounts.remove(event.getPlayer().getUniqueId());
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-timer");
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
