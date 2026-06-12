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
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiElytraFly implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, ElytraData> elytraData = new ConcurrentHashMap<>();
    private static final double MAX_ELYTRA_SPEED = 2.0;
    private static final double MAX_VERTICAL_ACCELERATION = 1.5;
    private static final int MAX_VIOLATIONS = 3;

    public AntiElytraFly(AntiDumperPlugin plugin) {
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
        elytraData.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiElytraFly";
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-elytra-fly")) return;
        if (!player.isGliding()) {
            elytraData.remove(player.getUniqueId());
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        ElytraData data = elytraData.computeIfAbsent(player.getUniqueId(), k -> new ElytraData());

        if (data.lastTick > 0) {
            long tickDelta = 1;
            double dx = to.getX() - from.getX();
            double dz = to.getZ() - from.getZ();
            double dy = to.getY() - from.getY();

            double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);
            double verticalAccel = Math.abs(dy);

            Vector velocity = player.getVelocity();
            double expectedSpeed = Math.sqrt(
                velocity.getX() * velocity.getX() +
                velocity.getZ() * velocity.getZ()
            );

            if (horizontalSpeed > MAX_ELYTRA_SPEED && horizontalSpeed > expectedSpeed * 1.5) {
                data.violations++;

                if (data.violations >= MAX_VIOLATIONS) {
                    player.setGliding(false);
                    String detail = String.format("ElytraFly: speed=%.2f (max %.2f), expected=%.2f",
                        horizontalSpeed, MAX_ELYTRA_SPEED, expectedSpeed);
                    LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                    plugin.getStaffAlert().alert(player.getName(), detail, getName());
                    plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                    handleDetection(player);
                    elytraData.remove(player.getUniqueId());
                    return;
                }
            }

            if (verticalAccel > MAX_VERTICAL_ACCELERATION) {
                data.violations++;

                if (data.violations >= MAX_VIOLATIONS) {
                    player.setGliding(false);
                    String detail = String.format("ElytraFly: vertical=%.2f (max %.2f)",
                        verticalAccel, MAX_VERTICAL_ACCELERATION);
                    LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                    plugin.getStaffAlert().alert(player.getName(), detail, getName());
                    plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                    handleDetection(player);
                    elytraData.remove(player.getUniqueId());
                    return;
                }
            }
        } else {
            data.violations = 0;
        }

        data.lastTick = System.currentTimeMillis();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        elytraData.remove(event.getPlayer().getUniqueId());
    }

    private static class ElytraData {
        long lastTick = 0;
        int violations = 0;
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-elytra-fly");
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
