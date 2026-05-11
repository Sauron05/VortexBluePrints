package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintCollection;
import com.sauron.vortexblueprints.model.MarketView;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class CollectionBrowserGui extends BlueprintGui {

    private static final int PAGE_SIZE = 45;

    public CollectionBrowserGui(VortexBlueprintsPlugin plugin, Player viewer, int requestedPage, UUID ownerId, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        super(54, MessageUtil.parse(ownerId == null ? "<dark_gray>Collections" : "<dark_gray>Collections <gray>- <aqua><name>", "name", ownerId == null ? "" : plugin.getDataManager().getCreatorProfile(ownerId).map(profile -> profile.getCreatorName()).orElse("Creator")));
        build(plugin, viewer, requestedPage, ownerId, marketPage, view, categoryFilter);
    }

    private void build(VortexBlueprintsPlugin plugin, Player viewer, int requestedPage, UUID ownerId, int marketPage, MarketView view, BlueprintCategory categoryFilter) {
        List<BlueprintCollection> collections = ownerId == null ? plugin.getDataManager().getCollections() : plugin.getDataManager().getCollectionsByOwner(ownerId);
        int maxPage = Math.max(0, (collections.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        int startIndex = page * PAGE_SIZE;
        int endIndex = Math.min(collections.size(), startIndex + PAGE_SIZE);

        if (collections.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BOOK)
                .name("<yellow>No collections yet")
                .lore("<gray>Create one with <white>/vbp collection create <id> <title></white>.")
                .build());
        }

        for (int collectionIndex = startIndex; collectionIndex < endIndex; collectionIndex++) {
            BlueprintCollection collection = collections.get(collectionIndex);
            int slot = collectionIndex - startIndex;
            setItem(slot, collectionItem(collection), event -> new CollectionDetailGui(plugin, viewer, collection, page, ownerId, marketPage, view, categoryFilter).open(viewer));
        }

        setItem(45, new ItemBuilder(Material.ARROW)
            .name(page <= 0 ? "<dark_gray>Previous" : "<aqua>Previous")
            .lore("<gray>Page <white>" + (page + 1) + "</white>")
            .build(), event -> {
                if (page > 0) {
                    new CollectionBrowserGui(plugin, viewer, page - 1, ownerId, marketPage, view, categoryFilter).open(viewer);
                }
            });

        setItem(47, new ItemBuilder(Material.BOOKSHELF)
            .name(ownerId == null ? "<yellow>Back to Market" : "<yellow>Back to Storefront")
            .build(), event -> {
                if (ownerId == null) {
                    new MarketplaceGui(plugin, viewer, marketPage, view, categoryFilter).open(viewer);
                } else {
                    new CreatorStorefrontGui(plugin, viewer, ownerId, marketPage, view, categoryFilter).open(viewer);
                }
            });

        setItem(49, new ItemBuilder(Material.CHEST)
            .name("<gold>Collection Browser")
            .lore(
                "<gray>Total collections: <white>" + collections.size(),
                "<gray>Featured bundles surface first.",
                "<gray>Use <white>/vbp collection create</white> to curate themed packs."
            )
            .build());

        setItem(53, new ItemBuilder(Material.ARROW)
            .name(page >= maxPage ? "<dark_gray>Next" : "<aqua>Next")
            .lore("<gray>Page <white>" + (page + 1) + "</white>")
            .build(), event -> {
                if (page < maxPage) {
                    new CollectionBrowserGui(plugin, viewer, page + 1, ownerId, marketPage, view, categoryFilter).open(viewer);
                }
            });
    }

    private org.bukkit.inventory.ItemStack collectionItem(BlueprintCollection collection) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Curator: <white>" + collection.getOwnerName());
        lore.add("<gray>Listings: <white>" + collection.size());
        if (collection.isFeatured()) {
            lore.add("<gold>Featured bundle");
        }
        if (!collection.getDescription().isBlank()) {
            lore.add("<gray>" + collection.getDescription());
        }
        if (!collection.getBlueprintIds().isEmpty()) {
            lore.add("");
            lore.add("<gray>Preview: <white>" + String.join(", ", collection.getBlueprintIds().stream().limit(3).toList()));
        }
        lore.add("");
        lore.add("<yellow>Click to browse this bundle");
        return new ItemBuilder(collection.isFeatured() ? Material.ENDER_CHEST : Material.CHEST)
            .name("<gradient:#f59e0b:#38bdf8>" + collection.getTitle() + "</gradient>")
            .lore(lore.toArray(String[]::new))
            .build();
    }
}