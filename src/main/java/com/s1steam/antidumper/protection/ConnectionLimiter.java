package com.s1steam.antidumper.protection;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionLimiter implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private final Map<InetAddress, ConnectionCounter> connections = new ConcurrentHashMap<>();
    private boolean enabled = false;

    private static class ConnectionCounter {
        final AtomicInteger count = new AtomicInteger(0);
        long lastReset = System.currentTimeMillis();
    }

    public ConnectionLimiter(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getScheduler().syncTimer(() -> {
            long now = System.currentTimeMillis();
            connections.values().removeIf(c -> now - c.lastReset > 60000);
        }, 1200L, 1200L);
    }

    @Override
    public void disable() {
        if (!enabled) return;
        connections.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "ConnectionLimiter";
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        int maxConn = plugin.getConfigManager().getMaxConnectionsPerIP();
        if (maxConn <= 0) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.connection-limiter")) return;

        InetAddress addr = event.getAddress();
        ConnectionCounter c = connections.computeIfAbsent(addr, k -> new ConnectionCounter());
        long now = System.currentTimeMillis();

        if (now - c.lastReset > 60000) {
            c.count.set(0);
            c.lastReset = now;
        }

        int current = c.count.incrementAndGet();
        if (current > maxConn) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                plugin.getConfigManager().getPrefix() + " "
                + "§cСлишком много подключений с вашего IP. Подождите.");
            LoggerUtil.violation(plugin, "ConnectionLimiter", addr.getHostAddress(),
                "Max connections exceeded: " + current + "/" + maxConn);
        }
    }
}
