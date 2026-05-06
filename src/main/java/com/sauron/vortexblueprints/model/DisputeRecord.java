package com.sauron.vortexblueprints.model;

import java.util.UUID;

public final class DisputeRecord {

    public enum Status {
        OPEN,
        UNDER_REVIEW,
        RESOLVED,
        REJECTED
    }

    private final String id;
    private final String blueprintId;
    private final String againstBlueprintId;
    private final UUID reporterId;
    private final String reporterName;
    private final long createdAt;
    private String evidence;
    private String notes;
    private Status status;
    private long updatedAt;

    public DisputeRecord(String id, String blueprintId, String againstBlueprintId, UUID reporterId, String reporterName, String evidence, long createdAt) {
        this.id = id;
        this.blueprintId = blueprintId;
        this.againstBlueprintId = againstBlueprintId;
        this.reporterId = reporterId;
        this.reporterName = reporterName;
        this.evidence = evidence;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = Status.OPEN;
        this.notes = "";
    }

    public String getId() {
        return id;
    }

    public String getBlueprintId() {
        return blueprintId;
    }

    public String getAgainstBlueprintId() {
        return againstBlueprintId;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public String getReporterName() {
        return reporterName;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getNotes() {
        return notes;
    }

    public Status getStatus() {
        return status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void update(Status status, String notes) {
        this.status = status;
        if (notes != null) {
            this.notes = notes;
        }
        this.updatedAt = System.currentTimeMillis();
    }

    public void load(Status status, String notes, long updatedAt) {
        this.status = status;
        this.notes = notes == null ? "" : notes;
        this.updatedAt = updatedAt;
    }
}