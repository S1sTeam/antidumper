package com.s1steam.antidumper.gui;

import com.s1steam.antidumper.AntiDumperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class MainGUI implements Listener {
    private final AntiDumperPlugin plugin;
    private final Map<UUID, Integer> playerPages = new HashMap<>();

    private static final int PAGE_CORE = 0;
    private static final int PAGE_PHANTOM = 1;

    private static final int[] BORDER = {
        0,1,2,3,5,6,7,8,
        9, 17,
        18, 26,
        27, 35,
        36, 44,
        45,46,47,48,50,51,52,53
    };
    private static final int SEP_4 = 4;
    private static final int SEP_49 = 49;
    private static final int NAV_PREV = 48;
    private static final int NAV_NEXT = 50;

    private static final int[] CORE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final String[] CORE_KEYS = {"wdl", "stealer", "cmdump", "filesteal", "crash", "connlimit", "cmdwhitelist"};
    private static final String[] CORE_PATHS = {
        "protection.anti-world-downloader.enabled",
        "protection.anti-plugin-stealer.enabled",
        "protection.anti-command-dump.enabled",
        "protection.anti-file-steal.enabled",
        "protection.anti-crash.enabled",
        "protection.connection-limiter.enabled",
        "command-whitelist.enabled"
    };

    private static final int HC_SLOT = 25;
    private static final int[] BOT_SLOTS = {20, 21, 23, 24};
    private static final String[] BOT_KEYS = {"captcha", "behaviour", "siege", "uuidwhitelist"};
    private static final String[] BOT_PATHS = {
        "protection.captcha.enabled",
        "protection.behaviour-check.enabled",
        "protection.siege-mode.enabled",
        "protection.uuid-whitelist.enabled"
    };

    private static final int[] PHANTOM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30
    };
    private static final String[] PHANTOM_KEYS = {
        "packetlimiter", "bookban", "chunkban", "lagmachine", "phase",
        "blink", "timer", "nofall", "boatfly", "elytrafly",
        "autopearl", "inventorymove", "disabler", "tower", "scaffold",
        "nbt", "commandexploit"
    };
    private static final String[] PHANTOM_PATHS = {
        "protection.packet-limiter.enabled",
        "protection.anti-book-ban.enabled",
        "protection.anti-chunk-ban.enabled",
        "protection.anti-lag-machine.enabled",
        "protection.anti-phase.enabled",
        "protection.anti-blink.enabled",
        "protection.anti-timer.enabled",
        "protection.anti-no-fall.enabled",
        "protection.anti-boat-fly.enabled",
        "protection.anti-elytra-fly.enabled",
        "protection.anti-auto-pearl.enabled",
        "protection.anti-inventory-move.enabled",
        "protection.anti-disabler.enabled",
        "protection.anti-tower.enabled",
        "protection.anti-scaffold.enabled",
        "protection.anti-nbt.enabled",
        "protection.anti-command-exploit.enabled"
    };

    private static final int LANG_RU = 28;
    private static final int LANG_EN = 29;
    private static final int GEOIP_SLOT = 30;
    private static final int INFO_SLOT = 22;
    private static final int PHANTOM_INFO_SLOT = 31;
    private static final int STATS_SLOT = 37;
    private static final int CHECK_SLOT = 38;
    private static final int RELOAD_SLOT = 39;
    private static final int CLOSE_SLOT = 40;

    public MainGUI(AntiDumperPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        openPage(player, PAGE_CORE);
    }

    private void openPage(Player player, int page) {
        playerPages.put(player.getUniqueId(), page);
        
        Inventory inv = Bukkit.createInventory(null, 54, plugin.getConfigManager().get("gui.title"));

        for (int s : BORDER) inv.setItem(s, pane("BLACK", 15, " "));
        inv.setItem(0, pane("RED", 14, " "));
        inv.setItem(8, pane("RED", 14, " "));
        inv.setItem(45, pane("RED", 14, " "));
        inv.setItem(53, pane("RED", 14, " "));

        if (page == PAGE_CORE) {
            buildCorePage(inv, player);
        } else {
            buildPhantomPage(inv);
        }

        player.openInventory(inv);
    }

    private void buildCorePage(Inventory inv, Player player) {
        for (int i = 0; i < CORE_SLOTS.length; i++) {
            String k = CORE_KEYS[i];
            boolean en = plugin.getConfigManager().isModuleEnabled(
                CORE_PATHS[i].substring(0, CORE_PATHS[i].length() - 8));
            if ("connlimit".equals(k)) {
                inv.setItem(CORE_SLOTS[i], mod("connlimit", en,
                    "%limit%", String.valueOf(plugin.getConfigManager().getMaxConnectionsPerIP())));
            } else if ("cmdwhitelist".equals(k)) {
                inv.setItem(CORE_SLOTS[i], mod("cmdwhitelist", en,
                    "%mode%", plugin.getConfigManager().getCommandWhitelistMode()));
            } else {
                inv.setItem(CORE_SLOTS[i], mod(k, en));
            }
        }

        // HackedClient at slot 25
        inv.setItem(HC_SLOT, mod("hackedclient", plugin.getConfigManager().isAntiHackedClient()));

        // Bot modules on row 2 (around INFO at 22)
        boolean captchaEn = plugin.getConfigManager().isModuleEnabled("protection.captcha");
        boolean bhvEn = plugin.getConfigManager().isModuleEnabled("protection.behaviour-check");
        boolean siegeEn = plugin.getConfigManager().isModuleEnabled("protection.siege-mode");
        boolean uuidEn = plugin.getConfigManager().isModuleEnabled("protection.uuid-whitelist");

        inv.setItem(BOT_SLOTS[0], mod("captcha", captchaEn));
        inv.setItem(BOT_SLOTS[1], mod("behaviour", bhvEn));
        inv.setItem(BOT_SLOTS[2], mod("siege", siegeEn,
            "%threshold%", String.valueOf(plugin.getConfigManager().getSiegeModeThreshold())));
        inv.setItem(BOT_SLOTS[3], mod("uuidwhitelist", uuidEn));

        String lng = plugin.getConfigManager().getCurrentLang();
        inv.setItem(LANG_RU, langItem("ru", lng));
        inv.setItem(LANG_EN, langItem("en", lng));
        inv.setItem(GEOIP_SLOT, toggle("geoip", plugin.getConfigManager().isGeoIPEnabled()));

        int totalV = plugin.getStats().getTotalViolations();
        String langDisplay = "ru".equals(lng) ? "Русский" : "English";
        inv.setItem(INFO_SLOT, makeItem(Material.BOOK, plugin.getConfigManager().get("gui.info.name"),
            apply(plugin.getConfigManager().get("gui.info.lore"), "%lang%", langDisplay, "%violations%", String.valueOf(totalV))));

        buildActions(inv, totalV);

        inv.setItem(NAV_NEXT, makeItem(Material.ARROW, plugin.getConfigManager().get("gui.next")));
        inv.setItem(SEP_4, pane("GRAY", 7, "§8⚔ AntiDumper v" + plugin.getDescription().getVersion()));
        inv.setItem(SEP_49, pane("GRAY", 7, "§8S1sTeam © 2026"));
    }

    private void buildPhantomPage(Inventory inv) {
        for (int i = 0; i < PHANTOM_SLOTS.length; i++) {
            String stripPath = PHANTOM_PATHS[i].endsWith(".enabled") ?
                PHANTOM_PATHS[i].substring(0, PHANTOM_PATHS[i].length() - 8) : PHANTOM_PATHS[i];
            boolean en = plugin.getConfigManager().isModuleEnabled(stripPath);
            inv.setItem(PHANTOM_SLOTS[i], mod(PHANTOM_KEYS[i], en));
        }

        int totalV = plugin.getStats().getTotalViolations();
        inv.setItem(PHANTOM_INFO_SLOT, makeItem(Material.BOOK, "§6Phantom §fModules",
            "§7" + countPhantomActive() + "/17 active",
            "§8Page 2/2"));

        buildActions(inv, totalV);

        inv.setItem(NAV_PREV, makeItem(Material.ARROW, plugin.getConfigManager().get("gui.prev")));
        inv.setItem(SEP_4, pane("GRAY", 7, "§8⚔ AntiDumper v" + plugin.getDescription().getVersion() + " §7[Phantom]"));
        inv.setItem(SEP_49, pane("GRAY", 7, "§8S1sTeam © 2026"));
    }

    private void buildActions(Inventory inv, int totalV) {
        inv.setItem(STATS_SLOT, makeItem(Material.PAPER, plugin.getConfigManager().get("gui.stats.name"),
            apply(plugin.getConfigManager().get("gui.stats.lore"),
                "%violations%", String.valueOf(totalV),
                "%modules%", String.valueOf(countActive()),
                "%version%", plugin.getDescription().getVersion())));
        inv.setItem(CHECK_SLOT, makeItem(Material.COMPASS, plugin.getConfigManager().get("gui.check.name"),
            plugin.getConfigManager().get("gui.check.lore")));
        inv.setItem(RELOAD_SLOT, makeItem(Material.ANVIL, plugin.getConfigManager().get("gui.reload.name"),
            plugin.getConfigManager().get("gui.reload.lore")));
        inv.setItem(CLOSE_SLOT, makeItem(Material.BARRIER, plugin.getConfigManager().get("gui.close.name")));
    }

    private ItemStack mod(String key, boolean enabled, String... replacements) {
        
        String name = plugin.getConfigManager().get("gui.modules." + key + ".name");
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfigManager().getStringList("gui.modules." + key + ".lore")) {
            lore.add(apply(line, replacements));
        }
        lore.add(enabled ? plugin.getConfigManager().get("gui.on") : plugin.getConfigManager().get("gui.off"));
        lore.add(plugin.getConfigManager().get("gui.click"));
        Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        return makeItem(mat, name, lore.toArray(new String[0]));
    }

    private ItemStack toggle(String key, boolean enabled, String... replacements) {
        
        String name = plugin.getConfigManager().get("gui." + key + ".name");
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfigManager().getStringList("gui." + key + ".lore")) {
            lore.add(apply(line, replacements));
        }
        lore.add(enabled ? plugin.getConfigManager().get("gui.on") : plugin.getConfigManager().get("gui.off"));
        lore.add(plugin.getConfigManager().get("gui.click"));
        Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        return makeItem(mat, name, lore.toArray(new String[0]));
    }

    private ItemStack langItem(String code, String current) {
        
        boolean active = code.equals(current);
        String status = active ? plugin.getConfigManager().get("gui.lang-on") : plugin.getConfigManager().get("gui.lang-off");
        Material mat = active ? Material.WRITABLE_BOOK : Material.BOOK;
        return makeItem(mat, plugin.getConfigManager().get("gui.lang-" + code), status);
    }

    private ItemStack pane(String color, int legacyData, String name) {
        Material mat = paneMat(color);
        ItemStack item = new ItemStack(mat, 1, (short) legacyData);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private Material paneMat(String color) {
        try {
            return Material.valueOf(color + "_STAINED_GLASS_PANE");
        } catch (Exception e) {
            return Material.valueOf("STAINED_GLASS_PANE");
        }
    }

    private ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String apply(String input, String... rep) {
        if (input == null || rep.length == 0) return input;
        String r = input;
        for (int i = 0; i < rep.length; i += 2)
            if (i + 1 < rep.length) r = r.replace(rep[i], rep[i + 1]);
        return r;
    }

    private String[] apply(String[] lines, String... rep) {
        String[] r = new String[lines.length];
        for (int i = 0; i < lines.length; i++) r[i] = apply(lines[i], rep);
        return r;
    }

    private int countActive() {
        int c = 0;
        if (plugin.getConfigManager().isAntiWorldDownloader()) c++;
        if (plugin.getConfigManager().isAntiPluginStealer()) c++;
        if (plugin.getConfigManager().isAntiCommandDump()) c++;
        if (plugin.getConfigManager().isAntiFileSteal()) c++;
        if (plugin.getConfigManager().isAntiCrash()) c++;
        if (plugin.getConfigManager().isConnectionLimiter()) c++;
        if (plugin.getConfigManager().isCommandWhitelistEnabled()) c++;
        if (plugin.getConfigManager().isAntiHackedClient()) c++;
        if (plugin.getConfigManager().isBotCAPTCHAEnabled()) c++;
        if (plugin.getConfigManager().isBehaviourCheckEnabled()) c++;
        if (plugin.getConfigManager().isSiegeModeEnabled()) c++;
        if (plugin.getConfigManager().isUUIDWhitelistEnabled()) c++;
        c += countPhantomActive();
        return c;
    }

    private int countPhantomActive() {
        int c = 0;
        for (String p : PHANTOM_PATHS) {
            String sp = p.endsWith(".enabled") ? p.substring(0, p.length() - 8) : p;
            if (plugin.getConfigManager().isModuleEnabled(sp)) c++;
        }
        return c;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        
        if (!e.getView().getTitle().equals(plugin.getConfigManager().get("gui.title"))) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        int page = playerPages.getOrDefault(p.getUniqueId(), PAGE_CORE);

        if (slot == CLOSE_SLOT || slot == 0 || slot == 8 || slot == 45 || slot == 53) {
            p.closeInventory();
            return;
        }

        if (slot == NAV_NEXT && page == PAGE_CORE) {
            openPage(p, PAGE_PHANTOM);
            return;
        }
        if (slot == NAV_PREV && page == PAGE_PHANTOM) {
            openPage(p, PAGE_CORE);
            return;
        }

        if (slot == LANG_RU) {
            plugin.getConfigManager().setLang("ru");
            p.sendMessage(plugin.getConfigManager().get("gui.lang-changed-ru"));
            plugin.getScheduler().syncLater(() -> openPage(p, page), 1L);
            return;
        }

        if (slot == LANG_EN) {
            plugin.getConfigManager().setLang("en");
            p.sendMessage(plugin.getConfigManager().get("gui.lang-changed-en"));
            plugin.getScheduler().syncLater(() -> openPage(p, page), 1L);
            return;
        }

        if (slot == RELOAD_SLOT) {
            plugin.reload();
            p.sendMessage(plugin.getConfigManager().getPrefixed("commands.reload"));
            plugin.getScheduler().syncLater(() -> openPage(p, page), 1L);
            return;
        }

        if (slot == CHECK_SLOT) {
            p.closeInventory();
            p.sendMessage(plugin.getConfigManager().get("gui.check-hint"));
            return;
        }

        if (slot == STATS_SLOT) {
            p.sendMessage(plugin.getStats().getFormattedStats());
            return;
        }

        if (slot == GEOIP_SLOT) {
            plugin.getConfigManager().toggleModule("geo-ip.enabled");
            plugin.getScheduler().syncLater(() -> openPage(p, page), 1L);
            return;
        }

        if (page == PAGE_CORE) {
            for (int i = 0; i < CORE_SLOTS.length; i++) {
                if (slot == CORE_SLOTS[i]) {
                    plugin.getConfigManager().toggleModule(CORE_PATHS[i]);
                    plugin.getScheduler().syncLater(() -> openPage(p, page), 1L);
                    return;
                }
            }
            if (slot == HC_SLOT) {
                plugin.getConfigManager().toggleModule("protection.anti-hacked-client.enabled");
                plugin.getScheduler().syncLater(() -> openPage(p, page), 1L);
                return;
            }
            for (int i = 0; i < BOT_SLOTS.length; i++) {
                if (slot == BOT_SLOTS[i]) {
                    plugin.getConfigManager().toggleModule(BOT_PATHS[i]);
                    plugin.getScheduler().syncLater(() -> openPage(p, page), 1L);
                    return;
                }
            }
        } else {
            for (int i = 0; i < PHANTOM_SLOTS.length; i++) {
                if (slot == PHANTOM_SLOTS[i]) {
                    plugin.getConfigManager().toggleModule(PHANTOM_PATHS[i]);
                    plugin.getScheduler().syncLater(() -> openPage(p, page), 1L);
                    return;
                }
            }
        }
    }
}
