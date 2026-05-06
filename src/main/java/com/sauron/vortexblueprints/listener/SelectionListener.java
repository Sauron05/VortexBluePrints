package com.sauron.vortexblueprints.listener;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.CuboidSelection;
import com.sauron.vortexblueprints.util.MessageUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class SelectionListener implements Listener {

    public static final String WAND_KEY = "selection-wand";

    private final VortexBlueprintsPlugin plugin;
    private final NamespacedKey wandKey;

    public SelectionListener(VortexBlueprintsPlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, WAND_KEY);
    }

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!isSelectionWand(event.getItem())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);
        if (!event.getPlayer().hasPermission("vortexblueprints.create")) {
            MessageUtil.send(event.getPlayer(), plugin.getConfigManager().message("no-permission"));
            return;
        }
        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }
        if (action == Action.LEFT_CLICK_BLOCK) {
            CuboidSelection selection = plugin.getSelectionManager().setFirstCorner(event.getPlayer(), clickedBlock.getLocation());
            MessageUtil.send(event.getPlayer(), plugin.getConfigManager().message("selection-pos1"),
                "location", MessageUtil.formatLocation(clickedBlock.getLocation()));
            sendSelectionSize(event, selection);
            return;
        }
        CuboidSelection selection = plugin.getSelectionManager().setSecondCorner(event.getPlayer(), clickedBlock.getLocation());
        MessageUtil.send(event.getPlayer(), plugin.getConfigManager().message("selection-pos2"),
            "location", MessageUtil.formatLocation(clickedBlock.getLocation()));
        sendSelectionSize(event, selection);
    }

    private void sendSelectionSize(PlayerInteractEvent event, CuboidSelection selection) {
        if (!selection.isComplete()) {
            return;
        }
        if (selection.volume() > plugin.getConfigManager().maxVolume()) {
            MessageUtil.send(event.getPlayer(), plugin.getConfigManager().message("selection-too-large"),
                "volume", String.valueOf(selection.volume()),
                "limit", String.valueOf(plugin.getConfigManager().maxVolume()));
        }
    }

    private boolean isSelectionWand(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta.getPersistentDataContainer().has(wandKey, PersistentDataType.STRING);
    }
}