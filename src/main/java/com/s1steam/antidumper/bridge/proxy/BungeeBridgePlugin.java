package com.s1steam.antidumper.bridge.proxy;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class BungeeBridgePlugin extends Plugin implements Listener {

    private static final String CHANNEL = "antidumper:main";
    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private final Gson gson = new Gson();
    private final Type mapType = new TypeToken<Map<String, Object>>() {}.getType();

    @Override
    public void onEnable() {
        ProxyServer.getInstance().getPluginManager().registerListener(this, this);
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new BridgeCommand());
        ProxyServer.getInstance().registerChannel(CHANNEL);
        getLogger().info("AntiDumper Bridge enabled!");
    }

    @Override
    public void onDisable() {
        ProxyServer.getInstance().unregisterChannel(CHANNEL);
        getLogger().info("AntiDumper Bridge disabled.");
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(CHANNEL)) return;

        if (!(event.getReceiver() instanceof ProxiedPlayer)) return;
        ProxiedPlayer sender = (ProxiedPlayer) event.getReceiver();

        try {
            String json = new String(event.getData(), "UTF-8");
            Map<String, Object> data = gson.fromJson(json, mapType);
            if (data == null) return;

            String type = (String) data.get("type");
            if (type == null) return;

            switch (type) {
                case "command":
                    String command = (String) data.get("command");
                    String playerName = (String) data.get("player");
                    if (command != null && playerName != null) {
                        forwardCommand(playerName, command);
                    }
                    break;

                case "violation":
                case "punish":
                case "siege_mode":
                case "module_toggle":
                    broadcastToServers(json, sender.getServer());
                    break;
            }
        } catch (Exception e) {
            getLogger().warning("Failed to process bridge message: " + e.getMessage());
        }
    }

    private void forwardCommand(String playerName, String commandLine) {
        String msg = "antidumper_bridge " + commandLine;
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("ForwardToOnServer");
        out.writeUTF(msg);

        ProxiedPlayer player = ProxyServer.getInstance().getPlayer(playerName);
        if (player != null && player.getServer() != null) {
            player.getServer().sendData(BUNGEE_CHANNEL, out.toByteArray());
        }
    }

    private void broadcastToServers(String json, Server exclude) {
        byte[] data = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
            Server s = p.getServer();
            if (s != null && !s.equals(exclude)) {
                s.sendData(CHANNEL, data);
            }
        }
    }

    private class BridgeCommand extends Command {
        public BridgeCommand() {
            super("antidumper", "antidumper.admin", "ad");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (!(sender instanceof ProxiedPlayer)) {
                sender.sendMessage(new TextComponent("§cOnly players can use this command on proxy."));
                return;
            }

            ProxiedPlayer player = (ProxiedPlayer) sender;
            if (args.length == 0) {
                player.sendMessage(new TextComponent("§8[§cAntiDumper§8] §7Bridge proxy active."));
                return;
            }

            StringBuilder cmdLine = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) cmdLine.append(" ");
                cmdLine.append(args[i]);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("type", "command");
            data.put("command", cmdLine.toString());
            data.put("player", player.getName());
            String json = gson.toJson(data);

            player.getServer().sendData(CHANNEL, json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            player.sendMessage(new TextComponent("§8[§cAntiDumper§8] §7Command forwarded to server."));
        }
    }
}
