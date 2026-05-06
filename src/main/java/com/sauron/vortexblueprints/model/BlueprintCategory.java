package com.sauron.vortexblueprints.model;

import java.util.Locale;

public enum BlueprintCategory {
    HOUSE("House", "Auto-highlights room count and cozy layouts."),
    SPAWN("Spawn", "Highlights traffic-friendly layouts and landmark scale."),
    FARM("Farm", "Summarises crop volume and harvest footprint."),
    PVP("PvP", "Highlights combat lanes and estimated player flow."),
    REDSTONE("Redstone", "Warns buyers about moving parts and complexity."),
    SHOP("Shop", "Highlights storefront space and merchant capacity."),
    DUNGEON("Dungeon", "Highlights traversal depth and encounter scale."),
    MEDIEVAL("Medieval", "Surfaces palette and silhouette information."),
    SCIFI("Sci-Fi", "Surfaces palette and geometric intensity."),
    DECOR("Decor", "Highlights compactness and placement flexibility."),
    OTHER("Other", "General-purpose blueprint category.");

    private final String displayName;
    private final String perkText;

    BlueprintCategory(String displayName, String perkText) {
        this.displayName = displayName;
        this.perkText = perkText;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPerkText() {
        return perkText;
    }

    public static BlueprintCategory from(String raw) {
        if (raw == null || raw.isBlank()) {
            return OTHER;
        }
        String normalized = raw.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        for (BlueprintCategory category : values()) {
            if (category.name().equals(normalized)) {
                return category;
            }
        }
        return OTHER;
    }
}