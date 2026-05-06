package com.sauron.vortexblueprints.model;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public final class CuboidSelection {

    private UUID worldId;
    private String worldName;
    private Location firstCorner;
    private Location secondCorner;

    public void setFirstCorner(Location location) {
        setCorner(location, true);
    }

    public void setSecondCorner(Location location) {
        setCorner(location, false);
    }

    public boolean isComplete() {
        return firstCorner != null && secondCorner != null && sameWorld(firstCorner, secondCorner);
    }

    public UUID getWorldId() {
        return worldId;
    }

    public String getWorldName() {
        return worldName;
    }

    public int minX() {
        return Math.min(firstCorner.getBlockX(), secondCorner.getBlockX());
    }

    public int minY() {
        return Math.min(firstCorner.getBlockY(), secondCorner.getBlockY());
    }

    public int minZ() {
        return Math.min(firstCorner.getBlockZ(), secondCorner.getBlockZ());
    }

    public int maxX() {
        return Math.max(firstCorner.getBlockX(), secondCorner.getBlockX());
    }

    public int maxY() {
        return Math.max(firstCorner.getBlockY(), secondCorner.getBlockY());
    }

    public int maxZ() {
        return Math.max(firstCorner.getBlockZ(), secondCorner.getBlockZ());
    }

    public int width() {
        return maxX() - minX() + 1;
    }

    public int height() {
        return maxY() - minY() + 1;
    }

    public int depth() {
        return maxZ() - minZ() + 1;
    }

    public long volume() {
        return (long) width() * height() * depth();
    }

    private void setCorner(Location location, boolean first) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        if (worldId == null || !worldId.equals(world.getUID())) {
            worldId = world.getUID();
            worldName = world.getName();
            firstCorner = null;
            secondCorner = null;
        }
        Location blockLocation = location.getBlock().getLocation();
        if (first) {
            firstCorner = blockLocation;
        } else {
            secondCorner = blockLocation;
        }
    }

    private boolean sameWorld(Location firstLocation, Location secondLocation) {
        World firstWorld = firstLocation.getWorld();
        World secondWorld = secondLocation.getWorld();
        return firstWorld != null && secondWorld != null && firstWorld.getUID().equals(secondWorld.getUID());
    }
}