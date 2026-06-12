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
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiPhase implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, Integer> phaseViolations = new ConcurrentHashMap<>();
    private static final int MAX_VIOLATIONS = 3;

    public AntiPhase(AntiDumperPlugin plugin) {
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
        phaseViolations.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiPhase";
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-phase")) return;
        if (player.isInsideVehicle() || player.isFlying()) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        if (from.getBlockX() == to.getBlockX() &&
            from.getBlockY() == to.getBlockY() &&
            from.getBlockZ() == to.getBlockZ()) return;

        Block headBlock = to.getBlock().getRelative(0, 1, 0);
        Block feetBlock = to.getBlock();
        Block belowBlock = to.getBlock().getRelative(0, -1, 0);

        boolean insideSolid = headBlock.getType().isSolid() && feetBlock.getType().isSolid();
        boolean insidePartial = isNonFullBlock(headBlock.getType()) && isNonFullBlock(feetBlock.getType());
        boolean clipped = isPhaseMaterial(headBlock.getType()) && isPhaseMaterial(feetBlock.getType());

        if (insideSolid || clipped) {
            int violations = phaseViolations.merge(player.getUniqueId(), 1, Integer::sum);
            long now = System.currentTimeMillis();
            plugin.getScheduler().syncLater(() ->
                phaseViolations.merge(player.getUniqueId(), -1, (a, b) -> Math.max(0, a + b)), 100L);

            if (violations >= MAX_VIOLATIONS) {
                Vector back = from.toVector().subtract(to.toVector()).normalize().multiply(2);
                Location safe = from.clone().add(back.getX(), 0, back.getZ());
                safe.setYaw(to.getYaw());
                safe.setPitch(to.getPitch());
                player.teleport(safe);

                String detail = "Phase attempt into " + headBlock.getType().name() +
                    " at " + to.getBlockX() + "," + to.getBlockY() + "," + to.getBlockZ();
                LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                plugin.getStaffAlert().alert(player.getName(), detail, getName());
                plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                handleDetection(player);
                phaseViolations.remove(player.getUniqueId());
            }
        } else {
            phaseViolations.remove(player.getUniqueId());
        }
    }

    private boolean isPhaseMaterial(Material material) {
        return material == Material.WATER || material == Material.LAVA ||
               material == Material.BUBBLE_COLUMN || material == Material.KELP_PLANT ||
               material == Material.SEAGRASS || material == Material.TALL_SEAGRASS ||
               material.name().contains("CARPET") || material.name().contains("SLAB");
    }

    private boolean isNonFullBlock(Material material) {
        return !material.isSolid() || material.name().contains("FENCE") ||
               material.name().contains("DOOR") || material.name().contains("GATE") ||
               material.name().contains("TRAPDOOR");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        phaseViolations.remove(event.getPlayer().getUniqueId());
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-phase");
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
