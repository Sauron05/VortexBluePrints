package com.sauron.vortexblueprints.manager;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintLicenseType;
import com.sauron.vortexblueprints.model.BuildStyle;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.io.File;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigManager {

    private final VortexBlueprintsPlugin plugin;
    private YamlConfiguration messages;

    public ConfigManager(VortexBlueprintsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        MessageUtil.setPrefix(messages.getString("prefix", ""));
    }

    public String message(String path) {
        return messages.getString(path, "<prefix><red>Missing message: " + path);
    }

    public int maxVolume() {
        return config().getInt("selection.max-volume", 25000);
    }

    public Material wandMaterial() {
        String configured = config().getString("selection.wand-material", "GOLDEN_AXE");
        Material material = Material.matchMaterial(configured == null ? "GOLDEN_AXE" : configured);
        return material == null ? Material.GOLDEN_AXE : material;
    }

    public boolean storeAir() {
        return config().getBoolean("selection.store-air", false);
    }

    public double minPrice() {
        return config().getDouble("marketplace.min-price", 0.0D);
    }

    public double maxPrice() {
        return config().getDouble("marketplace.max-price", 1000000.0D);
    }

    public double defaultRoyaltyPercent() {
        return config().getDouble("marketplace.default-royalty-percent", 90.0D);
    }

    public BlueprintLicenseType defaultLicenseType() {
        return BlueprintLicenseType.from(config().getString("marketplace.default-license-type", "single_use"));
    }

    public BlueprintCategory defaultCategory() {
        return BlueprintCategory.from(config().getString("marketplace.default-category", "other"));
    }

    public BuildStyle defaultBuildStyle() {
        return BuildStyle.from(config().getString("marketplace.default-build-style", "animated"));
    }

    public double maxRoyaltyPercent() {
        return config().getDouble("marketplace.max-royalty-percent", 100.0D);
    }

    public boolean ownersBuildFree() {
        return config().getBoolean("marketplace.owners-build-free", true);
    }

    public boolean blockSelfRoyalty() {
        return config().getBoolean("marketplace.block-self-royalty", true);
    }

    public double similarityThreshold() {
        return config().getDouble("originality.similarity-threshold", 0.92D);
    }

    public double warnThreshold() {
        return config().getDouble("originality.warn-threshold", 0.78D);
    }

    public double derivedThreshold() {
        return config().getDouble("originality.derived-threshold", 0.62D);
    }

    public double reviewThreshold() {
        return config().getDouble("originality.review-threshold", 0.78D);
    }

    public double partialCopyThreshold() {
        return config().getDouble("originality.partial-copy-threshold", 0.48D);
    }

    public double revisionLockThreshold() {
        return config().getDouble("originality.revision-lock-threshold", 0.84D);
    }

    public boolean allowOwnerUpdates() {
        return config().getBoolean("originality.allow-owner-updates", true);
    }

    public boolean preferVault() {
        return config().getBoolean("economy.prefer-vault", true);
    }

    public double internalStartingBalance() {
        return config().getDouble("economy.internal-starting-balance", 2500.0D);
    }

    public String currencySingular() {
        return config().getString("economy.currency-singular", "credit");
    }

    public String currencyPlural() {
        return config().getString("economy.currency-plural", "credits");
    }

    public boolean rotateToPlayerFacing() {
        return config().getBoolean("building.rotate-to-player-facing", true);
    }

    public int blocksPerTick() {
        return Math.max(1, config().getInt("building.blocks-per-tick", 450));
    }

    public boolean previewParticles() {
        return config().getBoolean("building.preview-particles", true);
    }

    public int previewSeconds() {
        return Math.max(1, config().getInt("building.preview-seconds", 8));
    }

    public int previewMaxPoints() {
        return Math.max(50, config().getInt("building.preview-max-points", 750));
    }

    public boolean showroomHologram() {
        return config().getBoolean("building.showroom-hologram", true);
    }

    public String materialReadinessMode() {
        return config().getString("building.material-readiness-mode", "report");
    }

    public boolean blockOnMissingMaterials() {
        return materialReadinessMode().equalsIgnoreCase("block");
    }

    public boolean warnOnCollisions() {
        return config().getBoolean("building.warn-on-collisions", true);
    }

    public boolean warnOnLiquid() {
        return config().getBoolean("building.warn-on-liquid", true);
    }

    public boolean blockOnProtectionDeny() {
        return config().getBoolean("building.block-on-protection-deny", true);
    }

    public boolean exclusiveSaleLock() {
        return config().getBoolean("marketplace.exclusive-sale-lock", true);
    }

    public List<Integer> milestoneRevenueThresholds() {
        return config().getIntegerList("marketplace.milestone-revenue-thresholds");
    }

    public boolean useWorldGuardProtection() {
        return config().getBoolean("integrations.protection.worldguard", true);
    }

    public boolean useTownyProtection() {
        return config().getBoolean("integrations.protection.towny", true);
    }

    public boolean useLandsProtection() {
        return config().getBoolean("integrations.protection.lands", true);
    }

    public boolean useGriefPreventionProtection() {
        return config().getBoolean("integrations.protection.griefprevention", true);
    }

    public boolean useResidenceProtection() {
        return config().getBoolean("integrations.protection.residence", true);
    }

    public boolean usePlotSquaredProtection() {
        return config().getBoolean("integrations.protection.plotsquared", true);
    }

    public String discordWebhookUrl() {
        return config().getString("external.discord.webhook-url", "");
    }

    public boolean discordNotifyReviews() {
        return config().getBoolean("external.discord.notify-reviews", true);
    }

    public boolean discordNotifyDisputes() {
        return config().getBoolean("external.discord.notify-disputes", true);
    }

    public boolean discordNotifyMilestones() {
        return config().getBoolean("external.discord.notify-milestones", true);
    }

    public boolean webPanelEnabled() {
        return config().getBoolean("external.web-panel.enabled", true);
    }

    public int webPanelPort() {
        return config().getInt("external.web-panel.port", 9876);
    }

    public String webPanelToken() {
        return config().getString("external.web-panel.token", "change-me");
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }
}