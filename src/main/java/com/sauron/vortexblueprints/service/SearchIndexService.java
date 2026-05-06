package com.sauron.vortexblueprints.service;

import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.BlueprintStatus;
import com.sauron.vortexblueprints.model.MarketView;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class SearchIndexService {

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(new Snapshot(List.of(), Map.of()));

    public void rebuild(Iterable<BlueprintListing> listings) {
        List<BlueprintListing> liveListings = java.util.stream.StreamSupport.stream(listings.spliterator(), false)
            .filter(listing -> listing.getStatus() == BlueprintStatus.LIVE)
            .toList();
        snapshot.set(new Snapshot(
            liveListings,
            liveListings.stream().collect(Collectors.groupingBy(BlueprintListing::getCategory))
        ));
    }

    public List<BlueprintListing> search(MarketQuery query) {
        Snapshot currentSnapshot = snapshot.get();
        List<BlueprintListing> base = query.category() == null
            ? currentSnapshot.liveListings()
            : currentSnapshot.byCategory().getOrDefault(query.category(), List.of());
        return base.stream()
            .filter(listing -> matchesText(listing, query.text()))
            .filter(listing -> query.minPrice() < 0.0D || listing.getPrice() >= query.minPrice())
            .filter(listing -> query.maxPrice() < 0.0D || listing.getPrice() <= query.maxPrice())
            .sorted(comparator(query.view()))
            .toList();
    }

    private boolean matchesText(BlueprintListing listing, String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        if (listing.getId().toLowerCase(java.util.Locale.ROOT).contains(lower)) {
            return true;
        }
        if (listing.getOwnerName().toLowerCase(java.util.Locale.ROOT).contains(lower)) {
            return true;
        }
        if (listing.getDescription().toLowerCase(java.util.Locale.ROOT).contains(lower)) {
            return true;
        }
        for (String tag : listing.getTags()) {
            if (tag.contains(lower)) {
                return true;
            }
        }
        return false;
    }

    private Comparator<BlueprintListing> comparator(MarketView view) {
        MarketView safeView = view == null ? MarketView.TRENDING : view;
        return switch (safeView) {
            case FEATURED -> Comparator.comparing(BlueprintListing::isFeatured).reversed()
                .thenComparing(BlueprintListing::isStaffPick).reversed()
                .thenComparingLong(BlueprintListing::getViews).reversed();
            case NEWEST -> Comparator.comparingLong(BlueprintListing::getCreatedAt).reversed();
            case CHEAPEST -> Comparator.comparingDouble(BlueprintListing::getPrice)
                .thenComparingLong(BlueprintListing::getBuilds).reversed();
            case PROFITABLE -> Comparator.comparingDouble(BlueprintListing::getTotalRevenue).reversed()
                .thenComparingDouble(BlueprintListing::getTotalRoyaltiesPaid).reversed();
            case CREATOR_TOP -> Comparator.comparingDouble(BlueprintListing::getAverageRating).reversed()
                .thenComparingDouble(BlueprintListing::getConversionRate).reversed();
            case TRENDING -> Comparator.comparingLong(BlueprintListing::getBuilds).reversed()
                .thenComparingLong(BlueprintListing::getPurchases).reversed()
                .thenComparingLong(BlueprintListing::getViews).reversed();
        };
    }

    private record Snapshot(List<BlueprintListing> liveListings, Map<BlueprintCategory, List<BlueprintListing>> byCategory) {
    }
}