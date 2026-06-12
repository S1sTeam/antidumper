package com.s1steam.antidumper.protection;

import com.s1steam.antidumper.AntiDumperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AntiPluginStealer implements ProtectionModule, Listener, PluginMessageListener {
    private final AntiDumperPlugin plugin;
    private final Set<UUID> flagged = new HashSet<>();
    private boolean enabled = false;

    private static final String[] STEALER_CHANNELS = {
        "stealer:main", "stealer:file",
        "plugingrabber:main", "plugingrabber:file",
        "plugin_grabber", "plugin-grabber",
        "dl:file", "downloader:file",
        "filegrab:main", "fg:main",
        "worlddownloader:file", "wdl:file"
    };

    private static final String[] HEURISTIC_KEYWORDS = {
        "stealer", "grabber", "plugingrabber",
        "filegrab", "dl:", "download"
    };

    public AntiPluginStealer(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;

        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "REGISTER", this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "MC|REGISTER", this);

        for (String ch : STEALER_CHANNELS) {
            try {
                Bukkit.getMessenger().registerIncomingPluginChannel(plugin, ch, this);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void disable() {
        if (!enabled) return;
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin);
        flagged.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiPluginStealer";
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().isPluginStealerCommandScan()) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-plugin-stealer")) return;
        String msg = event.getMessage().toLowerCase();
        for (String kw : HEURISTIC_KEYWORDS) {
            if (msg.contains(kw)) {
                event.setCancelled(true);
                plugin.getStaffAlert().alert(event.getPlayer().getName(), "Stealer command: " + msg, getName());
                handleDetection(event.getPlayer());
                return;
            }
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-plugin-stealer")) return;
        if (channel.equals("REGISTER") || channel.equals("MC|REGISTER")) {
            String data = new String(message, StandardCharsets.UTF_8);
            for (String ch : data.split("\u0000")) {
                if (isStealerChannel(ch)) {
                    plugin.getLogger().warning("[AntiDumper] PluginStealer detected: " + player.getName() + " ch: " + ch);
                    plugin.getStaffAlert().alert(player.getName(), "Stealer channel: " + ch, getName());
                    handleDetection(player);
                    return;
                }
            }
        } else if (isStealerChannel(channel)) {
            plugin.getLogger().warning("[AntiDumper] PluginStealer detected: " + player.getName() + " ch: " + channel);
            plugin.getStaffAlert().alert(player.getName(), "Stealer channel: " + channel, getName());
            handleDetection(player);
        }
    }

    private boolean isStealerChannel(String channel) {
        if (channel == null || channel.isEmpty()) return false;
        String lower = channel.toLowerCase();

        // Exact match against known channels
        for (String s : STEALER_CHANNELS) {
            if (lower.equals(s)) return true;
        }

        // Heuristic: contains stealer/grabber keywords
        for (String kw : HEURISTIC_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }

        // Heuristic: suspicious prefix patterns used by grabber variants
        if (lower.startsWith("fg:") || lower.startsWith("dl:") ||
            lower.startsWith("grab:")) {
            return true;
        }

        return false;
    }

    private void handleDetection(Player player) {
        if (!flagged.add(player.getUniqueId())) return;
        plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), "Stealer channel");
        String action = plugin.getConfigManager().getPluginStealerAction();

        switch (action) {
            case "KICK":
                plugin.getScheduler().runKickTask(player, () ->
                    player.kickPlayer(plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().get("protection.plugin-stealer.kick"))
                );
                break;
            case "WARN":
                player.sendMessage(plugin.getConfigManager().getPrefixed("protection.plugin-stealer.warn"));
                break;
            case "LOG":
            default:
                break;
        }
    }
}
