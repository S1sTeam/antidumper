package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiScaffold implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, ScaffoldData> scaffoldData = new ConcurrentHashMap<>();
    private static final int MAX_BLOCKS_PER_SECOND = 8;
    private static final int MAX_BLOCKS_WITHOUT_MOVEMENT = 3;
    private static final int MAX_GAPS = 1;

    public AntiScaffold(AntiDumperPlugin plugin) {
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
        scaffoldData.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiScaffold";
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-scaffold")) return;

        long now = System.currentTimeMillis();
        ScaffoldData data = scaffoldData.computeIfAbsent(player.getUniqueId(), k -> new ScaffoldData());

        data.placedBlocks.addLast(now);
        while (!data.placedBlocks.isEmpty() && data.placedBlocks.peekFirst() < now - 1000) {
            data.placedBlocks.pollFirst();
        }

        int blocksPerSecond = data.placedBlocks.size();

        if (blocksPerSecond > MAX_BLOCKS_PER_SECOND) {
            event.setCancelled(true);
            String detail = "Scaffold: " + blocksPerSecond + " blocks/s (max " + MAX_BLOCKS_PER_SECOND + ")";
            LoggerUtil.violation(plugin, getName(), player.getName(), detail);
            plugin.getStaffAlert().alert(player.getName(), detail, getName());
            plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
            handleDetection(player);
            scaffoldData.remove(player.getUniqueId());
            return;
        }

        Location playerLoc = player.getLocation();
        Block placed = event.getBlock();
        Block against = event.getBlockAgainst();

        if (placed.getLocation().distanceSquared(playerLoc) > 36) {
            data.violations++;
            if (data.violations >= 2) {
                event.setCancelled(true);
                String detail = "Scaffold: block placed too far";
                LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                plugin.getStaffAlert().alert(player.getName(), detail, getName());
                plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                handleDetection(player);
                scaffoldData.remove(player.getUniqueId());
                return;
            }
        } else {
            data.violations = Math.max(0, data.violations - 1);
        }

        if (data.lastPlacedLocation != null) {
            int gap = Math.abs(placed.getX() - data.lastPlacedLocation.getBlockX()) +
                      Math.abs(placed.getZ() - data.lastPlacedLocation.getBlockZ());

            if (gap > MAX_GAPS + 1 && placed.getY() >= data.lastPlacedLocation.getBlockY()) {
                data.airPlaceViolations++;

                if (data.airPlaceViolations >= 3) {
                    event.setCancelled(true);
                    String detail = "Scaffold: suspicious block gap (" + gap + ")";
                    LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                    plugin.getStaffAlert().alert(player.getName(), detail, getName());
                    plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                    handleDetection(player);
                    scaffoldData.remove(player.getUniqueId());
                    return;
                }
            } else {
                data.airPlaceViolations = Math.max(0, data.airPlaceViolations - 1);
            }
        }

        data.lastPlacedLocation = placed.getLocation();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();

        ScaffoldData data = scaffoldData.get(player.getUniqueId());
        if (data != null) {
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                data.blocksWithoutMovement = 0;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scaffoldData.remove(event.getPlayer().getUniqueId());
    }

    private static class ScaffoldData {
        final Deque<Long> placedBlocks = new ArrayDeque<>();
        Location lastPlacedLocation = null;
        int violations = 0;
        int airPlaceViolations = 0;
        int blocksWithoutMovement = 0;
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-scaffold");
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
