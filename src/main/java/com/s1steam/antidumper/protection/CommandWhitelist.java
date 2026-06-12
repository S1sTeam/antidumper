package com.s1steam.antidumper.protection;

import com.s1steam.antidumper.AntiDumperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashSet;
import java.util.Set;

public class CommandWhitelist implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private final Set<String> allowedCommands = new HashSet<>();
    private boolean enabled = false;

    public CommandWhitelist(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;
        reloadAllowed();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        if (!enabled) return;
        allowedCommands.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "CommandWhitelist";
    }

    public void reloadAllowed() {
        allowedCommands.clear();
        allowedCommands.addAll(plugin.getConfigManager().getCommandWhitelistAllowed());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().isCommandWhitelistEnabled()) return;
        if (!enabled) return;
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (player.hasPermission(plugin.getConfigManager().getCommandWhitelistBypassPermission())) return;

        String msg = event.getMessage().trim();
        if (!msg.startsWith("/")) return;

        String fullCommand = msg.substring(1).toLowerCase();
        String baseCommand = fullCommand.split(" ")[0];

        String mode = plugin.getConfigManager().getCommandWhitelistMode();

        if ("STRICT".equals(mode)) {
            if (!allowedCommands.contains(baseCommand)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getPrefixed("protection.command-whitelist.blocked"));
                plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(),
                    "Blocked command: /" + baseCommand + " (mode: STRICT)");
            }
        } else if ("NORMAL".equals(mode)) {
            if (allowedCommands.contains(baseCommand)) {
                event.setCancelled(true);
                player.sendMessage(plugin.getConfigManager().getPrefixed("protection.command-whitelist.blocked"));
                plugin.getStats().logViolation(player.getUniqueId(), player.getName(), getName(),
                    "Blocked command: /" + baseCommand + " (mode: NORMAL)");
            }
        }
    }
}
