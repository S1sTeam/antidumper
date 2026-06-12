package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiNoFall implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, FallData> fallData = new ConcurrentHashMap<>();
    private static final double MAX_SAFE_FALL_DISTANCE = 3.0;
    private static final int MAX_VIOLATIONS = 2;

    public AntiNoFall(AntiDumperPlugin plugin) {
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
        fallData.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiNoFall";
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-no-fall")) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isFlying()) return;

        double fallDistance = player.getFallDistance();
        boolean onGround = player.isOnGround();
        boolean wasOnGround = event.getFrom().getBlockY() == Math.floor(event.getFrom().getY()) &&
            event.getFrom().getBlock().getRelative(0, -1, 0).getType().isSolid();

        FallData data = fallData.computeIfAbsent(player.getUniqueId(), k -> new FallData());

        if (!onGround && fallDistance > 0) {
            data.currentFallStart = System.currentTimeMillis();
            data.fallDistance = Math.max(data.fallDistance, fallDistance);
        }

        if (onGround && data.fallDistance > MAX_SAFE_FALL_DISTANCE) {
            long fallTime = System.currentTimeMillis() - data.currentFallStart;
            boolean hadLandingPacket = data.groundPackets > 0;

            if (!hadLandingPacket || fallTime < 50) {
                data.violations++;

                if (data.violations >= MAX_VIOLATIONS) {
                    String detail = String.format("NoFall: fell %.1f blocks without damage (time=%dms, groundPackets=%d)",
                        data.fallDistance, fallTime, data.groundPackets);
                    LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                    plugin.getStaffAlert().alert(player.getName(), detail, getName());
                    plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                    handleDetection(player);
                    fallData.remove(player.getUniqueId());
                    return;
                }
            }

            data.fallDistance = 0;
            data.groundPackets = 0;
        }

        if (onGround) {
            data.groundPackets++;
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-no-fall")) return;

        FallData data = fallData.get(player.getUniqueId());
        if (data != null) {
            data.fallDistance = 0;
            data.groundPackets = 0;
            data.violations = 0;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fallData.remove(event.getPlayer().getUniqueId());
    }

    private static class FallData {
        double fallDistance = 0;
        int groundPackets = 0;
        int violations = 0;
        long currentFallStart = 0;
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-no-fall");
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
