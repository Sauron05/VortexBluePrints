package com.sauron.vortexblueprints.service;

import com.sauron.vortexblueprints.api.event.BlueprintPublishEvent;
import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.manager.ConfigManager;
import com.sauron.vortexblueprints.manager.DataManager;
import com.sauron.vortexblueprints.model.BlockEntry;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintLicenseType;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.BlueprintStatus;
import com.sauron.vortexblueprints.model.BuildStyle;
import com.sauron.vortexblueprints.model.LedgerEntry;
import com.sauron.vortexblueprints.model.ReviewTicket;
import com.sauron.vortexblueprints.model.RoyaltyTier;
import com.sauron.vortexblueprints.model.CuboidSelection;
import com.sauron.vortexblueprints.service.OriginalityService.Fingerprint;
import com.sauron.vortexblueprints.service.OriginalityService.OriginalityReport;
import com.sauron.vortexblueprints.util.FoliaUtil;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class BlueprintCaptureService {

    private final VortexBlueprintsPlugin plugin;
    private final ConfigManager configManager;
    private final DataManager dataManager;
    private final OriginalityService originalityService;

    public BlueprintCaptureService(
        VortexBlueprintsPlugin plugin,
        ConfigManager configManager,
        DataManager dataManager,
        OriginalityService originalityService
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataManager = dataManager;
        this.originalityService = originalityService;
    }

    public void captureAndPublish(Player player, String id, double price, double royaltyPercent) {
        captureAndPublish(player, id, price, royaltyPercent, configManager.defaultCategory(), configManager.defaultLicenseType(), configManager.defaultBuildStyle(), "");
    }

    public void captureAndPublish(
        Player player,
        String id,
        double price,
        double royaltyPercent,
        BlueprintCategory category,
        BlueprintLicenseType licenseType,
        BuildStyle buildStyle,
        String description
    ) {
        Optional<CuboidSelection> selectionOptional = plugin.getSelectionManager().getSelection(player.getUniqueId());
        if (selectionOptional.isEmpty()) {
            MessageUtil.send(player, configManager.message("selection-missing"));
            return;
        }
        CuboidSelection selection = selectionOptional.get();
        if (selection.volume() > configManager.maxVolume()) {
            MessageUtil.send(player, configManager.message("selection-too-large"),
                "volume", String.valueOf(selection.volume()),
                "limit", String.valueOf(configManager.maxVolume()));
            return;
        }
        Optional<BlueprintListing> existingListing = dataManager.getBlueprint(id);
        if (existingListing.isPresent() && !existingListing.get().isOwner(player.getUniqueId())) {
            MessageUtil.send(player, configManager.message("save-exists"), "id", id);
            return;
        }
        if (existingListing.isPresent() && !configManager.allowOwnerUpdates()) {
            MessageUtil.send(player, configManager.message("save-exists"), "id", id);
            return;
        }

        World world = Bukkit.getWorld(selection.getWorldId());
        if (world == null) {
            MessageUtil.send(player, "<prefix><red>The selected world is no longer loaded.");
            return;
        }

        MessageUtil.send(player, configManager.message("save-started"));
        if (FoliaUtil.isFolia()) {
            captureFolia(player, selection, world, id, price, royaltyPercent, category, licenseType, buildStyle, description, existingListing.orElse(null));
            return;
        }
        FoliaUtil.runGlobal(plugin, () -> {
            List<BlockEntry> entries = captureSync(selection, world);
            finishCapture(player, selection, id, price, royaltyPercent, category, licenseType, buildStyle, description, existingListing.orElse(null), entries);
        });
    }

    private List<BlockEntry> captureSync(CuboidSelection selection, World world) {
        List<BlockEntry> entries = new ArrayList<>();
        for (int blockX = selection.minX(); blockX <= selection.maxX(); blockX++) {
            for (int blockY = selection.minY(); blockY <= selection.maxY(); blockY++) {
                for (int blockZ = selection.minZ(); blockZ <= selection.maxZ(); blockZ++) {
                    Block block = world.getBlockAt(blockX, blockY, blockZ);
                    if (!configManager.storeAir() && block.getType().isAir()) {
                        continue;
                    }
                    entries.add(new BlockEntry(
                        blockX - selection.minX(),
                        blockY - selection.minY(),
                        blockZ - selection.minZ(),
                        block.getBlockData().getAsString()
                    ));
                }
            }
        }
        return entries;
    }

    private void captureFolia(
        Player player,
        CuboidSelection selection,
        World world,
        String id,
        double price,
        double royaltyPercent,
        BlueprintCategory category,
        BlueprintLicenseType licenseType,
        BuildStyle buildStyle,
        String description,
        BlueprintListing existingListing
    ) {
        List<BlockEntry> entries = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger remainingBlocks = new AtomicInteger((int) selection.volume());
        for (int blockX = selection.minX(); blockX <= selection.maxX(); blockX++) {
            for (int blockY = selection.minY(); blockY <= selection.maxY(); blockY++) {
                for (int blockZ = selection.minZ(); blockZ <= selection.maxZ(); blockZ++) {
                    int capturedX = blockX;
                    int capturedY = blockY;
                    int capturedZ = blockZ;
                    Location blockLocation = new Location(world, capturedX, capturedY, capturedZ);
                    FoliaUtil.runAtLocation(plugin, blockLocation, () -> {
                        Block block = world.getBlockAt(capturedX, capturedY, capturedZ);
                        if (configManager.storeAir() || !block.getType().isAir()) {
                            entries.add(new BlockEntry(
                                capturedX - selection.minX(),
                                capturedY - selection.minY(),
                                capturedZ - selection.minZ(),
                                block.getBlockData().getAsString()
                            ));
                        }
                        if (remainingBlocks.decrementAndGet() == 0) {
                            finishCapture(player, selection, id, price, royaltyPercent, category, licenseType, buildStyle, description, existingListing, entries);
                        }
                    });
                }
            }
        }
    }

    private void finishCapture(
        Player player,
        CuboidSelection selection,
        String id,
        double price,
        double royaltyPercent,
        BlueprintCategory category,
        BlueprintLicenseType licenseType,
        BuildStyle buildStyle,
        String description,
        BlueprintListing existingListing,
        List<BlockEntry> entries
    ) {
        if (entries.isEmpty()) {
            FoliaUtil.runForEntity(plugin, player, () -> MessageUtil.send(player, "<prefix><red>Your selection did not contain any saved blocks."));
            return;
        }
        Fingerprint fingerprint = originalityService.fingerprint(entries, selection.width(), selection.height(), selection.depth());
        OriginalityReport report = originalityService.evaluate(fingerprint, dataManager.getBlueprints(), player.getUniqueId(), id);
        if (!report.allowed() && !report.reviewRequired()) {
            BlueprintListing match = report.bestMatch().orElse(null);
            FoliaUtil.runForEntity(plugin, player, () -> MessageUtil.send(player, configManager.message("save-duplicate"),
                "other", match == null ? "unknown" : match.getId(),
                "owner", match == null ? "unknown" : match.getOwnerName(),
                "score", MessageUtil.percent(report.bestSimilarity())));
            return;
        }

        if (existingListing != null && existingListing.hasSales()) {
            Fingerprint existingFingerprint = originalityService.fingerprint(existingListing.getEntries(), existingListing.getWidth(), existingListing.getHeight(), existingListing.getDepth());
            OriginalityReport existingComparison = originalityService.evaluate(fingerprint, List.of(existingListing), player.getUniqueId(), existingListing.getId());
            if (!fingerprint.canonicalHash().equals(existingFingerprint.canonicalHash()) && existingComparison.bestSimilarity() < configManager.revisionLockThreshold()) {
                FoliaUtil.runForEntity(plugin, player, () -> MessageUtil.send(player, configManager.message("save-revision-locked")));
                return;
            }
        }

        long now = System.currentTimeMillis();
        BlueprintListing listing = new BlueprintListing(
            id,
            player.getUniqueId(),
            player.getName(),
            selection.width(),
            selection.height(),
            selection.depth(),
            entries,
            fingerprint.materialCounts(),
            fingerprint.blockDataCounts(),
            fingerprint.exactHash(),
            fingerprint.canonicalHash(),
            fingerprint.variantHashes(),
            existingListing == null ? now : existingListing.getCreatedAt()
        );
        listing.loadUpdatedAt(now);
        listing.setPrice(price);
        listing.setBaseRoyaltyPercent(royaltyPercent);
        listing.setOriginality(report.originalityScore(), report.classification(), report.bestMatch().map(BlueprintListing::getId).orElse(""), report.bestMatch().map(BlueprintListing::getOwnerName).orElse(""));
        listing.setCategory(existingListing == null ? category : existingListing.getCategory());
        listing.setLicenseType(existingListing == null ? licenseType : existingListing.getLicenseType());
        listing.setBuildStyle(existingListing == null ? buildStyle : existingListing.getBuildStyle());
        listing.setDescription(existingListing == null ? (description == null || description.isBlank() ? categoryDescription(listing.getCategory(), selection.width(), selection.height(), selection.depth()) : description) : existingListing.getDescription());
        listing.setRoyaltyTiers(List.of(
            new RoyaltyTier(0, royaltyPercent),
            new RoyaltyTier(25, Math.max(25.0D, royaltyPercent - 20.0D))
        ));
        listing.setOwnerShares(existingListing == null ? List.of() : existingListing.getOwnerShares());
        listing.setTeamKey(existingListing == null ? "" : existingListing.getTeamKey());
        listing.setTags(existingListing == null ? List.of(listing.getCategory().name().toLowerCase(java.util.Locale.ROOT)) : existingListing.getTags());
        listing.setMilestoneLevel(existingListing == null ? 0 : existingListing.getMilestoneLevel());
        listing.setRevision(existingListing == null ? 1 : existingListing.getRevision() + 1);
        listing.setUpgradeChain(existingListing == null ? "" : existingListing.getParentBlueprintId(), existingListing == null ? 0.0D : existingListing.getUpgradeDiscountPercent());
        listing.setDerivedFromBlueprintId(report.classification() == com.sauron.vortexblueprints.model.OriginalityClassification.DERIVED ? report.bestMatch().map(BlueprintListing::getId).orElse("") : "");
        if (report.reviewRequired()) {
            listing.setStatus(BlueprintStatus.PENDING_REVIEW);
        }
        if (existingListing != null) {
            listing.loadStats(existingListing.getViews(), existingListing.getPurchases(), existingListing.getBuilds(), existingListing.getRepeatBuyers(), existingListing.getTotalRoyaltiesPaid(), existingListing.getTotalRevenue());
        }
        BlueprintPublishEvent publishEvent = new BlueprintPublishEvent(player, listing);
        Bukkit.getPluginManager().callEvent(publishEvent);
        if (publishEvent.isCancelled()) {
            FoliaUtil.runForEntity(plugin, player, () -> MessageUtil.send(player, "<prefix><red>Another plugin cancelled this blueprint publication."));
            return;
        }
        dataManager.putBlueprint(listing);
        dataManager.appendLedgerAsync(new LedgerEntry(now, now, "publish", listing.getId(), player.getUniqueId(), player.getName(), listing.getPrice(), "classification=" + report.classification().name()));
        if (report.reviewRequired()) {
            ReviewTicket ticket = new ReviewTicket(UUID.randomUUID().toString(), listing.getId(), player.getUniqueId(), player.getName(), "Originality score entered the review band.", report.classification(), report.bestSimilarity(), now);
            dataManager.saveReviewTicketAsync(ticket);
            plugin.getExternalBridgeService().notifyReview(ticket);
            FoliaUtil.runForEntity(plugin, player, () -> MessageUtil.send(player, configManager.message("save-review"),
                "id", listing.getId(),
                "score", MessageUtil.percent(report.bestSimilarity())));
            return;
        }
        FoliaUtil.runForEntity(plugin, player, () -> MessageUtil.send(player, configManager.message("save-complete"),
            "id", listing.getId(),
            "price", plugin.getEconomyService().format(price),
            "score", MessageUtil.percent(report.originalityScore())));
    }

    private String categoryDescription(BlueprintCategory category, int width, int height, int depth) {
        return switch (category) {
            case FARM -> "Farm blueprint with a " + width + "x" + depth + " footprint and " + height + " blocks of vertical space.";
            case PVP -> "PvP-ready arena footprint sized at " + width + "x" + depth + ".";
            case REDSTONE -> "Redstone blueprint with " + width + "x" + depth + " footprint; inspect moving parts before purchase.";
            case HOUSE -> "Residential blueprint with a " + width + "x" + depth + " footprint.";
            default -> category.getPerkText();
        };
    }
}