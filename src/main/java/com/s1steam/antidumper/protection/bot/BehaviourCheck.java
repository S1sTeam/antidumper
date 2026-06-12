package com.s1steam.antidumper.protection.bot;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BehaviourCheck implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private final Map<UUID, Long> joinTime = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerBehaviour> behaviourMap = new ConcurrentHashMap<>();
    private boolean enabled = false;

    private static final long FIRST_MOVE_THRESHOLD_MS = 500L;
    private static final long FAST_ACTION_THRESHOLD_MS = 1000L;

    private static class PlayerBehaviour {
        boolean moved;
        boolean chatted;
        boolean commanded;
        Location lastLocation;
        int suspiciousMovements;
    }

    public BehaviourCheck(AntiDumperPlugin plugin) {
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
        joinTime.clear();
        behaviourMap.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "BehaviourCheck";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.behaviour-check")) return;
        long now = System.currentTimeMillis();
        joinTime.put(player.getUniqueId(), now);
        PlayerBehaviour behaviour = new PlayerBehaviour();
        behaviour.lastLocation = player.getLocation().clone();
        behaviourMap.put(player.getUniqueId(), behaviour);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.behaviour-check")) return;
        Long joined = joinTime.get(player.getUniqueId());
        if (joined == null) return;

        PlayerBehaviour behaviour = behaviourMap.get(player.getUniqueId());
        if (behaviour == null) return;

        long elapsed = System.currentTimeMillis() - joined;

        if (!behaviour.moved) {
            behaviour.moved = true;
            if (elapsed < FIRST_MOVE_THRESHOLD_MS) {
                flag(player, "First move too fast: " + elapsed + "ms");
                return;
            }
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        float yawDelta = Math.abs(to.getYaw() - from.getYaw());
        float pitchDelta = Math.abs(to.getPitch() - from.getPitch());

        if (elapsed < FAST_ACTION_THRESHOLD_MS) {
            if (yawDelta > 0 || pitchDelta > 0) {
                flag(player, "Movement within " + elapsed + "ms of join");
            }
        }

        if (yawDelta > 0 && pitchDelta > 0) {
            float ratio = yawDelta / pitchDelta;
            if (ratio > 50 || ratio < 0.02) {
                behaviour.suspiciousMovements++;
                if (behaviour.suspiciousMovements > 5) {
                    flag(player, "Suspicious yaw/pitch ratio: " + String.format("%.2f", ratio));
                }
            }
        }

        if (behaviour.lastLocation != null) {
            double dx = to.getX() - behaviour.lastLocation.getX();
            double dz = to.getZ() - behaviour.lastLocation.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > 0 && yawDelta == 0 && pitchDelta == 0) {
                behaviour.suspiciousMovements++;
                if (behaviour.suspiciousMovements > 5) {
                    flag(player, "Perfect movement without rotation change");
                }
            }
        }

        behaviour.lastLocation = to.clone();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.behaviour-check")) return;
        Long joined = joinTime.get(player.getUniqueId());
        if (joined == null) return;

        PlayerBehaviour behaviour = behaviourMap.get(player.getUniqueId());
        if (behaviour == null) return;

        behaviour.chatted = true;
        long elapsed = System.currentTimeMillis() - joined;
        if (elapsed < FAST_ACTION_THRESHOLD_MS) {
            flag(player, "Chat message within " + elapsed + "ms of join");
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.behaviour-check")) return;
        Long joined = joinTime.get(player.getUniqueId());
        if (joined == null) return;

        PlayerBehaviour behaviour = behaviourMap.get(player.getUniqueId());
        if (behaviour == null) return;

        behaviour.commanded = true;
        long elapsed = System.currentTimeMillis() - joined;
        if (elapsed < FAST_ACTION_THRESHOLD_MS) {
            flag(player, "Command within " + elapsed + "ms of join: " + event.getMessage());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.behaviour-check")) return;
        joinTime.remove(player.getUniqueId());
        behaviourMap.remove(player.getUniqueId());
    }

    private void flag(Player player, String reason) {
        LoggerUtil.violation(plugin, getName(), player.getName(), reason);
        plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), reason);
        plugin.getStaffAlert().alert(player.getName(), reason, getName());
        handleDetection(player);
    }

    private void handleDetection(Player player) {
        
        String action = plugin.getConfigManager().getModuleAction("protection.behaviour-check");
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
