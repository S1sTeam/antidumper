package com.s1steam.antidumper.protection;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerListPingEvent;

import java.util.*;

public class AntiCommandDump implements ProtectionModule, Listener {
    private final AntiDumperPlugin plugin;
    private final Set<String> blockedCommands = new HashSet<>();
    private boolean enabled = false;

    private static final String[] HELP_VARIANTS = {"/help", "/?", "/bukkit:help", "/minecraft:help"};

    public AntiCommandDump(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        if (enabled) return;
        enabled = true;
        buildBlockedList();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerTabCompleteHandler();
    }

    private void buildBlockedList() {
        blockedCommands.clear();

        if (plugin.getConfigManager().isBlockPlugins())
            blockedCommands.addAll(Arrays.asList("plugins", "pl", "bukkit:plugins", "bukkit:pl"));
        if (plugin.getConfigManager().isBlockVersion())
            blockedCommands.addAll(Arrays.asList("version", "ver", "bukkit:version", "bukkit:ver"));
        if (plugin.getConfigManager().isBlockDump())
            blockedCommands.addAll(Arrays.asList("dump", "debug", "paper dump", "bukkit:dump"));
        if (plugin.getConfigManager().isBlockAbout())
            blockedCommands.addAll(Arrays.asList("about", "bukkit:about"));
        if (plugin.getConfigManager().isBlockIcanhasbukkit())
            blockedCommands.add("icanhasbukkit");
        if (plugin.getConfigManager().isBlockTimings())
            blockedCommands.add("timings");
        if (plugin.getConfigManager().isBlockPlugman())
            blockedCommands.addAll(Arrays.asList("plugman", "pm", "plugman:*"));
        if (plugin.getConfigManager().isBlockEssentials())
            blockedCommands.addAll(Arrays.asList("essentials", "essentials:*"));

        if (plugin.getConfigManager().isBlockPing()) {
            blockedCommands.addAll(Arrays.asList("ping", "bukkit:ping"));
        }

        blockedCommands.addAll(Arrays.asList(
            "worldedit:help", "worldedit:version", "worldedit:world",
            "fastasyncworldedit:help", "fastasyncworldedit:version",
            "minecraft:version", "minecraft:about"
        ));

        blockedCommands.addAll(plugin.getConfigManager().getCustomBlockedCommands());
    }

    @SuppressWarnings("unchecked")
    private void registerTabCompleteHandler() {
        try {
            Class<?> raw = Class.forName("org.bukkit.event.server.TabCompleteEvent");
            Class<? extends org.bukkit.event.Event> eventClass = (Class<? extends org.bukkit.event.Event>) raw;
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.LOW,
                (listener, event) -> {
                    if (eventClass.isInstance(event)) handleTabComplete(event);
                }, plugin);
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void handleTabComplete(Object event) {
        try {
            if (!plugin.getConfigManager().isHideBlockedInTabComplete()) return;

            CommandSender s = (CommandSender) event.getClass().getMethod("getSender").invoke(event);
            if (s instanceof Player && (((Player) s).hasPermission("antidumper.bypass") || ((Player) s).hasPermission("antidumper.bypass.anti-command-dump"))) return;

            String buffer = (String) event.getClass().getMethod("getBuffer").invoke(event);
            List<String> completions = (List<String>) event.getClass().getMethod("getCompletions").invoke(event);

            if (!buffer.startsWith("/")) return;
            String cmd = buffer.substring(1).toLowerCase().trim().split(" ")[0];

            if (blockedCommands.contains(cmd) || blockedCommands.contains(cmd + ":*")) {
                event.getClass().getMethod("setCompletions", List.class)
                    .invoke(event, Collections.emptyList());
                return;
            }

            if (!buffer.contains(" ")) {
                List<String> filtered = new ArrayList<>();
                for (String c : completions) {
                    String cleaned = c.startsWith("/") ? c.substring(1).toLowerCase() : c.toLowerCase();
                    boolean block = blockedCommands.contains(cleaned)
                        || blockedCommands.contains(cleaned.split(":")[0] + ":*");
                    if (!block) filtered.add(c);
                }
                event.getClass().getMethod("setCompletions", List.class).invoke(event, filtered);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void disable() {
        if (!enabled) return;
        blockedCommands.clear();
        enabled = false;
    }

    @Override
    public String getName() {
        return "AntiCommandDump";
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (player.hasPermission("antidumper.bypass") || player.hasPermission("antidumper.bypass.anti-command-dump")) return;

        String fullMsg = event.getMessage().toLowerCase().trim();
        String baseCommand = fullMsg.substring(1).split(" ")[0];

        for (String blocked : blockedCommands) {
            if (baseCommand.equals(blocked) || blocked.equals(baseCommand.split(":")[0] + ":*")) {
                event.setCancelled(true);
                if (baseCommand.equals("ping") || baseCommand.equals("bukkit:ping")) {
                    player.sendMessage(plugin.getConfigManager().getPrefixed("protection.command-dump.ping-blocked"));
                } else {
                    player.sendMessage(plugin.getConfigManager().getPrefixed("protection.command-dump.blocked"));
                }
                LoggerUtil.violation(plugin, "AntiCommandDump", player.getName(), "Blocked command: /" + baseCommand);
                return;
            }
        }

        if (plugin.getConfigManager().isHelpFilterEnabled()) {
            String cmdOnly = fullMsg.substring(1);
            for (String help : HELP_VARIANTS) {
                if (cmdOnly.startsWith(help.substring(1))) {
                    event.setCancelled(true);
                    player.sendMessage(plugin.getConfigManager().getPrefixed("protection.command-dump.help-filtered"));
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onServerPing(ServerListPingEvent event) {
        if (!plugin.getConfigManager().isHidePluginsInPing()) return;

        try {
            Class<?> paperPing = Class.forName("com.destroystokyo.paper.event.server.PaperServerListPingEvent");
            if (paperPing.isInstance(event)) {
                paperPing.getMethod("setHidePlugins", boolean.class).invoke(event, true);
            }
        } catch (Exception ignored) {}

        String motd = event.getMotd();
        if (motd != null && (motd.contains("Paper") || motd.contains("Spigot") || motd.contains("Bukkit") || motd.contains("Purpur"))) {
            event.setMotd(plugin.getConfigManager().getPrefix() + " §7Server");
        }
    }
}
