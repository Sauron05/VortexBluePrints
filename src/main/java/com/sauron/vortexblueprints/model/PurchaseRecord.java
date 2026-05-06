package com.sauron.vortexblueprints.model;

import java.util.UUID;

public final class PurchaseRecord {

    private final String blueprintId;
    private final UUID buyerId;
    private String buyerName;
    private BlueprintLicenseType licenseType;
    private int usesRemaining;
    private int totalPurchases;
    private int totalBuilds;
    private double prepaidValueRemaining;
    private long firstPurchasedAt;
    private long lastPurchaseAt;
    private long lastBuildAt;

    public PurchaseRecord(String blueprintId, UUID buyerId, String buyerName, BlueprintLicenseType licenseType, long purchasedAt) {
        this.blueprintId = blueprintId;
        this.buyerId = buyerId;
        this.buyerName = buyerName;
        this.licenseType = licenseType;
        this.firstPurchasedAt = purchasedAt;
        this.lastPurchaseAt = purchasedAt;
    }

    public String getBlueprintId() {
        return blueprintId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public BlueprintLicenseType getLicenseType() {
        return licenseType;
    }

    public int getUsesRemaining() {
        return usesRemaining;
    }

    public int getTotalPurchases() {
        return totalPurchases;
    }

    public int getTotalBuilds() {
        return totalBuilds;
    }

    public double getPrepaidValueRemaining() {
        return prepaidValueRemaining;
    }

    public long getFirstPurchasedAt() {
        return firstPurchasedAt;
    }

    public long getLastPurchaseAt() {
        return lastPurchaseAt;
    }

    public long getLastBuildAt() {
        return lastBuildAt;
    }

    public void rename(String buyerName) {
        if (buyerName != null && !buyerName.isBlank()) {
            this.buyerName = buyerName;
        }
    }

    public void topUp(BlueprintLicenseType purchaseType, double purchasePrice, long purchasedAt) {
        this.licenseType = purchaseType;
        this.usesRemaining += purchaseType.getUsesPerPurchase();
        this.totalPurchases += 1;
        this.prepaidValueRemaining += Math.max(0.0D, purchasePrice);
        this.lastPurchaseAt = purchasedAt;
        if (firstPurchasedAt == 0L) {
            firstPurchasedAt = purchasedAt;
        }
    }

    public boolean hasUsesRemaining() {
        return usesRemaining > 0;
    }

    public double consumeBuild(long builtAt) {
        if (usesRemaining <= 0) {
            return 0.0D;
        }
        double perUseValue = prepaidValueRemaining <= 0.0D ? 0.0D : prepaidValueRemaining / usesRemaining;
        usesRemaining -= 1;
        prepaidValueRemaining = Math.max(0.0D, prepaidValueRemaining - perUseValue);
        totalBuilds += 1;
        lastBuildAt = builtAt;
        return perUseValue;
    }

    public void load(int usesRemaining, int totalPurchases, int totalBuilds, double prepaidValueRemaining, long firstPurchasedAt, long lastPurchaseAt, long lastBuildAt) {
        this.usesRemaining = usesRemaining;
        this.totalPurchases = totalPurchases;
        this.totalBuilds = totalBuilds;
        this.prepaidValueRemaining = prepaidValueRemaining;
        this.firstPurchasedAt = firstPurchasedAt;
        this.lastPurchaseAt = lastPurchaseAt;
        this.lastBuildAt = lastBuildAt;
    }
}