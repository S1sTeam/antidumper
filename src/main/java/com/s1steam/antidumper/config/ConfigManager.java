package com.s1steam.antidumper.config;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.s1steam.antidumper.utils.ColorUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigManager {
    private final AntiDumperPlugin plugin;
    private FileConfiguration langConfig;
    private FileConfiguration lang;
    private FileConfiguration fallback;
    private String currentLang;

    public ConfigManager(AntiDumperPlugin plugin) {
        this.plugin = plugin;
        initFiles();
        loadConfig();
        loadLang();
    }

    private void initFiles() {
        saveIfNotExists("config.yml");
        saveIfNotExists("lang/en.yml");
        saveIfNotExists("lang/ru.yml");
    }

    private void saveIfNotExists(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            try {
                plugin.saveResource(name, false);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save " + name + ": " + e.getMessage());
            }
        }
    }

    public void load() {
        loadConfig();
        loadLang();
    }

    private void loadConfig() {
        File f = new File(plugin.getDataFolder(), "config.yml");
        if (f.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(f);
        } else {
            langConfig = new YamlConfiguration();
            plugin.getLogger().warning("config.yml not found!");
        }
        currentLang = langConfig.getString("lang", "en");
    }

    private void loadLang() {
        String langCode = currentLang;
        File langFile = new File(plugin.getDataFolder(), "lang" + File.separator + langCode + ".yml");

        if (!langFile.exists()) {
            langCode = "en";
            langFile = new File(plugin.getDataFolder(), "lang" + File.separator + "en.yml");
            if (!langFile.exists()) {
                plugin.getLogger().warning("Language file not found: lang/" + currentLang + ".yml, falling back to en");
                lang = new YamlConfiguration();
                fallback = new YamlConfiguration();
                return;
            }
        }

        lang = YamlConfiguration.loadConfiguration(langFile);

        try (InputStream in = plugin.getResource("lang/en.yml")) {
            if (in != null) {
                fallback = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load fallback language: " + e.getMessage());
            fallback = new YamlConfiguration();
        }
    }

    public void reload() {
        load();
    }

    // ───── Language / Translation ─────

    public String get(String path) {
        String msg = lang.getString(path);
        if (msg != null) return ColorUtil.translate(msg);
        if (fallback != null) msg = fallback.getString(path);
        return msg != null ? ColorUtil.translate(msg) : "§cMissing lang: " + path;
    }

    public String getPrefixed(String path) {
        return getPrefix() + " " + get(path);
    }

    public List<String> getStringList(String path) {
        List<String> list = lang.getStringList(path);
        if (!list.isEmpty()) {
            List<String> translated = new java.util.ArrayList<>(list.size());
            for (String s : list) translated.add(ColorUtil.translate(s));
            return translated;
        }
        if (fallback != null) list = fallback.getStringList(path);
        if (!list.isEmpty()) {
            List<String> translated = new java.util.ArrayList<>(list.size());
            for (String s : list) translated.add(ColorUtil.translate(s));
            return translated;
        }
        return Collections.singletonList("§cMissing lang list: " + path);
    }

    public String getPrefix() {
        return ColorUtil.translate(langConfig.getString("settings.prefix", "§8[§cAntiDumper§8]"));
    }

    public String getCurrentLang() {
        return currentLang;
    }

    // ───── Debug Mode ─────

    public boolean isDebugEnabled() {
        return langConfig.getBoolean("debug", false);
    }

    // ───── Alert Sound ─────

    public boolean isAlertSoundEnabled() {
        return langConfig.getBoolean("staff-alerts.sound.enabled", true);
    }

    public String getAlertSoundName() {
        return langConfig.getString("staff-alerts.sound.sound", "BLOCK_NOTE_BLOCK_PLING");
    }

    public float getAlertSoundVolume() {
        return (float) langConfig.getDouble("staff-alerts.sound.volume", 0.5);
    }

    public float getAlertSoundPitch() {
        return (float) langConfig.getDouble("staff-alerts.sound.pitch", 1.0);
    }

    public List<String> getAvailableLanguages() {
        File langDir = new File(plugin.getDataFolder(), "lang");
        File[] files = langDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return Arrays.asList("en", "ru");
        return Arrays.stream(files)
            .map(f -> f.getName().replace(".yml", ""))
            .collect(Collectors.toList());
    }

    public void setLang(String langCode) {
        currentLang = langCode;
        langConfig.set("lang", langCode);
        try {
            langConfig.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (Exception ignored) {}
        loadLang();
    }

    // ───── AntiWorldDownloader ─────

    public boolean isAntiWorldDownloader() {
        return langConfig.getBoolean("protection.anti-world-downloader.enabled", true);
    }

    public String getWDLModuleAction() {
        return langConfig.getString("protection.anti-world-downloader.action", "KICK").toUpperCase();
    }

    public List<String> getWDLBlockedChannels() {
        return langConfig.getStringList("protection.anti-world-downloader.blocked-channels");
    }

    // ───── AntiPluginStealer ─────

    public boolean isAntiPluginStealer() {
        return langConfig.getBoolean("protection.anti-plugin-stealer.enabled", true);
    }

    public String getPluginStealerAction() {
        return langConfig.getString("protection.anti-plugin-stealer.action", "KICK").toUpperCase();
    }

    public boolean isPluginStealerCommandScan() {
        return langConfig.getBoolean("protection.anti-plugin-stealer.detection.command-scan", true);
    }

    // ───── AntiCommandDump ─────

    public boolean isAntiCommandDump() {
        return langConfig.getBoolean("protection.anti-command-dump.enabled", true);
    }

    public boolean isBlockPlugins() {
        return langConfig.getBoolean("protection.anti-command-dump.commands.plugins", true);
    }

    public boolean isBlockVersion() {
        return langConfig.getBoolean("protection.anti-command-dump.commands.version", true);
    }

    public boolean isBlockDump() {
        return langConfig.getBoolean("protection.anti-command-dump.commands.dump", true);
    }

    public boolean isBlockAbout() {
        return langConfig.getBoolean("protection.anti-command-dump.commands.about", true);
    }

    public boolean isBlockIcanhasbukkit() {
        return langConfig.getBoolean("protection.anti-command-dump.commands.icanhasbukkit", true);
    }

    public boolean isBlockTimings() {
        return langConfig.getBoolean("protection.anti-command-dump.commands.timings", true);
    }

    public boolean isBlockPlugman() {
        return langConfig.getBoolean("protection.anti-command-dump.commands.plugman", true);
    }

    public boolean isBlockEssentials() {
        return langConfig.getBoolean("protection.anti-command-dump.commands.essentials", true);
    }

    public boolean isBlockPing() {
        return langConfig.getBoolean("protection.anti-command-dump.commands.ping", true);
    }

    public boolean isHelpFilterEnabled() {
        return langConfig.getBoolean("protection.anti-command-dump.help-filter.enabled", true);
    }

    public List<String> getCustomBlockedCommands() {
        return langConfig.getStringList("protection.anti-command-dump.commands.custom");
    }

    public boolean isHideBlockedInTabComplete() {
        return langConfig.getBoolean("protection.anti-command-dump.tab-complete.hide-blocked-commands", true);
    }

    public boolean isHidePluginsInPing() {
        return langConfig.getBoolean("protection.anti-command-dump.server-ping.hide-plugins", true);
    }

    // ───── AntiFileSteal ─────

    public boolean isAntiFileSteal() {
        return langConfig.getBoolean("protection.anti-file-steal.enabled", true);
    }

    public String getFileStealAction() {
        return langConfig.getString("protection.anti-file-steal.action", "KICK").toUpperCase();
    }

    public boolean isPathTraversalEnabled() {
        return langConfig.getBoolean("protection.anti-file-steal.features.path-traversal", true);
    }

    public boolean isExploitChannelsEnabled() {
        return langConfig.getBoolean("protection.anti-file-steal.features.exploit-channels", true);
    }

    public List<String> getProtectedExtensions() {
        return langConfig.getStringList("protection.anti-file-steal.protected-extensions");
    }

    // ───── AntiCrash ─────

    public boolean isAntiCrash() {
        return langConfig.getBoolean("protection.anti-crash.enabled", true);
    }

    public int getCrashMaxPacketSize() {
        return langConfig.getInt("protection.anti-crash.max-packet-size", 5000);
    }

    public int getCrashMaxPluginMessageSize() {
        return langConfig.getInt("protection.anti-crash.max-plugin-message-size", 500);
    }

    public boolean isCrashBlockCommands() {
        return langConfig.getBoolean("protection.anti-crash.block-crash-commands", true);
    }

    public boolean isCrashBlockPayloads() {
        return langConfig.getBoolean("protection.anti-crash.block-crash-payloads", true);
    }

    // ───── ConnectionLimiter ─────

    public boolean isConnectionLimiter() {
        return langConfig.getBoolean("protection.connection-limiter.enabled", true);
    }

    public int getMaxConnectionsPerIP() {
        return langConfig.getInt("protection.connection-limiter.max-connections-per-ip", 3);
    }

    // ───── Auto-Punish ─────

    public boolean isAutoPunishEnabled() {
        return langConfig.getBoolean("auto-punish.enabled", true);
    }

    public int getPunishThresholdWarn() {
        return langConfig.getInt("auto-punish.thresholds.warn", 1);
    }

    public int getPunishThresholdKick() {
        return langConfig.getInt("auto-punish.thresholds.kick", 3);
    }

    public int getPunishThresholdTempban() {
        return langConfig.getInt("auto-punish.thresholds.tempban", 5);
    }

    public int getPunishTempbanDurationHours() {
        return langConfig.getInt("auto-punish.tempban-duration-hours", 24);
    }

    public int getPunishResetAfterDays() {
        return langConfig.getInt("auto-punish.reset-after-days", 30);
    }

    public String getPunishExemptPermission() {
        return langConfig.getString("auto-punish.exempt-permission", "antidumper.punish.exempt");
    }

    // ───── CommandWhitelist ─────

    public boolean isCommandWhitelistEnabled() {
        return langConfig.getBoolean("command-whitelist.enabled", false);
    }

    public String getCommandWhitelistMode() {
        return langConfig.getString("command-whitelist.mode", "STRICT").toUpperCase();
    }

    public List<String> getCommandWhitelistAllowed() {
        return langConfig.getStringList("command-whitelist.allowed-commands");
    }

    public String getCommandWhitelistBypassPermission() {
        return langConfig.getString("command-whitelist.bypass-permission", "antidumper.commandwhitelist.bypass");
    }

    // ───── StaffAlerts ─────

    public boolean isStaffAlertsEnabled() {
        return langConfig.getBoolean("staff-alerts.enabled", true);
    }

    public String getStaffAlertPermission() {
        return langConfig.getString("staff-alerts.alert-permission", "antidumper.alert");
    }

    public boolean isStaffAlertEvent(String event) {
        return langConfig.getBoolean("staff-alerts.events." + event, true);
    }

    public String getStaffAlertFormat() {
        return ColorUtil.translate(langConfig.getString("staff-alerts.format", "§8[§cAntiDumper§8] §e⚡ §f%player% §7→ %action%"));
    }

    // ───── OnlineStats ─────

    public boolean isOnlineStatsEnabled() {
        return langConfig.getBoolean("online-stats.enabled", true);
    }

    public int getOnlineStatsTrackInterval() {
        return langConfig.getInt("online-stats.track-interval-minutes", 5);
    }

    public int getOnlineStatsStoreDays() {
        return langConfig.getInt("online-stats.store-days", 30);
    }

    public void setLangConfigValue(String path, Object value) {
        langConfig.set(path, value);
        saveCurrentLangConfig();
    }

    public boolean toggleModule(String path) {
        boolean current = langConfig.getBoolean(path, true);
        boolean next = !current;
        langConfig.set(path, next);
        saveCurrentLangConfig();
        return next;
    }

    public void saveCurrentLangConfig() {
        try {
            langConfig.save(new File(plugin.getDataFolder(), "config.yml"));
        } catch (Exception ignored) {}
    }

    // ───── AntiHackedClient ─────

    public boolean isAntiHackedClient() {
        return langConfig.getBoolean("protection.anti-hacked-client.enabled", true);
    }

    public String getHCAction() {
        return langConfig.getString("protection.anti-hacked-client.action", "KICK").toUpperCase();
    }

    // ───── Webhook ─────

    public boolean isWebhookEnabled() {
        return langConfig.getBoolean("settings.webhook.enabled", false);
    }

    public String getWebhookUrl() {
        return langConfig.getString("settings.webhook.url", "");
    }

    // ───── GeoIP ─────

    public boolean isGeoIPEnabled() {
        return langConfig.getBoolean("geo-ip.enabled", true);
    }

    public boolean isGeoLogOnJoin() {
        return langConfig.getBoolean("geo-ip.log-on-join", true);
    }

    // ───── GUI ─────

    public boolean isGUIEnabled() {
        return langConfig.getBoolean("gui.enabled", true);
    }

    // ───── Bot Protection ─────

    public boolean isBotCAPTCHAEnabled() {
        return langConfig.getBoolean("protection.captcha.enabled", true);
    }

    public boolean isBehaviourCheckEnabled() {
        return langConfig.getBoolean("protection.behaviour-check.enabled", true);
    }

    public boolean isSiegeModeEnabled() {
        return langConfig.getBoolean("protection.siege-mode.enabled", true);
    }

    public boolean isUUIDWhitelistEnabled() {
        return langConfig.getBoolean("protection.uuid-whitelist.enabled", false);
    }

    // ───── Generic Module Access ─────

    public boolean isModuleEnabled(String path) {
        return langConfig.getBoolean(path + ".enabled", true);
    }

    public String getModuleAction(String path) {
        return langConfig.getString(path + ".action", "KICK").toUpperCase();
    }

    public int getModuleInt(String path, String sub, int def) {
        return langConfig.getInt(path + "." + sub, def);
    }

    public double getModuleDouble(String path, String sub, double def) {
        return langConfig.getDouble(path + "." + sub, def);
    }

    // ───── Phantom: PacketLimiter ─────

    public int getPacketLimiterMaxPPS() {
        return getModuleInt("protection.packet-limiter", "max-packets-per-second", 500);
    }

    // ───── Phantom: AntiBookBan ─────

    public int getBookBanMaxPages() {
        return getModuleInt("protection.anti-book-ban", "max-book-pages", 50);
    }

    public int getBookBanMaxPageLength() {
        return getModuleInt("protection.anti-book-ban", "max-page-length", 200);
    }

    // ───── Phantom: AntiChunkBan ─────

    public int getChunkBanMaxCPS() {
        return getModuleInt("protection.anti-chunk-ban", "max-chunks-per-second", 25);
    }

    // ───── Phantom: AntiLagMachine ─────

    public int getLagMachineMaxEntities() {
        return getModuleInt("protection.anti-lag-machine", "max-entities-per-chunk", 40);
    }

    // ───── Phantom: AntiBlink ─────

    public int getBlinkMaxTimeMs() {
        return getModuleInt("protection.anti-blink", "max-blink-time-ms", 1000);
    }

    // ───── Phantom: AntiTimer ─────

    public int getTimerMaxPPS() {
        return getModuleInt("protection.anti-timer", "max-packets-per-second", 22);
    }

    // ───── Phantom: AntiBoatFly ─────

    public double getBoatFlyMaxSpeed() {
        return getModuleDouble("protection.anti-boat-fly", "max-boat-speed", 0.6);
    }

    // ───── Phantom: AntiElytraFly ─────

    public double getElytraFlyMaxSpeed() {
        return getModuleDouble("protection.anti-elytra-fly", "max-elytra-speed", 2.0);
    }

    // ───── Phantom: AntiAutoPearl ─────

    public int getAutoPearlMaxPerSecond() {
        return getModuleInt("protection.anti-auto-pearl", "max-pearls-per-second", 1);
    }

    // ───── Phantom: AntiTower ─────

    public int getTowerMaxHeight() {
        return getModuleInt("protection.anti-tower", "max-tower-height", 5);
    }

    // ───── Phantom: AntiScaffold ─────

    public int getScaffoldMaxBPS() {
        return getModuleInt("protection.anti-scaffold", "max-blocks-per-second", 8);
    }

    // ───── Phantom: AntiNBT ─────

    public int getNBTMaxSize() {
        return getModuleInt("protection.anti-nbt", "max-nbt-size", 5000);
    }

    // ───── Bot: Captcha ─────

    public int getCaptchaTimeout() {
        return getModuleInt("protection.captcha", "timeout-seconds", 30);
    }

    public boolean isCaptchaKickOnTimeout() {
        return langConfig.getBoolean("protection.captcha.kick-after-timeout", true);
    }

    // ───── Bot: SiegeMode ─────

    public int getSiegeModeThreshold() {
        return getModuleInt("protection.siege-mode", "auto-enable-threshold", 10);
    }

    // ───── Generic Config Access ─────

    public String getString(String path, String def) {
        return langConfig.getString(path, def);
    }

    public int getInt(String path, int def) {
        return langConfig.getInt(path, def);
    }

    public boolean getBoolean(String path, boolean def) {
        return langConfig.getBoolean(path, def);
    }

    public String getServerName() {
        return langConfig.getString("server-name", "server1");
    }

    public void setModuleEnabled(String path, boolean enabled) {
        langConfig.set(path, enabled);
    }

    public void saveConfig() {
        saveCurrentLangConfig();
    }
}
