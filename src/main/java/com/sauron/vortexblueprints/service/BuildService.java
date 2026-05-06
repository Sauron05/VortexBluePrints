package com.sauron.vortexblueprints.service;

import com.sauron.vortexblueprints.api.event.BlueprintBuildCompleteEvent;
import com.sauron.vortexblueprints.api.event.BlueprintPurchaseEvent;
import com.sauron.vortexblueprints.api.event.RoyaltyPaidEvent;
import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.manager.ConfigManager;
import com.sauron.vortexblueprints.manager.DataManager;
import com.sauron.vortexblueprints.model.Account;
import com.sauron.vortexblueprints.model.BlockEntry;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.BlueprintStatus;
import com.sauron.vortexblueprints.model.LedgerEntry;
import com.sauron.vortexblueprints.model.OwnerShare;
import com.sauron.vortexblueprints.model.PurchaseRecord;
import com.sauron.vortexblueprints.util.FoliaUtil;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

public final class BuildService {

    private final VortexBlueprintsPlugin plugin;
    private final ConfigManager configManager;
    private final DataManager dataManager;
    private final EconomyService economyService;
    private final ProtectionService protectionService;

    public BuildService(
        VortexBlueprintsPlugin plugin,
        ConfigManager configManager,
        DataManager dataManager,
        EconomyService economyService,
        ProtectionService protectionService
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataManager = dataManager;
        this.economyService = economyService;
        this.protectionService = protectionService;
    }

    public void build(Player player, String id) {
        BlueprintListing listing = dataManager.getBlueprint(id).orElse(null);
        if (listing == null) {
            MessageUtil.send(player, configManager.message("not-found"), "id", id);
            return;
        }
        if (listing.getStatus() != BlueprintStatus.LIVE) {
            MessageUtil.send(player, "<prefix><red>This blueprint is not currently live. Status: <white><status></white>.", "status", listing.getStatus().name());
            return;
        }
        BuildPreparation preparation = prepare(player, listing);
        emitReadiness(player, preparation);
        if (preparation.analysis().deniedCount() > 0 && configManager.blockOnProtectionDeny()) {
            MessageUtil.send(player, configManager.message("build-blocked-protection"), "count", String.valueOf(preparation.analysis().deniedCount()));
            return;
        }
        if (!preparation.analysis().missingMaterials().isEmpty() && configManager.blockOnMissingMaterials()) {
            MessageUtil.send(player, configManager.message("build-blocked-materials"), "materials", formatMaterials(preparation.analysis().missingMaterials()));
            return;
        }
        for (String warning : preparation.analysis().warnings()) {
            MessageUtil.send(player, configManager.message("build-warning"), "warning", warning);
        }
        LicenseUse licenseUse = resolveLicenseUse(player, listing);
        if (!licenseUse.allowed()) {
            return;
        }
        MessageUtil.send(player, configManager.message("build-started"), "id", listing.getId());
        switch (listing.getBuildStyle()) {
            case INSTANT -> buildInstant(player, preparation, licenseUse);
            case DRONE -> buildAnimated(player, preparation, licenseUse, true, false);
            case CRATE -> buildAnimated(player, preparation, licenseUse, false, true);
            case ANIMATED -> buildAnimated(player, preparation, licenseUse, false, false);
        }
    }

    public void preview(Player player, String id) {
        BlueprintListing listing = dataManager.getBlueprint(id).orElse(null);
        if (listing == null) {
            MessageUtil.send(player, configManager.message("not-found"), "id", id);
            return;
        }
        if (!configManager.previewParticles()) {
            MessageUtil.send(player, "<prefix><yellow>Preview particles are disabled.");
            return;
        }
        BuildPreparation preparation = prepare(player, listing);
        emitReadiness(player, preparation);
        MessageUtil.send(player, configManager.message("preview"), "id", listing.getId());
        previewTick(player, preparation, configManager.previewSeconds() * 20);
    }

    private BuildPreparation prepare(Player player, BlueprintListing listing) {
        Location origin = player.getLocation().getBlock().getLocation();
        Rotation rotation = configManager.rotateToPlayerFacing() ? Rotation.fromYaw(player.getLocation().getYaw()) : Rotation.NONE;
        PlacementAnalysis analysis = analyze(player, listing, origin, rotation);
        return new BuildPreparation(listing, origin, rotation, analysis);
    }

