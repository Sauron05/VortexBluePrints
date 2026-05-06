package com.sauron.vortexblueprints.model;

public record TimelineEntry(long createdAt, String type, String actor, String summary) {
}