package com.s1steam.antidumper.platform;

import org.bukkit.Bukkit;

public final class Platform {

    private static final boolean FOLIA;
    private static final boolean PAPER;
    private static final boolean BUNGEE;
    private static final boolean VELOCITY;

    static {
        boolean folia = false, paper = false, bungee = false, velocity = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {}
        try {
            Class.forName("io.papermc.paper.ServerBuildInfo");
            paper = true;
        } catch (ClassNotFoundException ignored) {}
        try {
            Class.forName("net.md_5.bungee.api.ProxyServer");
            bungee = true;
        } catch (ClassNotFoundException ignored) {}
        try {
            Class.forName("com.velocitypowered.api.proxy.ProxyServer");
            velocity = true;
        } catch (ClassNotFoundException ignored) {}
        FOLIA = folia;
        PAPER = paper || folia;
        BUNGEE = bungee;
        VELOCITY = velocity;
    }

    public static boolean isFolia() { return FOLIA; }
    public static boolean isPaper() { return PAPER; }
    public static boolean isBungee() { return BUNGEE; }
    public static boolean isVelocity() { return VELOCITY; }
    public static boolean isProxy() { return BUNGEE || VELOCITY; }
    public static boolean isBukkit() { return !isProxy(); }

    public static String getName() {
        if (isVelocity()) return "Velocity";
        if (isBungee()) return "BungeeCord";
        if (isFolia()) return "Folia";
        if (isPaper()) return "Paper";
        String name = Bukkit.getServer().getName();
        return name != null ? name : "Bukkit";
    }

    public static String getVersion() {
        return Bukkit.getServer().getVersion();
    }

    private Platform() {}
}
