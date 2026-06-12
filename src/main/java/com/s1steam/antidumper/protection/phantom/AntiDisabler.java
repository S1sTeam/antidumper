package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiDisabler implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, Integer> disablerAttempts = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 2;

    private static final Set<String> DISABLER_COMMANDS = new HashSet<>(Arrays.asList(
        "/antidisabler", "/disabler", "/bypass", "/cheats",
        "/enableanticheat", "/disableanticheat", "/toggleanticheat",
        "/anticheat", "/ac", "/nocheatplus", "/ncp",
        "/anticrash", "/antispam", "/antibot",
        "/antidump", "/antigrabber", "/antisteal",
        "/steal", "/grab", "/dump", "/grabber",
        "/worlddownloader", "/worlddownload", "/wdl",
        "/litematica", "/schematica", "/baritone",
        "/meteor", "/wurst", "/aristois", "/impact",
        "/future", "/liquidbounce", "/lbow",
        "/authmet", "/loginsec", "/security",
        "/admin", "/opme", "/give", "/gamemode",
        "/antivpn", "/vpn", "/proxy"
    ));

    private static final Set<String> DISABLER_COMMANDS_EXACT = new HashSet<>(Arrays.asList(
        "//calc", "//eval", "//solve",
        "/crashserver", "/crashme", "/crash"
    ));

    private static final Set<String> SUSPICIOUS_PATTERNS = new HashSet<>(Arrays.asList(
        "nocheatplus", "ncp", "anticheat", "antidump",
        "disable", "bypass", "crack", "exploit"
    ));

    public AntiDisabler(AntiDumperPlugin plugin) {
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
        disablerAttempts.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiDisabler";
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-disabler")) return;

        String message = event.getMessage().toLowerCase().trim();

        if (DISABLER_COMMANDS_EXACT.contains(message)) {
            event.setCancelled(true);
            handleDisablerDetection(player, "Exact disabler command: " + message);
            return;
        }

        String firstArg = message.split(" ")[0];
        if (DISABLER_COMMANDS.contains(firstArg)) {
            event.setCancelled(true);
            handleDisablerDetection(player, "Disabler command: " + firstArg);
            return;
        }

        if (message.length() > 2 && message.length() < 50) {
            for (String pattern : SUSPICIOUS_PATTERNS) {
                if (message.contains(pattern)) {
                    int attempts = disablerAttempts.merge(player.getUniqueId(), 1, Integer::sum);
                    if (attempts >= MAX_ATTEMPTS) {
                        event.setCancelled(true);
                        handleDisablerDetection(player, "Suspicious command: " + message);
                        return;
                    }

                    LoggerUtil.violation(plugin, getName(), player.getName(),
                        "Suspicious command pattern: " + message);
                    break;
                }
            }
        }
    }

    private void handleDisablerDetection(Player player, String reason) {
        String detail = "Disabler attempt: " + reason;
        LoggerUtil.violation(plugin, getName(), player.getName(), detail);
        plugin.getStaffAlert().alert(player.getName(), detail, getName());
        plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
        handleDetection(player);
        disablerAttempts.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        disablerAttempts.remove(event.getPlayer().getUniqueId());
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-disabler");
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
