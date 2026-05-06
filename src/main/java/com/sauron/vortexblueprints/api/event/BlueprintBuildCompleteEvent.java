package com.sauron.vortexblueprints.api.event;

import com.sauron.vortexblueprints.model.BlueprintListing;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BlueprintBuildCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final BlueprintListing listing;
    private final double royaltyPaid;

    public BlueprintBuildCompleteEvent(Player player, BlueprintListing listing, double royaltyPaid) {
        this.player = player;
        this.listing = listing;
        this.royaltyPaid = royaltyPaid;
    }

    public Player getPlayer() {
        return player;
    }

    public BlueprintListing getListing() {
        return listing;
    }

    public double getRoyaltyPaid() {
        return royaltyPaid;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}