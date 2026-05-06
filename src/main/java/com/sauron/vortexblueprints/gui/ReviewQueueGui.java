package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.ReviewTicket;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class ReviewQueueGui extends BlueprintGui {

    private static final int PAGE_SIZE = 45;

    public ReviewQueueGui(VortexBlueprintsPlugin plugin, Player viewer, int requestedPage) {
        super(54, MessageUtil.parse("<dark_gray>Review Queue"));
        build(plugin, viewer, requestedPage);
    }

    private void build(VortexBlueprintsPlugin plugin, Player viewer, int requestedPage) {
        List<ReviewTicket> openTickets = plugin.getDataManager().getReviewTickets().stream()
            .filter(ticket -> ticket.getStatus() == ReviewTicket.Status.OPEN)
            .sorted(Comparator.comparingLong(ReviewTicket::getCreatedAt))
            .toList();
        int maxPage = Math.max(0, (openTickets.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        int start = page * PAGE_SIZE;
        int end = Math.min(openTickets.size(), start + PAGE_SIZE);
        if (openTickets.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BOOK)
                .name("<yellow>No review tickets")
                .lore("<gray>Originality review is currently clear.")
                .build());
        }
        for (int index = start; index < end; index++) {
            ReviewTicket ticket = openTickets.get(index);
            int slot = index - start;
            setItem(slot, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("<aqua>" + ticket.getBlueprintId())
                .lore(
                    "<gray>Ticket: <white>" + ticket.getId(),
                    "<gray>Creator: <white>" + ticket.getCreatorName(),
                    "<gray>Classification: <white>" + ticket.getClassification().name(),
                    "<gray>Similarity: <white>" + MessageUtil.percent(ticket.getSimilarity()) + "%",
                    "<gray>Reason: <white>" + ticket.getReason(),
                    "",
                    "<green>Left click: approve",
                    "<red>Right click: reject"
                )
                .build(), event -> {
                    boolean reject = event.isRightClick();
                    java.util.Optional<String> failure = reject
                        ? plugin.getModerationService().rejectReview(ticket.getId(), viewer.getName(), "Handled in review GUI")
                        : plugin.getModerationService().approveReview(ticket.getId(), viewer.getName(), "Handled in review GUI");
                    if (failure.isPresent()) {
                        MessageUtil.send(viewer, "<prefix><red>" + failure.get());
                    } else {
                        MessageUtil.send(viewer, reject
                            ? "<prefix><yellow>Rejected <white><id></white>."
                            : "<prefix><green>Approved <white><id></white>.", "id", ticket.getId());
                    }
                    new ReviewQueueGui(plugin, viewer, page).open(viewer);
                });
        }

        setItem(45, new ItemBuilder(Material.ARROW)
            .name(page <= 0 ? "<dark_gray>Previous" : "<aqua>Previous")
            .build(), event -> {
                if (page > 0) {
                    new ReviewQueueGui(plugin, viewer, page - 1).open(viewer);
                }
            });
        setItem(49, new ItemBuilder(Material.ANVIL)
            .name("<gold>Review Actions")
            .lore("<gray>Left click items to approve.", "<gray>Right click items to reject.")
            .build());
        setItem(53, new ItemBuilder(Material.ARROW)
            .name(page >= maxPage ? "<dark_gray>Next" : "<aqua>Next")
            .build(), event -> {
                if (page < maxPage) {
                    new ReviewQueueGui(plugin, viewer, page + 1).open(viewer);
                }
            });
    }
}