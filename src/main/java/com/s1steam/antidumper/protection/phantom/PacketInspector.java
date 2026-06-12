package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class PacketInspector implements ProtectionModule, Listener {

    private final AntiDumperPlugin plugin;
    private boolean enabled;
    private boolean packetInjectionWorking;

    private final Map<UUID, PlayerPacketData> playerData = new ConcurrentHashMap<>();
    private final Set<UUID> injected = ConcurrentHashMap.newKeySet();

    private static final Set<String> BLOCKED_PACKETS = new HashSet<>(Arrays.asList(
        "PacketPlayInBlockPlace", "PacketPlayInFlying",
        "PacketPlayInArmAnimation", "PacketPlayInUseEntity",
        "PacketPlayInChat", "PacketPlayInTabComplete",
        "PacketPlayInCustomPayload", "PacketPlayInTransaction",
        "PacketPlayInKeepAlive", "PacketPlayInEntityAction",
        "PacketPlayInSteerVehicle", "PacketPlayInHeldItemSlot",
        "PacketPlayInCloseWindow", "PacketPlayInWindowClick",
        "PacketPlayInTeleportAccept", "PacketPlayInTileNBTQuery",
        "PacketPlayInBeacon", "PacketPlayInPickItem",
        "PacketPlayInRecipeDisplayed", "PacketPlayInRecipeSettings",
        "PacketPlayInItemName", "PacketPlayInEnchantItem",
        "PacketPlayInBoatMove", "PacketPlayInVehicleMove",
        "ServerboundInteractPacket", "ServerboundMovePlayerPacket",
        "ServerboundChatPacket", "ServerboundCommandPacket",
        "ServerboundContainerClickPacket", "ServerboundPlaceRecipePacket",
        "ServerboundPlayerActionPacket", "ServerboundUseItemPacket",
        "ServerboundCustomPayloadPacket", "ServerboundSwingPacket"
    ));

    private static final int WARN_PPS = 800;
    private static final int KICK_PPS = 1500;
    private static final int MOVEMENT_WARN = 300;
    private static final int MOVEMENT_KICK = 600;
    private static final int WINDOW_WARN = 30;
    private static final int WINDOW_KICK = 80;
    private static final int PAYLOAD_WARN_SIZE = 5000;
    private static final int PAYLOAD_KICK_SIZE = 25000;

    private static final long WINDOW_MS = 1000L;
    private static final long CLEANUP_INTERVAL = 60000L;

    public PacketInspector(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;

        try {
            Class.forName("io.netty.channel.ChannelDuplexHandler");
            packetInjectionWorking = true;
        } catch (ClassNotFoundException e) {
            packetInjectionWorking = false;
            plugin.getLogger().warning("PacketInspector: Netty not available, disabled");
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);

        for (Player p : Bukkit.getOnlinePlayers()) {
            injectPlayer(p);
        }

        plugin.getScheduler().asyncTimer(this::cleanup, CLEANUP_INTERVAL / 50, CLEANUP_INTERVAL / 50);
        plugin.getLogger().info("  \u2713 PacketInspector");
    }

    @Override
    public void disable() {
        if (!enabled) return;
        for (Player p : Bukkit.getOnlinePlayers()) uninjectPlayer(p);
        playerData.clear();
        injected.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "PacketInspector";
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        injectPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        playerData.remove(p.getUniqueId());
        uninjectPlayer(p);
    }

    private void injectPlayer(Player player) {
        if (!packetInjectionWorking) return;
        UUID uid = player.getUniqueId();
        if (injected.contains(uid)) return;

        try {
            Object craftPlayer = Class.forName(
                "org.bukkit.craftbukkit." + getNMSVersion() + ".entity.CraftPlayer").cast(player);
            Object entityPlayer = craftPlayer.getClass().getMethod("getHandle").invoke(craftPlayer);
            Object playerConnection = getField(entityPlayer, "playerConnection", "b", "connection");
            Object networkManager = getField(playerConnection, "networkManager", "a", "networkManager");
            Channel channel = (Channel) getField(networkManager, "channel", "k", "m");

            if (channel == null || channel.pipeline().get("packet_handler") == null) return;

            channel.pipeline().addBefore("packet_handler", "ad_packet_inspector_" + uid, new ChannelDuplexHandler() {
                @Override
                public void channelRead(ChannelHandlerContext ctx, Object packet) throws Exception {
                    if (enabled) {
                        try {
                            inspectPacket(player, packet);
                        } catch (Exception ignored) {}
                    }
                    ctx.fireChannelRead(packet);
                }
            });

            injected.add(uid);
        } catch (Exception e) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().fine("[PacketInspector] Inject failed for " + player.getName());
            }
        }
    }

    private void uninjectPlayer(Player player) {
        if (!packetInjectionWorking) return;
        UUID uid = player.getUniqueId();

        try {
            Object craftPlayer = Class.forName(
                "org.bukkit.craftbukkit." + getNMSVersion() + ".entity.CraftPlayer").cast(player);
            Object entityPlayer = craftPlayer.getClass().getMethod("getHandle").invoke(craftPlayer);
            Object playerConnection = getField(entityPlayer, "playerConnection", "b", "connection");
            Object networkManager = getField(playerConnection, "networkManager", "a", "networkManager");
            Channel channel = (Channel) getField(networkManager, "channel", "k", "m");

            String handlerName = "ad_packet_inspector_" + uid;
            if (channel != null && channel.pipeline().get(handlerName) != null) {
                channel.pipeline().remove(handlerName);
            }
        } catch (Exception ignored) {}

        injected.remove(uid);
    }

    private void inspectPacket(Player player, Object packet) {
        String className = packet.getClass().getSimpleName();
        UUID uid = player.getUniqueId();
        PlayerPacketData data = playerData.computeIfAbsent(uid, k -> new PlayerPacketData());

        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        data.packets.add(now);
        while (!data.packets.isEmpty() && data.packets.peekFirst() < windowStart) {
            data.packets.pollFirst();
        }

        int totalPPS = data.packets.size();

        if (className.contains("Flying") || className.contains("MovePlayer") ||
            className.contains("MoveVehicle") || className.contains("VehicleMove") ||
            className.contains("BoatMove") || className.contains("PlayerMove")) {
            data.movements.add(now);
            while (!data.movements.isEmpty() && data.movements.peekFirst() < windowStart) {
                data.movements.pollFirst();
            }
        }

        if (className.contains("WindowClick") || className.contains("ContainerClick") ||
            className.contains("CloseWindow") || className.contains("CreativeInventory") ||
            className.contains("EnchantItem") || className.contains("ItemName") ||
            className.contains("Beacon") || className.contains("PickItem")) {
            data.windowOps.add(now);
            while (!data.windowOps.isEmpty() && data.windowOps.peekFirst() < windowStart) {
                data.windowOps.pollFirst();
            }
        }

        int movementPPS = data.movements.size();
        int windowPPS = data.windowOps.size();

        int payloadSize = 0;
        if (className.contains("CustomPayload")) {
            try {
                byte[] payload = extractPayload(packet);
                if (payload != null) payloadSize = payload.length;
            } catch (Exception ignored) {}
        }

        if (payloadSize > PAYLOAD_KICK_SIZE) {
            flag(player, "Oversized payload: " + payloadSize + " bytes", "payload_oversize");
            return;
        }
        if (payloadSize > PAYLOAD_WARN_SIZE) {
            data.warningLevel += 2;
            data.reasons.add("large_payload:" + payloadSize);
        }

        if (totalPPS > KICK_PPS) {
            flag(player, "PPS " + totalPPS + " > " + KICK_PPS, "pps_kick:" + totalPPS);
            return;
        }
        if (totalPPS > WARN_PPS) {
            data.warningLevel++;
            data.reasons.add("high_pps:" + totalPPS);
        }

        if (movementPPS > MOVEMENT_KICK) {
            flag(player, "Movement PPS " + movementPPS + " > " + MOVEMENT_KICK, "move_kick:" + movementPPS);
            return;
        }
        if (movementPPS > MOVEMENT_WARN) {
            data.warningLevel++;
            data.reasons.add("high_movement:" + movementPPS);
        }

        if (windowPPS > WINDOW_KICK) {
            flag(player, "Window PPS " + windowPPS + " > " + WINDOW_KICK, "window_kick:" + windowPPS);
            return;
        }
        if (windowPPS > WINDOW_WARN) {
            data.warningLevel++;
            data.reasons.add("high_window:" + windowPPS);
        }

        if (data.warningLevel >= 5) {
            handleDetection(player, String.join(", ", data.reasons));
            data.warningLevel = 0;
            data.reasons.clear();
        }
    }

    private void flag(Player player, String reason, String logReason) {
        LoggerUtil.violation(plugin, getName(), player.getName(), logReason);
        plugin.getStaffAlert().alert(player.getName(), reason, getName());
        plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), logReason);
        handleDetection(player, reason);
    }

    private void handleDetection(Player player, String reason) {
        String action = plugin.getConfigManager().getModuleAction("protection.packet-inspector");
        switch (action) {
            case "KICK":
                String msg = plugin.getConfigManager().getPrefix() + " Packet flood detected";
                plugin.getScheduler().runKickTask(player, () -> player.kickPlayer(msg));
                break;
            case "WARN":
                player.sendMessage(plugin.getConfigManager().getPrefixed("protection.packet-inspector.warn"));
                break;
        }
    }

    private byte[] extractPayload(Object packet) throws Exception {
        for (Method m : packet.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && m.getReturnType() == byte[].class) {
                return (byte[]) m.invoke(packet);
            }
        }
        for (Field f : packet.getClass().getDeclaredFields()) {
            if (f.getType() == byte[].class) {
                f.setAccessible(true);
                return (byte[]) f.get(packet);
            }
        }
        return null;
    }

    private Object getField(Object obj, String... names) throws Exception {
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

    private String getNMSVersion() {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            return pkg.substring(pkg.lastIndexOf('.') + 1);
        } catch (Exception e) {
            return "v1_21_R1";
        }
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - 300000L;
        playerData.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            return p == null || !p.isOnline();
        });
    }

    private static class PlayerPacketData {
        final Deque<Long> packets = new ConcurrentLinkedDeque<>();
        final Deque<Long> movements = new ConcurrentLinkedDeque<>();
        final Deque<Long> windowOps = new ConcurrentLinkedDeque<>();
        final List<String> reasons = new ArrayList<>();
        int warningLevel = 0;
    }
}
