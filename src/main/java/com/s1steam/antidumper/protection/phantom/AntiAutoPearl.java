package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiAutoPearl implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, Deque<Long>> pearlThrows = new ConcurrentHashMap<>();
    private static final int MAX_PEARLS_PER_SECOND = 1;
    private static final int MAX_VIOLATIONS = 2;
    private final Map<UUID, Integer> violations = new ConcurrentHashMap<>();

    public AntiAutoPearl(AntiDumperPlugin plugin) {
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
        pearlThrows.clear();
        violations.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiAutoPearl";
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-auto-pearl")) return;

        if (event.getItem() == null || event.getItem().getType() != Material.ENDER_PEARL) return;

        long now = System.currentTimeMillis();
        Deque<Long> times = pearlThrows.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        times.addLast(now);

        while (!times.isEmpty() && times.peekFirst() < now - 1000) {
            times.pollFirst();
        }

        if (times.size() > MAX_PEARLS_PER_SECOND) {
            event.setCancelled(true);
            int v = violations.merge(player.getUniqueId(), 1, Integer::sum);

            if (v >= MAX_VIOLATIONS) {
                String detail = "AutoPearl: " + times.size() + " pearls/s (max " + MAX_PEARLS_PER_SECOND + ")";
                LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                plugin.getStaffAlert().alert(player.getName(), detail, getName());
                plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                handleDetection(player);
                pearlThrows.remove(player.getUniqueId());
                violations.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pearlThrows.remove(event.getPlayer().getUniqueId());
        violations.remove(event.getPlayer().getUniqueId());
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-auto-pearl");
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
