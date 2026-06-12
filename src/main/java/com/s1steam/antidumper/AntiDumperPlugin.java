package com.s1steam.antidumper;

import com.s1steam.antidumper.api.AntiDumperAPI;
import com.s1steam.antidumper.bridge.BridgeManager;
import com.s1steam.antidumper.config.ConfigManager;
import com.s1steam.antidumper.database.DatabaseManager;
import com.s1steam.antidumper.expansion.AntiDumperExpansion;
import com.s1steam.antidumper.geo.GeoIPManager;
import com.s1steam.antidumper.gui.MainGUI;
import com.s1steam.antidumper.platform.Platform;
import com.s1steam.antidumper.platform.SchedulerAdapter;
import com.s1steam.antidumper.protection.*;
import com.s1steam.antidumper.protection.bot.*;
import com.s1steam.antidumper.protection.phantom.*;
import com.s1steam.antidumper.punish.PunishmentManager;
import com.s1steam.antidumper.redis.RedisManager;
import com.s1steam.antidumper.stats.OnlineStatsTracker;
import com.s1steam.antidumper.stats.StatsTracker;
import com.s1steam.antidumper.utils.StaffAlertManager;
import com.s1steam.antidumper.utils.WebhookManager;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AntiDumperPlugin extends JavaPlugin implements Listener {
    private static AntiDumperPlugin instance;
    private ConfigManager configManager;
    private WebhookManager webhook;
    private StaffAlertManager staffAlert;
    private GeoIPManager geoIP;
    private StatsTracker stats;
    private OnlineStatsTracker onlineStats;
    private PunishmentManager punishment;
    private MainGUI gui;
    private CommandWhitelist commandWhitelist;
    private BridgeManager bridge;
    private AntiDumperExpansion expansion;
    private SchedulerAdapter scheduler;
    private DatabaseManager database;
    private RedisManager redis;
    private Metrics metrics;
    private ModuleManager moduleManager;
    private final List<ProtectionModule> modules = new ArrayList<>();

    @Override
    public void onEnable() {
        instance = this;
        scheduler = new SchedulerAdapter(this);

        extractResources();
        configManager = new ConfigManager(this);
        webhook = new WebhookManager(this);
        staffAlert = new StaffAlertManager(this);
        geoIP = new GeoIPManager(this);
        stats = new StatsTracker(this);
        onlineStats = new OnlineStatsTracker(this);
        punishment = new PunishmentManager(this);
        gui = new MainGUI(this);
        bridge = new BridgeManager(this);
        expansion = new AntiDumperExpansion(this);
        database = new DatabaseManager(this);
        redis = new RedisManager(this);
        moduleManager = new ModuleManager(this);

        AntiDumperAPI.init(this);

        database.enable();
        redis.enable();

        Bukkit.getPluginManager().registerEvents(this, this);

        AntiDumperCommand cmd = new AntiDumperCommand(this);
        getCommand("antidumper").setExecutor(cmd);
        getCommand("antidumper").setTabCompleter(cmd);

        moduleManager.loadModules();
        moduleManager.enableModules();
        modules.addAll(moduleManager.getLoaded().values());
        onlineStats.startTracking();
        bridge.enable();

        metrics = new Metrics(this, 0);

        printBanner();
    }

    private void extractResources() {
        String[] resources = {"config.yml", "lang/en.yml", "lang/ru.yml"};
        for (String res : resources) {
            File f = new File(getDataFolder(), res.replace('/', File.separatorChar));
            if (!f.exists()) {
                try {
                    saveResource(res, false);
                } catch (Exception e) {
                    getLogger().warning("Could not extract " + res + ": " + e.getMessage());
                }
            }
        }
    }

    private void loadModules() {
        modules.clear();
        if (configManager.isAntiWorldDownloader())
            modules.add(new AntiWorldDownloader(this));
        if (configManager.isAntiPluginStealer())
            modules.add(new AntiPluginStealer(this));
        if (configManager.isAntiCommandDump())
            modules.add(new AntiCommandDump(this));
        if (configManager.isAntiFileSteal())
            modules.add(new AntiFileSteal(this));
        if (configManager.isAntiCrash())
            modules.add(new AntiCrash(this));
        if (configManager.isConnectionLimiter())
            modules.add(new ConnectionLimiter(this));
        if (configManager.isCommandWhitelistEnabled()) {
            commandWhitelist = new CommandWhitelist(this);
            modules.add(commandWhitelist);
        }
        if (configManager.isAntiHackedClient())
            modules.add(new AntiHackedClient(this));
        if (configManager.isBotCAPTCHAEnabled())
            modules.add(new BotCAPTCHA(this));
        if (configManager.isBehaviourCheckEnabled())
            modules.add(new BehaviourCheck(this));
        if (configManager.isSiegeModeEnabled())
            modules.add(new SiegeMode(this));
        if (configManager.isUUIDWhitelistEnabled())
            modules.add(new UUIDWhitelist(this));

        if (configManager.isModuleEnabled("protection.packet-limiter"))
            modules.add(new PacketLimiter(this));
        if (configManager.isModuleEnabled("protection.anti-book-ban"))
            modules.add(new AntiBookBan(this));
        if (configManager.isModuleEnabled("protection.anti-chunk-ban"))
            modules.add(new AntiChunkBan(this));
        if (configManager.isModuleEnabled("protection.anti-lag-machine"))
            modules.add(new AntiLagMachine(this));
        if (configManager.isModuleEnabled("protection.anti-phase"))
            modules.add(new AntiPhase(this));
        if (configManager.isModuleEnabled("protection.anti-blink"))
            modules.add(new AntiBlink(this));
        if (configManager.isModuleEnabled("protection.anti-timer"))
            modules.add(new AntiTimer(this));
        if (configManager.isModuleEnabled("protection.anti-no-fall"))
            modules.add(new AntiNoFall(this));
        if (configManager.isModuleEnabled("protection.anti-boat-fly"))
            modules.add(new AntiBoatFly(this));
        if (configManager.isModuleEnabled("protection.anti-elytra-fly"))
            modules.add(new AntiElytraFly(this));
        if (configManager.isModuleEnabled("protection.anti-auto-pearl"))
            modules.add(new AntiAutoPearl(this));
        if (configManager.isModuleEnabled("protection.anti-inventory-move"))
            modules.add(new AntiInventoryMove(this));
        if (configManager.isModuleEnabled("protection.anti-disabler"))
            modules.add(new AntiDisabler(this));
        if (configManager.isModuleEnabled("protection.anti-tower"))
            modules.add(new AntiTower(this));
        if (configManager.isModuleEnabled("protection.anti-scaffold"))
            modules.add(new AntiScaffold(this));
        if (configManager.isModuleEnabled("protection.anti-nbt"))
            modules.add(new AntiNBT(this));
        if (configManager.isModuleEnabled("protection.anti-command-exploit"))
            modules.add(new AntiCommandExploit(this));
    }

    private void enableModules() {
        for (ProtectionModule module : modules) {
            try {
                module.enable();
                getLogger().info("  \u2713 " + module.getName());
            } catch (Exception e) {
                getLogger().warning("  x " + module.getName() + " - " + e.getMessage());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (configManager.isGeoIPEnabled() && configManager.isGeoLogOnJoin()) {
            geoIP.lookup(p);
        }
    }

    @Override
    public void onDisable() {
        bridge.disable();
        onlineStats.stopTracking();
        redis.disable();
        database.disable();
        moduleManager.disableModules();
        modules.clear();
        getLogger().info("AntiDumper disabled.");
    }

    public void reload() {
        onDisable();
        configManager.reload();
        moduleManager.loadModules();
        moduleManager.enableModules();
        modules.clear();
        modules.addAll(moduleManager.getLoaded().values());
        onlineStats.startTracking();
        bridge.enable();
    }

    public void setCommandWhitelist(CommandWhitelist cw) {
        this.commandWhitelist = cw;
    }

    private void printBanner() {
        String v = getDescription().getVersion();
        String p = Platform.getName();
        String db = database.isConnected() ? "§a" + database.getType().toUpperCase() : "§cOFF";
        String r = redis.isEnabled() ? "§aON" : "§7OFF";
        String mods = String.valueOf(modules.size());

        getLogger().info("");
        getLogger().info("§8╔══════════════════════════════════════════════╗");
        getLogger().info("§8║§c          █████╗ ███╗   ██╗████████╗██╗     §8║");
        getLogger().info("§8║§c         ██╔══██╗████╗  ██║╚══██╔══╝██║     §8║");
        getLogger().info("§8║§c         ███████║██╔██╗ ██║   ██║   ██║     §8║");
        getLogger().info("§8║§c         ██╔══██║██║╚██╗██║   ██║   ██║     §8║");
        getLogger().info("§8║§c         ██║  ██║██║ ╚████║   ██║   ███████╗§8║");
        getLogger().info("§8║§c         ╚═╝  ╚═╝╚═╝  ╚═══╝   ╚═╝   ╚══════╝§8║");
        getLogger().info("§8║                                              §8║");
        getLogger().info("§8║  §fVersion: §c" + v + "§7                  §8║");
        getLogger().info("§8║  §fPlatform: §c" + p + "§7               §8║");
        getLogger().info("§8║  §fModules: §c" + mods + "§7                  §8║");
        getLogger().info("§8║  §fDatabase: " + db + "§7            §8║");
        getLogger().info("§8║  §fRedis: " + r + "§7                §8║");
        getLogger().info("§8║                                              §8║");
        getLogger().info("§8║  §8Author: §fS1sTeam §8| © 2026             §8║");
        getLogger().info("§8╚══════════════════════════════════════════════╝");
        getLogger().info("");
    }

    public static AntiDumperPlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public WebhookManager getWebhook() { return webhook; }
    public StaffAlertManager getStaffAlert() { return staffAlert; }
    public GeoIPManager getGeoIP() { return geoIP; }
    public StatsTracker getStats() { return stats; }
    public OnlineStatsTracker getOnlineStats() { return onlineStats; }
    public PunishmentManager getPunishment() { return punishment; }
    public MainGUI getGui() { return gui; }
    public List<ProtectionModule> getModules() { return modules; }
    public CommandWhitelist getCommandWhitelist() { return commandWhitelist; }
    public BridgeManager getBridge() { return bridge; }
    public AntiDumperExpansion getExpansion() { return expansion; }
    public SchedulerAdapter getScheduler() { return scheduler; }
    public DatabaseManager getDatabase() { return database; }
    public RedisManager getRedis() { return redis; }
    public ModuleManager getModuleManager() { return moduleManager; }
}
