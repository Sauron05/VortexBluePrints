package com.sauron.vortexblueprints.model;

import java.util.UUID;

public record LedgerEntry(
    long id,
    long createdAt,
    String type,
    String blueprintId,
    UUID actorId,
    String actorName,
    double amount,
    String payload
) {
}