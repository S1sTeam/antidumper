package com.s1steam.antidumper.protection;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

public class AntiWorldDownloader implements ProtectionModule, Listener, PluginMessageListener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;

    private static final String[] HEURISTIC_KEYWORDS = {
        "wdl", "worlddownloader", "litematica", "schematica", "malilib",
        "minihud", "tweakeroo", "xaero", "journeymap", "voxelmap",
        "baritone", "worldedit", "cui", "cubeside"
    };

    private static final Pattern RANDOM_HEX = Pattern.compile("^[0-9a-f]{4,}:[a-z0-9]+$");
    private static final Pattern RANDOM_CHANNEL = Pattern.compile("^[0-9a-f]{8,}:[0-9a-f]{4,}$");

    private static final String[] SUSPICIOUS_PATTERNS = {
        "downloader", "stealer", "grabber", "dump", "file:",
        "schematic", "minimap", "worldmap", "waypoint"
    };

    public AntiWorldDownloader(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;

        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "REGISTER", this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "MC|REGISTER", this);

        for (String ch : plugin.getConfigManager().getWDLBlockedChannels()) {
            try {
                Bukkit.getMessenger().registerIncomingPluginChannel(plugin, ch, this);
            } catch (Exception ignored) {}
        }

        registerChannelEvent();
    }

    @SuppressWarnings("unchecked")
    private void registerChannelEvent() {
        try {
            Class<?> raw = Class.forName("org.bukkit.event.player.PlayerRegisterChannelEvent");
            Class<? extends org.bukkit.event.Event> eventClass = (Class<? extends org.bukkit.event.Event>) raw;
            Bukkit.getPluginManager().registerEvent(eventClass, this,
                org.bukkit.event.EventPriority.LOWEST,
                (listener, event) -> {
                    if (eventClass.isInstance(event)) {
                        try {
                            Player p = (Player) eventClass.getMethod("getPlayer").invoke(event);
                            String ch = (String) eventClass.getMethod("getChannel").invoke(event);
                            if (p != null && isBlockedChannel(ch)) {
                                LoggerUtil.violation(plugin, "AntiWorldDownloader", p.getName(), "Mod: " + ch);
                                plugin.getStaffAlert().alert(p.getName(), "World download mod detected: " + ch, getName());
                                handleDetection(p);
                            }
                        } catch (Exception ignored) {}
                    }
                }, plugin);
        } catch (Exception ignored) {}
    }

    @Override
    public void disable() {
        if (!enabled) return;
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin);
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiWorldDownloader";
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!enabled) return;
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-world-downloader")) return;

        if (channel.equals("REGISTER") || channel.equals("MC|REGISTER")) {
            String data = new String(message, StandardCharsets.UTF_8);
            for (String ch : data.split("\u0000")) {
                if (isBlockedChannel(ch)) {
                    LoggerUtil.violation(plugin, "AntiWorldDownloader", player.getName(), "Mod: " + ch);
                    plugin.getStaffAlert().alert(player.getName(), "World download mod: " + ch, getName());
                    handleDetection(player);
                    return;
                }
            }
        } else if (isBlockedChannel(channel)) {
            LoggerUtil.violation(plugin, "AntiWorldDownloader", player.getName(), "Mod: " + channel);
            plugin.getStaffAlert().alert(player.getName(), "World download mod: " + channel, getName());
            handleDetection(player);
        }
    }

    private boolean isBlockedChannel(String channel) {
        if (channel == null || channel.isEmpty()) return false;
        String lower = channel.toLowerCase();

        // Exact match against config list
        List<String> blocked = plugin.getConfigManager().getWDLBlockedChannels();
        for (String b : blocked) {
            String bl = b.toLowerCase();
            if (lower.equals(bl) || lower.equals(bl.replace("|", ":"))) {
                return true;
            }
        }

        // Heuristic: keyword match
        for (String kw : HEURISTIC_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }

        // Heuristic: suspicious substrings
        for (String pat : SUSPICIOUS_PATTERNS) {
            if (lower.contains(pat)) {
                return true;
            }
        }

        // Heuristic: random hex channel (obfuscated WDL variants)
        if (RANDOM_HEX.matcher(lower).matches() || RANDOM_CHANNEL.matcher(lower).matches()) {
            return true;
        }

        // Heuristic: channel with "::" (unusual separator used by some grabbers)
        if (lower.contains("::")) {
            return true;
        }

        return false;
    }

    private void handleDetection(Player player) {
        plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), "Mod detected");
        String action = plugin.getConfigManager().getWDLModuleAction();

        switch (action) {
            case "KICK":
                plugin.getScheduler().runKickTask(player, () ->
                    player.kickPlayer(plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().get("protection.wdl.kick"))
                );
                break;
            case "WARN":
                player.sendMessage(plugin.getConfigManager().getPrefixed("protection.wdl.warn"));
                break;
            case "LOG":
            default:
                break;
        }
    }
}
