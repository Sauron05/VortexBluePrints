package com.sauron.vortexblueprints.manager;

import com.sauron.vortexblueprints.model.CuboidSelection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class SelectionManager {

    private final Map<UUID, CuboidSelection> selections = new ConcurrentHashMap<>();

    public CuboidSelection setFirstCorner(Player player, Location location) {
        CuboidSelection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new CuboidSelection());
        selection.setFirstCorner(location);
        return selection;
    }

    public CuboidSelection setSecondCorner(Player player, Location location) {
        CuboidSelection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new CuboidSelection());
        selection.setSecondCorner(location);
        return selection;
    }

    public Optional<CuboidSelection> getSelection(UUID uuid) {
        return Optional.ofNullable(selections.get(uuid)).filter(CuboidSelection::isComplete);
    }
}