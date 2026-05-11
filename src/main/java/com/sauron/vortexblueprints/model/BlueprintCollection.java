package com.sauron.vortexblueprints.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BlueprintCollection {

    private final String id;
    private final UUID ownerId;
    private String ownerName;
    private String title;
    private String description;
    private boolean featured;
    private long createdAt;
    private long updatedAt;
    private final Set<String> blueprintIds;

    public BlueprintCollection(String id, UUID ownerId, String ownerName, String title, String description, long createdAt) {
        this.id = normalize(id);
        this.ownerId = ownerId;
        this.ownerName = ownerName == null || ownerName.isBlank() ? "Unknown" : ownerName;
        this.title = sanitize(title, 32, this.id);
        this.description = sanitize(description, 160, "");
        this.featured = false;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.blueprintIds = new LinkedHashSet<>();
    }

    public String getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFeatured() {
        return featured;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public List<String> getBlueprintIds() {
        return List.copyOf(blueprintIds);
    }

    public int size() {
        return blueprintIds.size();
    }

    public void setOwnerName(String ownerName) {
        if (ownerName == null || ownerName.isBlank()) {
            return;
        }
        this.ownerName = ownerName;
        touch();
    }

    public void setTitle(String title) {
        this.title = sanitize(title, 32, this.title);
        touch();
    }

    public void setDescription(String description) {
        this.description = sanitize(description, 160, "");
        touch();
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
        touch();
    }

    public void setBlueprintIds(Collection<String> blueprintIds) {
        this.blueprintIds.clear();
        if (blueprintIds != null) {
            for (String blueprintId : blueprintIds) {
                if (this.blueprintIds.size() >= 12) {
                    break;
                }
                addNormalized(blueprintId);
            }
        }
        touch();
    }

    public boolean addBlueprint(String blueprintId) {
        String normalized = normalize(blueprintId);
        if (normalized.isBlank() || blueprintIds.contains(normalized) || blueprintIds.size() >= 12) {
            return false;
        }
        blueprintIds.add(normalized);
        touch();
        return true;
    }

    public boolean removeBlueprint(String blueprintId) {
        boolean removed = blueprintIds.remove(normalize(blueprintId));
        if (removed) {
            touch();
        }
        return removed;
    }

    public void loadTimestamps(long createdAt, long updatedAt) {
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    private void addNormalized(String blueprintId) {
        String normalized = normalize(blueprintId);
        if (!normalized.isBlank()) {
            blueprintIds.add(normalized);
        }
    }

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String sanitize(String raw, int maxLength, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String sanitized = raw.trim();
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
        }
        return sanitized;
    }

    private void touch() {
        updatedAt = System.currentTimeMillis();
    }
}