    private PlacementAnalysis analyze(Player player, BlueprintListing listing, Location origin, Rotation rotation) {
        Map<String, Integer> requiredMaterials = new HashMap<>(listing.getMaterialCounts());
        Map<String, Integer> missingMaterials = new HashMap<>();
        Map<String, Integer> inventoryCounts = countInventory(player.getInventory().getContents());
        int collisionCount = 0;
        int liquidCount = 0;
        int deniedCount = 0;
        Set<String> warnings = new java.util.LinkedHashSet<>();
        Set<String> chunks = new HashSet<>();
        List<PreviewPoint> previewPoints = new ArrayList<>();
        int sampleStride = Math.max(1, listing.getEntries().size() / configManager.previewMaxPoints());
        int index = 0;

        for (BlockEntry entry : listing.getEntries()) {
            Location target = targetLocation(origin, listing, entry, rotation);
            Block block = target.getBlock();
            Material material = block.getType();
            chunks.add((target.getBlockX() >> 4) + ":" + (target.getBlockZ() >> 4));
            boolean denied = protectionService.firstDenial(player, target).isPresent();
            if (denied) {
                deniedCount++;
            }
            if (!material.isAir() && !isSoftReplaceable(material)) {
                collisionCount++;
            }
            if (material == Material.WATER || material == Material.LAVA || block.isLiquid()) {
                liquidCount++;
            }
            if (index % sampleStride == 0) {
                previewPoints.add(new PreviewPoint(target.clone().add(0.5D, 0.35D, 0.5D), !denied && material.isAir()));
            }
            index++;
        }

        if (chunks.size() > 1) {
            warnings.add("Spans " + chunks.size() + " chunks.");
        }
        if (configManager.warnOnCollisions() && collisionCount > 0) {
            warnings.add(collisionCount + " target blocks are occupied.");
        }
        if (configManager.warnOnLiquid() && liquidCount > 0) {
            warnings.add(liquidCount + " target blocks contain liquids.");
        }

        for (Map.Entry<String, Integer> entry : requiredMaterials.entrySet()) {
            int available = inventoryCounts.getOrDefault(entry.getKey(), 0);
            if (available < entry.getValue()) {
                missingMaterials.put(entry.getKey(), entry.getValue() - available);
            }
        }

        return new PlacementAnalysis(requiredMaterials, missingMaterials, collisionCount, liquidCount, deniedCount, List.copyOf(warnings), previewPoints);
    }

    private void emitReadiness(Player player, BuildPreparation preparation) {
        MessageUtil.send(player, configManager.message("build-readiness"),
            "id", preparation.listing().getId(),
            "materials", String.valueOf(preparation.analysis().requiredMaterials().size()),
            "collisions", String.valueOf(preparation.analysis().collisionCount()),
            "liquids", String.valueOf(preparation.analysis().liquidCount()),
            "denials", String.valueOf(preparation.analysis().deniedCount()));
    }

    private LicenseUse resolveLicenseUse(Player player, BlueprintListing listing) {
        boolean ownerBuild = listing.isOwner(player.getUniqueId());
        if (ownerBuild && configManager.ownersBuildFree()) {
            return new LicenseUse(true, 0.0D, null);
        }
        PurchaseRecord purchaseRecord = dataManager.getPurchase(listing.getId(), player.getUniqueId()).orElseGet(() -> new PurchaseRecord(
            listing.getId(),
            player.getUniqueId(),
            player.getName(),
            listing.getLicenseType(),
            System.currentTimeMillis()
        ));

        if (!purchaseRecord.hasUsesRemaining()) {
            if (listing.getLicenseType().isExclusive() && configManager.exclusiveSaleLock() && !dataManager.getPurchasesForBlueprint(listing.getId()).isEmpty()) {
                MessageUtil.send(player, "<prefix><red>This exclusive license has already been sold.");
                return new LicenseUse(false, 0.0D, null);
            }
            BlueprintPurchaseEvent purchaseEvent = new BlueprintPurchaseEvent(player, listing, purchaseRecord, listing.getPrice());
            Bukkit.getPluginManager().callEvent(purchaseEvent);
            if (purchaseEvent.isCancelled()) {
                MessageUtil.send(player, "<prefix><red>Another plugin cancelled this license purchase.");
                return new LicenseUse(false, 0.0D, null);
            }
            if (!economyService.withdraw(player, listing.getPrice())) {
                MessageUtil.send(player, configManager.message("build-failed-payment"), "price", economyService.format(listing.getPrice()));
                return new LicenseUse(false, 0.0D, null);
            }
            purchaseRecord.topUp(listing.getLicenseType(), listing.getPrice(), System.currentTimeMillis());
            dataManager.savePurchaseAsync(purchaseRecord);
            boolean repeatBuyer = purchaseRecord.getTotalPurchases() > 1;
            int previousMilestoneLevel = listing.getMilestoneLevel();
            listing.recordPurchase(player.getName(), repeatBuyer, listing.getPrice());
            listing.setMilestoneLevel(resolveMilestoneLevel(listing.getTotalRevenue()));
            dataManager.saveBlueprintAsync(listing);
            dataManager.appendLedgerAsync(new LedgerEntry(System.nanoTime(), System.currentTimeMillis(), "purchase", listing.getId(), player.getUniqueId(), player.getName(), listing.getPrice(), "license=" + listing.getLicenseType().name()));
            Account buyerAccount = dataManager.getOrCreateAccount(player.getUniqueId(), player.getName());
            buyerAccount.recordPurchase(listing.getPrice());
            dataManager.saveAccountsAsync();
            if (listing.getMilestoneLevel() > previousMilestoneLevel) {
                plugin.getExternalBridgeService().notifyMilestone(listing);
            }
            MessageUtil.send(player, configManager.message("build-license-purchased"),
                "license", listing.getLicenseType().getDisplayName(),
                "price", economyService.format(listing.getPrice()));
            if (listing.getLicenseType().isExclusive() && configManager.exclusiveSaleLock()) {
                listing.setStatus(BlueprintStatus.ARCHIVED);
            }
        }
        double licenseValue = purchaseRecord.consumeBuild(System.currentTimeMillis());
        dataManager.savePurchaseAsync(purchaseRecord);
        return new LicenseUse(true, licenseValue, purchaseRecord);
    }

