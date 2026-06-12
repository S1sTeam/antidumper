package com.s1steam.antidumper.protection;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.utils.LoggerUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AntiHackedClient implements ProtectionModule, Listener, PluginMessageListener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private boolean packetInjectionWorking = false;

    private final Map<UUID, ClientData> clientData = new ConcurrentHashMap<>();
    private final Set<UUID> injectedPlayers = ConcurrentHashMap.newKeySet();

    private static final Set<String> SUSPICIOUS_BRANDS = new HashSet<>(Arrays.asList(
        "liquidbounce", "liquidbounce+", "lpx", "lbow", "bounce",
        "aristois", "wurst", "impact", "future", "rusherhack",
        "meteor", "inertia", "kami", "salhack", "phobos",
        "sigma", "huzuni", "nodus", "autism",
        "fdp", "fdpclient", "novoline", "tenacity", "raven",
        "zero.day", "zeroday", "nightx", "resolver"
    ));

    private static final Set<String> FORGE_CHANNELS = new HashSet<>(Arrays.asList(
        "FML|HS", "FML|MP", "FML|SM", "FORGE", "fml:handshake"
    ));

    private static final Pattern HEX_CHANNEL = Pattern.compile("^[0-9a-f]{4,}$");
    private static final Pattern HEX_PREFIX = Pattern.compile("^[0-9a-f]{4,}:");
    private static final Pattern LPX_PREFIX = Pattern.compile("^(lpx|lb|lc|lm|lma|ld|lg|le|lr)[:|]");
    private static final Pattern SUSPICIOUS_RANDOM = Pattern.compile("^[a-z0-9]{6,16}$");
    private static final Pattern HEX_OBFUSCATED = Pattern.compile("(?:[0-9a-f]{2,}[:.][0-9a-f]{2,})");
    private static final Pattern LPX_KEYWORD = Pattern.compile(
        "(lpx|liquidbounce|bounce|steal|grab|dump|download)", Pattern.CASE_INSENSITIVE);

    private static final int MAX_REGISTER_FREQ = 5;
    private static final long FREQ_WINDOW_MS = 30000L;
    private static final int SUSPICIOUS_THRESHOLD = 3;

    public AntiHackedClient(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;

        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "REGISTER", this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "MC|REGISTER", this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "minecraft:brand", this);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, "MC|Brand", this);

        for (String ch : FORGE_CHANNELS) {
            try {
                Bukkit.getMessenger().registerIncomingPluginChannel(plugin, ch, this);
            } catch (Exception ignored) {}
        }

        tryInjectPacketHandler();
        injectExistingPlayers();
    }

    @Override
    public void disable() {
        if (!enabled) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            uninjectPlayer(p);
        }
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin);
        clientData.clear();
        injectedPlayers.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiHackedClient";
    }

    private void tryInjectPacketHandler() {
        try {
            Class.forName("io.netty.channel.ChannelDuplexHandler");
            packetInjectionWorking = true;
        } catch (ClassNotFoundException e) {
            packetInjectionWorking = false;
        }
    }

    private void injectExistingPlayers() {
        if (!packetInjectionWorking) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            injectPlayer(p);
        }
    }

    private void injectPlayer(Player player) {
        if (!packetInjectionWorking) return;
        UUID uid = player.getUniqueId();
        if (injectedPlayers.contains(uid)) return;

        try {
            Object craftPlayer = Class.forName(
                "org.bukkit.craftbukkit." + getNMSVersion() + ".entity.CraftPlayer").cast(player);
            Object entityPlayer = craftPlayer.getClass().getMethod("getHandle").invoke(craftPlayer);
            Object playerConnection = getFieldValue(entityPlayer, "playerConnection", "b", "connection");
            Object networkManager = getFieldValue(playerConnection, "networkManager", "a", "networkManager");
            Channel channel = (Channel) getFieldValue(networkManager, "channel", "k", "m");

            if (channel == null) return;
            if (channel.pipeline().get("packet_handler") == null) return;

            channel.pipeline().addBefore("packet_handler", "anti_dumper_hc_" + uid, new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
                    if (enabled && isCustomPayload(packet)) {
                        try {
                            handleRawPacket(player, packet);
                        } catch (Exception ignored) {}
                    }
                    ctx.fireChannelRead(packet);
                }
            });

            injectedPlayers.add(uid);
        } catch (Exception e) {
            plugin.getLogger().fine("[AntiHackedClient] Packet injection failed for " + player.getName());
        }
    }

    private void uninjectPlayer(Player player) {
        if (!packetInjectionWorking) return;
        UUID uid = player.getUniqueId();
        if (!injectedPlayers.contains(uid)) return;

        try {
            Object craftPlayer = Class.forName(
                "org.bukkit.craftbukkit." + getNMSVersion() + ".entity.CraftPlayer").cast(player);
            Object entityPlayer = craftPlayer.getClass().getMethod("getHandle").invoke(craftPlayer);
            Object playerConnection = getFieldValue(entityPlayer, "playerConnection", "b", "connection");
            Object networkManager = getFieldValue(playerConnection, "networkManager", "a", "networkManager");
            Channel channel = (Channel) getFieldValue(networkManager, "channel", "k", "m");

            if (channel != null && channel.pipeline().get("anti_dumper_hc_" + uid) != null) {
                channel.pipeline().remove("anti_dumper_hc_" + uid);
            }
        } catch (Exception ignored) {}

        injectedPlayers.remove(uid);
    }

    private Object getFieldValue(Object obj, String... names) throws Exception {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            for (String name : names) {
                try {
                    Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    return f.get(obj);
                } catch (NoSuchFieldException ignored) {}
            }
            clazz = clazz.getSuperclass();
        }
        throw new NoSuchFieldException("No field " + Arrays.toString(names) + " in " + obj.getClass());
    }

    private boolean isCustomPayload(Object packet) {
        String name = packet.getClass().getName();
        return name.contains("PacketPlayInCustomPayload") ||
               name.contains("ServerboundCustomPayloadPacket");
    }

    private void handleRawPacket(Player player, Object packet) {
        try {
            String channel = extractChannel(packet);
            byte[] data = extractData(packet);

            if (channel == null || channel.isEmpty()) return;

            ClientData d = getOrCreateData(player);

            if (!isSafeChannel(channel) && !FORGE_CHANNELS.contains(channel) &&
                !channel.equals("REGISTER") && !channel.equals("MC|REGISTER") &&
                !channel.equals("minecraft:brand") && !channel.equals("MC|Brand")) {
                d.rawChannels.add(channel);
                analyzeChannel(d, channel);
            }

            if (data != null && data.length > 0) {
                analyzePayload(d, channel, data);
            }

            checkScore(player, d);
        } catch (Exception ignored) {}
    }

    private String extractChannel(Object packet) throws Exception {
        for (Method m : packet.getClass().getMethods()) {
            if (m.getParameterCount() > 0) continue;
            String mn = m.getName().toLowerCase();
            if (mn.equals("getchannelname") || mn.equals("channelname") ||
                mn.equals("b") && m.getReturnType() == String.class) {
                Object val = m.invoke(packet);
                if (val instanceof String) return (String) val;
            }
            if (mn.contains("channel") || mn.contains("name")) {
                Object val = m.invoke(packet);
                if (val instanceof String) return (String) val;
            }
        }
        return null;
    }

    private byte[] extractData(Object packet) throws Exception {
        for (Method m : packet.getClass().getMethods()) {
            if (m.getParameterCount() > 0) continue;
            if (m.getReturnType() == byte[].class) {
                return (byte[]) m.invoke(packet);
            }
        }
        try {
            for (Field f : packet.getClass().getDeclaredFields()) {
                if (f.getType() == byte[].class) {
                    f.setAccessible(true);
                    return (byte[]) f.get(packet);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (p.hasPermission("antidumper.bypass") || p.hasPermission("antidumper.bypass.anti-hacked-client")) return;
        ClientData d = new ClientData();
        d.joinTime = System.currentTimeMillis();
        clientData.put(p.getUniqueId(), d);
        injectPlayer(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        if (p.hasPermission("antidumper.bypass") || p.hasPermission("antidumper.bypass.anti-hacked-client")) return;
        clientData.remove(p.getUniqueId());
        uninjectPlayer(p);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!enabled) return;
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-hacked-client")) return;
        ClientData d = getOrCreateData(player);

        switch (channel) {
            case "minecraft:brand":
            case "MC|Brand":
                handleBrand(player, message, d);
                return;
            case "FML|HS":
            case "FML|MP":
            case "FML|SM":
            case "FORGE":
            case "fml:handshake":
                handleForge(player, channel, d);
                return;
            case "REGISTER":
            case "MC|REGISTER":
                handleRegister(player, message, d);
                return;
        }
    }

    private ClientData getOrCreateData(Player player) {
        return clientData.computeIfAbsent(player.getUniqueId(), k -> {
            ClientData d = new ClientData();
            d.joinTime = System.currentTimeMillis();
            return d;
        });
    }

    private void handleRegister(Player player, byte[] message, ClientData d) {
        long now = System.currentTimeMillis();
        d.registerEvents++;
        if (d.registerFirstEvent == 0) d.registerFirstEvent = now;

        if (d.registerEvents >= MAX_REGISTER_FREQ &&
            (now - d.registerFirstEvent) < FREQ_WINDOW_MS) {
            String reason = "Rapid REGISTER: " + d.registerEvents + " in " +
                ((now - d.registerFirstEvent) / 1000) + "s";
            LoggerUtil.violation(plugin, getName(), player.getName(), reason);
            plugin.getStaffAlert().alert(player.getName(), reason, getName());
            addSuspicion(d, 2, "rapid_register");
            checkScore(player, d);
            return;
        }

        String data = new String(message, StandardCharsets.UTF_8);
        for (String ch : data.split("\u0000")) {
            ch = ch.trim();
            if (ch.isEmpty()) continue;
            d.knownChannels.add(ch);
            if (!isSafeChannel(ch)) {
                d.rawChannels.add(ch);
                analyzeChannel(d, ch);
            }
        }
        checkScore(player, d);
    }

    private void handleBrand(Player player, byte[] message, ClientData d) {
        String raw = new String(message, StandardCharsets.UTF_8).toLowerCase().trim();
        String brand = cleanBrand(raw);
        d.clientBrand = brand;

        for (String sus : SUSPICIOUS_BRANDS) {
            if (brand.contains(sus)) {
                LoggerUtil.violation(plugin, getName(), player.getName(), "Suspicious brand: " + brand);
                plugin.getStaffAlert().alert(player.getName(),
                    "Hacked client brand: " + brand, getName());
                d.isSuspiciousBrand = true;
                addSuspicion(d, 3, "brand:" + sus);
                checkScore(player, d);
                return;
            }
        }

        if (brand.contains("forge") || brand.contains("fml")) {
            d.isForge = true;
        }

        if (!brand.contains("vanilla") && !brand.equals("unknown") && !brand.isEmpty()) {
            addSuspicion(d, 1, "unknown_brand:" + brand);
        }

        checkScore(player, d);
    }

    private void handleForge(Player player, String channel, ClientData d) {
        d.isForge = true;
        d.forgeChannels.add(channel);
        addSuspicion(d, 2, "forge:" + channel);

        LoggerUtil.violation(plugin, getName(), player.getName(), "Forge channel: " + channel);
        plugin.getStaffAlert().alert(player.getName(),
            "Forge/FML detected (" + channel + ")", getName());
        checkScore(player, d);
    }

    private void analyzeChannel(ClientData d, String channel) {
        String lower = channel.toLowerCase();

        if (LPX_PREFIX.matcher(lower).find()) {
            d.detectedLPXPrefix = true;
            addSuspicion(d, 3, "lpx_prefix:" + lower);
            return;
        }

        if (LPX_KEYWORD.matcher(lower).find()) {
            addSuspicion(d, 3, "keyword:" + lower);
            return;
        }

        if (HEX_CHANNEL.matcher(lower).matches() || HEX_PREFIX.matcher(lower).find()) {
            d.detectedHexChannels = true;
            addSuspicion(d, 2, "hex:" + lower);
            return;
        }

        if (HEX_OBFUSCATED.matcher(lower).find()) {
            addSuspicion(d, 2, "hex_obf:" + lower);
            return;
        }

        if (SUSPICIOUS_RANDOM.matcher(lower).matches()) {
            double ent = shannonEntropy(lower);
            if (ent > 3.5 && !isKnownChannel(lower)) {
                d.randomChannelCount++;
                addSuspicion(d, 1, "random:" + lower + "(e" + String.format("%.1f", ent) + ")");
            }
            return;
        }

        if (lower.contains("::")) {
            addSuspicion(d, 1, "dbl_colon:" + lower);
        }
    }

    private void analyzePayload(ClientData d, String channel, byte[] data) {
        String content = new String(data, StandardCharsets.UTF_8).toLowerCase();
        if (content.contains("lpx") || content.contains("liquidbounce") ||
            content.contains("bounce") || content.contains("stealer") ||
            content.contains("grabber") || content.contains("dumper")) {
            addSuspicion(d, 2, "payload_kw:" + channel);
        }
    }

    private boolean isSafeChannel(String channel) {
        if (channel == null) return true;
        String lower = channel.toLowerCase();
        if (channel.length() <= 3) return false;
        if (lower.startsWith("minecraft:") || lower.startsWith("mc|") ||
            lower.startsWith("bungeecord:")) return true;
        return false;
    }

    private boolean isKnownChannel(String channel) {
        if (channel == null || channel.isEmpty()) return false;
        String lower = channel.toLowerCase();
        return lower.equals("vanilla") ||
               lower.startsWith("minecraft:") || lower.startsWith("mc|") ||
               lower.startsWith("fml|") || lower.startsWith("fml:") ||
               lower.startsWith("forge") ||
               lower.startsWith("bungeecord:");
    }

    private double shannonEntropy(String s) {
        if (s == null || s.isEmpty()) return 0;
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : s.toCharArray()) freq.merge(c, 1, Integer::sum);
        double entropy = 0;
        int len = s.length();
        for (int count : freq.values()) {
            double p = (double) count / len;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        return entropy;
    }

    private void addSuspicion(ClientData d, int points, String reason) {
        d.suspicionScore += points;
        if (d.reasons.size() < 10) d.reasons.add(reason);

        if (d.isForge && d.detectedHexChannels) {
            d.suspicionScore += 1;
        }
        if (d.isSuspiciousBrand && d.isForge) {
            d.suspicionScore += 1;
        }
        if (d.clientBrand != null && d.clientBrand.contains("vanilla") &&
            (d.isForge || d.detectedHexChannels || d.detectedLPXPrefix)) {
            d.suspicionScore += 2;
        }
        if (d.randomChannelCount >= 3) {
            d.suspicionScore += 2;
        }
    }

    private void checkScore(Player player, ClientData d) {
        String trigger = null;
        if (d.detectedLPXPrefix) {
            trigger = "LPX prefix channel";
        } else if (d.suspicionScore >= SUSPICIOUS_THRESHOLD) {
            trigger = "Score " + d.suspicionScore + "/" + SUSPICIOUS_THRESHOLD;
        }
        if (trigger == null) return;

        String reasons = String.join(", ", d.reasons);
        String detail = trigger + " [" + reasons + "]";
        LoggerUtil.violation(plugin, getName(), player.getName(), detail);
        plugin.getStaffAlert().alert(player.getName(), detail, getName());
        plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
        handleDetection(player, trigger);
    }

    private void handleDetection(Player player, String reason) {
        String action = plugin.getConfigManager().getHCAction();

        switch (action) {
            case "KICK":
                String msg = plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().get("protection.hacked-client.kick");
                plugin.getScheduler().runKickTask(player, () -> player.kickPlayer(msg));
                break;
            case "WARN":
                player.sendMessage(plugin.getConfigManager().getPrefixed("protection.hacked-client.warn"));
                break;
            case "LOG":
            default:
                break;
        }
    }

    private String cleanBrand(String raw) {
        if (raw == null || raw.isEmpty()) return "unknown";
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c >= 0x20 && c < 0x7F) sb.append(c);
        }
        String result = sb.toString().toLowerCase().trim();
        return result.isEmpty() ? "unknown" : result;
    }

    private String getNMSVersion() {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            return pkg.substring(pkg.lastIndexOf('.') + 1);
        } catch (Exception e) {
            return "v1_21_R1";
        }
    }

    private static class ClientData {
        String clientBrand = "unknown";
        boolean isForge = false;
        boolean isSuspiciousBrand = false;
        boolean detectedHexChannels = false;
        boolean detectedLPXPrefix = false;
        final Set<String> forgeChannels = new HashSet<>();
        final Set<String> knownChannels = new HashSet<>();
        final Set<String> rawChannels = new HashSet<>();
        final List<String> reasons = new ArrayList<>();
        int registerEvents = 0;
        long registerFirstEvent = 0;
        long joinTime;
        int suspicionScore = 0;
        int randomChannelCount = 0;
    }
}
