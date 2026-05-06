package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.OwnerShare;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class TeamOwnershipGui extends BlueprintGui {

    private static final int PAGE_SIZE = 27;

    public TeamOwnershipGui(VortexBlueprintsPlugin plugin, Player viewer, String blueprintId) {
        this(plugin, viewer, blueprintId, 0, 10.0D);
    }

    public TeamOwnershipGui(VortexBlueprintsPlugin plugin, Player viewer, String blueprintId, int requestedPage, double transferShare) {
        super(54, MessageUtil.parse("<dark_gray>Team Editor <gray>- <aqua><id>", "id", blueprintId));
        build(plugin, viewer, blueprintId, requestedPage, transferShare);
    }

    private void build(VortexBlueprintsPlugin plugin, Player viewer, String blueprintId, int requestedPage, double transferShare) {
        BlueprintListing listing = plugin.getDataManager().getBlueprint(blueprintId).orElse(null);
        if (listing == null) {
            setItem(22, new ItemBuilder(Material.BARRIER)
                .name("<red>Blueprint missing")
                .lore("<gray>This blueprint no longer exists.")
                .build());
            return;
        }

        List<OwnerShare> ownerShares = listing.getOwnerShares();
        for (int index = 0; index < Math.min(ownerShares.size(), 9); index++) {
            OwnerShare ownerShare = ownerShares.get(index);
            boolean primaryOwner = ownerShare.uuid().equals(listing.getOwnerId());
            setItem(index, new ItemBuilder(primaryOwner ? Material.EMERALD_BLOCK : Material.NAME_TAG)
                .name((primaryOwner ? "<green>" : "<aqua>") + ownerShare.name())
                .lore(
                    "<gray>Share: <white>" + MessageUtil.number(ownerShare.percent()) + "%",
                    primaryOwner ? "<gray>Primary owner" : "<yellow>Click to remove"
                )
                .build(), event -> {
                    if (primaryOwner) {
                        return;
                    }
                    if (plugin.getModerationService().removeCoOwner(listing, ownerShare.uuid())) {
                        MessageUtil.send(viewer, "<prefix><yellow>Removed <white><player></white> from <white><id></white>.",
                            "player", ownerShare.name(),
                            "id", listing.getId());
                    } else {
                        MessageUtil.send(viewer, "<prefix><red>Could not remove that co-owner.");
                    }
                    new TeamOwnershipGui(plugin, viewer, blueprintId, requestedPage, transferShare).open(viewer);
                });
        }

        setItem(13, new ItemBuilder(Material.BOOK)
            .name("<gold>Team Ownership")
            .lore(
                "<gray>Blueprint: <white>" + listing.getId(),
                "<gray>Transfer share: <white>" + MessageUtil.number(transferShare) + "%",
                "<gray>Click players below to add them.",
                "<gray>Click current co-owners above to remove them."
            )
            .build());

        Set<java.util.UUID> existingOwners = new HashSet<>();
        for (OwnerShare ownerShare : ownerShares) {
            existingOwners.add(ownerShare.uuid());
        }
        List<Player> candidates = new ArrayList<>(plugin.getServer().getOnlinePlayers().stream()
            .filter(player -> !existingOwners.contains(player.getUniqueId()))
            .filter(player -> !player.getUniqueId().equals(listing.getOwnerId()))
            .toList());
        int maxPage = Math.max(0, (candidates.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        int start = page * PAGE_SIZE;
        int end = Math.min(candidates.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            Player candidate = candidates.get(index);
            int slot = 18 + (index - start);
            setItem(slot, new ItemBuilder(Material.GREEN_WOOL)
                .name("<green>Add " + candidate.getName())
                .lore(
                    "<gray>Transfer: <white>" + MessageUtil.number(transferShare) + "%",
                    "<yellow>Click to add as co-owner"
                )
                .build(), event -> {
                    if (plugin.getModerationService().addCoOwner(listing, candidate.getUniqueId(), candidate.getName(), transferShare)) {
                        MessageUtil.send(viewer, "<prefix><green>Added <white><player></white> to <white><id></white> for <white><share>%</white>.",
                            "player", candidate.getName(),
                            "id", listing.getId(),
                            "share", MessageUtil.number(transferShare));
                    } else {
                        MessageUtil.send(viewer, "<prefix><red>Could not transfer that ownership share.");
                    }
                    new TeamOwnershipGui(plugin, viewer, blueprintId, page, transferShare).open(viewer);
                });
        }

        setItem(45, new ItemBuilder(Material.ARROW)
            .name(page <= 0 ? "<dark_gray>Previous" : "<aqua>Previous")
            .build(), event -> {
                if (page > 0) {
                    new TeamOwnershipGui(plugin, viewer, blueprintId, page - 1, transferShare).open(viewer);
                }
            });
        setItem(47, new ItemBuilder(Material.RED_WOOL)
            .name("<red>-5%")
            .build(), event -> new TeamOwnershipGui(plugin, viewer, blueprintId, page, clampShare(transferShare - 5.0D)).open(viewer));
        setItem(48, new ItemBuilder(Material.PAPER)
            .name("<white>Transfer <gold>" + MessageUtil.number(transferShare) + "%")
            .lore("<gray>Adjust the share before adding a co-owner.")
            .build());
        setItem(49, new ItemBuilder(Material.LIME_WOOL)
            .name("<green>+5%")
            .build(), event -> new TeamOwnershipGui(plugin, viewer, blueprintId, page, clampShare(transferShare + 5.0D)).open(viewer));
        setItem(51, new ItemBuilder(Material.BARRIER)
            .name("<red>Close")
            .build(), event -> viewer.closeInventory());
        setItem(53, new ItemBuilder(Material.ARROW)
            .name(page >= maxPage ? "<dark_gray>Next" : "<aqua>Next")
            .build(), event -> {
                if (page < maxPage) {
                    new TeamOwnershipGui(plugin, viewer, blueprintId, page + 1, transferShare).open(viewer);
                }
            });
    }

    private double clampShare(double value) {
        return Math.max(5.0D, Math.min(95.0D, value));
    }
}