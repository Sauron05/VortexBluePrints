package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.CreatorAnalytics;
import com.sauron.vortexblueprints.model.CreatorProfile;
import com.sauron.vortexblueprints.model.MarketView;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class WishlistGui extends BlueprintGui {

    private static final int PAGE_SIZE = 36;

    public WishlistGui(VortexBlueprintsPlugin plugin, Player viewer, int requestedPage, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        super(54, MessageUtil.parse("<dark_gray>Wishlist <gray>& <aqua>Following"));
        build(plugin, viewer, requestedPage, marketPage, view, categoryFilter);
    }

    private void build(VortexBlueprintsPlugin plugin, Player viewer, int requestedPage, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        List<CreatorProfile> followedCreators = plugin.getDataManager().getFollowedCreatorProfiles(viewer.getUniqueId());
        for (int index = 0; index < Math.min(9, followedCreators.size()); index++) {
            CreatorProfile profile = followedCreators.get(index);
            CreatorAnalytics analytics = plugin.getDataManager().analyticsFor(profile.getCreatorId());
            setItem(index, new ItemBuilder(Material.NAME_TAG)
                .name("<aqua>" + profile.getCreatorName())
                .lore(
                    "<gray>Followers: <white>" + plugin.getDataManager().getFollowerCount(profile.getCreatorId()),
                    "<gray>Revenue: <white>" + plugin.getEconomyService().format(analytics.totalRevenue()),
                    "<gray>Headline: <white>" + (profile.getHeadline().isBlank() ? "Creator storefront" : profile.getHeadline()),
                    "<yellow>Click to open storefront"
                )
                .build(), event -> new CreatorStorefrontGui(plugin, viewer, profile.getCreatorId(), marketPage, view, categoryFilter).open(viewer));
        }
        if (followedCreators.isEmpty()) {
            setItem(4, new ItemBuilder(Material.BOOK)
                .name("<yellow>No followed creators")
                .lore("<gray>Open any creator storefront and click follow.")
                .build());
        }

        List<BlueprintListing> wishlistedListings = plugin.getDataManager().getWishlistListings(viewer.getUniqueId());
        int maxPage = Math.max(0, (wishlistedListings.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(wishlistedListings.size(), startIndex + PAGE_SIZE);

        if (wishlistedListings.isEmpty()) {
            setItem(22, new ItemBuilder(Material.PAPER)
                .name("<yellow>Your wishlist is empty")
                .lore("<gray>Use the blueprint detail screen to save future favorites.")
                .build());
        }

        for (int listingIndex = startIndex; listingIndex < endIndex; listingIndex++) {
            BlueprintListing listing = wishlistedListings.get(listingIndex);
            int slot = 9 + (listingIndex - startIndex);
            setItem(slot, new ItemBuilder(Material.PAPER)
                .name("<gradient:#38bdf8:#22c55e>" + listing.getId() + "</gradient>")
                .lore(
                    "<gray>Creator: <white>" + listing.getOwnerName(),
                    "<gray>Price: <green>" + plugin.getEconomyService().format(listing.getPrice()),
                    "<gray>Rating: <white>" + MessageUtil.number(listing.getAverageRating()),
                    "<gray>Wishlists: <white>" + plugin.getDataManager().getWishlistCount(listing.getId()),
                    "<yellow>Click to inspect"
                )
                .build(), event -> new BlueprintDetailGui(plugin, viewer, listing, marketPage, view, categoryFilter).open(viewer));
        }

        setItem(45, new ItemBuilder(Material.ARROW)
            .name(page <= 0 ? "<dark_gray>Previous" : "<aqua>Previous")
            .lore("<gray>Page <white>" + (page + 1) + "</white>")
            .build(), event -> {
                if (page > 0) {
                    new WishlistGui(plugin, viewer, page - 1, marketPage, view, categoryFilter).open(viewer);
                }
            });

        setItem(47, new ItemBuilder(Material.COMPASS)
            .name("<yellow>Back to Market")
            .build(), event -> new MarketplaceGui(plugin, viewer, marketPage, view, categoryFilter).open(viewer));

        setItem(49, new ItemBuilder(Material.REDSTONE)
            .name("<gold>Wishlist Hub")
            .lore(
                "<gray>Saved blueprints: <white>" + wishlistedListings.size(),
                "<gray>Followed creators: <white>" + followedCreators.size(),
                "<gray>Keep tabs on your next premium drop."
            )
            .build());

        setItem(51, new ItemBuilder(Material.BOOKSHELF)
            .name("<aqua>My Storefront")
            .lore("<gray>Open your creator page.")
            .build(), event -> new CreatorStorefrontGui(plugin, viewer, viewer.getUniqueId(), marketPage, view, categoryFilter).open(viewer));

        setItem(53, new ItemBuilder(Material.ARROW)
            .name(page >= maxPage ? "<dark_gray>Next" : "<aqua>Next")
            .lore("<gray>Page <white>" + (page + 1) + "</white>")
            .build(), event -> {
                if (page < maxPage) {
                    new WishlistGui(plugin, viewer, page + 1, marketPage, view, categoryFilter).open(viewer);
                }
            });
    }
}