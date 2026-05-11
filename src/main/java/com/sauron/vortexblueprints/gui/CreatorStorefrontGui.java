package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintCollection;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.CreatorAnalytics;
import com.sauron.vortexblueprints.model.CreatorProfile;
import com.sauron.vortexblueprints.model.MarketView;
import com.sauron.vortexblueprints.model.SocialState;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class CreatorStorefrontGui extends BlueprintGui {

    public CreatorStorefrontGui(VortexBlueprintsPlugin plugin, Player viewer, UUID creatorId, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        super(54, MessageUtil.parse("<dark_gray>Storefront <gray>- <aqua><name>", "name", resolveName(plugin, creatorId)));
        build(plugin, viewer, creatorId, marketPage, view, categoryFilter);
    }

    private void build(VortexBlueprintsPlugin plugin, Player viewer, UUID creatorId, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        List<BlueprintListing> listings = plugin.getDataManager().getBlueprintsByOwner(creatorId);
        String creatorName = listings.isEmpty() ? plugin.getDataManager().getCreatorProfile(creatorId).map(CreatorProfile::getCreatorName).orElse("Unknown") : listings.getFirst().getOwnerName();
        CreatorProfile profile = plugin.getDataManager().getOrCreateCreatorProfile(creatorId, creatorName);
        List<BlueprintCollection> collections = plugin.getDataManager().getCollectionsByOwner(creatorId);
        CreatorAnalytics analytics = plugin.getDataManager().analyticsFor(creatorId);
        SocialState socialState = plugin.getDataManager().getOrCreateSocialState(viewer.getUniqueId(), viewer.getName());

        setItem(10, new ItemBuilder(Material.EMERALD)
            .name("<green>Storefront Stats")
            .lore(
                "<gray>Followers: <white>" + plugin.getDataManager().getFollowerCount(creatorId),
                "<gray>Revenue: <white>" + plugin.getEconomyService().format(analytics.totalRevenue()),
                "<gray>Builds: <white>" + analytics.totalBuilds(),
                "<gray>Average rating: <white>" + MessageUtil.number(averageRating(listings))
            )
            .build());

        List<String> profileLore = new ArrayList<>();
        profileLore.add("<gray>Creator: <white>" + profile.getCreatorName());
        profileLore.add("<gray>Headline: <white>" + (profile.getHeadline().isBlank() ? "Creator storefront" : profile.getHeadline()));
        if (!profile.getBio().isBlank()) {
            profileLore.add("<gray>Bio: <white>" + profile.getBio());
        }
        if (!analytics.badges().isEmpty()) {
            profileLore.add("<gray>Badges: <white>" + analytics.badges().stream().map(badge -> badge.getDisplayName()).reduce((first, second) -> first + ", " + second).orElse("None"));
        }
        profileLore.add("");
        profileLore.add("<gray>Use <white>/vbp storefront headline|bio|feature</white> to refine this page.");
        setItem(12, new ItemBuilder(Material.WRITABLE_BOOK)
            .name("<aqua>Creator Profile")
            .lore(profileLore.toArray(String[]::new))
            .build());

        if (viewer.getUniqueId().equals(creatorId)) {
            setItem(14, new ItemBuilder(Material.NAME_TAG)
                .name("<gold>Edit Storefront")
                .lore(
                    "<gray>/vbp storefront headline <text>",
                    "<gray>/vbp storefront bio <text>",
                    "<gray>/vbp storefront feature <blueprintId>",
                    "<gray>/vbp storefront pincollection <collectionId>"
                )
                .build());
        } else {
            boolean following = socialState.isFollowing(creatorId);
            setItem(14, new ItemBuilder(following ? Material.BELL : Material.IRON_NUGGET)
                .name(following ? "<green>Following Creator" : "<yellow>Follow Creator")
                .lore(
                    "<gray>Followers: <white>" + plugin.getDataManager().getFollowerCount(creatorId),
                    following ? "<yellow>Click to unfollow" : "<yellow>Click to follow"
                )
                .build(), event -> {
                    boolean nowFollowing = socialState.toggleFollow(creatorId);
                    plugin.getDataManager().saveSocialStateAsync(socialState);
                    MessageUtil.send(viewer,
                        nowFollowing
                            ? "<prefix><green>You are now following <white><creator></white>."
                            : "<prefix><yellow>You unfollowed <white><creator></white>.",
                        "creator", profile.getCreatorName());
                    new CreatorStorefrontGui(plugin, viewer, creatorId, marketPage, view, categoryFilter).open(viewer);
                });
        }

        setItem(16, new ItemBuilder(Material.CHEST)
            .name("<gold>Collections")
            .lore(
                "<gray>Published collections: <white>" + collections.size(),
                collections.isEmpty() ? "<gray>No curated bundles yet." : "<yellow>Click to browse collections"
            )
            .build(), event -> new CollectionBrowserGui(plugin, viewer, 0, creatorId, marketPage, view, categoryFilter).open(viewer));

        List<BlueprintListing> featuredListings = resolveFeaturedListings(plugin, profile, listings);
        int[] featuredSlots = {20, 21, 22, 23, 24};
        if (featuredListings.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BOOK)
                .name("<yellow>No featured blueprints")
                .lore("<gray>Feature listings with <white>/vbp storefront feature <id></white>.")
                .build());
        } else {
            for (int index = 0; index < Math.min(featuredSlots.length, featuredListings.size()); index++) {
                BlueprintListing listing = featuredListings.get(index);
                setItem(featuredSlots[index], storefrontListing(plugin, listing, "<yellow>Featured blueprint"), event -> new BlueprintDetailGui(plugin, viewer, listing, marketPage, view, categoryFilter).open(viewer));
            }
        }

        List<BlueprintListing> recentListings = listings.stream().limit(5).toList();
        int[] recentSlots = {29, 30, 31, 32, 33};
        for (int index = 0; index < Math.min(recentSlots.length, recentListings.size()); index++) {
            BlueprintListing listing = recentListings.get(index);
            setItem(recentSlots[index], storefrontListing(plugin, listing, "<aqua>Recent drop"), event -> new BlueprintDetailGui(plugin, viewer, listing, marketPage, view, categoryFilter).open(viewer));
        }

        setItem(40, new ItemBuilder(Material.NETHER_STAR)
            .name("<gold>Creator Analytics")
            .lore(
                "<gray>Views: <white>" + analytics.totalViews(),
                "<gray>Purchases: <white>" + analytics.totalPurchases(),
                "<gray>Conversion: <white>" + MessageUtil.number(analytics.conversionRate() * 100.0D) + "%",
                viewer.getUniqueId().equals(creatorId) ? "<yellow>Click to open your dashboard" : "<gray>Private dashboard for the creator"
            )
            .build(), event -> {
                if (viewer.getUniqueId().equals(creatorId)) {
                    new CreatorAnalyticsGui(plugin, viewer).open(viewer);
                }
            });

        setItem(44, new ItemBuilder(Material.ARROW)
            .name("<yellow>Back to Market")
            .build(), event -> new MarketplaceGui(plugin, viewer, marketPage, view, categoryFilter).open(viewer));
    }

    private static String resolveName(VortexBlueprintsPlugin plugin, UUID creatorId) {
        return plugin.getDataManager().getCreatorProfile(creatorId).map(CreatorProfile::getCreatorName)
            .orElseGet(() -> plugin.getDataManager().getBlueprintsByOwner(creatorId).stream().findFirst().map(BlueprintListing::getOwnerName).orElse("Unknown"));
    }

    private List<BlueprintListing> resolveFeaturedListings(VortexBlueprintsPlugin plugin, CreatorProfile profile, List<BlueprintListing> listings) {
        List<BlueprintListing> featured = profile.getFeaturedBlueprintIds().stream()
            .map(plugin.getDataManager()::getBlueprint)
            .flatMap(java.util.Optional::stream)
            .toList();
        if (!featured.isEmpty()) {
            return featured;
        }
        return listings.stream()
            .sorted(java.util.Comparator.comparing(BlueprintListing::isFeatured).reversed()
                .thenComparingLong(BlueprintListing::getBuilds).reversed())
            .limit(5)
            .toList();
    }

    private org.bukkit.inventory.ItemStack storefrontListing(VortexBlueprintsPlugin plugin, BlueprintListing listing, String label) {
        return new ItemBuilder(Material.PAPER)
            .name("<gradient:#38bdf8:#22c55e>" + listing.getId() + "</gradient>")
            .lore(
                label,
                "<gray>Price: <green>" + plugin.getEconomyService().format(listing.getPrice()),
                "<gray>Builds: <white>" + listing.getBuilds(),
                "<gray>Rating: <white>" + MessageUtil.number(listing.getAverageRating()),
                "<gray>Wishlists: <white>" + plugin.getDataManager().getWishlistCount(listing.getId()),
                "<yellow>Click to inspect"
            )
            .build();
    }

    private double averageRating(List<BlueprintListing> listings) {
        return listings.isEmpty() ? 0.0D : listings.stream().mapToDouble(BlueprintListing::getAverageRating).average().orElse(0.0D);
    }
}