package com.s1steam.antidumper.platform;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SchedulerAdapter {

    private final Plugin plugin;

    public SchedulerAdapter(Plugin plugin) {
        this.plugin = plugin;
    }

    public void sync(Runnable task) {
        if (Platform.isFolia()) {
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void syncLater(Runnable task, long delayTicks) {
        if (Platform.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public BukkitTask syncTimer(Runnable task, long delayTicks, long periodTicks) {
        if (Platform.isFolia()) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), delayTicks, periodTicks);
            return null;
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    public void async(Runnable task) {
        if (Platform.isFolia()) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public void asyncLater(Runnable task, long delayTicks) {
        if (Platform.isFolia()) {
            long millis = delayTicks * 50L;
            Bukkit.getAsyncScheduler().runDelayed(plugin, t -> task.run(), millis, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public Object asyncTimer(Runnable task, long delayTicks, long periodTicks) {
        if (Platform.isFolia()) {
            long delay = delayTicks * 50L;
            long period = periodTicks * 50L;
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(), delay, period, TimeUnit.MILLISECONDS);
            return null;
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }

    public void cancelTask(Object task) {
        if (task instanceof BukkitTask) {
            ((BukkitTask) task).cancel();
        }
    }

    public void syncAtLocation(Location location, Runnable task) {
        if (Platform.isFolia()) {
            Bukkit.getRegionScheduler().run(plugin, location, t -> task.run());
        } else {
            task.run();
        }
    }

    public void syncForPlayer(Player player, Runnable task) {
        if (Platform.isFolia()) {
            player.getScheduler().run(plugin, t -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public void runKickTask(Player player, Runnable kick) {
        if (Platform.isFolia()) {
            player.getScheduler().run(plugin, t -> kick.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, kick);
        }
    }
}
