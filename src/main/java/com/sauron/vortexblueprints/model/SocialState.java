package com.sauron.vortexblueprints.model;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class SocialState {

    private final UUID playerId;
    private String playerName;
    private long updatedAt;
    private final Set<String> wishlistBlueprintIds;
    private final Set<UUID> followedCreators;

    public SocialState(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName == null || playerName.isBlank() ? "Unknown" : playerName;
        this.updatedAt = System.currentTimeMillis();
        this.wishlistBlueprintIds = new LinkedHashSet<>();
        this.followedCreators = new LinkedHashSet<>();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public List<String> getWishlistBlueprintIds() {
        return List.copyOf(wishlistBlueprintIds);
    }

    public List<UUID> getFollowedCreators() {
        return List.copyOf(followedCreators);
    }

    public void setPlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        this.playerName = playerName;
        touch();
    }

    public void setWishlistBlueprintIds(Collection<String> blueprintIds) {
        wishlistBlueprintIds.clear();
        if (blueprintIds != null) {
            for (String blueprintId : blueprintIds) {
                addNormalized(wishlistBlueprintIds, blueprintId);
            }
        }
        touch();
    }

    public void setFollowedCreators(Collection<UUID> creatorIds) {
        followedCreators.clear();
        if (creatorIds != null) {
            followedCreators.addAll(creatorIds);
        }
        touch();
    }

    public boolean hasWishlisted(String blueprintId) {
        return wishlistBlueprintIds.contains(normalize(blueprintId));
    }

    public boolean isFollowing(UUID creatorId) {
        return followedCreators.contains(creatorId);
    }

    public boolean toggleWishlist(String blueprintId) {
        String normalized = normalize(blueprintId);
        if (normalized.isBlank()) {
            return false;
        }
        boolean added;
        if (wishlistBlueprintIds.contains(normalized)) {
            wishlistBlueprintIds.remove(normalized);
            added = false;
        } else {
            wishlistBlueprintIds.add(normalized);
            added = true;
        }
        touch();
        return added;
    }

    public boolean removeFromWishlist(String blueprintId) {
        boolean removed = wishlistBlueprintIds.remove(normalize(blueprintId));
        if (removed) {
            touch();
        }
        return removed;
    }

    public boolean toggleFollow(UUID creatorId) {
        if (creatorId == null || creatorId.equals(playerId)) {
            return false;
        }
        boolean following;
        if (followedCreators.contains(creatorId)) {
            followedCreators.remove(creatorId);
            following = false;
        } else {
            followedCreators.add(creatorId);
            following = true;
        }
        touch();
        return following;
    }

    public boolean unfollow(UUID creatorId) {
        boolean removed = followedCreators.remove(creatorId);
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

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void touch() {
        updatedAt = System.currentTimeMillis();
    }
}