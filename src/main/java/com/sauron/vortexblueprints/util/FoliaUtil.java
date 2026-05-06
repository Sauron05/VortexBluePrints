package com.sauron.vortexblueprints.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class FoliaUtil {

    private static final boolean FOLIA = detectFolia();

    private FoliaUtil() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (FOLIA) {
            entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static void runLaterForEntity(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (FOLIA) {
            entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (FOLIA) {
            plugin.getServer().getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static void runGlobal(Plugin plugin, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (FOLIA) {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    public static void runLaterGlobal(Plugin plugin, Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (FOLIA) {
            plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (FOLIA) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}