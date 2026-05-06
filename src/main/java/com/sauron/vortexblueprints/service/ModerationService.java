package com.sauron.vortexblueprints.service;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.BlueprintStatus;
import com.sauron.vortexblueprints.model.DisputeRecord;
import com.sauron.vortexblueprints.model.OwnerShare;
import com.sauron.vortexblueprints.model.ReviewTicket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ModerationService {

    private final VortexBlueprintsPlugin plugin;

    public ModerationService(VortexBlueprintsPlugin plugin) {
        this.plugin = plugin;
    }

    public Optional<String> approveReview(String ticketId, String moderatorName, String notes) {
        return updateReview(ticketId, ReviewTicket.Status.APPROVED, moderatorName, notes, BlueprintStatus.LIVE);
    }

    public Optional<String> rejectReview(String ticketId, String moderatorName, String notes) {
        return updateReview(ticketId, ReviewTicket.Status.REJECTED, moderatorName, notes, BlueprintStatus.REJECTED);
    }

    public Optional<String> resolveDispute(String disputeId, String moderatorName, String notes) {
        return updateDispute(disputeId, DisputeRecord.Status.RESOLVED, moderatorName, notes, BlueprintStatus.ARCHIVED);
    }

    public Optional<String> rejectDispute(String disputeId, String moderatorName, String notes) {
        return updateDispute(disputeId, DisputeRecord.Status.REJECTED, moderatorName, notes, BlueprintStatus.LIVE);
    }

    public DisputeRecord openDispute(BlueprintListing listing, BlueprintListing against, UUID reporterId, String reporterName, String evidence) {
        DisputeRecord dispute = new DisputeRecord(UUID.randomUUID().toString(), listing.getId(), against.getId(), reporterId, reporterName, evidence, System.currentTimeMillis());
        listing.setStatus(BlueprintStatus.DISPUTED);
        plugin.getDataManager().saveDisputeAsync(dispute);
        plugin.getDataManager().saveBlueprintAsync(listing);
        plugin.getExternalBridgeService().notifyDispute(dispute);
        return dispute;
    }

    public boolean addCoOwner(BlueprintListing listing, UUID targetId, String targetName, double shareValue) {
        if (shareValue <= 0.0D || shareValue >= 100.0D) {
            return false;
        }
        List<OwnerShare> shares = new ArrayList<>(listing.getOwnerShares());
        for (OwnerShare share : shares) {
            if (share.uuid().equals(targetId)) {
                return false;
            }
        }
        int ownerIndex = ensureOwnerShare(listing, shares);
        OwnerShare ownerShare = shares.get(ownerIndex);
        if (ownerShare.percent() <= shareValue) {
            return false;
        }
        shares.set(ownerIndex, new OwnerShare(ownerShare.uuid(), ownerShare.name(), ownerShare.percent() - shareValue));
        shares.add(new OwnerShare(targetId, targetName, shareValue));
        listing.setTeamKey(listing.getTeamKey().isBlank() ? listing.getId() + "-team" : listing.getTeamKey());
        listing.setOwnerShares(shares);
        plugin.getDataManager().saveBlueprintAsync(listing);
        return true;
    }

    public boolean removeCoOwner(BlueprintListing listing, UUID targetId) {
        List<OwnerShare> shares = new ArrayList<>(listing.getOwnerShares());
        int ownerIndex = ensureOwnerShare(listing, shares);
        int removedIndex = -1;
        for (int index = 0; index < shares.size(); index++) {
            if (shares.get(index).uuid().equals(targetId) && !shares.get(index).uuid().equals(listing.getOwnerId())) {
                removedIndex = index;
                break;
            }
        }
        if (removedIndex == -1) {
            return false;
        }
        OwnerShare removed = shares.remove(removedIndex);
        if (removedIndex < ownerIndex) {
            ownerIndex--;
        }
        OwnerShare ownerShare = shares.get(ownerIndex);
        shares.set(ownerIndex, new OwnerShare(ownerShare.uuid(), ownerShare.name(), ownerShare.percent() + removed.percent()));
        listing.setOwnerShares(shares);
        plugin.getDataManager().saveBlueprintAsync(listing);
        return true;
    }

    private Optional<String> updateReview(String ticketId, ReviewTicket.Status status, String moderatorName, String notes, BlueprintStatus blueprintStatus) {
        ReviewTicket ticket = plugin.getDataManager().getReviewTicket(ticketId).orElse(null);
        if (ticket == null) {
            return Optional.of("Review ticket not found.");
        }
        BlueprintListing listing = plugin.getDataManager().getBlueprint(ticket.getBlueprintId()).orElse(null);
        if (listing == null) {
            return Optional.of("Blueprint for that ticket no longer exists.");
        }
        ticket.update(status, mergeNotes(moderatorName, notes));
        listing.setStatus(blueprintStatus);
        plugin.getDataManager().saveReviewTicketAsync(ticket);
        plugin.getDataManager().saveBlueprintAsync(listing);
        return Optional.empty();
    }

    private Optional<String> updateDispute(String disputeId, DisputeRecord.Status status, String moderatorName, String notes, BlueprintStatus blueprintStatus) {
        DisputeRecord dispute = plugin.getDataManager().getDispute(disputeId).orElse(null);
        if (dispute == null) {
            return Optional.of("Dispute not found.");
        }
        dispute.update(status, mergeNotes(moderatorName, notes));
        BlueprintListing listing = plugin.getDataManager().getBlueprint(dispute.getBlueprintId()).orElse(null);
        if (listing != null) {
            listing.setStatus(blueprintStatus);
            plugin.getDataManager().saveBlueprintAsync(listing);
        }
        plugin.getDataManager().saveDisputeAsync(dispute);
        return Optional.empty();
    }

    private String mergeNotes(String actorName, String notes) {
        String trimmedNotes = notes == null ? "" : notes.trim();
        if (trimmedNotes.isEmpty()) {
            return actorName == null || actorName.isBlank() ? "" : "Handled by " + actorName;
        }
        return actorName == null || actorName.isBlank() ? trimmedNotes : actorName + ": " + trimmedNotes;
    }

    private int ensureOwnerShare(BlueprintListing listing, List<OwnerShare> shares) {
        for (int index = 0; index < shares.size(); index++) {
            if (shares.get(index).uuid().equals(listing.getOwnerId())) {
                return index;
            }
        }
        shares.add(0, new OwnerShare(listing.getOwnerId(), listing.getOwnerName(), 100.0D));
        return 0;
    }
}