    private void buildInstant(Player player, BuildPreparation preparation, LicenseUse licenseUse) {
        if (FoliaUtil.isFolia()) {
            AtomicInteger remainingEntries = new AtomicInteger(preparation.listing().getEntries().size());
            for (BlockEntry entry : preparation.listing().getEntries()) {
                Location target = targetLocation(preparation.origin(), preparation.listing(), entry, preparation.rotation());
                FoliaUtil.runAtLocation(plugin, target, () -> {
                    placeEntry(preparation.origin(), preparation.listing(), entry, preparation.rotation());
                    if (remainingEntries.decrementAndGet() == 0) {
                        finishBuild(player, preparation.listing(), licenseUse);
                    }
                });
            }
            return;
        }
        FoliaUtil.runGlobal(plugin, () -> {
            for (BlockEntry entry : preparation.listing().getEntries()) {
                placeEntry(preparation.origin(), preparation.listing(), entry, preparation.rotation());
            }
            finishBuild(player, preparation.listing(), licenseUse);
        });
    }

    private void buildAnimated(Player player, BuildPreparation preparation, LicenseUse licenseUse, boolean droneEffects, boolean crateEffects) {
        if (crateEffects) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0F, 0.8F);
        }
        Deque<BlockEntry> remainingEntries = new ArrayDeque<>(preparation.listing().getEntries());
        FoliaUtil.runGlobal(plugin, () -> pasteBatch(player, preparation, licenseUse, remainingEntries, droneEffects, crateEffects));
    }

    private void pasteBatch(
        Player player,
        BuildPreparation preparation,
        LicenseUse licenseUse,
        Deque<BlockEntry> remainingEntries,
        boolean droneEffects,
        boolean crateEffects
    ) {
        int placedThisTick = 0;
        while (placedThisTick < configManager.blocksPerTick() && !remainingEntries.isEmpty()) {
            BlockEntry entry = remainingEntries.removeFirst();
            Location target = targetLocation(preparation.origin(), preparation.listing(), entry, preparation.rotation());
            placeEntry(preparation.origin(), preparation.listing(), entry, preparation.rotation());
            if (droneEffects) {
                player.spawnParticle(Particle.WAX_ON, target.clone().add(0.5D, 0.5D, 0.5D), 2, 0.15D, 0.15D, 0.15D, 0.0D);
            }
            if (crateEffects) {
                player.spawnParticle(Particle.CLOUD, target.clone().add(0.5D, 0.5D, 0.5D), 1, 0.1D, 0.1D, 0.1D, 0.0D);
            }
            placedThisTick++;
        }
        if (remainingEntries.isEmpty()) {
            finishBuild(player, preparation.listing(), licenseUse);
            return;
        }
        FoliaUtil.runLaterGlobal(plugin, () -> pasteBatch(player, preparation, licenseUse, remainingEntries, droneEffects, crateEffects), 1L);
    }

    private void finishBuild(Player player, BlueprintListing listing, LicenseUse licenseUse) {
        double grossRoyalty = licenseUse.licenseValue() * (listing.getCurrentRoyaltyPercent() / 100.0D);
        if (listing.isOwner(player.getUniqueId()) && configManager.blockSelfRoyalty()) {
            grossRoyalty = 0.0D;
        }
        int previousMilestoneLevel = listing.getMilestoneLevel();
        for (OwnerShare ownerShare : listing.getOwnerShares()) {
            double shareValue = grossRoyalty * (ownerShare.percent() / 100.0D);
            if (shareValue > 0.0D) {
                economyService.deposit(ownerShare.uuid(), ownerShare.name(), shareValue);
                Bukkit.getPluginManager().callEvent(new RoyaltyPaidEvent(player, listing, ownerShare, shareValue));
            }
        }
        listing.recordBuild(grossRoyalty, player.getName());
        listing.setMilestoneLevel(resolveMilestoneLevel(listing.getTotalRevenue()));
        dataManager.saveBlueprintAsync(listing);
        dataManager.appendLedgerAsync(new LedgerEntry(System.nanoTime(), System.currentTimeMillis(), "build", listing.getId(), player.getUniqueId(), player.getName(), grossRoyalty, "style=" + listing.getBuildStyle().name()));
        Account account = dataManager.getOrCreateAccount(player.getUniqueId(), player.getName());
        account.recordBuild();
        dataManager.saveAccountsAsync();
        if (listing.getMilestoneLevel() > previousMilestoneLevel) {
            plugin.getExternalBridgeService().notifyMilestone(listing);
        }
        Bukkit.getPluginManager().callEvent(new BlueprintBuildCompleteEvent(player, listing, grossRoyalty));
        double finalRoyalty = grossRoyalty;
        FoliaUtil.runForEntity(plugin, player, () -> MessageUtil.send(player, configManager.message("build-complete"),
            "id", listing.getId(),
            "royalty", economyService.format(finalRoyalty)));
    }

    private void previewTick(Player player, BuildPreparation preparation, int ticksLeft) {
        if (!player.isOnline() || ticksLeft <= 0) {
            return;
        }
        FoliaUtil.runForEntity(plugin, player, () -> {
            spawnPreview(player, preparation);
            if (configManager.showroomHologram()) {
                spawnShowroomHologram(player, preparation);
            }
            FoliaUtil.runLaterForEntity(plugin, player, () -> previewTick(player, preparation, ticksLeft - 10), 10L);
        });
    }

    private void spawnPreview(Player player, BuildPreparation preparation) {
        for (PreviewPoint previewPoint : preparation.analysis().previewPoints()) {
            player.spawnParticle(
                Particle.DUST,
                previewPoint.location(),
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                new Particle.DustOptions(previewPoint.allowed() ? Color.LIME : Color.RED, previewPoint.allowed() ? 0.8F : 1.2F)
            );
        }
    }

    private void spawnShowroomHologram(Player player, BuildPreparation preparation) {
        Location hologramLocation = preparation.origin().clone().add(
            Math.max(1, preparation.listing().getWidth()) / 2.0D,
            preparation.listing().getHeight() + 1.5D,
            Math.max(1, preparation.listing().getDepth()) / 2.0D
        );
        TextDisplay display = player.getWorld().spawn(hologramLocation, TextDisplay.class, entity -> {
            entity.text(Component.text(preparation.listing().getId() + " | " + preparation.listing().getCategory().getDisplayName() + " | " + economyService.format(preparation.listing().getPrice())));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(false);
        });
        FoliaUtil.runLaterGlobal(plugin, display::remove, 20L);
    }

    private Map<String, Integer> countInventory(ItemStack[] contents) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack itemStack : contents) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            counts.merge(itemStack.getType().name(), itemStack.getAmount(), (left, right) -> left + right);
        }
        return counts;
    }

    private String formatMaterials(Map<String, Integer> materials) {
        return materials.entrySet().stream()
            .limit(5)
            .map(entry -> entry.getKey().toLowerCase(java.util.Locale.ROOT) + " x" + entry.getValue())
            .reduce((first, second) -> first + ", " + second)
            .orElse("none");
    }

    private int resolveMilestoneLevel(double totalRevenue) {
        int milestoneLevel = 0;
        for (Integer threshold : configManager.milestoneRevenueThresholds()) {
            if (totalRevenue >= threshold) {
                milestoneLevel++;
            }
        }
        return milestoneLevel;
    }

    private boolean isSoftReplaceable(Material material) {
        return switch (material) {
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, VINE, DEAD_BUSH, SNOW, WATER, LAVA, AIR, CAVE_AIR, VOID_AIR -> true;
            default -> false;
        };
    }

    private void placeEntry(Location origin, BlueprintListing listing, BlockEntry entry, Rotation rotation) {
        Location target = targetLocation(origin, listing, entry, rotation);
        World world = target.getWorld();
        if (world == null) {
            return;
        }
        try {
            BlockData blockData = Bukkit.createBlockData(entry.blockData());
            rotateBlockData(blockData, rotation);
            Block block = world.getBlockAt(target.getBlockX(), target.getBlockY(), target.getBlockZ());
            block.setBlockData(blockData, false);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Skipped invalid block data in " + listing.getId() + ": " + entry.blockData());
        }
    }

    private Location targetLocation(Location origin, BlueprintListing listing, BlockEntry entry, Rotation rotation) {
        RotatedCoordinate coordinate = rotation.rotate(entry.relativeX(), entry.relativeZ(), listing.getWidth(), listing.getDepth());
        return origin.clone().add(coordinate.relativeX(), entry.relativeY(), coordinate.relativeZ());
    }

    private void rotateBlockData(BlockData blockData, Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return;
        }
        if (blockData instanceof Directional directional) {
            BlockFace rotatedFace = rotation.rotateFace(directional.getFacing());
            if (directional.getFaces().contains(rotatedFace)) {
                directional.setFacing(rotatedFace);
            }
        }
        if (blockData instanceof Rotatable rotatable) {
            rotatable.setRotation(rotation.rotateFace(rotatable.getRotation()));
        }
        if (blockData instanceof Orientable orientable && rotation.swapsAxes()) {
            if (orientable.getAxis() == Axis.X && orientable.getAxes().contains(Axis.Z)) {
                orientable.setAxis(Axis.Z);
            } else if (orientable.getAxis() == Axis.Z && orientable.getAxes().contains(Axis.X)) {
                orientable.setAxis(Axis.X);
            }
        }
    }

    private record BuildPreparation(BlueprintListing listing, Location origin, Rotation rotation, PlacementAnalysis analysis) {
    }

    private record PlacementAnalysis(
        Map<String, Integer> requiredMaterials,
        Map<String, Integer> missingMaterials,
        int collisionCount,
        int liquidCount,
        int deniedCount,
        List<String> warnings,
        List<PreviewPoint> previewPoints
    ) {
    }

    private record PreviewPoint(Location location, boolean allowed) {
    }

    private record LicenseUse(boolean allowed, double licenseValue, PurchaseRecord purchaseRecord) {
    }

    private record RotatedCoordinate(int relativeX, int relativeZ) {
    }

    private enum Rotation {
        NONE,
        CLOCKWISE_90,
        CLOCKWISE_180,
        CLOCKWISE_270;

        private static Rotation fromYaw(float yaw) {
            int quadrant = Math.floorMod(Math.round(yaw / 90.0F), 4);
            return switch (quadrant) {
                case 1 -> CLOCKWISE_90;
                case 2 -> CLOCKWISE_180;
                case 3 -> CLOCKWISE_270;
                default -> NONE;
            };
        }

        private RotatedCoordinate rotate(int relativeX, int relativeZ, int width, int depth) {
            return switch (this) {
                case NONE -> new RotatedCoordinate(relativeX, relativeZ);
                case CLOCKWISE_90 -> new RotatedCoordinate(depth - 1 - relativeZ, relativeX);
                case CLOCKWISE_180 -> new RotatedCoordinate(width - 1 - relativeX, depth - 1 - relativeZ);
                case CLOCKWISE_270 -> new RotatedCoordinate(relativeZ, width - 1 - relativeX);
            };
        }

        private boolean swapsAxes() {
            return this == CLOCKWISE_90 || this == CLOCKWISE_270;
        }

        private BlockFace rotateFace(BlockFace face) {
            if (!isCardinal(face)) {
                return face;
            }
            BlockFace rotatedFace = face;
            int turns = switch (this) {
                case NONE -> 0;
                case CLOCKWISE_90 -> 1;
                case CLOCKWISE_180 -> 2;
                case CLOCKWISE_270 -> 3;
            };
            for (int turnIndex = 0; turnIndex < turns; turnIndex++) {
                rotatedFace = switch (rotatedFace) {
                    case NORTH -> BlockFace.EAST;
                    case EAST -> BlockFace.SOUTH;
                    case SOUTH -> BlockFace.WEST;
                    case WEST -> BlockFace.NORTH;
                    default -> rotatedFace;
                };
            }
            return rotatedFace;
        }

        private boolean isCardinal(BlockFace face) {
            return face == BlockFace.NORTH || face == BlockFace.EAST || face == BlockFace.SOUTH || face == BlockFace.WEST;
        }
    }
}