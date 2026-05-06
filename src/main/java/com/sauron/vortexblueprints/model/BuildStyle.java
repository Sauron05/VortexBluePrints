package com.sauron.vortexblueprints.model;

import java.util.Locale;

public enum BuildStyle {
    INSTANT("Instant"),
    ANIMATED("Animated"),
    DRONE("Builder Drone"),
    CRATE("Crate Unpacking");

    private final String displayName;

    BuildStyle(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static BuildStyle from(String raw) {
        if (raw == null || raw.isBlank()) {
            return ANIMATED;
        }
        String normalized = raw.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        for (BuildStyle style : values()) {
            if (style.name().equals(normalized)) {
                return style;
            }
        }
        return ANIMATED;
    }
}