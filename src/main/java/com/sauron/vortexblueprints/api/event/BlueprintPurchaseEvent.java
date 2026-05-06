package com.sauron.vortexblueprints.api.event;

import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.PurchaseRecord;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BlueprintPurchaseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final BlueprintListing listing;
    private final PurchaseRecord purchaseRecord;
    private final double price;
    private boolean cancelled;

    public BlueprintPurchaseEvent(Player player, BlueprintListing listing, PurchaseRecord purchaseRecord, double price) {
        this.player = player;
        this.listing = listing;
        this.purchaseRecord = purchaseRecord;
        this.price = price;
    }

    public Player getPlayer() {
        return player;
    }

    public BlueprintListing getListing() {
        return listing;
    }

    public PurchaseRecord getPurchaseRecord() {
        return purchaseRecord;
    }

    public double getPrice() {
        return price;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}