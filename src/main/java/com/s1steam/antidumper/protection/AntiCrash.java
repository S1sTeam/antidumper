package com.s1steam.antidumper.protection;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

public class AntiCrash implements ProtectionModule, Listener, PluginMessageListener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;

    private static final String[] CRASH_CMDS = {
        "//calc", "//eval", "//solve", "/crash",
        "/crashserver", "/crashme"
    };

    public AntiCrash(AntiDumperPlugin plugin) {
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
        return "AntiCrash";
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().isCrashBlockCommands()) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-crash")) return;
        String msg = event.getMessage().toLowerCase();

        for (String cmd : CRASH_CMDS) {
            if (msg.startsWith(cmd)) {
                event.setCancelled(true);
                LoggerUtil.violation(plugin, "AntiCrash", event.getPlayer().getName(), "Crash cmd: " + msg);
                kick(event.getPlayer());
                return;
            }
        }

        if (msg.length() > 256) {
            event.setCancelled(true);
            LoggerUtil.violation(plugin, "AntiCrash", event.getPlayer().getName(), "Oversized cmd: " + msg.length());
            kick(event.getPlayer());
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!plugin.getConfigManager().isCrashBlockPayloads()) return;
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-crash")) return;

        int max = plugin.getConfigManager().getCrashMaxPluginMessageSize();
        if (message != null && message.length > max) {
            LoggerUtil.violation(plugin, "AntiCrash", player.getName(),
                "Big packet: " + channel + " (" + message.length + "b)");
            kick(player);
        }
    }

    private void kick(Player player) {
        plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), "Kicked for crash");
        String msg = plugin.getConfigManager().get("protection.crash.kick");
        plugin.getScheduler().runKickTask(player, () ->
            player.kickPlayer(plugin.getConfigManager().getPrefix() + " " + msg)
        );
    }
}
