package com.sauron.vortexblueprints.api.event;

import com.sauron.vortexblueprints.model.BlueprintListing;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class BlueprintPublishEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final BlueprintListing listing;
    private boolean cancelled;

    public BlueprintPublishEvent(Player player, BlueprintListing listing) {
        this.player = player;
        this.listing = listing;
    }

    public Player getPlayer() {
        return player;
    }

    public BlueprintListing getListing() {
        return listing;
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