package com.sauron.vortexblueprints.api.event;

import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.OwnerShare;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class RoyaltyPaidEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player builder;
    private final BlueprintListing listing;
    private final OwnerShare ownerShare;
    private final double amount;

    public RoyaltyPaidEvent(Player builder, BlueprintListing listing, OwnerShare ownerShare, double amount) {
        this.builder = builder;
        this.listing = listing;
        this.ownerShare = ownerShare;
        this.amount = amount;
    }

    public Player getBuilder() {
        return builder;
    }

    public BlueprintListing getListing() {
        return listing;
    }

    public OwnerShare getOwnerShare() {
        return ownerShare;
    }

    public double getAmount() {
        return amount;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}