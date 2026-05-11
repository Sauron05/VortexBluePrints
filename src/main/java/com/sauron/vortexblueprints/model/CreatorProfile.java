package com.sauron.vortexblueprints.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CreatorProfile {

    private final UUID creatorId;
    private String creatorName;
    private String headline;
    private String accentColor;
    private String bio;
    private long updatedAt;
    private final Set<String> featuredBlueprintIds;
    private final Set<String> pinnedCollectionIds;

    public CreatorProfile(UUID creatorId, String creatorName) {
        this.creatorId = creatorId;
        this.creatorName = creatorName == null || creatorName.isBlank() ? "Unknown" : creatorName;
        this.headline = "Creator storefront";
        this.accentColor = "#38bdf8";
        this.bio = "";
        this.updatedAt = System.currentTimeMillis();
        this.featuredBlueprintIds = new LinkedHashSet<>();
        this.pinnedCollectionIds = new LinkedHashSet<>();
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public String getHeadline() {
        return headline;
    }

    public String getAccentColor() {
        return accentColor;
    }

    public String getBio() {
        return bio;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public List<String> getFeaturedBlueprintIds() {
        return List.copyOf(featuredBlueprintIds);
    }

    public List<String> getPinnedCollectionIds() {
        return List.copyOf(pinnedCollectionIds);
    }

    public void setCreatorName(String creatorName) {
        if (creatorName == null || creatorName.isBlank()) {
            return;
        }
        this.creatorName = creatorName;
        touch();
    }

    public void setHeadline(String headline) {
        this.headline = sanitizeLine(headline, 42);
        touch();
    }

    public void setAccentColor(String accentColor) {
        if (accentColor == null || accentColor.isBlank()) {
            return;
        }
        this.accentColor = accentColor.trim();
        touch();
    }

    public void setBio(String bio) {
        this.bio = sanitizeLine(bio, 160);
        touch();
    }

    public void setFeaturedBlueprintIds(Collection<String> blueprintIds) {
        featuredBlueprintIds.clear();
        if (blueprintIds != null) {
            for (String blueprintId : blueprintIds) {
                if (featuredBlueprintIds.size() >= 5) {
                    break;
                }
                addNormalized(featuredBlueprintIds, blueprintId);
            }
        }
        touch();
    }

    public void setPinnedCollectionIds(Collection<String> collectionIds) {
        pinnedCollectionIds.clear();
        if (collectionIds != null) {
            for (String collectionId : collectionIds) {
                if (pinnedCollectionIds.size() >= 4) {
                    break;
                }
                addNormalized(pinnedCollectionIds, collectionId);
            }
        }
        touch();
    }

    public boolean featureBlueprint(String blueprintId) {
        String normalized = normalize(blueprintId);
        if (normalized.isBlank() || featuredBlueprintIds.contains(normalized) || featuredBlueprintIds.size() >= 5) {
            return false;
        }
        featuredBlueprintIds.add(normalized);
        touch();
        return true;
    }

    public boolean unfeatureBlueprint(String blueprintId) {
        boolean removed = featuredBlueprintIds.remove(normalize(blueprintId));
        if (removed) {
            touch();
        }
        return removed;
    }

    public boolean pinCollection(String collectionId) {
        String normalized = normalize(collectionId);
        if (normalized.isBlank() || pinnedCollectionIds.contains(normalized) || pinnedCollectionIds.size() >= 4) {
            return false;
        }
        pinnedCollectionIds.add(normalized);
        touch();
        return true;
    }

    public boolean unpinCollection(String collectionId) {
        boolean removed = pinnedCollectionIds.remove(normalize(collectionId));
        if (removed) {
            touch();
        }
        return removed;
    }

    public void loadUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    private void addNormalized(Set<String> values, String raw) {
        String normalized = normalize(raw);
        if (!normalized.isBlank()) {
            values.add(normalized);
        }
    }

    private String sanitizeLine(String raw, int maxLength) {
        if (raw == null) {
            return "";
        }
        String sanitized = raw.trim();
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }
        return sanitized;
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void touch() {
        updatedAt = System.currentTimeMillis();
    }
}