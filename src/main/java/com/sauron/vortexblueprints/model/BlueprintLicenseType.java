package com.sauron.vortexblueprints.model;

import java.util.Locale;

public enum BlueprintLicenseType {
    SINGLE_USE("Single Use", 1, false),
    PACK_FIVE("5-Use Pack", 5, false),
    PACK_TWENTY("20-Use Pack", 20, false),
    SHOWCASE("Showcase Pack", 3, false),
    EXCLUSIVE("Exclusive License", 1, true);

    private final String displayName;
    private final int usesPerPurchase;
    private final boolean exclusive;

    BlueprintLicenseType(String displayName, int usesPerPurchase, boolean exclusive) {
        this.displayName = displayName;
        this.usesPerPurchase = usesPerPurchase;
        this.exclusive = exclusive;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getUsesPerPurchase() {
        return usesPerPurchase;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public static BlueprintLicenseType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return SINGLE_USE;
        }
        String normalized = raw.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        for (BlueprintLicenseType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return SINGLE_USE;
    }
}