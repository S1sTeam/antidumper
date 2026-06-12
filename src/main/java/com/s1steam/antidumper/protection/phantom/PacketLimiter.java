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

public class PacketLimiter implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, Deque<Long>> packetTimes = new ConcurrentHashMap<>();
    private static final int MAX_PACKETS_PER_SECOND = 500;

    public PacketLimiter(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        if (!enabled) return;
        HandlerList.unregisterAll(this);
        packetTimes.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "PacketLimiter";
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.packet-limiter")) return;

        long now = System.currentTimeMillis();
        Deque<Long> times = packetTimes.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        times.addLast(now);

        while (!times.isEmpty() && times.peekFirst() < now - 1000) {
            times.pollFirst();
        }

        if (times.size() > MAX_PACKETS_PER_SECOND) {
            String detail = "Packet flood: " + times.size() + "/s (max " + MAX_PACKETS_PER_SECOND + ")";
            LoggerUtil.violation(plugin, getName(), player.getName(), detail);
            plugin.getStaffAlert().alert(player.getName(), detail, getName());
            plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
            handleDetection(player);
            times.clear();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        packetTimes.remove(event.getPlayer().getUniqueId());
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.packet-limiter");
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
