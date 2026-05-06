package com.sauron.vortexblueprints.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record CreatorAnalytics(
    long totalViews,
    long totalPurchases,
    long totalBuilds,
    long repeatBuyers,
    double totalRoyalties,
    double totalRevenue,
    double conversionRate,
    int milestoneLevel,
    Map<BlueprintCategory, Long> topCategories,
    Set<CreatorBadge> badges,
    List<String> topBlueprints
) {
}