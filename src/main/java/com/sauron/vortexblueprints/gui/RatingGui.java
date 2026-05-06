package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.MarketView;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class RatingGui extends BlueprintGui {

    public RatingGui(VortexBlueprintsPlugin plugin, Player viewer, BlueprintListing listing) {
        this(plugin, viewer, listing, 0, MarketView.TRENDING, null, 3, 3, 3);
    }

    public RatingGui(
        VortexBlueprintsPlugin plugin,
        Player viewer,
        BlueprintListing listing,
        int marketPage,
        MarketView view,
        BlueprintCategory categoryFilter,
        int quality,
        int accuracy,
        int usefulness
    ) {
        super(27, MessageUtil.parse("<dark_gray>Rate <gray>- <aqua><id>", "id", listing.getId()));
        build(plugin, viewer, listing, marketPage, view, categoryFilter, quality, accuracy, usefulness);
    }

    private void build(
        VortexBlueprintsPlugin plugin,
        Player viewer,
        BlueprintListing listing,
        int marketPage,
        MarketView view,
        BlueprintCategory categoryFilter,
        int quality,
        int accuracy,
        int usefulness
    ) {
        setItem(10, scoreItem(Material.GOLD_INGOT, "Quality", quality), event ->
            new RatingGui(plugin, viewer, listing, marketPage, view, categoryFilter, nextRating(quality, event.isRightClick()), accuracy, usefulness).open(viewer)
        );
        setItem(13, scoreItem(Material.COMPASS, "Accuracy", accuracy), event ->
            new RatingGui(plugin, viewer, listing, marketPage, view, categoryFilter, quality, nextRating(accuracy, event.isRightClick()), usefulness).open(viewer)
        );
        setItem(16, scoreItem(Material.EMERALD, "Usefulness", usefulness), event ->
            new RatingGui(plugin, viewer, listing, marketPage, view, categoryFilter, quality, accuracy, nextRating(usefulness, event.isRightClick())).open(viewer)
        );

        setItem(22, new ItemBuilder(Material.LIME_DYE)
            .name("<green>Submit Rating")
            .lore(
                "<gray>Quality: <white>" + quality + "/5",
                "<gray>Accuracy: <white>" + accuracy + "/5",
                "<gray>Usefulness: <white>" + usefulness + "/5",
                "<yellow>Click to save"
            )
            .build(), event -> {
                listing.recordRating(viewer.getUniqueId(), quality, accuracy, usefulness, viewer.getName());
                plugin.getDataManager().saveBlueprintAsync(listing);
                MessageUtil.send(viewer, plugin.getConfigManager().message("rating-recorded"),
                    "id", listing.getId(),
                    "rating", MessageUtil.number(listing.getAverageRating()));
                new BlueprintDetailGui(plugin, viewer, listing, marketPage, view, categoryFilter).open(viewer);
            });

        setItem(24, new ItemBuilder(Material.ARROW)
            .name("<yellow>Back")
            .lore("<gray>Return to the blueprint details.")
            .build(), event -> new BlueprintDetailGui(plugin, viewer, listing, marketPage, view, categoryFilter).open(viewer));
    }

    private org.bukkit.inventory.ItemStack scoreItem(Material material, String label, int value) {
        return new ItemBuilder(material)
            .name("<aqua>" + label)
            .lore(
                "<gray>Score: <white>" + value + "/5",
                "<gray>Left click to increase",
                "<gray>Right click to decrease"
            )
            .build();
    }

    private int nextRating(int current, boolean decrement) {
        if (decrement) {
            return current <= 1 ? 5 : current - 1;
        }
        return current >= 5 ? 1 : current + 1;
    }
}