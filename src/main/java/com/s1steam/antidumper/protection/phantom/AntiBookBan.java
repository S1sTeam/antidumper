package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.meta.BookMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AntiBookBan implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, AtomicInteger> bookEdits = new ConcurrentHashMap<>();
    private static final int MAX_PAGES = 50;
    private static final int MAX_PAGE_LENGTH = 200;
    private static final int MAX_EDITS_PER_SECOND = 5;

    public AntiBookBan(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        if (!enabled) return;
        HandlerList.unregisterAll(this);
        bookEdits.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiBookBan";
    }

    @EventHandler
    public void onBookEdit(PlayerEditBookEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-book-ban")) return;

        BookMeta book = event.getNewBookMeta();
        if (book == null) return;

        int pages = book.getPageCount();
        if (pages > MAX_PAGES) {
            event.setCancelled(true);
            String detail = "Book with " + pages + " pages (max " + MAX_PAGES + ")";
            LoggerUtil.violation(plugin, getName(), player.getName(), detail);
            plugin.getStaffAlert().alert(player.getName(), detail, getName());
            plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
            handleDetection(player);
            return;
        }

        for (int i = 0; i < pages; i++) {
            String page = book.getPage(i + 1);
            if (page != null && page.length() > MAX_PAGE_LENGTH) {
                event.setCancelled(true);
                String detail = "Page " + (i + 1) + " length: " + page.length() + " (max " + MAX_PAGE_LENGTH + ")";
                LoggerUtil.violation(plugin, getName(), player.getName(), detail);
                plugin.getStaffAlert().alert(player.getName(), detail, getName());
                plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
                handleDetection(player);
                return;
            }
        }

        AtomicInteger count = bookEdits.computeIfAbsent(player.getUniqueId(), k -> new AtomicInteger(0));
        int current = count.incrementAndGet();
        if (current > MAX_EDITS_PER_SECOND) {
            event.setCancelled(true);
            String detail = "Rapid book edits: " + current + "/s";
            LoggerUtil.violation(plugin, getName(), player.getName(), detail);
            plugin.getStaffAlert().alert(player.getName(), detail, getName());
            plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
            handleDetection(player);
        }

        plugin.getScheduler().syncLater(() -> {
            AtomicInteger c = bookEdits.get(player.getUniqueId());
            if (c != null) c.set(0);
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bookEdits.remove(event.getPlayer().getUniqueId());
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-book-ban");
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
