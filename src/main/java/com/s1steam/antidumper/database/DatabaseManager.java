package com.s1steam.antidumper.database;

import com.s1steam.antidumper.AntiDumperPlugin;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class DatabaseManager {

    private final AntiDumperPlugin plugin;
    private HikariDataSource dataSource;
    private String type;

    public DatabaseManager(AntiDumperPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enable() {
        type = plugin.getConfigManager().getString("database.type", "h2").toLowerCase();
        String host = plugin.getConfigManager().getString("database.host", "localhost");
        int port = plugin.getConfigManager().getInt("database.port", 3306);
        String dbName = plugin.getConfigManager().getString("database.database", "antidumper");
        String user = plugin.getConfigManager().getString("database.user", "root");
        String pass = plugin.getConfigManager().getString("database.password", "");

        try {
            HikariConfig config = new HikariConfig();
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(5000);
            config.setIdleTimeout(300000);
            config.setMaxLifetime(600000);

            switch (type) {
                case "mysql":
                    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                    config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&characterEncoding=utf8");
                    config.setUsername(user);
                    config.setPassword(pass);
                    break;
                case "sqlite":
                    config.setDriverClassName("org.sqlite.JDBC");
                    File sqliteFile = new File(plugin.getDataFolder(), "data.db");
                    config.setJdbcUrl("jdbc:sqlite:" + sqliteFile.getAbsolutePath());
                    config.setConnectionTestQuery("SELECT 1");
                    break;
                default:
                    config.setDriverClassName("org.h2.Driver");
                    File h2File = new File(plugin.getDataFolder(), "data");
                    config.setJdbcUrl("jdbc:h2:" + h2File.getAbsolutePath() + ";MODE=MySQL");
                    config.setConnectionTestQuery("SELECT 1");
                    break;
            }

            dataSource = new HikariDataSource(config);
            createTables();
            plugin.getLogger().info("Database connected: " + type);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect database: " + e.getMessage());
            return false;
        }
    }

    public void disable() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void createTables() {
        String violationsTable = "CREATE TABLE IF NOT EXISTS violations (" +
                "id INTEGER PRIMARY KEY " + (type.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT") + "," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(16) NOT NULL," +
                "module VARCHAR(64) NOT NULL," +
                "server VARCHAR(64) DEFAULT ''," +
                "timestamp BIGINT NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                (type.equals("mysql") ? ") ENGINE=InnoDB" : "") + ")";

        String punishmentsTable = "CREATE TABLE IF NOT EXISTS punishments (" +
                "id INTEGER PRIMARY KEY " + (type.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT") + "," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(16) NOT NULL," +
                "action VARCHAR(32) NOT NULL," +
                "reason VARCHAR(256) DEFAULT ''," +
                "server VARCHAR(64) DEFAULT ''," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                (type.equals("mysql") ? ") ENGINE=InnoDB" : "") + ")";

        String statsTable = "CREATE TABLE IF NOT EXISTS stats (" +
                "id INTEGER PRIMARY KEY " + (type.equals("mysql") ? "AUTO_INCREMENT" : "AUTOINCREMENT") + "," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(16) NOT NULL," +
                "violations_total INTEGER DEFAULT 0," +
                "last_seen BIGINT," +
                "UNIQUE(player_uuid)" +
                (type.equals("mysql") ? ") ENGINE=InnoDB" : "") + ")";

        try (Connection conn = getConnection()) {
            conn.createStatement().execute(violationsTable);
            conn.createStatement().execute(punishmentsTable);
            conn.createStatement().execute(statsTable);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create tables: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database not connected");
        }
        return dataSource.getConnection();
    }

    public void logViolation(UUID playerUuid, String playerName, String module) {
        async(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO violations (player_uuid, player_name, module, server, timestamp) VALUES (?,?,?,?,?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, module);
                ps.setString(4, plugin.getConfigManager().getServerName());
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to log violation: " + e.getMessage());
            }
        });
        updateStats(playerUuid, playerName);
    }

    public void logPunishment(UUID playerUuid, String playerName, String action, String reason) {
        async(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO punishments (player_uuid, player_name, action, reason, server) VALUES (?,?,?,?,?)")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, action);
                ps.setString(4, reason);
                ps.setString(5, plugin.getConfigManager().getServerName());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to log punishment: " + e.getMessage());
            }
        });
    }

    private void updateStats(UUID playerUuid, String playerName) {
        async(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO stats (player_uuid, player_name, violations_total, last_seen) VALUES (?,?,1,?) " +
                                 "ON CONFLICT(player_uuid) DO UPDATE SET " +
                                 "violations_total = violations_total + 1, last_seen = ?, player_name = ?")) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setLong(3, System.currentTimeMillis());
                ps.setLong(4, System.currentTimeMillis());
                ps.setString(5, playerName);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to update stats: " + e.getMessage());
            }
        });
    }

    public int getViolationsTotal(UUID playerUuid) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT violations_total FROM stats WHERE player_uuid=?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get violations: " + e.getMessage());
        }
        return 0;
    }

    public String getType() { return type; }
    public boolean isConnected() { return dataSource != null && !dataSource.isClosed(); }

    private void async(Runnable task) {
        plugin.getScheduler().async(task);
    }
}
