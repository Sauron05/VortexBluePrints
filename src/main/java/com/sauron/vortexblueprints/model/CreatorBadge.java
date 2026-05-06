package com.sauron.vortexblueprints.model;

public enum CreatorBadge {
    VERIFIED_BUILDER("Verified Builder"),
    TOP_SELLER("Top Seller"),
    ORIGINAL_CREATOR("Original Creator"),
    FEATURED_CREATOR("Featured Creator"),
    REVIEWED_CREATOR("Reviewed Creator");

    private final String displayName;

    CreatorBadge(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}