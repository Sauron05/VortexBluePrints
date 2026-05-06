package com.sauron.vortexblueprints.listener;

import com.sauron.vortexblueprints.gui.BlueprintGui;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class GuiListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BlueprintGui gui)) {
            return;
        }
        event.setCancelled(true);
        gui.handleClick(event);
    }
}