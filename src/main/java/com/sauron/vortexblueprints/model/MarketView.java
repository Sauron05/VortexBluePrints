package com.sauron.vortexblueprints.model;

import java.util.Locale;

public enum MarketView {
    FEATURED,
    TRENDING,
    NEWEST,
    CHEAPEST,
    PROFITABLE,
    CREATOR_TOP;

    public static MarketView from(String raw) {
        if (raw == null || raw.isBlank()) {
            return TRENDING;
        }
        String normalized = raw.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        for (MarketView view : values()) {
            if (view.name().equals(normalized)) {
                return view;
            }
        }
        return TRENDING;
    }
}