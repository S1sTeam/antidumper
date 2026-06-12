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
import java.util.List;

public class AntiFileSteal implements ProtectionModule, Listener, PluginMessageListener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;

    private static final String[] EXPLOIT_KEYWORDS = {
        "viaversion", "protocolib", "protocol-lib"
    };

    public AntiFileSteal(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;

        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "REGISTER", this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "MC|REGISTER", this);
    }

    @Override
    public void disable() {
        if (!enabled) return;
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin);
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiFileSteal";
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-file-steal")) return;
        String msg = event.getMessage().toLowerCase();

        // Block any viaversion: or protocolib: commands (not just known ones)
        for (String kw : EXPLOIT_KEYWORDS) {
            if (msg.contains(kw)) {
                event.setCancelled(true);
                executeAction(player, "DB_STEAL");
                plugin.getLogger().severe("[AntiDumper] DB steal attempt: " + player.getName() + " cmd: " + msg);
                plugin.getStaffAlert().alert(player.getName(), "DB exploit command: " + msg, getName());
                return;
            }
        }

        if (plugin.getConfigManager().isPathTraversalEnabled() &&
            (msg.contains("../") || msg.contains("..\\") || msg.contains("~/"))) {
            event.setCancelled(true);
            executeAction(player, "FILE_STEAL");
            plugin.getLogger().severe("[AntiDumper] Path traversal: " + player.getName());
            plugin.getStaffAlert().alert(player.getName(), "Path traversal: " + msg, getName());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-file-steal")) return;
        if (channel.equals("REGISTER") || channel.equals("MC|REGISTER")) {
            String data = new String(message, StandardCharsets.UTF_8);
            for (String ch : data.split("\u0000")) {
                if (isExploitChannel(ch)) {
                    handleExploitChannel(player, ch);
                    return;
                }
            }
        } else if (isExploitChannel(channel)) {
            handleExploitChannel(player, channel);
        }
    }

    private boolean isExploitChannel(String channel) {
        if (channel == null || channel.isEmpty()) return false;
        String lower = channel.toLowerCase();

        // Block ANY viaversion:* or protocolib:* channels
        for (String kw : EXPLOIT_KEYWORDS) {
            if (lower.startsWith(kw + ":") || lower.equals(kw)) {
                return true;
            }
        }

        // Block any *:dump or *:debug channels
        if (lower.endsWith(":dump") || lower.endsWith(":debug")) {
            return true;
        }

        // Block channels containing file paths
        if (lower.contains("../") || lower.contains("..\\") ||
            lower.contains("file:") || lower.contains("/etc/") ||
            lower.contains("c:\\") || lower.contains("c:/")) {
            return true;
        }

        // Block known steal channels
        return lower.equals("worlddownloader:file") || lower.equals("wdl:file");
    }

    private void handleExploitChannel(Player player, String channel) {
        plugin.getLogger().severe("[AntiDumper] File steal attempt: " + player.getName() + " via " + channel);
        plugin.getStaffAlert().alert(player.getName(), "File steal via channel: " + channel, getName());
        String type = (channel.contains("dump") || channel.contains("debug")) ? "DB_STEAL" : "FILE_STEAL";
        executeAction(player, type);
    }

    private void executeAction(Player player, String type) {
        plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), type);
        String action = plugin.getConfigManager().getFileStealAction();

        String kickKey, warnKey;
        if ("DB_STEAL".equals(type)) {
            kickKey = "protection.db-steal.kick";
            warnKey = "protection.db-steal.warn";
        } else {
            kickKey = "protection.file-steal.kick";
            warnKey = "protection.file-steal.warn";
        }

        switch (action) {
            case "KICK":
                plugin.getScheduler().runKickTask(player, () ->
                    player.kickPlayer(plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().get(kickKey))
                );
                break;
            case "WARN":
                player.sendMessage(plugin.getConfigManager().getPrefixed(warnKey));
                break;
            case "LOG":
            default:
                break;
        }
    }
}
