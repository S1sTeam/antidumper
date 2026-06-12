package com.s1steam.antidumper.bridge.proxy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Plugin(id = "antidumper-bridge", name = "AntiDumperBridge", version = "2.0",
        description = "AntiDumper proxy bridge for Velocity", authors = {"S1sTeam"})
public class VelocityBridgePlugin {

    private static final String CHANNEL = "antidumper:main";

    private final ProxyServer server;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
    private MinecraftChannelIdentifier channel;

    @Inject
    public VelocityBridgePlugin(ProxyServer server, Logger logger, @DataDirectory java.nio.file.Path dataDir) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(com.velocitypowered.api.event.proxy.ProxyInitializeEvent event) {
        channel = MinecraftChannelIdentifier.create("antidumper", "main");
        server.getChannelRegistrar().register(channel);
        server.getCommandManager().register("ad", new BridgeCommand(), "antidumper");
        logger.info("AntiDumper Bridge enabled!");
    }

    @Subscribe
    public void onProxyShutdown(com.velocitypowered.api.event.proxy.ProxyShutdownEvent event) {
        server.getChannelRegistrar().unregister(channel);
        logger.info("AntiDumper Bridge disabled.");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equals(CHANNEL)) return;

        if (!(event.getSource() instanceof ServerConnection)) return;
        ServerConnection sourceServer = (ServerConnection) event.getSource();

        try {
            String json = new String(event.getData(), StandardCharsets.UTF_8);
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
                    broadcastToServers(json, sourceServer);
                    break;
            }
        } catch (Exception e) {
            logger.warn("Failed to process bridge message: {}", e.getMessage());
        }
    }

    private void forwardCommand(String playerName, String commandLine) {
        String msg = "antidumper_bridge " + commandLine;

        server.getPlayer(playerName).ifPresent(player -> {
            Optional<ServerConnection> currentServer = player.getCurrentServer();
            currentServer.ifPresent(conn -> {
                conn.sendPluginMessage(channel, msg.getBytes(StandardCharsets.UTF_8));
            });
        });
    }

    private void broadcastToServers(String json, ServerConnection exclude) {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        for (Player player : server.getAllPlayers()) {
            player.getCurrentServer().ifPresent(conn -> {
                if (!conn.getServerInfo().getName().equals(exclude.getServerInfo().getName())) {
                    conn.sendPluginMessage(channel, data);
                }
            });
        }
    }

    private class BridgeCommand implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            String[] args = invocation.arguments();

            if (!(source instanceof Player)) {
                source.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cOnly players can use this command on proxy."));
                return;
            }

            Player player = (Player) source;

            if (args.length == 0) {
                player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§8[§cAntiDumper§8] §7Bridge proxy active."));
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
            data.put("player", player.getUsername());
            String json = gson.toJson(data);

            player.getCurrentServer().ifPresent(conn -> {
                conn.sendPluginMessage(channel, json.getBytes(StandardCharsets.UTF_8));
            });

            player.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§8[§cAntiDumper§8] §7Command forwarded to server."));
        }

        @Override
        public boolean hasPermission(Invocation invocation) {
            return invocation.source().hasPermission("antidumper.admin");
        }
    }
}
