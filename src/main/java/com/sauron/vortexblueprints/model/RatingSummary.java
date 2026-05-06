package com.sauron.vortexblueprints.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class RatingSummary {

    private final Map<UUID, Review> reviews = new LinkedHashMap<>();

    public synchronized void rate(UUID reviewerId, int quality, int accuracy, int usefulness) {
        reviews.put(reviewerId, new Review(clamp(quality), clamp(accuracy), clamp(usefulness)));
    }

    public synchronized boolean hasReviewed(UUID reviewerId) {
        return reviews.containsKey(reviewerId);
    }

    public synchronized int getCount() {
        return reviews.size();
    }

    public synchronized double getAverageQuality() {
        return average(Review::quality);
    }

    public synchronized double getAverageAccuracy() {
        return average(Review::accuracy);
    }

    public synchronized double getAverageUsefulness() {
        return average(Review::usefulness);
    }

    public synchronized double getAverageOverall() {
        if (reviews.isEmpty()) {
            return 0.0D;
        }
        double total = 0.0D;
        for (Review review : reviews.values()) {
            total += (review.quality() + review.accuracy() + review.usefulness()) / 3.0D;
        }
        return total / reviews.size();
    }

    public synchronized Map<UUID, Review> getReviews() {
        return Map.copyOf(reviews);
    }

    public synchronized void load(Map<UUID, Review> loadedReviews) {
        reviews.clear();
        reviews.putAll(loadedReviews);
    }

    private int clamp(int value) {
        return Math.max(1, Math.min(5, value));
    }

    private double average(java.util.function.ToIntFunction<Review> extractor) {
        if (reviews.isEmpty()) {
            return 0.0D;
        }
        int total = 0;
        for (Review review : reviews.values()) {
            total += extractor.applyAsInt(review);
        }
        return (double) total / reviews.size();
    }

    public record Review(int quality, int accuracy, int usefulness) {
    }
}