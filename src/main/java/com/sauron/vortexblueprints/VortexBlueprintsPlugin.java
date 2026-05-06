package com.sauron.vortexblueprints;

import com.sauron.vortexblueprints.command.VortexBlueprintsCommand;
import com.sauron.vortexblueprints.listener.GuiListener;
import com.sauron.vortexblueprints.listener.SelectionListener;
import com.sauron.vortexblueprints.manager.ConfigManager;
import com.sauron.vortexblueprints.manager.DataManager;
import com.sauron.vortexblueprints.manager.SelectionManager;
import com.sauron.vortexblueprints.service.BlueprintCaptureService;
import com.sauron.vortexblueprints.service.BuildService;
import com.sauron.vortexblueprints.service.EconomyService;
import com.sauron.vortexblueprints.service.ExternalBridgeService;
import com.sauron.vortexblueprints.service.ModerationService;
import com.sauron.vortexblueprints.service.OriginalityService;
import com.sauron.vortexblueprints.service.ProtectionService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class VortexBlueprintsPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DataManager dataManager;
    private SelectionManager selectionManager;
    private OriginalityService originalityService;
    private EconomyService economyService;
    private BlueprintCaptureService captureService;
    private BuildService buildService;
    private ProtectionService protectionService;
    private ExternalBridgeService externalBridgeService;
    private ModerationService moderationService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        configManager.load();

        dataManager = new DataManager(this);
        dataManager.initialize();

        selectionManager = new SelectionManager();
        originalityService = new OriginalityService(configManager);
        economyService = new EconomyService(this, configManager, dataManager);
        protectionService = new ProtectionService(this, configManager);
        moderationService = new ModerationService(this);
        externalBridgeService = new ExternalBridgeService(this, configManager, dataManager);
        captureService = new BlueprintCaptureService(this, configManager, dataManager, originalityService);
        buildService = new BuildService(this, configManager, dataManager, economyService, protectionService);
        externalBridgeService.start();

        getServer().getPluginManager().registerEvents(new SelectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        PluginCommand command = getCommand("vortexblueprints");
        if (command == null) {
            throw new IllegalStateException("Command 'vortexblueprints' missing from plugin.yml");
        }
        VortexBlueprintsCommand commandHandler = new VortexBlueprintsCommand(this);
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getLogger().info("VortexBlueprints enabled with " + dataManager.getBlueprints().size() + " blueprint listings.");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveAllNow();
            dataManager.shutdown();
        }
        if (externalBridgeService != null) {
            externalBridgeService.stop();
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    public BlueprintCaptureService getCaptureService() {
        return captureService;
    }

    public BuildService getBuildService() {
        return buildService;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public ProtectionService getProtectionService() {
        return protectionService;
    }

    public ExternalBridgeService getExternalBridgeService() {
        return externalBridgeService;
    }

    public ModerationService getModerationService() {
        return moderationService;
    }
}