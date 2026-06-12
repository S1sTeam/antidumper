package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiBlink implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, PlayerMoveData> moveData = new ConcurrentHashMap<>();
    private static final double MAX_BLINK_DISTANCE = 15.0;
    private static final long MIN_TIME_BETWEEN_MOVES = 50;
    private static final int MAX_BLINK_VIOLATIONS = 2;

    public AntiBlink(AntiDumperPlugin plugin) {
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
        moveData.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiBlink";
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-blink")) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        long now = System.currentTimeMillis();
        PlayerMoveData data = moveData.computeIfAbsent(player.getUniqueId(), k -> new PlayerMoveData());

        if (data.lastMoveTime > 0) {
            long timeDelta = now - data.lastMoveTime;
            double distance = from.distanceSquared(to);

            if (timeDelta < MIN_TIME_BETWEEN_MOVES) return;

            if (distance > MAX_BLINK_DISTANCE * MAX_BLINK_DISTANCE) {
                data.blinkViolations++;

                if (data.blinkViolations > MAX_BLINK_VIOLATIONS) {
                    String detail = String.format("Blink detected: moved %.1f blocks in %dms (violation %d)",
                        Math.sqrt(distance), timeDelta, data.blinkViolations);
                    LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                    plugin.getStaffAlert().alert(player.getName(), detail, getName());
                    plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                    handleDetection(player);
                    moveData.remove(player.getUniqueId());
                    return;
                }

                String warnDetail = String.format("Suspicious teleport: %.1f blocks in %dms",
                    Math.sqrt(distance), timeDelta);
                LoggerUtil.violation(plugin, getName(), player.getName(), warnDetail);
            } else if (distance > MAX_BLINK_DISTANCE * MAX_BLINK_DISTANCE * 0.5 && timeDelta < 100) {
                data.blinkViolations = Math.max(0, data.blinkViolations - 1);
            }
        }

        data.lastLocation = to.clone();
        data.lastMoveTime = now;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        moveData.remove(event.getPlayer().getUniqueId());
    }

    private static class PlayerMoveData {
        long lastMoveTime = 0;
        Location lastLocation = null;
        int blinkViolations = 0;
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-blink");
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
