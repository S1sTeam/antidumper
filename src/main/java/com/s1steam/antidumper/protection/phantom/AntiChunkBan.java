package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiChunkBan implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, Deque<Long>> teleportTimes = new ConcurrentHashMap<>();
    private static final int MAX_CHUNKS_PER_SECOND = 25;

    public AntiChunkBan(AntiDumperPlugin plugin) {
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
        teleportTimes.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiChunkBan";
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-chunk-ban")) return;

        long now = System.currentTimeMillis();
        Deque<Long> times = teleportTimes.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        times.addLast(now);

        while (!times.isEmpty() && times.peekFirst() < now - 1000) {
            times.pollFirst();
        }

        if (times.size() > MAX_CHUNKS_PER_SECOND) {
            event.setCancelled(true);
            String detail = "Chunk request flood: " + times.size() + "/s";
            LoggerUtil.violation(plugin, getName(), player.getName(), detail);
            plugin.getStaffAlert().alert(player.getName(), detail, getName());
            plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
            handleDetection(player);
            times.clear();
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-chunk-ban")) return;

        long now = System.currentTimeMillis();
        Deque<Long> times = teleportTimes.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        times.addLast(now);

        while (!times.isEmpty() && times.peekFirst() < now - 1000) {
            times.pollFirst();
        }

        if (times.size() > MAX_CHUNKS_PER_SECOND) {
            String detail = "Rapid world change flood: " + times.size() + "/s";
            LoggerUtil.violation(plugin, getName(), player.getName(), detail);
            plugin.getStaffAlert().alert(player.getName(), detail, getName());
            plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
            handleDetection(player);
            times.clear();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        teleportTimes.remove(event.getPlayer().getUniqueId());
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-chunk-ban");
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
