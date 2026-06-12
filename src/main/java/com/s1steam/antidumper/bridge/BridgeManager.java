package com.s1steam.antidumper.bridge;

import com.google.gson.Gson;
import com.s1steam.antidumper.AntiDumperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BridgeManager implements PluginMessageListener {

    private static final String CHANNEL = "antidumper:main";
    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private final AntiDumperPlugin plugin;
    private final Gson gson = new Gson();
    private boolean proxyAvailable = false;

    public BridgeManager(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
        checkProxy();
    }

    public void disable() {
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEE_CHANNEL);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, BUNGEE_CHANNEL, this);
    }

    private void checkProxy() {
        plugin.getScheduler().syncLater(() -> {
            try {
                Player p = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                if (p != null) {
                    sendBungeeMessage(p, "GetServer", new byte[0]);
                }
            } catch (Exception ignored) {}
        }, 40L);
    }

    public void setProxyAvailable(boolean available) {
        this.proxyAvailable = available;
    }

    public boolean isProxyAvailable() {
        return proxyAvailable;
    }

    public void sendViolationSync(String playerName, UUID playerId, String module) {
        if (!proxyAvailable) return;
        Map<String, Object> data = new HashMap<>();
        data.put("type", "violation");
        data.put("player", playerName);
        data.put("uuid", playerId.toString());
        data.put("module", module);
        data.put("server", Bukkit.getServer().getName());
        broadcastToProxy(data);
    }

    public void sendPunishSync(String playerName, UUID playerId, String action) {
        if (!proxyAvailable) return;
        Map<String, Object> data = new HashMap<>();
        data.put("type", "punish");
        data.put("player", playerName);
        data.put("uuid", playerId.toString());
        data.put("action", action);
        data.put("server", Bukkit.getServer().getName());
        broadcastToProxy(data);
    }

    public void sendModuleToggleSync(String module, boolean enabled) {
        if (!proxyAvailable) return;
        Map<String, Object> data = new HashMap<>();
        data.put("type", "module_toggle");
        data.put("module", module);
        data.put("enabled", enabled);
        broadcastToProxy(data);
    }

    public void sendSiegeModeSync(boolean active) {
        if (!proxyAvailable) return;
        Map<String, Object> data = new HashMap<>();
        data.put("type", "siege_mode");
        data.put("active", active);
        broadcastToProxy(data);
    }

    private void broadcastToProxy(Map<String, Object> data) {
        String json = gson.toJson(data);
        Player p = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (p != null) {
            p.sendPluginMessage(plugin, CHANNEL, json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void sendBungeeMessage(Player player, String subchannel, byte[] data) {
        try {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(b);
            out.writeUTF(subchannel);
            out.writeShort(data.length);
            out.write(data);
            player.sendPluginMessage(plugin, BUNGEE_CHANNEL, b.toByteArray());
        } catch (Exception ignored) {}
    }

    public void onViolationSync(String playerName, UUID playerId, String module) {
        plugin.getLogger().info("[Bridge] Redis sync violation: " + playerName + " -> " + module);
    }

    public void onPunishSync(String playerName, UUID playerId, String action) {
        plugin.getLogger().info("[Bridge] Redis sync punish: " + playerName + " -> " + action);
    }

    public void onSiegeModeSync(boolean active) {
        plugin.getLogger().info("[Bridge] Redis sync siege: " + (active ? "ON" : "OFF"));
    }

    public void onModuleToggleSync(String module, boolean enabled) {
        plugin.getLogger().info("[Bridge] Redis sync toggle: " + module + " = " + enabled);
    }

    public void onRemoteCommand(String command) {
        plugin.getLogger().info("[Bridge] Redis command: " + command);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals(BUNGEE_CHANNEL)) {
            handleBungeeForward(player, message);
            return;
        }
        if (!CHANNEL.equals(channel)) return;

        try {
            String json = new String(message, StandardCharsets.UTF_8);

            if (json.startsWith("antidumper_bridge ")) {
                String cmdLine = json.substring("antidumper_bridge ".length());
                plugin.getLogger().info("[Bridge] Executing forwarded command: " + cmdLine);
                Bukkit.dispatchCommand(player, cmdLine);
                return;
            }

            Map<String, Object> data = gson.fromJson(json, Map.class);
            if (data == null) return;

            String type = (String) data.get("type");
            if (type == null) return;

            switch (type) {
                case "violation":
                    break;
                case "command":
                    String cmd = (String) data.get("command");
                    if (cmd != null) {
                        plugin.getLogger().info("[Bridge] Forwarded command: " + cmd + " from " + player.getName());
                        Bukkit.dispatchCommand(player, cmd);
                    }
                    break;
                case "punish":
                    String pName = (String) data.get("player");
                    String action = (String) data.get("action");
                    plugin.getLogger().info("[Bridge] Punish sync: " + pName + " -> " + action);
                    break;
                case "siege_mode":
                    boolean active = Boolean.TRUE.equals(data.get("active"));
                    plugin.getLogger().info("[Bridge] Siege mode sync: " + (active ? "ON" : "OFF"));
                    break;
                case "proxy_hello":
                    proxyAvailable = true;
                    plugin.getLogger().info("[Bridge] Proxy bridge connected!");
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Bridge] Failed to parse message: " + e.getMessage());
        }
    }

    private void handleBungeeForward(Player player, byte[] message) {
        try {
            DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(message));
            String subchannel = in.readUTF();
            if ("ForwardToOnServer".equals(subchannel)) {
                short len = in.readShort();
                byte[] data = new byte[len];
                in.readFully(data);
                String msg = new String(data, StandardCharsets.UTF_8);
                if (msg.startsWith("antidumper_bridge ")) {
                    String cmdLine = msg.substring("antidumper_bridge ".length());
                    plugin.getLogger().info("[Bridge] Forwarded command from proxy: " + cmdLine);
                    Bukkit.dispatchCommand(player, cmdLine);
                }
            }
        } catch (Exception ignored) {}
    }
}
