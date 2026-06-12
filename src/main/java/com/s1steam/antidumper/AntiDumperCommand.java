package com.s1steam.antidumper;

import com.s1steam.antidumper.geo.GeoIPManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class AntiDumperCommand implements CommandExecutor, TabCompleter {
    private final AntiDumperPlugin plugin;

    private static final Set<String> SUSPICIOUS_MODS = new HashSet<>(Arrays.asList(
        "WDL|CONTROL", "WDL|INIT", "wdl:control", "wdl:init",
        "worlddownloader:control", "worlddownloader:init",
        "litematica:hello", "litematica:world", "litematica:schematic",
        "plugingrabber:main", "plugingrabber:file",
        "stealer:main", "stealer:file",
        "plugin_grabber", "dl:file"
    ));

    public AntiDumperCommand(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (args.length == 0) {
            sender.sendMessage("§8[§cAntiDumper§8] §7Version: §f" + plugin.getDescription().getVersion());
            sender.sendMessage("§7Author: §fS1sTeam");
            sender.sendMessage(plugin.getConfigManager().get("commands.usage"));
            if (sender.hasPermission("antidumper.admin")) {
                sender.sendMessage("§7/ad gui §8- §7panel");
                sender.sendMessage(plugin.getConfigManager().get("commands.check.usage"));
                sender.sendMessage("§7/ad stats §8- §7statistics");
                sender.sendMessage("§7/ad onlinestats §8- §7online statistics");
                sender.sendMessage("§7/ad punish <player> [reset] §8- §7punishment info/reset");
                sender.sendMessage("§7/ad whitelist add|remove|list <command> §8- §7manage whitelist");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly for players.");
                return true;
            }
            if (!sender.hasPermission("antidumper.admin")) {
                sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.no-permission"));
                return true;
            }
            plugin.getGui().open((Player) sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("antidumper.admin")) {
                sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.no-permission"));
                return true;
            }
            plugin.reload();
            sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.reload"));
            return true;
        }

        if (args[0].equalsIgnoreCase("check")) {
            if (!sender.hasPermission("antidumper.admin")) {
                sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.no-permission"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(plugin.getConfigManager().get("commands.check.usage"));
                return true;
            }
            checkPlayer(sender, args[1]);
            return true;
        }

        if (args[0].equalsIgnoreCase("stats")) {
            if (!sender.hasPermission("antidumper.admin")) {
                sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.no-permission"));
                return true;
            }
            sender.sendMessage(plugin.getStats().getFormattedStats());
            return true;
        }

        if (args[0].equalsIgnoreCase("onlinestats")) {
            if (!sender.hasPermission("antidumper.admin")) {
                sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.no-permission"));
                return true;
            }
            sender.sendMessage(plugin.getOnlineStats().getFormattedStats());
            return true;
        }

        if (args[0].equalsIgnoreCase("punish")) {
            if (!sender.hasPermission("antidumper.admin")) {
                sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.no-permission"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§7/ad punish <player> [reset]");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.check.no-player"));
                return true;
            }
            if (args.length > 2 && args[2].equalsIgnoreCase("reset")) {
                plugin.getPunishment().resetPlayer(target.getUniqueId());
                sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.punish.reset").replace("%player%", target.getName()));
            } else {
                sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.punish.info").replace("%player%", target.getName()));
                sender.sendMessage(plugin.getPunishment().getFormattedData(target.getUniqueId()));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("whitelist")) {
            if (!sender.hasPermission("antidumper.admin")) {
                sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.no-permission"));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§7/ad whitelist add|remove|list [command]");
                return true;
            }
            handleWhitelist(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("module") || args[0].equalsIgnoreCase("mod")) {
            if (!sender.hasPermission("antidumper.admin")) {
                sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.no-permission"));
                return true;
            }
            handleModule(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            sender.sendMessage("§8[§cAntiDumper§8] §7Version: §f" + plugin.getDescription().getVersion());
            sender.sendMessage("§7Author: §fS1sTeam");
            sender.sendMessage("§7Active modules: §f" + plugin.getModuleManager().getActiveModules().size());
            return true;
        }

        sender.sendMessage(plugin.getConfigManager().get("commands.usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("info");
            if (sender.hasPermission("antidumper.admin")) {
                list.add("reload");
                list.add("check");
                list.add("gui");
                list.add("stats");
                list.add("onlinestats");
                list.add("punish");
                list.add("whitelist");
                list.add("module");
            }
            return list;
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("punish")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("whitelist")) {
                return Arrays.asList("add", "remove", "list");
            }
            if (args[0].equalsIgnoreCase("module") || args[0].equalsIgnoreCase("mod")) {
                return Arrays.asList("list", "toggle", "disable_all");
            }
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("module") || args[0].equalsIgnoreCase("mod"))
            && args[1].equalsIgnoreCase("toggle")) {
            return new ArrayList<>(plugin.getModuleManager().getLoaded().keySet());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("punish")) {
            return Collections.singletonList("reset");
        }
        return Collections.emptyList();
    }

    private void checkPlayer(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.check.no-player"));
            return;
        }

        sender.sendMessage(plugin.getConfigManager().getPrefixed("commands.check.checking").replace("%player%", target.getName()));

        Set<String> channels = target.getListeningPluginChannels();
        int suspicious = 0;

        StringBuilder result = new StringBuilder();
        result.append("\n").append(plugin.getConfigManager().get("commands.check.result-header").replace("%player%", target.getName())).append("\n");

        for (String ch : channels) {
            String lower = ch.toLowerCase();
            boolean sus = SUSPICIOUS_MODS.stream().anyMatch(m ->
                lower.equals(m.toLowerCase()) || lower.equals(m.replace("|", ":").toLowerCase()));

            if (sus) {
                result.append(plugin.getConfigManager().get("commands.check.suspicious").replace("%channel%", ch)).append("\n");
                suspicious++;
            } else {
                result.append(plugin.getConfigManager().get("commands.check.safe").replace("%channel%", ch)).append("\n");
            }
        }

        result.append(plugin.getConfigManager().get("commands.check.total-channels").replace("%count%", String.valueOf(channels.size()))).append("\n");

        if (suspicious == 0) {
            result.append(plugin.getConfigManager().get("commands.check.clean"));
        } else {
            result.append(plugin.getConfigManager().get("commands.check.detected").replace("%count%", String.valueOf(suspicious)));
        }

        GeoIPManager.GeoEntry geo = plugin.getGeoIP().getGeo(target.getUniqueId());
        if (geo != null) {
            result.append("\n§7Geo: §f").append(geo.getDisplay());
            if (!"?".equals(geo.getIsp())) {
                result.append(" §8(").append(geo.getIsp()).append(")");
            }
        }

        int violations = plugin.getStats().getTotalViolations(target.getUniqueId());
        result.append("\n§7Violations: §f").append(violations);
        int punishCount = plugin.getPunishment().getViolationCount(target.getUniqueId());
        result.append("\n§7Punish level: §f").append(punishCount);

        sender.sendMessage(result.toString());
    }

    private void handleModule(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§7/ad module list §8- §7list all modules");
            sender.sendMessage("§7/ad module toggle <name> §8- §7enable/disable live");
            return;
        }

        String sub = args[1].toLowerCase();

        if (sub.equals("list")) {
            sender.sendMessage("§6Active modules:");
            for (String name : plugin.getModuleManager().getActiveModules()) {
                sender.sendMessage("  §a\u2713 " + name);
            }
            sender.sendMessage("§6Disabled:");
            for (String name : plugin.getModuleManager().getDisabledModules()) {
                sender.sendMessage("  §c\u2717 " + name);
            }
            return;
        }

        if (sub.equals("toggle") || sub.equals("t")) {
            if (args.length < 3) {
                sender.sendMessage("§7/ad module toggle <name>");
                return;
            }
            String target = args[2];
            if (plugin.getModuleManager().getModule(target) == null) {
                sender.sendMessage("§cModule not found: " + target);
                sender.sendMessage("§7Use: /ad module list");
                return;
            }
            boolean now = plugin.getModuleManager().hotToggle(target);
            if (now) {
                sender.sendMessage("§aModule " + target + " enabled");
            } else {
                sender.sendMessage("§cModule " + target + " disabled");
            }
            return;
        }

        if (sub.equals("disable_all")) {
            plugin.getModuleManager().disableAll();
            sender.sendMessage("§cAll modules disabled");
            return;
        }

        sender.sendMessage("§7/ad module list|toggle <name>|disable_all");
    }

    private void handleWhitelist(CommandSender sender, String[] args) {
        String sub = args[1].toLowerCase();
        if (sub.equals("list")) {
            List<String> allowed = plugin.getConfigManager().getCommandWhitelistAllowed();
            sender.sendMessage("§6Allowed commands (§f" + allowed.size() + "§6):");
            StringBuilder sb = new StringBuilder();
            for (String c : allowed) {
                if (sb.length() > 0) sb.append("§7, ");
                sb.append("§f/").append(c);
            }
            sender.sendMessage(sb.toString());
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§7/ad whitelist " + sub + " <command>");
            return;
        }

        String command = args[2].toLowerCase();
        List<String> allowed = new ArrayList<>(plugin.getConfigManager().getCommandWhitelistAllowed());

        if (sub.equals("add")) {
            if (allowed.contains(command)) {
                sender.sendMessage("§cCommand /" + command + " is already in the whitelist.");
                return;
            }
            allowed.add(command);
            sender.sendMessage("§aCommand /" + command + " added to whitelist.");
        } else if (sub.equals("remove")) {
            if (!allowed.contains(command)) {
                sender.sendMessage("§cCommand /" + command + " is not in the whitelist.");
                return;
            }
            allowed.remove(command);
            sender.sendMessage("§aCommand /" + command + " removed from whitelist.");
        } else {
            sender.sendMessage("§7/ad whitelist add|remove|list [command]");
            return;
        }

        plugin.getConfigManager().setLangConfigValue("command-whitelist.allowed-commands", allowed);
        if (plugin.getCommandWhitelist() != null) {
            plugin.getCommandWhitelist().reloadAllowed();
        }
    }
}
