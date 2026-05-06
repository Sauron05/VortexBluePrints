package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.MarketView;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class MarketplaceGui extends BlueprintGui {

    private static final int PAGE_SIZE = 45;

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
            int slot = listingIndex - startIndex;
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

        setItem(47, new ItemBuilder(Material.COMPASS)
            .name("<aqua>View: <white>" + view.name())
            .lore("<gray>Click to cycle market curation.")
            .build(), event -> new MarketplaceGui(plugin, viewer, page, nextView(view), categoryFilter).open(viewer));

        setItem(49, new ItemBuilder(Material.EMERALD)
            .name("<green>Your Balance")
            .lore("<white>" + plugin.getEconomyService().format(plugin.getEconomyService().balance(viewer)),
                "<gray>Creators earn every time their build is used.")
            .build());

        setItem(51, new ItemBuilder(Material.HOPPER)
            .name("<gold>Category: <white>" + (categoryFilter == null ? "All" : categoryFilter.getDisplayName()))
            .lore("<gray>Click to cycle categories.")
            .build(), event -> new MarketplaceGui(plugin, viewer, 0, view, nextCategory(categoryFilter)).open(viewer));

        setItem(53, new ItemBuilder(Material.ARROW)
            .name(page >= maxPage ? "<dark_gray>Next" : "<aqua>Next")
            .lore("<gray>Page <white>" + (page + 1) + "</white>", "<gray>Navigate the market.")
            .build(), event -> {
                if (page < maxPage) {
                    new MarketplaceGui(plugin, viewer, page + 1, view, categoryFilter).open(viewer);
                }
            });
    }

    private org.bukkit.inventory.ItemStack listingItem(VortexBlueprintsPlugin plugin, BlueprintListing listing) {
        return new ItemBuilder(Material.PAPER)
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
                "<gray>Originality: <white>" + MessageUtil.percent(listing.getOriginalityScore()) + "%",
                "",
                "<yellow>Click to inspect"
            )
            .pdc(plugin, "blueprint-id", listing.getId())
            .build();
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