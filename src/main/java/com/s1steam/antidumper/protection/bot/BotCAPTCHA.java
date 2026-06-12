package com.s1steam.antidumper.protection.bot;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BotCAPTCHA implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private final Map<UUID, CaptchaData> captchaMap = new ConcurrentHashMap<>();
    private boolean enabled = false;

    private static final long TIMEOUT_MS = 30000L;

    private static class CaptchaData {
        final int number;
        final long timestamp;
        boolean verified;

        CaptchaData(int number) {
            this.number = number;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public BotCAPTCHA(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        plugin.getScheduler().syncTimer(() -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, CaptchaData> entry : captchaMap.entrySet()) {
                CaptchaData data = entry.getValue();
                if (!data.verified && now - data.timestamp > TIMEOUT_MS) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        
                        LoggerUtil.violation(plugin, getName(), player.getName(), "CAPTCHA timeout");
                        plugin.getStaffAlert().alert(player.getName(), "CAPTCHA timeout", getName());
                        plugin.getScheduler().runKickTask(player, () ->
                            player.kickPlayer(plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().get("protection.captcha.kick")));
                    }
                    captchaMap.remove(entry.getKey());
                }
            }
        }, 20L, 20L);
    }

    @Override
    public void disable() {
        if (!enabled) return;
        HandlerList.unregisterAll(this);
        captchaMap.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "BotCAPTCHA";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.captcha")) return;
        int number = 1000 + (int) (Math.random() * 9000);
        captchaMap.put(player.getUniqueId(), new CaptchaData(number));

        
        player.sendMessage(plugin.getConfigManager().getPrefixed("protection.captcha.prompt")
            .replace("%number%", String.valueOf(number)));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.captcha")) return;
        CaptchaData data = captchaMap.get(player.getUniqueId());
        if (data == null || data.verified) return;

        event.setCancelled(true);
        checkCaptcha(player, event.getMessage(), data);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.captcha")) return;
        CaptchaData data = captchaMap.get(player.getUniqueId());
        if (data == null || data.verified) return;

        event.setCancelled(true);
        checkCaptcha(player, event.getMessage(), data);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.captcha")) return;
        captchaMap.remove(player.getUniqueId());
    }

    private void checkCaptcha(Player player, String input, CaptchaData data) {
        long now = System.currentTimeMillis();
        if (now - data.timestamp > TIMEOUT_MS) {
            captchaMap.remove(player.getUniqueId());
            
            LoggerUtil.violation(plugin, getName(), player.getName(), "CAPTCHA timeout");
            plugin.getStaffAlert().alert(player.getName(), "CAPTCHA timeout", getName());
            plugin.getScheduler().runKickTask(player, () ->
                player.kickPlayer(plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().get("protection.captcha.kick")));
            return;
        }

        try {
            int answer = Integer.parseInt(input.trim());
            if (answer == data.number) {
                data.verified = true;
                player.sendMessage(plugin.getConfigManager().getPrefixed("protection.captcha.success"));
            } else {
                captchaMap.remove(player.getUniqueId());
                
                LoggerUtil.violation(plugin, getName(), player.getName(),
                    "Wrong CAPTCHA answer: " + input);
                plugin.getStaffAlert().alert(player.getName(),
                    "Wrong CAPTCHA answer", getName());
                plugin.getScheduler().runKickTask(player, () ->
                    player.kickPlayer(plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().get("protection.captcha.kick")));
            }
        } catch (NumberFormatException e) {
            captchaMap.remove(player.getUniqueId());
            
            LoggerUtil.violation(plugin, getName(), player.getName(),
                "Invalid CAPTCHA input: " + input);
            plugin.getStaffAlert().alert(player.getName(),
                "Invalid CAPTCHA input", getName());
            plugin.getScheduler().runKickTask(player, () ->
                player.kickPlayer(plugin.getConfigManager().getPrefix() + " " + plugin.getConfigManager().get("protection.captcha.kick")));
        }
    }

    private void handleDetection(Player player) {
        
        String action = plugin.getConfigManager().getModuleAction("protection.captcha");
        switch (action) {
            case "KICK":
                plugin.getScheduler().runKickTask(player, () ->
                    player.kickPlayer(plugin.getConfigManager().getPrefix() + " " +
                        plugin.getConfigManager().get("protection.exploit.kick").replace("%module%", getName())));
                break;
            case "WARN":
                player.sendMessage(plugin.getConfigManager().getPrefixed("protection.exploit.warn")
                    .replace("%module%", getName()));
                break;
        }
    }
}
