package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintCollection;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.MarketView;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class CollectionDetailGui extends BlueprintGui {

    private static final int[] BLUEPRINT_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    public CollectionDetailGui(VortexBlueprintsPlugin plugin, Player viewer, BlueprintCollection collection, int browserPage, UUID ownerId, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        super(54, MessageUtil.parse("<dark_gray>Bundle <gray>- <aqua><title>", "title", collection.getTitle()));
        build(plugin, viewer, collection, browserPage, ownerId, marketPage, view, categoryFilter);
    }

    private void build(VortexBlueprintsPlugin plugin, Player viewer, BlueprintCollection collection, int browserPage, UUID ownerId, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        List<BlueprintListing> listings = collection.getBlueprintIds().stream()
            .map(plugin.getDataManager()::getBlueprint)
            .flatMap(java.util.Optional::stream)
            .toList();

        setItem(4, new ItemBuilder(Material.WRITABLE_BOOK)
            .name("<gradient:#f59e0b:#38bdf8>" + collection.getTitle() + "</gradient>")
            .lore(
                "<gray>Curator: <white>" + collection.getOwnerName(),
                "<gray>Bundle id: <white>" + collection.getId(),
                "<gray>Listings: <white>" + collection.size(),
                collection.getDescription().isBlank() ? "<gray>No bundle description yet." : "<gray>" + collection.getDescription()
            )
            .build());

        setItem(6, new ItemBuilder(Material.BOOKSHELF)
            .name("<aqua>Open Creator Storefront")
            .lore("<gray>Jump back to <white>" + collection.getOwnerName() + "</white>'s creator page.")
            .build(), event -> new CreatorStorefrontGui(plugin, viewer, collection.getOwnerId(), marketPage, view, categoryFilter).open(viewer));

        if (listings.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BOOK)
                .name("<yellow>No live listings in this bundle")
                .lore("<gray>Add blueprints with <white>/vbp collection add <id> <blueprintId></white>.")
                .build());
        }

        for (int index = 0; index < Math.min(BLUEPRINT_SLOTS.length, listings.size()); index++) {
            BlueprintListing listing = listings.get(index);
            setItem(BLUEPRINT_SLOTS[index], new ItemBuilder(Material.PAPER)
                .name("<gradient:#38bdf8:#22c55e>" + listing.getId() + "</gradient>")
                .lore(
                    "<gray>Price: <green>" + plugin.getEconomyService().format(listing.getPrice()),
                    "<gray>Builds: <white>" + listing.getBuilds(),
                    "<gray>Rating: <white>" + MessageUtil.number(listing.getAverageRating()),
                    "<yellow>Click to inspect"
                )
                .build(), event -> new BlueprintDetailGui(plugin, viewer, listing, marketPage, view, categoryFilter).open(viewer));
        }

        setItem(49, new ItemBuilder(Material.ARROW)
            .name("<yellow>Back to Collections")
            .build(), event -> new CollectionBrowserGui(plugin, viewer, browserPage, ownerId, marketPage, view, categoryFilter).open(viewer));
    }
}