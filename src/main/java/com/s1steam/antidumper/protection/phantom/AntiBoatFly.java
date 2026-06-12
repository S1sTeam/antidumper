package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AntiBoatFly implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, BoatMoveData> boatData = new HashMap<>();
    private static final double MAX_BOAT_SPEED = 0.6;
    private static final double MAX_VERTICAL_SPEED = 0.5;
    private static final int MAX_VIOLATIONS = 3;

    public AntiBoatFly(AntiDumperPlugin plugin) {
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
        boatData.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiBoatFly";
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!enabled) return;
        Entity vehicle = event.getVehicle();
        if (!(vehicle instanceof Boat)) return;

        Boat boat = (Boat) vehicle;
        if (boat.getPassengers().isEmpty()) return;

        Entity passenger = boat.getPassengers().get(0);
        if (!(passenger instanceof Player)) return;

        Player player = (Player) passenger;
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-boat-fly")) return;

        BoatMoveData data = boatData.computeIfAbsent(player.getUniqueId(), k -> new BoatMoveData());

        if (data.lastX != Double.MAX_VALUE) {
            double dx = event.getTo().getX() - data.lastX;
            double dz = event.getTo().getZ() - data.lastZ;
            double dy = event.getTo().getY() - data.lastY;

            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double vertical = Math.abs(dy);

            if (horizontal > MAX_BOAT_SPEED || vertical > MAX_VERTICAL_SPEED) {
                data.violations++;

                if (data.violations >= MAX_VIOLATIONS) {
                    boat.eject();
                    vehicle.remove();
                    String detail = String.format("BoatFly: speed=%.2f (max %.2f), vertical=%.2f (max %.2f)",
                        horizontal, MAX_BOAT_SPEED, vertical, MAX_VERTICAL_SPEED);
                    LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                    plugin.getStaffAlert().alert(player.getName(), detail, getName());
                    plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                    handleDetection(player);
                    boatData.remove(player.getUniqueId());
                    return;
                }
            } else {
                data.violations = Math.max(0, data.violations - 1);
            }
        }

        data.lastX = event.getTo().getX();
        data.lastY = event.getTo().getY();
        data.lastZ = event.getTo().getZ();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boatData.remove(event.getPlayer().getUniqueId());
    }

    private static class BoatMoveData {
        double lastX = Double.MAX_VALUE;
        double lastY = Double.MAX_VALUE;
        double lastZ = Double.MAX_VALUE;
        int violations = 0;
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-boat-fly");
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
