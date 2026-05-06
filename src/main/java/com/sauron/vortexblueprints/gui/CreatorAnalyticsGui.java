package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.CreatorAnalytics;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class CreatorAnalyticsGui extends BlueprintGui {

    public CreatorAnalyticsGui(VortexBlueprintsPlugin plugin, Player viewer) {
        super(27, MessageUtil.parse("<dark_gray>Creator Analytics"));
        build(plugin, viewer);
    }

    private void build(VortexBlueprintsPlugin plugin, Player viewer) {
        CreatorAnalytics analytics = plugin.getDataManager().analyticsFor(viewer.getUniqueId());
        setItem(10, new ItemBuilder(Material.EMERALD)
            .name("<green>Revenue")
            .lore(
                "<gray>Total revenue: <white>" + plugin.getEconomyService().format(analytics.totalRevenue()),
                "<gray>Total royalties: <white>" + plugin.getEconomyService().format(analytics.totalRoyalties()),
                "<gray>Milestone level: <white>" + analytics.milestoneLevel()
            )
            .build());
        setItem(12, new ItemBuilder(Material.BOOK)
            .name("<aqua>Engagement")
            .lore(
                "<gray>Views: <white>" + analytics.totalViews(),
                "<gray>Purchases: <white>" + analytics.totalPurchases(),
                "<gray>Builds: <white>" + analytics.totalBuilds(),
                "<gray>Conversion: <white>" + MessageUtil.number(analytics.conversionRate() * 100.0D) + "%"
            )
            .build());
        setItem(14, new ItemBuilder(Material.NETHER_STAR)
            .name("<gold>Badges")
            .lore(
                analytics.badges().isEmpty() ? "<gray>No creator badges yet." : "<gray>" + analytics.badges().stream().map(badge -> badge.getDisplayName()).reduce((first, second) -> first + ", " + second).orElse("None"),
                "<gray>Repeat buyers: <white>" + analytics.repeatBuyers()
            )
            .build());
        setItem(16, new ItemBuilder(Material.COMPASS)
            .name("<yellow>Top Categories")
            .lore(topCategories(analytics.topCategories()))
            .build());
        setItem(22, new ItemBuilder(Material.ARROW)
            .name("<yellow>Back to Market")
            .build(), event -> new MarketplaceGui(plugin, viewer, 0).open(viewer));
    }

    private String[] topCategories(Map<com.sauron.vortexblueprints.model.BlueprintCategory, Long> topCategories) {
        if (topCategories.isEmpty()) {
            return new String[]{"<gray>No published categories yet."};
        }
        return topCategories.entrySet().stream()
            .sorted(Map.Entry.<com.sauron.vortexblueprints.model.BlueprintCategory, Long>comparingByValue().reversed())
            .limit(4)
            .map(entry -> "<gray>" + entry.getKey().getDisplayName() + ": <white>" + entry.getValue())
            .toArray(String[]::new);
    }
}