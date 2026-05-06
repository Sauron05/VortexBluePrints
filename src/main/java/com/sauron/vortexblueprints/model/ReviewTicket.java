package com.sauron.vortexblueprints.model;

import java.util.UUID;

public final class ReviewTicket {

    public enum Status {
        OPEN,
        APPROVED,
        REJECTED
    }

    private final String id;
    private final String blueprintId;
    private final UUID creatorId;
    private final String creatorName;
    private final String reason;
    private final OriginalityClassification classification;
    private final double similarity;
    private final long createdAt;
    private Status status;
    private String moderatorNotes;
    private long updatedAt;

    public ReviewTicket(
        String id,
        String blueprintId,
        UUID creatorId,
        String creatorName,
        String reason,
        OriginalityClassification classification,
        double similarity,
        long createdAt
    ) {
        this.id = id;
        this.blueprintId = blueprintId;
        this.creatorId = creatorId;
        this.creatorName = creatorName;
        this.reason = reason;
        this.classification = classification;
        this.similarity = similarity;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = Status.OPEN;
        this.moderatorNotes = "";
    }

    public String getId() {
        return id;
    }

    public String getBlueprintId() {
        return blueprintId;
    }

    public UUID getCreatorId() {
        return creatorId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public String getReason() {
        return reason;
    }

    public OriginalityClassification getClassification() {
        return classification;
    }

    public double getSimilarity() {
        return similarity;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public Status getStatus() {
        return status;
    }

    public String getModeratorNotes() {
        return moderatorNotes;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void update(Status status, String moderatorNotes) {
        this.status = status;
        this.moderatorNotes = moderatorNotes == null ? "" : moderatorNotes;
        this.updatedAt = System.currentTimeMillis();
    }

    public void load(Status status, String moderatorNotes, long updatedAt) {
        this.status = status;
        this.moderatorNotes = moderatorNotes == null ? "" : moderatorNotes;
        this.updatedAt = updatedAt;
    }
}