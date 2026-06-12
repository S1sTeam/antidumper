package com.s1steam.antidumper.geo;

import com.s1steam.antidumper.AntiDumperPlugin;
import org.bukkit.entity.Player;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GeoIPManager {
    private final AntiDumperPlugin plugin;
    private final File cacheFile;
    private final Map<String, GeoEntry> cache = new ConcurrentHashMap<>();
    private final Map<UUID, GeoEntry> playerGeo = new ConcurrentHashMap<>();

    public GeoIPManager(AntiDumperPlugin plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "geo-cache.json");
        loadCache();
    }

    @SuppressWarnings("unchecked")
    private void loadCache() {
        if (!cacheFile.exists()) return;
        try {
            String json = new String(Files.readAllBytes(cacheFile.toPath()), StandardCharsets.UTF_8);
            com.google.gson.Gson gson = new com.google.gson.Gson();
            Map<String, Map<String, String>> raw = gson.fromJson(json, Map.class);
            if (raw != null) {
                for (Map.Entry<String, Map<String, String>> e : raw.entrySet()) {
                    Map<String, String> v = e.getValue();
                    cache.put(e.getKey(), new GeoEntry(v.get("country"), v.get("city"), v.get("isp")));
                }
            }
        } catch (Exception ignored) {}
    }

    private void saveCache() {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            String json = gson.toJson(cache);
            Files.write(cacheFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    public void lookup(Player player) {
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        GeoEntry cached = cache.get(ip);
        if (cached != null) {
            playerGeo.put(player.getUniqueId(), cached);
            return;
        }
        plugin.getScheduler().async(() -> {
            try {
                String json = fetch("http://ip-api.com/json/" + ip + "?fields=country,city,isp,query");
                if (json == null) return;
                com.google.gson.JsonObject obj = new com.google.gson.Gson().fromJson(json, com.google.gson.JsonObject.class);
                if (obj == null || obj.has("status") && "fail".equals(obj.get("status").getAsString())) return;

                String country = obj.has("country") ? obj.get("country").getAsString() : "?";
                String city = obj.has("city") ? obj.get("city").getAsString() : "?";
                String isp = obj.has("isp") ? obj.get("isp").getAsString() : "?";

                GeoEntry entry = new GeoEntry(country, city, isp);
                cache.put(ip, entry);
                playerGeo.put(player.getUniqueId(), entry);
                saveCache();
            } catch (Exception ignored) {}
        });
    }

    private String fetch(String urlStr) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public GeoEntry getGeo(UUID uuid) {
        return playerGeo.get(uuid);
    }

    public GeoEntry getGeoByIP(String ip) {
        return cache.get(ip);
    }

    public static class GeoEntry {
        private final String country, city, isp;
        public GeoEntry(String country, String city, String isp) {
            this.country = country;
            this.city = city;
            this.isp = isp;
        }
        public String getCountry() { return country; }
        public String getCity() { return city; }
        public String getIsp() { return isp; }
        public String getDisplay() {
            StringBuilder sb = new StringBuilder();
            if (country != null && !"?".equals(country)) sb.append(country);
            if (city != null && !"?".equals(city)) sb.append(", ").append(city);
            return sb.toString();
        }
    }
}
