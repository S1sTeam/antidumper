package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiTower implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, TowerData> towerData = new ConcurrentHashMap<>();
    private static final int MAX_TOWER_HEIGHT = 5;
    private static final long TOWER_TIME_WINDOW_MS = 1000;
    private static final int MIN_BLOCKS_FOR_TOWER = 3;

    public AntiTower(AntiDumperPlugin plugin) {
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
        towerData.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiTower";
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-tower")) return;

        Location blockLoc = event.getBlock().getLocation();
        Location playerLoc = player.getLocation();
        long now = System.currentTimeMillis();

        TowerData data = towerData.computeIfAbsent(player.getUniqueId(), k -> new TowerData());

        if (blockLoc.getBlockY() > playerLoc.getBlockY() &&
            Math.abs(blockLoc.getBlockX() - playerLoc.getBlockX()) <= 1 &&
            Math.abs(blockLoc.getBlockZ() - playerLoc.getBlockZ()) <= 1) {

            if (now - data.lastPlaceTime > TOWER_TIME_WINDOW_MS * 2) {
                data.towerBlocks = 0;
                data.startY = blockLoc.getBlockY();
                data.startTime = now;
            }

            if (data.towerBlocks == 0) {
                data.startTime = now;
            }

            data.towerBlocks++;
            data.lastPlaceTime = now;

            if (data.towerBlocks >= MIN_BLOCKS_FOR_TOWER) {
                int towerHeight = blockLoc.getBlockY() - data.startY;

                if (towerHeight >= MAX_TOWER_HEIGHT) {
                    String detail = String.format("Tower: %d blocks in %dms (height=%d)",
                        data.towerBlocks, now - data.startTime, towerHeight);
                    LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                    plugin.getStaffAlert().alert(player.getName(), detail, getName());
                    plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                    handleDetection(player);
                    towerData.remove(player.getUniqueId());
                    return;
                }
            }
        } else if (data.towerBlocks > 0) {
            data.towerBlocks = 0;
            data.startY = 0;
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-tower")) return;

        TowerData data = towerData.get(player.getUniqueId());
        if (data == null) return;

        double dy = event.getTo().getY() - event.getFrom().getY();

        if (dy > 0.5 && data.towerBlocks < MIN_BLOCKS_FOR_TOWER) {
            Block below = event.getTo().getBlock().getRelative(0, -1, 0);
            if (below.getType() != Material.AIR && data.lastPlaceTime > 0) {
                long delta = System.currentTimeMillis() - data.lastPlaceTime;
                if (delta < 100) {
                    data.towerBlocks++;
                    if (data.towerBlocks >= MIN_BLOCKS_FOR_TOWER) {
                        data.startY = event.getFrom().getBlockY();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        towerData.remove(event.getPlayer().getUniqueId());
    }

    private static class TowerData {
        long lastPlaceTime = 0;
        long startTime = 0;
        int towerBlocks = 0;
        int startY = 0;
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-tower");
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
