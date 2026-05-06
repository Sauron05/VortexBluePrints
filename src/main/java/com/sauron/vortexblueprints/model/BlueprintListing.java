package com.sauron.vortexblueprints.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BlueprintListing {

    private final String id;
    private final UUID ownerId;
    private final String ownerName;
    private final int width;
    private final int height;
    private final int depth;
    private final List<BlockEntry> entries;
    private final Map<String, Integer> materialCounts;
    private final Map<String, Integer> blockDataCounts;
    private final String exactHash;
    private final String canonicalHash;
    private final List<String> variantHashes;
    private final long createdAt;
    private long updatedAt;
    private double price;
    private double baseRoyaltyPercent;
    private double originalityScore;
    private long views;
    private long purchases;
    private long builds;
    private long repeatBuyers;
    private double totalRoyaltiesPaid;
    private double totalRevenue;
    private boolean featured;
    private boolean staffPick;
    private boolean revisionLocked;
    private int milestoneLevel;
    private int revision;
    private double upgradeDiscountPercent;
    private String teamKey;
    private String parentBlueprintId;
    private String derivedFromBlueprintId;
    private String bestMatchBlueprintId;
    private String bestMatchOwnerName;
    private String description;
    private BlueprintCategory category;
    private BlueprintLicenseType licenseType;
    private BuildStyle buildStyle;
    private BlueprintStatus status;
    private OriginalityClassification originalityClassification;
    private final List<OwnerShare> ownerShares;
    private final List<RoyaltyTier> royaltyTiers;
    private final List<TimelineEntry> timeline;
    private final Set<CreatorBadge> badges;
    private final Set<String> tags;
    private final RatingSummary ratingSummary;

    public BlueprintListing(
        String id,
        UUID ownerId,
        String ownerName,
        int width,
        int height,
        int depth,
        List<BlockEntry> entries,
        Map<String, Integer> materialCounts,
        Map<String, Integer> blockDataCounts,
        String exactHash,
        String canonicalHash,
        List<String> variantHashes,
        long createdAt
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.entries = List.copyOf(entries);
        this.materialCounts = Collections.unmodifiableMap(new HashMap<>(materialCounts));
        this.blockDataCounts = Collections.unmodifiableMap(new HashMap<>(blockDataCounts));
        this.exactHash = exactHash;
        this.canonicalHash = canonicalHash;
        this.variantHashes = List.copyOf(variantHashes);
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.price = 0.0D;
        this.baseRoyaltyPercent = 90.0D;
        this.originalityScore = 1.0D;
        this.featured = false;
        this.staffPick = false;
        this.revisionLocked = false;
        this.milestoneLevel = 0;
        this.revision = 1;
        this.upgradeDiscountPercent = 0.0D;
        this.teamKey = "";
        this.parentBlueprintId = "";
        this.derivedFromBlueprintId = "";
        this.bestMatchBlueprintId = "";
        this.bestMatchOwnerName = "";
        this.description = "";
        this.category = BlueprintCategory.OTHER;
        this.licenseType = BlueprintLicenseType.SINGLE_USE;
        this.buildStyle = BuildStyle.ANIMATED;
        this.status = BlueprintStatus.LIVE;
        this.originalityClassification = OriginalityClassification.ORIGINAL;
        this.ownerShares = new ArrayList<>();
        this.royaltyTiers = new ArrayList<>();
        this.timeline = new ArrayList<>();
        this.badges = EnumSet.noneOf(CreatorBadge.class);
        this.tags = new LinkedHashSet<>();
        this.ratingSummary = new RatingSummary();
        this.ownerShares.add(new OwnerShare(ownerId, ownerName, 100.0D));
        this.royaltyTiers.add(new RoyaltyTier(0, 90.0D));
        addTimeline("published", ownerName, "Blueprint created.");
    }

    public BlueprintListing(
        String id,
        UUID ownerId,
        String ownerName,
        int width,
        int height,
        int depth,
        List<BlockEntry> entries,
        Map<String, Integer> materialCounts,
        Map<String, Integer> blockDataCounts,
        String exactHash,
        long createdAt,
        long updatedAt,
        double price,
        double royaltyPercent,
        double originalityScore,
        long builds,
        double totalRoyaltiesPaid
    ) {
        this(id, ownerId, ownerName, width, height, depth, entries, materialCounts, blockDataCounts, exactHash, exactHash, List.of(exactHash), createdAt);
        loadUpdatedAt(updatedAt);
        setPrice(price);
        setBaseRoyaltyPercent(royaltyPercent);
        setOriginality(originalityScore, OriginalityClassification.ORIGINAL, "", "");
        loadStats(0L, 0L, builds, 0L, totalRoyaltiesPaid, 0.0D);
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

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDepth() {
        return depth;
    }

    public int getVolume() {
        return width * height * depth;
    }

    public List<BlockEntry> getEntries() {
        return entries;
    }

    public Map<String, Integer> getMaterialCounts() {
        return materialCounts;
    }

    public Map<String, Integer> getBlockDataCounts() {
        return blockDataCounts;
    }

    public String getExactHash() {
        return exactHash;
    }

    public String getCanonicalHash() {
        return canonicalHash;
    }

    public List<String> getVariantHashes() {
        return variantHashes;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public double getPrice() {
        return price;
    }

    public double getBaseRoyaltyPercent() {
        return baseRoyaltyPercent;
    }

    public double getOriginalityScore() {
        return originalityScore;
    }

    public long getViews() {
        return views;
    }

    public long getPurchases() {
        return purchases;
    }

    public long getBuilds() {
        return builds;
    }

    public long getRepeatBuyers() {
        return repeatBuyers;
    }

    public double getTotalRoyaltiesPaid() {
        return totalRoyaltiesPaid;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public boolean isFeatured() {
        return featured;
    }

    public boolean isStaffPick() {
        return staffPick;
    }

    public boolean isRevisionLocked() {
        return revisionLocked;
    }

    public int getMilestoneLevel() {
        return milestoneLevel;
    }

    public int getRevision() {
        return revision;
    }

    public double getUpgradeDiscountPercent() {
        return upgradeDiscountPercent;
    }

    public String getTeamKey() {
        return teamKey;
    }

    public String getParentBlueprintId() {
        return parentBlueprintId;
    }

    public String getDerivedFromBlueprintId() {
        return derivedFromBlueprintId;
    }

    public String getBestMatchBlueprintId() {
        return bestMatchBlueprintId;
    }

    public String getBestMatchOwnerName() {
        return bestMatchOwnerName;
    }

    public String getDescription() {
        return description;
    }

    public BlueprintCategory getCategory() {
        return category;
    }

    public BlueprintLicenseType getLicenseType() {
        return licenseType;
    }

    public BuildStyle getBuildStyle() {
        return buildStyle;
    }

    public BlueprintStatus getStatus() {
        return status;
    }

    public OriginalityClassification getOriginalityClassification() {
        return originalityClassification;
    }

    public List<OwnerShare> getOwnerShares() {
        return List.copyOf(ownerShares);
    }

    public List<RoyaltyTier> getRoyaltyTiers() {
        return List.copyOf(royaltyTiers);
    }

    public List<TimelineEntry> getTimeline() {
        return List.copyOf(timeline);
    }

    public Set<CreatorBadge> getBadges() {
        return Collections.unmodifiableSet(badges);
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public RatingSummary getRatingSummary() {
        return ratingSummary;
    }

    public boolean isOwner(UUID uuid) {
        for (OwnerShare ownerShare : ownerShares) {
            if (ownerShare.uuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSales() {
        return purchases > 0 || builds > 0;
    }

    public double getAverageRating() {
        return ratingSummary.getAverageOverall();
    }

    public double getConversionRate() {
        return views <= 0 ? 0.0D : (double) purchases / views;
    }

    public double getCurrentRoyaltyPercent() {
        double royaltyPercent = baseRoyaltyPercent;
        for (RoyaltyTier royaltyTier : royaltyTiers) {
            if (builds >= royaltyTier.minBuilds()) {
                royaltyPercent = royaltyTier.royaltyPercent();
            }
        }
        return Math.min(100.0D, royaltyPercent + (milestoneLevel * 2.0D));
    }

    public double getRoyaltyPercent() {
        return getCurrentRoyaltyPercent();
    }

    public void setPrice(double price) {
        this.price = price;
        touch("pricing", ownerName, "Price updated to " + price + ".");
    }

    public void setBaseRoyaltyPercent(double baseRoyaltyPercent) {
        this.baseRoyaltyPercent = baseRoyaltyPercent;
    }

    public void setOriginality(double originalityScore, OriginalityClassification classification, String bestMatchBlueprintId, String bestMatchOwnerName) {
        this.originalityScore = originalityScore;
        this.originalityClassification = classification;
        this.bestMatchBlueprintId = bestMatchBlueprintId == null ? "" : bestMatchBlueprintId;
        this.bestMatchOwnerName = bestMatchOwnerName == null ? "" : bestMatchOwnerName;
    }

    public void setCategory(BlueprintCategory category) {
        this.category = category == null ? BlueprintCategory.OTHER : category;
    }

    public void setLicenseType(BlueprintLicenseType licenseType) {
        this.licenseType = licenseType == null ? BlueprintLicenseType.SINGLE_USE : licenseType;
    }

    public void setBuildStyle(BuildStyle buildStyle) {
        this.buildStyle = buildStyle == null ? BuildStyle.ANIMATED : buildStyle;
    }

    public void setStatus(BlueprintStatus status) {
        this.status = status == null ? BlueprintStatus.LIVE : status;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public void setStaffPick(boolean staffPick) {
        this.staffPick = staffPick;
    }

    public void setRevisionLocked(boolean revisionLocked) {
        this.revisionLocked = revisionLocked;
    }

    public void setMilestoneLevel(int milestoneLevel) {
        this.milestoneLevel = Math.max(0, milestoneLevel);
    }

    public void setRevision(int revision) {
        this.revision = Math.max(1, revision);
    }

    public void setUpgradeChain(String parentBlueprintId, double upgradeDiscountPercent) {
        this.parentBlueprintId = parentBlueprintId == null ? "" : parentBlueprintId;
        this.upgradeDiscountPercent = Math.max(0.0D, upgradeDiscountPercent);
    }

    public void setTeamKey(String teamKey) {
        this.teamKey = teamKey == null ? "" : teamKey;
    }

    public void setDerivedFromBlueprintId(String derivedFromBlueprintId) {
        this.derivedFromBlueprintId = derivedFromBlueprintId == null ? "" : derivedFromBlueprintId;
    }

    public void setOwnerShares(Collection<OwnerShare> ownerShares) {
        this.ownerShares.clear();
        if (ownerShares == null || ownerShares.isEmpty()) {
            this.ownerShares.add(new OwnerShare(ownerId, ownerName, 100.0D));
            return;
        }
        this.ownerShares.addAll(ownerShares);
    }

    public void setRoyaltyTiers(Collection<RoyaltyTier> royaltyTiers) {
        this.royaltyTiers.clear();
        if (royaltyTiers == null || royaltyTiers.isEmpty()) {
            this.royaltyTiers.add(new RoyaltyTier(0, baseRoyaltyPercent));
            return;
        }
        this.royaltyTiers.addAll(royaltyTiers);
        this.royaltyTiers.sort(java.util.Comparator.comparingInt(RoyaltyTier::minBuilds));
    }

    public void setTags(Collection<String> tags) {
        this.tags.clear();
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && !tag.isBlank()) {
                    this.tags.add(tag.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
    }

    public void setBadges(Collection<CreatorBadge> badges) {
        this.badges.clear();
        if (badges != null) {
            this.badges.addAll(badges);
        }
    }

    public void setTimeline(Collection<TimelineEntry> timelineEntries) {
        this.timeline.clear();
        if (timelineEntries != null) {
            this.timeline.addAll(timelineEntries);
        }
    }

    public void loadStats(long views, long purchases, long builds, long repeatBuyers, double totalRoyaltiesPaid, double totalRevenue) {
        this.views = views;
        this.purchases = purchases;
        this.builds = builds;
        this.repeatBuyers = repeatBuyers;
        this.totalRoyaltiesPaid = totalRoyaltiesPaid;
        this.totalRevenue = totalRevenue;
    }

    public void loadUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void recordView() {
        views += 1;
        touch(null, null, null);
    }

    public void recordPurchase(String buyerName, boolean repeatBuyer, double purchaseValue) {
        purchases += 1;
        if (repeatBuyer) {
            repeatBuyers += 1;
        }
        totalRevenue += Math.max(0.0D, purchaseValue);
        touch("purchase", buyerName, "Purchased a license.");
        refreshBadges();
    }

    public void recordBuild(double royaltyPaid, String builderName) {
        builds += 1;
        totalRoyaltiesPaid += Math.max(0.0D, royaltyPaid);
        touch("build", builderName, "Built the blueprint.");
        refreshBadges();
    }

    public void recordBuild(double royaltyPaid) {
        recordBuild(royaltyPaid, "system");
    }

    public void recordRating(UUID reviewerId, int quality, int accuracy, int usefulness, String reviewerName) {
        ratingSummary.rate(reviewerId, quality, accuracy, usefulness);
        touch("rating", reviewerName, "Left a review.");
        refreshBadges();
    }

    public void addTimeline(String type, String actor, String summary) {
        timeline.add(new TimelineEntry(System.currentTimeMillis(), type, actor == null ? "system" : actor, summary == null ? "" : summary));
        this.updatedAt = System.currentTimeMillis();
    }

    public void refreshBadges() {
        badges.clear();
        if (getAverageRating() >= 4.5D && ratingSummary.getCount() >= 3) {
            badges.add(CreatorBadge.VERIFIED_BUILDER);
        }
        if (purchases >= 10 || totalRevenue >= 10000.0D) {
            badges.add(CreatorBadge.TOP_SELLER);
        }
        if (originalityClassification == OriginalityClassification.ORIGINAL && originalityScore >= 0.85D) {
            badges.add(CreatorBadge.ORIGINAL_CREATOR);
        }
        if (featured || staffPick) {
            badges.add(CreatorBadge.FEATURED_CREATOR);
        }
        if (status == BlueprintStatus.LIVE && originalityClassification != OriginalityClassification.SUSPICIOUS) {
            badges.add(CreatorBadge.REVIEWED_CREATOR);
        }
    }

    private void touch(String type, String actor, String summary) {
        updatedAt = System.currentTimeMillis();
        if (type != null && summary != null) {
            timeline.add(new TimelineEntry(updatedAt, type, actor == null ? "system" : actor, summary));
        }
    }
}