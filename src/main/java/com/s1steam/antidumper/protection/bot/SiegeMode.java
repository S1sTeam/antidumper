package com.s1steam.antidumper.protection.bot;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SiegeMode implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private boolean siegeActive = false;

    private final AtomicInteger connectionsThisMinute = new AtomicInteger(0);
    private final Map<InetAddress, AtomicInteger> ipConnections = new ConcurrentHashMap<>();

    private static final int THRESHOLD = 10;
    private static final long INTERVAL_TICKS = 1200L;

    public SiegeMode(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        plugin.getScheduler().syncTimer(() -> {
            int count = connectionsThisMinute.getAndSet(0);
            ipConnections.clear();

            if (count > THRESHOLD) {
                siegeActive = true;
                LoggerUtil.warn(plugin, getName(),
                    "Siege mode ACTIVATED: " + count + " connections in last minute");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.hasPermission("antidumper.alert")) {
                        p.sendMessage(plugin.getConfigManager().getPrefixed("protection.siege-mode.activated")
                            .replace("%count%", String.valueOf(count)));
                    }
                }
            } else {
                if (siegeActive) {
                    siegeActive = false;
                    LoggerUtil.info(plugin, getName(), "Siege mode deactivated");
                }
            }
        }, INTERVAL_TICKS, INTERVAL_TICKS);
    }

    @Override
    public void disable() {
        if (!enabled) return;
        HandlerList.unregisterAll(this);
        siegeActive = false;
        connectionsThisMinute.set(0);
        ipConnections.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "SiegeMode";
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.siege-mode")) return;

        InetAddress address = event.getAddress();
        ipConnections.computeIfAbsent(address, k -> new AtomicInteger()).incrementAndGet();
        connectionsThisMinute.incrementAndGet();

        int ipCount = ipConnections.get(address).get();
        if (ipCount > THRESHOLD / 2) {
            LoggerUtil.violation(plugin, getName(), address.getHostAddress(),
                "Botnet pattern: " + ipCount + " connections from same IP");
            plugin.getStaffAlert().alert(address.getHostAddress(),
                "Botnet pattern: " + ipCount + " connections from same IP", getName());
        }

        if (siegeActive) {
            
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().get("protection.siege-mode.kick"));
            LoggerUtil.violation(plugin, getName(), address.getHostAddress(),
                "Rejected during siege mode");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.siege-mode")) return;
        if (siegeActive) {
            
            LoggerUtil.violation(plugin, getName(), player.getName(),
                "Joined during siege mode");
            plugin.getScheduler().runKickTask(player, () ->
                player.kickPlayer(plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().get("protection.siege-mode.kick")));
        }
    }

    public boolean isSiegeActive() {
        return siegeActive;
    }

    private void handleDetection(Player player) {
        
        String action = plugin.getConfigManager().getModuleAction("protection.siege-mode");
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
