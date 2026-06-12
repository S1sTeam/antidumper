package com.s1steam.antidumper.protection.phantom;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AntiNBT implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private boolean enabled = false;
    private final Map<UUID, Integer> nbtViolations = new ConcurrentHashMap<>();
    private static final int MAX_NBT_SIZE = 5000;
    private static final int MAX_VIOLATIONS = 2;

    public AntiNBT(AntiDumperPlugin plugin) {
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
        nbtViolations.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiNBT";
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!enabled) return;
        if (!(event.getView().getPlayer() instanceof Player)) return;
        Player player = (Player) event.getView().getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-nbt")) return;

        ItemStack result = event.getResult();
        if (result == null || result.getType().isAir()) return;

        int size = estimateNBTSize(result);
        if (size > MAX_NBT_SIZE) {
            event.setResult(null);
            handleExploit(player, "Anvil NBT: " + size + "b (max " + MAX_NBT_SIZE + ")");
        }
    }

    @EventHandler
    public void onBookEdit(PlayerEditBookEvent event) {
        if (!enabled) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-nbt")) return;

        BookMeta book = event.getNewBookMeta();
        if (book == null) return;

        int size = estimateBookNBTSize(book);
        if (size > MAX_NBT_SIZE * 2) {
            event.setCancelled(true);
            handleExploit(player, "Book NBT: " + size + "b");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!enabled) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-nbt")) return;

        if (event.getInventory().getType() != InventoryType.ANVIL) return;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir()) return;

        int size = estimateNBTSize(current);
        if (size > MAX_NBT_SIZE) {
            event.setCancelled(true);
            handleExploit(player, "Inventory NBT: " + size + "b");
        }
    }

    private void handleExploit(Player player, String reason) {
        int violations = nbtViolations.merge(player.getUniqueId(), 1, Integer::sum);

        if (violations >= MAX_VIOLATIONS) {
            String detail = "NBT exploit: " + reason;
            LoggerUtil.violation(plugin, getName(), player.getName(), detail);
            plugin.getStaffAlert().alert(player.getName(), detail, getName());
            plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(), detail);
            handleDetection(player);
            nbtViolations.remove(player.getUniqueId());
        }
    }

    private int estimateNBTSize(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;

        int size = 0;
        size += 4;
        size += item.getAmount() * 2;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            try {
                String serialized = meta.serialize().toString();
                size += serialized.length() * 2;
            } catch (Exception ignored) {
                size += 100;
            }

            if (meta.hasLore()) {
                for (String line : meta.getLore()) {
                    size += line.length() * 2;
                }
            }

            if (meta.hasEnchants()) {
                size += meta.getEnchants().size() * 10;
            }
        }

        return size;
    }

    private int estimateBookNBTSize(BookMeta book) {
        int size = 0;
        if (book.hasTitle()) size += book.getTitle().length() * 2;
        if (book.hasAuthor()) size += book.getAuthor().length() * 2;

        if (book.hasPages()) {
            for (String page : book.getPages()) {
                size += page.length() * 2;
            }
        }

        return size;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nbtViolations.remove(event.getPlayer().getUniqueId());
    }

    private void handleDetection(Player player) {
        String action = plugin.getConfigManager().getModuleAction("protection.anti-nbt");
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
