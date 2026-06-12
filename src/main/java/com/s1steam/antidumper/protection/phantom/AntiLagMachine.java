package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiLagMachine implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, Integer> spawnWarnings = new ConcurrentHashMap<>();
    private static final int MAX_ENTITIES_PER_CHUNK = 40;
    private static final int MAX_SPAWN_WARNINGS = 3;

    public AntiLagMachine(AntiDumperPlugin plugin) {
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
        spawnWarnings.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiLagMachine";
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!enabled) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;

        Chunk chunk = event.getLocation().getChunk();
        int count = countEntitiesInChunk(chunk);

        if (count > MAX_ENTITIES_PER_CHUNK) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player) return;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!enabled) return;

        Chunk chunk = event.getLocation().getChunk();
        int count = countEntitiesInChunk(chunk);

        if (count > MAX_ENTITIES_PER_CHUNK * 2) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntitySpawn(org.bukkit.event.entity.EntitySpawnEvent event) {
        if (!enabled) return;
        if (event.getEntity() instanceof Player) return;

        Chunk chunk = event.getLocation().getChunk();
        int count = countEntitiesInChunk(chunk);

        if (count > MAX_ENTITIES_PER_CHUNK) {
            String name = event.getEntityType().name();
            if (name.contains("MINECART") || name.contains("BOAT") ||
                name.contains("DRAGON") || name.contains("CRYSTAL") ||
                name.equals("DROPPED_ITEM") || name.contains("ORB") ||
                name.contains("PRIMED_TNT")) {
                event.setCancelled(true);
            }
        }

        if (count > MAX_ENTITIES_PER_CHUNK * 3) {
            event.setCancelled(true);
        }
    }

    private int countEntitiesInChunk(Chunk chunk) {
        int count = 0;
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Player)) {
                count++;
            }
        }
        return count;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-lag-machine")) return;
        spawnWarnings.remove(player.getUniqueId());
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-lag-machine");
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
