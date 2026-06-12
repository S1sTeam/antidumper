package com.s1steam.antidumper.redis;

import com.google.gson.Gson;
import com.s1steam.antidumper.AntiDumperPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RedisManager {

    private static final String CHANNEL = "antidumper:sync";

    private final AntiDumperPlugin plugin;
    private final Gson gson = new Gson();
    private JedisPool pool;
    private JedisPubSub subscriber;
    private boolean enabled;

    public RedisManager(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enable() {
        if (!plugin.getConfigManager().getBoolean("redis.enabled", false)) {
            plugin.getLogger().info("Redis disabled.");
            return false;
        }

        String host = plugin.getConfigManager().getString("redis.host", "localhost");
        int port = plugin.getConfigManager().getInt("redis.port", 6379);
        String password = plugin.getConfigManager().getString("redis.password", "");
        int timeout = plugin.getConfigManager().getInt("redis.timeout", 5000);

        try {
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(4);
            config.setMaxIdle(2);
            config.setMinIdle(1);

            if (!password.isEmpty()) {
                pool = new JedisPool(config, host, port, timeout, password);
            } else {
                pool = new JedisPool(config, host, port, timeout);
            }

            testConnection();
            startSubscriber();
            enabled = true;
            plugin.getLogger().info("Redis connected: " + host + ":" + port);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Redis connection failed: " + e.getMessage());
            return false;
        }
    }

    public void disable() {
        enabled = false;
        if (subscriber != null) subscriber.unsubscribe();
        if (pool != null) pool.close();
    }

    private void testConnection() {
        try (Jedis jedis = pool.getResource()) {
            jedis.ping();
        }
    }

    private void startSubscriber() {
        plugin.getScheduler().async(() -> {
            try (Jedis jedis = pool.getResource()) {
                subscriber = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        handleMessage(message);
                    }
                };
                jedis.subscribe(subscriber, CHANNEL);
            } catch (Exception e) {
                if (enabled) {
                    plugin.getLogger().warning("Redis subscriber disconnected, reconnecting in 10s...");
                    plugin.getScheduler().asyncLater(this::startSubscriber, 200L);
                }
            }
        });
    }

    private void handleMessage(String json) {
        try {
            Map<String, Object> data = gson.fromJson(json, Map.class);
            if (data == null) return;
            String type = (String) data.get("type");
            if (type == null) return;

            switch (type) {
                case "violation":
                    plugin.getBridge().onViolationSync(
                            (String) data.get("player"),
                            UUID.fromString((String) data.get("uuid")),
                            (String) data.get("module")
                    );
                    break;
                case "punish":
                    plugin.getBridge().onPunishSync(
                            (String) data.get("player"),
                            UUID.fromString((String) data.get("uuid")),
                            (String) data.get("action")
                    );
                    break;
                case "siege_mode":
                    plugin.getBridge().onSiegeModeSync(
                            Boolean.TRUE.equals(data.get("active"))
                    );
                    break;
                case "module_toggle":
                    plugin.getBridge().onModuleToggleSync(
                            (String) data.get("module"),
                            Boolean.TRUE.equals(data.get("enabled"))
                    );
                    break;
                case "command":
                    String cmd = (String) data.get("command");
                    if (cmd != null) {
                        plugin.getBridge().onRemoteCommand(cmd);
                    }
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Redis message error: " + e.getMessage());
        }
    }

    public void publish(String type, Map<String, Object> data) {
        if (!enabled) return;
        Map<String, Object> message = new HashMap<>(data);
        message.put("type", type);
        message.put("server", plugin.getConfigManager().getServerName());
        String json = gson.toJson(message);

        plugin.getScheduler().async(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.publish(CHANNEL, json);
            } catch (Exception e) {
                plugin.getLogger().warning("Redis publish failed: " + e.getMessage());
            }
        });
    }

    public void publishViolation(String playerName, UUID playerId, String module) {
        Map<String, Object> data = new HashMap<>();
        data.put("player", playerName);
        data.put("uuid", playerId.toString());
        data.put("module", module);
        publish("violation", data);
    }

    public void publishPunish(String playerName, UUID playerId, String action) {
        Map<String, Object> data = new HashMap<>();
        data.put("player", playerName);
        data.put("uuid", playerId.toString());
        data.put("action", action);
        publish("punish", data);
    }

    public void publishSiegeMode(boolean active) {
        Map<String, Object> data = new HashMap<>();
        data.put("active", active);
        publish("siege_mode", data);
    }

    public void publishModuleToggle(String module, boolean enabled) {
        Map<String, Object> data = new HashMap<>();
        data.put("module", module);
        data.put("enabled", enabled);
        publish("module_toggle", data);
    }

    public boolean isEnabled() { return enabled; }
}
