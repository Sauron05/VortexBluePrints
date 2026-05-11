package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintCollection;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.MarketView;
import com.sauron.vortexblueprints.model.SocialState;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class BlueprintDetailGui extends BlueprintGui {

    public BlueprintDetailGui(VortexBlueprintsPlugin plugin, Player viewer, BlueprintListing listing, int marketPage) {
        this(plugin, viewer, listing, marketPage, MarketView.TRENDING, null);
    }

    public BlueprintDetailGui(VortexBlueprintsPlugin plugin, Player viewer, BlueprintListing listing, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        super(45, MessageUtil.parse("<dark_gray>Blueprint <gray>- <aqua><id>", "id", listing.getId()));
        build(plugin, viewer, listing, marketPage, view, categoryFilter);
    }

    private void build(VortexBlueprintsPlugin plugin, Player viewer, BlueprintListing listing, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        List<BlueprintCollection> collections = plugin.getDataManager().getCollectionsContaining(listing.getId());
        SocialState socialState = plugin.getDataManager().getOrCreateSocialState(viewer.getUniqueId(), viewer.getName());

        setItem(10, new ItemBuilder(Material.WRITABLE_BOOK)
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
                "<gray>Status: <white>" + listing.getStatus().name(),
                collections.isEmpty() ? "<gray>Bundle: <white>Standalone drop" : "<gray>Bundle: <white>" + collections.getFirst().getTitle()
            )
            .build());

        setItem(14, new ItemBuilder(Material.BELL)
            .name("<gold>Premium Signals")
            .lore(
                listing.isStaffPick() ? "<gold>Staff Pick spotlight" : listing.isFeatured() ? "<aqua>Featured creator slot" : "<green>Originality verified",
                "<gray>Wishlists: <white>" + plugin.getDataManager().getWishlistCount(listing.getId()),
                "<gray>Creator followers: <white>" + plugin.getDataManager().getFollowerCount(listing.getOwnerId()),
                collections.isEmpty() ? "<gray>No curated bundle yet." : "<gray>Bundle memberships: <white>" + collections.size()
            )
            .build());

        setItem(20, new ItemBuilder(Material.EMERALD_BLOCK)
            .name("<green>Build Here")
            .lore("<gray>Paste this blueprint at your feet.", "<yellow>Click to build")
            .build(), event -> {
                viewer.closeInventory();
                plugin.getBuildService().build(viewer, listing.getId());
            });

        setItem(22, new ItemBuilder(Material.SPYGLASS)
            .name("<aqua>Preview Footprint")
            .lore("<gray>Show the build outline before placing it.", "<yellow>Click to preview")
            .build(), event -> {
                viewer.closeInventory();
                plugin.getBuildService().preview(viewer, listing.getId());
            });

        if (listing.isOwner(viewer.getUniqueId()) || plugin.getDataManager().getPurchase(listing.getId(), viewer.getUniqueId()).isPresent()) {
            setItem(24, new ItemBuilder(Material.GLOW_INK_SAC)
                .name("<gold>Rate Blueprint")
                .lore("<gray>Leave a quality, accuracy, and usefulness rating.")
                .build(), event -> new RatingGui(plugin, viewer, listing, marketPage, view, categoryFilter, 3, 3, 3).open(viewer));
        }

        boolean wishlisted = socialState.hasWishlisted(listing.getId());
        setItem(29, new ItemBuilder(wishlisted ? Material.REDSTONE : Material.GRAY_DYE)
            .name(wishlisted ? "<red>Remove from Wishlist" : "<yellow>Add to Wishlist")
            .lore(
                "<gray>Wishlists: <white>" + plugin.getDataManager().getWishlistCount(listing.getId()),
                wishlisted ? "<yellow>Click to remove this listing from your wishlist" : "<yellow>Click to save this listing for later"
            )
            .build(), event -> {
                boolean added = socialState.toggleWishlist(listing.getId());
                plugin.getDataManager().saveSocialStateAsync(socialState);
                MessageUtil.send(viewer,
                    added
                        ? "<prefix><green>Saved <white><id></white> to your wishlist."
                        : "<prefix><yellow>Removed <white><id></white> from your wishlist.",
                    "id", listing.getId());
                new BlueprintDetailGui(plugin, viewer, listing, marketPage, view, categoryFilter).open(viewer);
            });

        setItem(31, new ItemBuilder(Material.BOOKSHELF)
            .name("<aqua>Creator Storefront")
            .lore(
                "<gray>Followers: <white>" + plugin.getDataManager().getFollowerCount(listing.getOwnerId()),
                "<gray>Open <white>" + listing.getOwnerName() + "</white>'s premium storefront."
            )
            .build(), event -> new CreatorStorefrontGui(plugin, viewer, listing.getOwnerId(), marketPage, view, categoryFilter).open(viewer));

        setItem(33, new ItemBuilder(Material.CHEST)
            .name("<gold>Curated Bundles")
            .lore(
                collections.isEmpty() ? "<gray>This listing is not in a curated bundle yet." : "<gray>Open bundles curated by <white>" + listing.getOwnerName(),
                "<yellow>Click to browse creator collections"
            )
            .build(), event -> new CollectionBrowserGui(plugin, viewer, 0, listing.getOwnerId(), marketPage, view, categoryFilter).open(viewer));

        setItem(40, new ItemBuilder(Material.NETHER_STAR)
            .name("<gold>Creator Analytics")
            .lore("<gray>Open your creator dashboard.")
            .build(), event -> new CreatorAnalyticsGui(plugin, viewer).open(viewer));

        if (listing.isOwner(viewer.getUniqueId()) || viewer.hasPermission("vortexblueprints.admin")) {
            setItem(42, new ItemBuilder(Material.NAME_TAG)
                .name("<aqua>Manage Team")
                .lore("<gray>Edit co-owner shares for this blueprint.")
                .build(), event -> new TeamOwnershipGui(plugin, viewer, listing.getId()).open(viewer));
        }

        setItem(44, new ItemBuilder(Material.ARROW)
            .name("<yellow>Back to Market")
            .build(), event -> new MarketplaceGui(plugin, viewer, marketPage, view, categoryFilter).open(viewer));
    }
}