package com.sauron.vortexblueprints.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public abstract class BlueprintGui implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> clickActions = new HashMap<>();

    protected BlueprintGui(int size, Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = clickActions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    protected void setItem(int slot, ItemStack itemStack) {
        inventory.setItem(slot, itemStack);
    }

    protected void setItem(int slot, ItemStack itemStack, Consumer<InventoryClickEvent> action) {
        inventory.setItem(slot, itemStack);
        clickActions.put(slot, action);
    }
}