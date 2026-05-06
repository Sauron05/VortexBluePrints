package com.sauron.vortexblueprints.service;

import com.sauron.vortexblueprints.manager.ConfigManager;
import com.sauron.vortexblueprints.model.BlockEntry;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.OriginalityClassification;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class OriginalityService {

    private final ConfigManager configManager;

    public OriginalityService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Fingerprint fingerprint(List<BlockEntry> entries, int width, int height, int depth) {
        Map<String, Integer> materialCounts = new HashMap<>();
        Map<String, Integer> blockDataCounts = new HashMap<>();
        for (BlockEntry entry : entries) {
            materialCounts.merge(materialKey(entry.blockData()), 1, (left, right) -> left + right);
            blockDataCounts.merge(entry.blockData(), 1, (left, right) -> left + right);
        }
        List<String> variantHashes = variantHashes(entries, width, height, depth);
        return new Fingerprint(
            exactHash(entries, width, height, depth),
            variantHashes.get(0),
            variantHashes,
            materialCounts,
            blockDataCounts,
            width,
            height,
            depth,
            entries.size()
        );
    }

    public OriginalityReport evaluate(
        Fingerprint fingerprint,
        Iterable<BlueprintListing> existingListings,
        UUID ownerId,
        String replacingId
    ) {
        BlueprintListing bestMatch = null;
        double bestSimilarity = 0.0D;
        double bestPartialSimilarity = 0.0D;
        boolean exactDuplicate = false;
        for (BlueprintListing existingListing : existingListings) {
            if (existingListing.getId().equalsIgnoreCase(replacingId) && existingListing.isOwner(ownerId)) {
                continue;
            }
            if (configManager.allowOwnerUpdates() && existingListing.isOwner(ownerId)) {
                continue;
            }
            if (!fingerprint.variantHashes().isEmpty() && intersects(fingerprint.variantHashes(), existingListing.getVariantHashes())) {
                exactDuplicate = true;
                bestMatch = existingListing;
                bestSimilarity = 1.0D;
                bestPartialSimilarity = 1.0D;
                break;
            }
            double similarity = similarity(fingerprint, existingListing);
            double partialSimilarity = partialSimilarity(fingerprint, existingListing);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = existingListing;
            }
            if (partialSimilarity > bestPartialSimilarity) {
                bestPartialSimilarity = partialSimilarity;
            }
        }
        double originalityScore = Math.max(0.0D, 1.0D - bestSimilarity);
        if (exactDuplicate) {
            return new OriginalityReport(false, false, true, Optional.ofNullable(bestMatch), bestSimilarity, bestPartialSimilarity, originalityScore, OriginalityClassification.DUPLICATE);
        }
        if (bestSimilarity >= configManager.similarityThreshold()) {
            return new OriginalityReport(false, true, false, Optional.ofNullable(bestMatch), bestSimilarity, bestPartialSimilarity, originalityScore, OriginalityClassification.SUSPICIOUS);
        }
        if (bestSimilarity >= configManager.reviewThreshold() || bestPartialSimilarity >= configManager.partialCopyThreshold()) {
            return new OriginalityReport(false, true, false, Optional.ofNullable(bestMatch), bestSimilarity, bestPartialSimilarity, originalityScore, OriginalityClassification.SUSPICIOUS);
        }
        if (bestSimilarity >= configManager.derivedThreshold()) {
            return new OriginalityReport(true, false, false, Optional.ofNullable(bestMatch), bestSimilarity, bestPartialSimilarity, originalityScore, OriginalityClassification.DERIVED);
        }
        return new OriginalityReport(true, false, false, Optional.ofNullable(bestMatch), bestSimilarity, bestPartialSimilarity, originalityScore, OriginalityClassification.ORIGINAL);
    }

    private double similarity(Fingerprint fingerprint, BlueprintListing listing) {
        double materialScore = cosine(fingerprint.materialCounts(), listing.getMaterialCounts());
        double blockDataScore = weightedJaccard(fingerprint.blockDataCounts(), listing.getBlockDataCounts());
        double shapeScore = shapeSimilarity(fingerprint, listing);
        double canonicalBonus = fingerprint.canonicalHash().equals(listing.getCanonicalHash()) ? 1.0D : 0.0D;
        return (materialScore * 0.45D) + (blockDataScore * 0.25D) + (shapeScore * 0.10D) + (canonicalBonus * 0.20D);
    }

    private double partialSimilarity(Fingerprint fingerprint, BlueprintListing listing) {
        double material = weightedJaccard(fingerprint.materialCounts(), listing.getMaterialCounts());
        double blockData = weightedJaccard(fingerprint.blockDataCounts(), listing.getBlockDataCounts());
        return (material * 0.60D) + (blockData * 0.40D);
    }

    private double cosine(Map<String, Integer> firstCounts, Map<String, Integer> secondCounts) {
        double dotProduct = 0.0D;
        double firstMagnitude = 0.0D;
        double secondMagnitude = 0.0D;
        for (Map.Entry<String, Integer> entry : firstCounts.entrySet()) {
            int firstValue = entry.getValue();
            int secondValue = secondCounts.getOrDefault(entry.getKey(), 0);
            dotProduct += (double) firstValue * secondValue;
            firstMagnitude += (double) firstValue * firstValue;
        }
        for (int secondValue : secondCounts.values()) {
            secondMagnitude += (double) secondValue * secondValue;
        }
        if (firstMagnitude == 0.0D || secondMagnitude == 0.0D) {
            return 0.0D;
        }
        return dotProduct / (Math.sqrt(firstMagnitude) * Math.sqrt(secondMagnitude));
    }

    private double weightedJaccard(Map<String, Integer> firstCounts, Map<String, Integer> secondCounts) {
        double intersection = 0.0D;
        double union = 0.0D;
        Map<String, Integer> combined = new HashMap<>(firstCounts);
        secondCounts.forEach((blockData, count) -> combined.merge(blockData, count, (left, right) -> Math.max(left, right)));
        for (String blockData : combined.keySet()) {
            int firstValue = firstCounts.getOrDefault(blockData, 0);
            int secondValue = secondCounts.getOrDefault(blockData, 0);
            intersection += Math.min(firstValue, secondValue);
            union += Math.max(firstValue, secondValue);
        }
        return union == 0.0D ? 0.0D : intersection / union;
    }

    private double shapeSimilarity(Fingerprint fingerprint, BlueprintListing listing) {
        double widthRatio = ratio(fingerprint.width(), listing.getWidth());
        double heightRatio = ratio(fingerprint.height(), listing.getHeight());
        double depthRatio = ratio(fingerprint.depth(), listing.getDepth());
        double countRatio = ratio(fingerprint.blockCount(), listing.getEntries().size());
        return (widthRatio + heightRatio + depthRatio + countRatio) / 4.0D;
    }

    private double ratio(int firstValue, int secondValue) {
        int largest = Math.max(firstValue, secondValue);
        if (largest == 0) {
            return 1.0D;
        }
        return (double) Math.min(firstValue, secondValue) / largest;
    }

    private String exactHash(List<BlockEntry> entries, int width, int height, int depth) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((width + "x" + height + "x" + depth + "\n").getBytes(StandardCharsets.UTF_8));
            entries.stream()
                .sorted(Comparator.comparingInt(BlockEntry::relativeX)
                    .thenComparingInt(BlockEntry::relativeY)
                    .thenComparingInt(BlockEntry::relativeZ)
                    .thenComparing(BlockEntry::blockData))
                .map(BlockEntry::serialize)
                .forEach(serialized -> digest.update((serialized + "\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private List<String> variantHashes(List<BlockEntry> entries, int width, int height, int depth) {
        Set<String> hashes = new LinkedHashSet<>();
        for (int rotation = 0; rotation < 4; rotation++) {
            hashes.add(materialVariantHash(entries, width, depth, rotation, false));
            hashes.add(materialVariantHash(entries, width, depth, rotation, true));
        }
        return hashes.stream().sorted().toList();
    }

    private String materialVariantHash(List<BlockEntry> entries, int width, int depth, int rotation, boolean mirror) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            entries.stream()
                .map(entry -> transform(entry, width, depth, rotation, mirror))
                .sorted(Comparator.comparingInt(VariantEntry::x)
                    .thenComparingInt(VariantEntry::y)
                    .thenComparingInt(VariantEntry::z)
                    .thenComparing(VariantEntry::material))
                .forEach(variantEntry -> digest.update((variantEntry.x() + "," + variantEntry.y() + "," + variantEntry.z() + "|" + variantEntry.material() + "\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private VariantEntry transform(BlockEntry entry, int width, int depth, int rotation, boolean mirror) {
        int x = entry.relativeX();
        int z = entry.relativeZ();
        int currentWidth = width;
        int currentDepth = depth;
        for (int turn = 0; turn < rotation; turn++) {
            int rotatedX = currentDepth - 1 - z;
            int rotatedZ = x;
            x = rotatedX;
            z = rotatedZ;
            int previousWidth = currentWidth;
            currentWidth = currentDepth;
            currentDepth = previousWidth;
        }
        if (mirror) {
            x = currentWidth - 1 - x;
        }
        return new VariantEntry(x, entry.relativeY(), z, materialKey(entry.blockData()));
    }

    private boolean intersects(List<String> currentHashes, List<String> existingHashes) {
        Set<String> existing = Set.copyOf(existingHashes);
        for (String currentHash : currentHashes) {
            if (existing.contains(currentHash)) {
                return true;
            }
        }
        return false;
    }

    private String materialKey(String blockData) {
        int stateIndex = blockData.indexOf('[');
        String namespaced = stateIndex >= 0 ? blockData.substring(0, stateIndex) : blockData;
        int namespaceIndex = namespaced.indexOf(':');
        String material = namespaceIndex >= 0 ? namespaced.substring(namespaceIndex + 1) : namespaced;
        return material.toUpperCase(Locale.ROOT);
    }

    public record Fingerprint(
        String exactHash,
        String canonicalHash,
        List<String> variantHashes,
        Map<String, Integer> materialCounts,
        Map<String, Integer> blockDataCounts,
        int width,
        int height,
        int depth,
        int blockCount
    ) {
    }

    public record OriginalityReport(
        boolean allowed,
        boolean reviewRequired,
        boolean exactDuplicate,
        Optional<BlueprintListing> bestMatch,
        double bestSimilarity,
        double partialSimilarity,
        double originalityScore
        , OriginalityClassification classification
    ) {
    }

    private record VariantEntry(int x, int y, int z, String material) {
    }
}