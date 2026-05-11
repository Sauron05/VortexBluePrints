package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintCollection;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.CreatorProfile;
import com.sauron.vortexblueprints.model.MarketView;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class MarketplaceGui extends BlueprintGui {

    private static final int PAGE_SIZE = 36;

    public MarketplaceGui(VortexBlueprintsPlugin plugin, Player viewer, int requestedPage) {
        this(plugin, viewer, requestedPage, MarketView.TRENDING, null);
    }

    public MarketplaceGui(VortexBlueprintsPlugin plugin, Player viewer, int requestedPage, MarketView view, BlueprintCategory categoryFilter) {
        super(54, MessageUtil.parse("<dark_gray>Blueprint Market <gray>- <aqua><view>", "view", view.name()));
        List<BlueprintListing> listings = plugin.getDataManager().searchBlueprints(new com.sauron.vortexblueprints.service.MarketQuery("", categoryFilter, view, -1.0D, -1.0D));
        int maxPage = Math.max(0, (listings.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        build(plugin, viewer, listings, page, maxPage, view, categoryFilter);
    }

    private void build(VortexBlueprintsPlugin plugin, Player viewer, List<BlueprintListing> listings, int page, int maxPage, MarketView view, BlueprintCategory categoryFilter) {
        buildPremiumHeader(plugin, viewer, listings, page, view, categoryFilter);

        if (listings.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BOOK)
                .name("<yellow>No blueprints yet")
                .lore("<gray>Save a selection with <white>/vbp save</white>.")
                .build());
        }

        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(listings.size(), startIndex + PAGE_SIZE);
        for (int listingIndex = startIndex; listingIndex < endIndex; listingIndex++) {
            BlueprintListing listing = listings.get(listingIndex);
            int slot = 9 + (listingIndex - startIndex);
            listing.recordView();
            plugin.getDataManager().saveBlueprintAsync(listing);
            setItem(slot, listingItem(plugin, listing), event -> new BlueprintDetailGui(plugin, viewer, listing, page, view, categoryFilter).open(viewer));
        }

        setItem(45, new ItemBuilder(Material.ARROW)
            .name(page <= 0 ? "<dark_gray>Previous" : "<aqua>Previous")
            .lore("<gray>Page <white>" + (page + 1) + "</white>", "<gray>Navigate the market.")
            .build(), event -> {
                if (page > 0) {
                    new MarketplaceGui(plugin, viewer, page - 1, view, categoryFilter).open(viewer);
                }
            });

        setItem(47, new ItemBuilder(Material.HOPPER)
            .name("<gold>Category: <white>" + (categoryFilter == null ? "All" : categoryFilter.getDisplayName()))
            .lore("<gray>Click to cycle categories.")
            .build(), event -> new MarketplaceGui(plugin, viewer, 0, view, nextCategory(categoryFilter)).open(viewer));

        setItem(49, new ItemBuilder(Material.EMERALD)
            .name("<green>Your Balance")
            .lore("<white>" + plugin.getEconomyService().format(plugin.getEconomyService().balance(viewer)),
                "<gray>Creators earn every time their build is used.")
            .build());

        setItem(51, new ItemBuilder(Material.PAPER)
            .name("<aqua>Page Summary")
            .lore(
                "<gray>View: <white>" + view.name(),
                "<gray>Category: <white>" + (categoryFilter == null ? "All" : categoryFilter.getDisplayName()),
                "<gray>Page: <white>" + (page + 1) + " / " + (maxPage + 1)
            )
            .build());

        setItem(53, new ItemBuilder(Material.ARROW)
            .name(page >= maxPage ? "<dark_gray>Next" : "<aqua>Next")
            .lore("<gray>Page <white>" + (page + 1) + "</white>", "<gray>Navigate the market.")
            .build(), event -> {
                if (page < maxPage) {
                    new MarketplaceGui(plugin, viewer, page + 1, view, categoryFilter).open(viewer);
                }
            });
    }

    private void buildPremiumHeader(VortexBlueprintsPlugin plugin, Player viewer, List<BlueprintListing> listings, int page, MarketView view, BlueprintCategory categoryFilter) {
        BlueprintListing spotlight = listings.stream()
            .filter(BlueprintListing::isFeatured)
            .findFirst()
            .orElseGet(() -> listings.stream().findFirst().orElse(null));
        if (spotlight != null) {
            setItem(0, new ItemBuilder(Material.NETHER_STAR)
                .name("<gold>Featured Drop")
                .lore(
                    "<white>" + spotlight.getId(),
                    "<gray>Creator: <white>" + spotlight.getOwnerName(),
                    "<gray>Rating: <white>" + MessageUtil.number(spotlight.getAverageRating()),
                    "<yellow>Click to inspect"
                )
                .build(), event -> new BlueprintDetailGui(plugin, viewer, spotlight, page, view, categoryFilter).open(viewer));
        }

        BlueprintListing staffPick = listings.stream().filter(BlueprintListing::isStaffPick).findFirst().orElse(null);
        setItem(1, new ItemBuilder(staffPick == null ? Material.GOLD_INGOT : Material.GLOWSTONE)
            .name("<gold>Staff Picks")
            .lore(
                staffPick == null ? "<gray>No staff-picked listing on this slice yet." : "<white>" + staffPick.getId(),
                "<gray>Click to browse the featured view."
            )
            .build(), event -> new MarketplaceGui(plugin, viewer, 0, MarketView.FEATURED, categoryFilter).open(viewer));

        CreatorProfile topCreator = plugin.getDataManager().getCreatorProfiles().stream()
            .max(java.util.Comparator.comparingDouble(profile -> plugin.getDataManager().analyticsFor(profile.getCreatorId()).totalRevenue()))
            .orElse(null);
        setItem(2, new ItemBuilder(Material.BOOKSHELF)
            .name("<aqua>Creator Hall")
            .lore(
                topCreator == null ? "<gray>No creator storefronts yet." : "<white>" + topCreator.getCreatorName(),
                topCreator == null ? "<gray>Publish a blueprint to get started." : "<gray>Click to visit the top storefront."
            )
            .build(), event -> {
                if (topCreator != null) {
                    new CreatorStorefrontGui(plugin, viewer, topCreator.getCreatorId(), page, view, categoryFilter).open(viewer);
                }
            });

        BlueprintCollection featuredCollection = plugin.getDataManager().getCollections().stream().findFirst().orElse(null);
        setItem(3, new ItemBuilder(Material.CHEST)
            .name("<gold>Curated Bundles")
            .lore(
                featuredCollection == null ? "<gray>No collections curated yet." : "<white>" + featuredCollection.getTitle(),
                "<gray>Click to browse creator bundles."
            )
            .build(), event -> new CollectionBrowserGui(plugin, viewer, 0, null, page, view, categoryFilter).open(viewer));

        setItem(4, new ItemBuilder(Material.COMPASS)
            .name("<aqua>View: <white>" + view.name())
            .lore("<gray>Click to cycle market curation.")
            .build(), event -> new MarketplaceGui(plugin, viewer, page, nextView(view), categoryFilter).open(viewer));

        setItem(5, new ItemBuilder(Material.REDSTONE)
            .name("<red>Wishlist Hub")
            .lore(
                "<gray>Saved blueprints: <white>" + plugin.getDataManager().getWishlistListings(viewer.getUniqueId()).size(),
                "<gray>Click to open wishlist & following."
            )
            .build(), event -> new WishlistGui(plugin, viewer, 0, page, view, categoryFilter).open(viewer));

        setItem(6, new ItemBuilder(Material.BELL)
            .name("<yellow>Following Feed")
            .lore(
                "<gray>Creators followed: <white>" + plugin.getDataManager().getFollowedCreatorProfiles(viewer.getUniqueId()).size(),
                "<gray>Open your social hub for premium drops."
            )
            .build(), event -> new WishlistGui(plugin, viewer, 0, page, view, categoryFilter).open(viewer));

        setItem(7, new ItemBuilder(Material.NAME_TAG)
            .name("<aqua>My Storefront")
            .lore("<gray>Open and polish your creator page.")
            .build(), event -> new CreatorStorefrontGui(plugin, viewer, viewer.getUniqueId(), page, view, categoryFilter).open(viewer));

        setItem(8, new ItemBuilder(Material.NETHER_STAR)
            .name("<gold>Creator Analytics")
            .lore("<gray>Open your revenue and conversion dashboard.")
            .build(), event -> new CreatorAnalyticsGui(plugin, viewer).open(viewer));
    }

    private org.bukkit.inventory.ItemStack listingItem(VortexBlueprintsPlugin plugin, BlueprintListing listing) {
        List<BlueprintCollection> bundleMembership = plugin.getDataManager().getCollectionsContaining(listing.getId());
        return new ItemBuilder(categoryMaterial(listing.getCategory()))
            .name("<gradient:#38bdf8:#22c55e>" + listing.getId() + "</gradient>")
            .lore(
                "<gray>Creator: <white>" + listing.getOwnerName(),
                "<gray>Category: <white>" + listing.getCategory().getDisplayName(),
                "<gray>License: <white>" + listing.getLicenseType().getDisplayName(),
                "<gray>Price: <green>" + plugin.getEconomyService().format(listing.getPrice()),
                "<gray>Royalty: <white>" + MessageUtil.number(listing.getCurrentRoyaltyPercent()) + "%",
                "<gray>Size: <white>" + listing.getWidth() + "x" + listing.getHeight() + "x" + listing.getDepth(),
                "<gray>Builds: <white>" + listing.getBuilds(),
                "<gray>Rating: <white>" + MessageUtil.number(listing.getAverageRating()),
                "<gray>Wishlists: <white>" + plugin.getDataManager().getWishlistCount(listing.getId()),
                bundleMembership.isEmpty() ? "<gray>Bundle: <white>Standalone drop" : "<gray>Bundle: <white>" + bundleMembership.getFirst().getTitle(),
                "<gray>Originality: <white>" + MessageUtil.percent(listing.getOriginalityScore()) + "%",
                trustLine(listing),
                "",
                "<yellow>Click to inspect"
            )
            .pdc(plugin, "blueprint-id", listing.getId())
            .build();
    }

    private String trustLine(BlueprintListing listing) {
        if (listing.isStaffPick()) {
            return "<gold>Trust: <white>Staff Pick";
        }
        if (listing.isFeatured()) {
            return "<aqua>Trust: <white>Featured Creator";
        }
        return "<green>Trust: <white>Originality Verified";
    }

    private Material categoryMaterial(BlueprintCategory category) {
        return switch (category) {
            case HOUSE -> Material.BRICKS;
            case SPAWN -> Material.BEACON;
            case FARM -> Material.HAY_BLOCK;
            case PVP -> Material.SHIELD;
            case REDSTONE -> Material.REDSTONE;
            case SHOP -> Material.EMERALD;
            case DUNGEON -> Material.SPAWNER;
            case MEDIEVAL -> Material.STONE_BRICKS;
            case SCIFI -> Material.SEA_LANTERN;
            case DECOR -> Material.FLOWER_POT;
            case OTHER -> Material.PAPER;
        };
    }

    private MarketView nextView(MarketView current) {
        MarketView[] values = MarketView.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private BlueprintCategory nextCategory(BlueprintCategory current) {
        BlueprintCategory[] values = BlueprintCategory.values();
        if (current == null) {
            return values[0];
        }
        int nextIndex = current.ordinal() + 1;
        return nextIndex >= values.length ? null : values[nextIndex];
    }
}