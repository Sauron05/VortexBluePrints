package com.sauron.vortexblueprints.model;

import java.util.UUID;

public final class Account {

    private final UUID uuid;
    private String name;
    private double balance;
    private double earned;
    private double spent;
    private long purchases;
    private long builds;
    private long sales;
    private double reputation;
    private long lastSeen;

    public Account(UUID uuid, String name, double balance, double earned, double spent) {
        this.uuid = uuid;
        this.name = name;
        this.balance = balance;
        this.earned = earned;
        this.spent = spent;
        this.lastSeen = System.currentTimeMillis();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public synchronized double getBalance() {
        return balance;
    }

    public synchronized double getEarned() {
        return earned;
    }

    public synchronized double getSpent() {
        return spent;
    }

    public synchronized long getPurchases() {
        return purchases;
    }

    public synchronized long getBuilds() {
        return builds;
    }

    public synchronized long getSales() {
        return sales;
    }

    public synchronized double getReputation() {
        return reputation;
    }

    public synchronized long getLastSeen() {
        return lastSeen;
    }

    public synchronized boolean withdraw(double amount) {
        if (amount <= 0.0D) {
            return true;
        }
        if (balance < amount) {
            return false;
        }
        balance -= amount;
        spent += amount;
        return true;
    }

    public synchronized void deposit(double amount) {
        if (amount <= 0.0D) {
            return;
        }
        balance += amount;
        earned += amount;
        sales += 1;
        lastSeen = System.currentTimeMillis();
    }

    public synchronized void recordPurchase(double amount) {
        purchases += 1;
        spent += Math.max(0.0D, amount);
        lastSeen = System.currentTimeMillis();
    }

    public synchronized void recordBuild() {
        builds += 1;
        lastSeen = System.currentTimeMillis();
    }

    public synchronized void addReputation(double reputationDelta) {
        reputation += reputationDelta;
        lastSeen = System.currentTimeMillis();
    }

    public synchronized void load(long purchases, long builds, long sales, double reputation, long lastSeen) {
        this.purchases = purchases;
        this.builds = builds;
        this.sales = sales;
        this.reputation = reputation;
        this.lastSeen = lastSeen;
    }
}