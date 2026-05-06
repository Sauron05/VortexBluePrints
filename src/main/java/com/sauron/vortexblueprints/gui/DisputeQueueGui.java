package com.sauron.vortexblueprints.gui;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.DisputeRecord;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class DisputeQueueGui extends BlueprintGui {

    private static final int PAGE_SIZE = 45;

    public DisputeQueueGui(VortexBlueprintsPlugin plugin, Player viewer, int requestedPage) {
        super(54, MessageUtil.parse("<dark_gray>Dispute Queue"));
        build(plugin, viewer, requestedPage);
    }

    private void build(VortexBlueprintsPlugin plugin, Player viewer, int requestedPage) {
        List<DisputeRecord> disputes = plugin.getDataManager().getDisputes().stream()
            .filter(dispute -> dispute.getStatus() == DisputeRecord.Status.OPEN || dispute.getStatus() == DisputeRecord.Status.UNDER_REVIEW)
            .sorted(Comparator.comparingLong(DisputeRecord::getCreatedAt).reversed())
            .toList();
        int maxPage = Math.max(0, (disputes.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        int start = page * PAGE_SIZE;
        int end = Math.min(disputes.size(), start + PAGE_SIZE);
        if (disputes.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BOOK)
                .name("<yellow>No disputes")
                .lore("<gray>There are no active originality disputes.")
                .build());
        }
        for (int index = start; index < end; index++) {
            DisputeRecord dispute = disputes.get(index);
            int slot = index - start;
            setItem(slot, new ItemBuilder(Material.RED_BANNER)
                .name("<red>" + dispute.getBlueprintId())
                .lore(
                    "<gray>Dispute: <white>" + dispute.getId(),
                    "<gray>Against: <white>" + dispute.getAgainstBlueprintId(),
                    "<gray>Reporter: <white>" + dispute.getReporterName(),
                    "<gray>Status: <white>" + dispute.getStatus().name(),
                    "<gray>Evidence: <white>" + dispute.getEvidence(),
                    "",
                    "<green>Left click: resolve",
                    "<red>Right click: reject"
                )
                .build(), event -> {
                    boolean reject = event.isRightClick();
                    java.util.Optional<String> failure = reject
                        ? plugin.getModerationService().rejectDispute(dispute.getId(), viewer.getName(), "Handled in dispute GUI")
                        : plugin.getModerationService().resolveDispute(dispute.getId(), viewer.getName(), "Handled in dispute GUI");
                    if (failure.isPresent()) {
                        MessageUtil.send(viewer, "<prefix><red>" + failure.get());
                    } else {
                        MessageUtil.send(viewer, reject
                            ? "<prefix><yellow>Rejected dispute <white><id></white>."
                            : "<prefix><green>Resolved dispute <white><id></white>.", "id", dispute.getId());
                    }
                    new DisputeQueueGui(plugin, viewer, page).open(viewer);
                });
        }

        setItem(45, new ItemBuilder(Material.ARROW)
            .name(page <= 0 ? "<dark_gray>Previous" : "<aqua>Previous")
            .build(), event -> {
                if (page > 0) {
                    new DisputeQueueGui(plugin, viewer, page - 1).open(viewer);
                }
            });
        setItem(49, new ItemBuilder(Material.IRON_SWORD)
            .name("<gold>Dispute Actions")
            .lore("<gray>Left click items to resolve.", "<gray>Right click items to reject.")
            .build());
        setItem(53, new ItemBuilder(Material.ARROW)
            .name(page >= maxPage ? "<dark_gray>Next" : "<aqua>Next")
            .build(), event -> {
                if (page < maxPage) {
                    new DisputeQueueGui(plugin, viewer, page + 1).open(viewer);
                }
            });
    }
}