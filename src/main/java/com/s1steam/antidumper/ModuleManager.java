package com.s1steam.antidumper;

import com.s1steam.antidumper.protection.ProtectionModule;
import com.s1steam.antidumper.protection.*;
import com.s1steam.antidumper.protection.bot.*;
import com.s1steam.antidumper.protection.phantom.*;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ModuleManager {

    private final AntiDumperPlugin plugin;
    private final Map<String, ProtectionModule> loaded = new LinkedHashMap<>();
    private final Set<String> disabledOverrides = new HashSet<>();
    private File overridesFile;

    public ModuleManager(AntiDumperPlugin plugin) {
        this.plugin = plugin;
        this.overridesFile = new File(plugin.getDataFolder(), "module-overrides.yml");
        loadOverrides();
    }

    public Map<String, ProtectionModule> getLoaded() {
        return loaded;
    }

    public boolean isHotDisabled(String name) {
        return disabledOverrides.contains(name);
    }

    public ProtectionModule getModule(String name) {
        return loaded.get(name);
    }

    public void loadModules() {
        loaded.clear();
        register("AntiWorldDownloader", new AntiWorldDownloader(plugin));
        register("AntiPluginStealer", new AntiPluginStealer(plugin));
        register("AntiCommandDump", new AntiCommandDump(plugin));
        register("AntiFileSteal", new AntiFileSteal(plugin));
        register("AntiCrash", new AntiCrash(plugin));
        register("ConnectionLimiter", new ConnectionLimiter(plugin));
        if (plugin.getConfigManager().isCommandWhitelistEnabled()) {
            CommandWhitelist cw = new CommandWhitelist(plugin);
            register("CommandWhitelist", cw);
            plugin.setCommandWhitelist(cw);
        }
        register("AntiHackedClient", new AntiHackedClient(plugin));
        register("BotCAPTCHA", new BotCAPTCHA(plugin));
        register("BehaviourCheck", new BehaviourCheck(plugin));
        register("SiegeMode", new SiegeMode(plugin));
        register("UUIDWhitelist", new UUIDWhitelist(plugin));
        register("PacketLimiter", new PacketLimiter(plugin));
        register("AntiBookBan", new AntiBookBan(plugin));
        register("AntiChunkBan", new AntiChunkBan(plugin));
        register("AntiLagMachine", new AntiLagMachine(plugin));
        register("AntiPhase", new AntiPhase(plugin));
        register("AntiBlink", new AntiBlink(plugin));
        register("AntiTimer", new AntiTimer(plugin));
        register("AntiNoFall", new AntiNoFall(plugin));
        register("AntiBoatFly", new AntiBoatFly(plugin));
        register("AntiElytraFly", new AntiElytraFly(plugin));
        register("AntiAutoPearl", new AntiAutoPearl(plugin));
        register("AntiInventoryMove", new AntiInventoryMove(plugin));
        register("AntiDisabler", new AntiDisabler(plugin));
        register("AntiTower", new AntiTower(plugin));
        register("AntiScaffold", new AntiScaffold(plugin));
        register("AntiNBT", new AntiNBT(plugin));
        register("AntiCommandExploit", new AntiCommandExploit(plugin));
        if (plugin.getConfigManager().isModuleEnabled("protection.packet-inspector")) {
            register("PacketInspector", new PacketInspector(plugin));
        }
    }

    private void register(String name, ProtectionModule module) {
        loaded.put(name, module);
    }

    public void enableModules() {
        for (ProtectionModule module : loaded.values()) {
            try {
                if (!disabledOverrides.contains(module.getName())) {
                    module.enable();
                }
            } catch (Exception e) {
                plugin.getLogger().warning("  x " + module.getName() + " - " + e.getMessage());
            }
        }
    }

    public void disableModules() {
        for (ProtectionModule module : loaded.values()) {
            try { module.disable(); } catch (Exception ignored) {}
        }
    }

    public void disableAll() {
        disabledOverrides.addAll(loaded.keySet());
        for (ProtectionModule m : loaded.values()) {
            try { m.disable(); } catch (Exception ignored) {}
        }
        saveOverrides();
    }

    public boolean hotToggle(String moduleName) {
        ProtectionModule module = loaded.get(moduleName);
        if (module == null) return false;

        if (disabledOverrides.contains(moduleName)) {
            disabledOverrides.remove(moduleName);
            try { module.enable(); } catch (Exception ignored) {}
        } else {
            disabledOverrides.add(moduleName);
            try { module.disable(); } catch (Exception ignored) {}
        }
        saveOverrides();
        return true;
    }

    public boolean isActive(String moduleName) {
        ProtectionModule module = loaded.get(moduleName);
        if (module == null) return false;
        return !disabledOverrides.contains(moduleName);
    }

    public List<String> getActiveModules() {
        List<String> active = new ArrayList<>();
        for (String name : loaded.keySet()) {
            if (!disabledOverrides.contains(name)) active.add(name);
        }
        return active;
    }

    public List<String> getDisabledModules() {
        List<String> disabled = new ArrayList<>();
        for (String name : loaded.keySet()) {
            if (disabledOverrides.contains(name)) disabled.add(name);
        }
        return disabled;
    }

    private void loadOverrides() {
        if (!overridesFile.exists()) return;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(overridesFile);
            disabledOverrides.addAll(cfg.getStringList("disabled"));
        } catch (Exception ignored) {}
    }

    private void saveOverrides() {
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("disabled", new ArrayList<>(disabledOverrides));
            cfg.save(overridesFile);
        } catch (Exception ignored) {}
    }
}
