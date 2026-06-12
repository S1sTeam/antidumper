package com.s1steam.antidumper.utils;

import com.s1steam.antidumper.AntiDumperPlugin;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class WebhookManager {
    private final AntiDumperPlugin plugin;

    public WebhookManager(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        String url = plugin.getConfigManager().getWebhookUrl();
        return plugin.getConfigManager().isWebhookEnabled()
            && url != null && !url.isEmpty();
    }

    public void send(String title, String description, int color) {
        if (!isEnabled()) return;
        String url = plugin.getConfigManager().getWebhookUrl();

        plugin.getScheduler().async(() -> {
            try {
                String json = String.format(
                    "{\"embeds\":[{\"title\":\"%s\",\"description\":\"```\\n%s\\n```\",\"color\":%d,\"timestamp\":\"%s\"}]}",
                    escape(title), escape(description), color, java.time.Instant.now()
                );

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code < 200 || code > 299) {
                    plugin.getLogger().warning("Webhook returned HTTP " + code);
                }
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Webhook failed: " + e.getMessage());
            }
        });
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
