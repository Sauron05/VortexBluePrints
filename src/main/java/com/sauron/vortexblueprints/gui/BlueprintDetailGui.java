package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.MarketView;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class BlueprintDetailGui extends BlueprintGui {

    public BlueprintDetailGui(VortexBlueprintsPlugin plugin, Player viewer, BlueprintListing listing, int marketPage) {
        this(plugin, viewer, listing, marketPage, MarketView.TRENDING, null);
    }

    public BlueprintDetailGui(VortexBlueprintsPlugin plugin, Player viewer, BlueprintListing listing, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        super(27, MessageUtil.parse("<dark_gray>Blueprint <gray>- <aqua><id>", "id", listing.getId()));
        build(plugin, viewer, listing, marketPage, view, categoryFilter);
    }

    private void build(VortexBlueprintsPlugin plugin, Player viewer, BlueprintListing listing, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        setItem(11, new ItemBuilder(Material.WRITABLE_BOOK)
            .name("<aqua>" + listing.getId())
            .lore(
                "<gray>Creator: <white>" + listing.getOwnerName(),
                "<gray>Category: <white>" + listing.getCategory().getDisplayName(),
                "<gray>License: <white>" + listing.getLicenseType().getDisplayName(),
                "<gray>Style: <white>" + listing.getBuildStyle().getDisplayName(),
                "<gray>Price: <green>" + plugin.getEconomyService().format(listing.getPrice()),
                "<gray>Royalty: <white>" + MessageUtil.number(listing.getCurrentRoyaltyPercent()) + "%",
                "<gray>Size: <white>" + listing.getWidth() + "x" + listing.getHeight() + "x" + listing.getDepth(),
                "<gray>Saved blocks: <white>" + listing.getEntries().size(),
                "<gray>Builds: <white>" + listing.getBuilds(),
                "<gray>Paid to creator: <white>" + plugin.getEconomyService().format(listing.getTotalRoyaltiesPaid()),
                "<gray>Originality: <white>" + MessageUtil.percent(listing.getOriginalityScore()) + "%",
                "<gray>Rating: <white>" + MessageUtil.number(listing.getAverageRating()),
                "<gray>Revision: <white>" + listing.getRevision(),
                "<gray>Status: <white>" + listing.getStatus().name()
            )
            .build());

        setItem(13, new ItemBuilder(Material.EMERALD_BLOCK)
            .name("<green>Build Here")
            .lore("<gray>Paste this blueprint at your feet.", "<yellow>Click to build")
            .build(), event -> {
                viewer.closeInventory();
                plugin.getBuildService().build(viewer, listing.getId());
            });

        setItem(15, new ItemBuilder(Material.SPYGLASS)
            .name("<aqua>Preview Footprint")
            .lore("<gray>Show the build outline before placing it.", "<yellow>Click to preview")
            .build(), event -> {
                viewer.closeInventory();
                plugin.getBuildService().preview(viewer, listing.getId());
            });

        if (listing.isOwner(viewer.getUniqueId()) || plugin.getDataManager().getPurchase(listing.getId(), viewer.getUniqueId()).isPresent()) {
            setItem(19, new ItemBuilder(Material.GLOW_INK_SAC)
                .name("<gold>Rate Blueprint")
                .lore("<gray>Leave a quality, accuracy, and usefulness rating.")
                .build(), event -> new RatingGui(plugin, viewer, listing, marketPage, view, categoryFilter, 3, 3, 3).open(viewer));
        }

        setItem(21, new ItemBuilder(Material.NETHER_STAR)
            .name("<gold>Creator Analytics")
            .lore("<gray>Open your creator dashboard.")
            .build(), event -> new CreatorAnalyticsGui(plugin, viewer).open(viewer));

        if (listing.isOwner(viewer.getUniqueId()) || viewer.hasPermission("vortexblueprints.admin")) {
            setItem(23, new ItemBuilder(Material.NAME_TAG)
                .name("<aqua>Manage Team")
                .lore("<gray>Edit co-owner shares for this blueprint.")
                .build(), event -> new TeamOwnershipGui(plugin, viewer, listing.getId()).open(viewer));
        }

        setItem(22, new ItemBuilder(Material.ARROW)
            .name("<yellow>Back to Market")
            .build(), event -> new MarketplaceGui(plugin, viewer, marketPage, view, categoryFilter).open(viewer));
    }
}