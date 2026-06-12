package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiInventoryMove implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, InventoryMoveData> inventoryData = new ConcurrentHashMap<>();
    private static final int MAX_VIOLATIONS = 2;

    public AntiInventoryMove(AntiDumperPlugin plugin) {
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
        inventoryData.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiInventoryMove";
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!enabled) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-inventory-move")) return;

        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) return;

        InventoryMoveData data = inventoryData.computeIfAbsent(player.getUniqueId(), k -> new InventoryMoveData());
        data.lastClickTime = System.currentTimeMillis();
        data.inventoryOpen = true;
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!enabled) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-inventory-move")) return;

        InventoryType type = event.getInventory().getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.CREATIVE) return;

        InventoryMoveData data = inventoryData.computeIfAbsent(player.getUniqueId(), k -> new InventoryMoveData());
        data.lastClickTime = System.currentTimeMillis();
        data.inventoryOpen = true;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-inventory-move")) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
            event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        InventoryMoveData data = inventoryData.get(player.getUniqueId());
        if (data == null || !data.inventoryOpen) return;

        long now = System.currentTimeMillis();
        if (now - data.lastClickTime < 100) {
            data.violations++;

            if (data.violations >= MAX_VIOLATIONS) {
                String detail = "InventoryMove: moved while inventory open";
                LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                plugin.getStaffAlert().alert(player.getName(), detail, getName());
                plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                handleDetection(player);
                inventoryData.remove(player.getUniqueId());
                return;
            }

            player.closeInventory();
            String warnDetail = "Moving with open inventory (violation " + data.violations + ")";
            LoggerUtil.violation(plugin, getName(), player.getName(), warnDetail);
        }

        data.inventoryOpen = false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inventoryData.remove(event.getPlayer().getUniqueId());
    }

    private static class InventoryMoveData {
        long lastClickTime = 0;
        boolean inventoryOpen = false;
        int violations = 0;
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-inventory-move");
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
