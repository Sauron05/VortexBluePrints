package com.sauron.vortexblueprints.service;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.manager.ConfigManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ProtectionService {

    private final List<ProtectionHook> hooks = new ArrayList<>();
    private final List<HookRegistration> registrations = new ArrayList<>();

    public ProtectionService(VortexBlueprintsPlugin plugin, ConfigManager configManager) {
        registerHook(plugin, "WorldGuard", configManager.useWorldGuardProtection(), "WorldGuard", () -> new WorldGuardHook(plugin));
        registerHook(plugin, "Towny", configManager.useTownyProtection(), "Towny", TownyHook::new);
        registerHook(plugin, "Lands", configManager.useLandsProtection(), "Lands", () -> new LandsHook(plugin));
        registerHook(plugin, "GriefPrevention", configManager.useGriefPreventionProtection(), "GriefPrevention", GriefPreventionHook::new);
        registerHook(plugin, "Residence", configManager.useResidenceProtection(), "Residence", ResidenceHook::new);
        registerHook(plugin, "PlotSquared", configManager.usePlotSquaredProtection(), "PlotSquared", PlotSquaredHook::new);
        plugin.getLogger().info("Protection hook states: " + registrations.stream().map(registration -> registration.name() + "=" + registration.status()).toList());
    }

    public Optional<String> firstDenial(Player player, Location location) {
        for (ProtectionHook hook : hooks) {
            Optional<String> denial = hook.deniedBy(player, location);
            if (denial.isPresent()) {
                return denial;
            }
        }
        return Optional.empty();
    }

    public List<ProtectionDiagnosis> diagnose(Player player, Location location) {
        List<ProtectionDiagnosis> diagnoses = new ArrayList<>();
        for (HookRegistration registration : registrations) {
            if (registration.hook() == null) {
                diagnoses.add(new ProtectionDiagnosis(registration.name(), false, false, registration.status()));
                continue;
            }
            Optional<String> denial = registration.hook().deniedBy(player, location);
            diagnoses.add(new ProtectionDiagnosis(
                registration.name(),
                true,
                denial.isPresent(),
                denial.isPresent() ? "build denied at location" : "allowed at location"
            ));
        }
        return List.copyOf(diagnoses);
    }

    private void registerHook(VortexBlueprintsPlugin plugin, String name, boolean enabledInConfig, String dependencyName, Supplier<ProtectionHook> factory) {
        if (!enabledInConfig) {
            registrations.add(new HookRegistration(name, "disabled in config", null));
            return;
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled(dependencyName)) {
            registrations.add(new HookRegistration(name, "plugin not installed", null));
            return;
        }
        ProtectionHook hook = factory.get();
        hooks.add(hook);
        registrations.add(new HookRegistration(name, "active", hook));
    }

    public record ProtectionDiagnosis(String hookName, boolean active, boolean denied, String detail) {
    }

    private record HookRegistration(String name, String status, ProtectionHook hook) {
    }

    private interface ProtectionHook {
        String name();

        Optional<String> deniedBy(Player player, Location location);
    }

    private static final class WorldGuardHook implements ProtectionHook {

        private final Plugin worldGuardPlugin;

        private WorldGuardHook(VortexBlueprintsPlugin plugin) {
            this.worldGuardPlugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        }

        @Override
        public String name() {
            return "WorldGuard";
        }

        @Override
        public Optional<String> deniedBy(Player player, Location location) {
            try {
                Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard");
                Object worldGuard = worldGuardClass.getMethod("getInstance").invoke(null);
                Object platform = worldGuardClass.getMethod("getPlatform").invoke(worldGuard);
                Object regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                Object query = regionContainer.getClass().getMethod("createQuery").invoke(regionContainer);
                Class<?> adapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                Object adaptedLocation = adapterClass.getMethod("adapt", Location.class).invoke(null, location);
                Object localPlayer = worldGuardPlugin.getClass().getMethod("wrapPlayer", Player.class).invoke(worldGuardPlugin, player);
                Object result = invokeCompatibleMethod(query, "testBuild", adaptedLocation, localPlayer);
                if (result instanceof Boolean allowed && !allowed) {
                    return Optional.of(name());
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return Optional.empty();
        }
    }

    private static final class TownyHook implements ProtectionHook {

        @Override
        public String name() {
            return "Towny";
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public Optional<String> deniedBy(Player player, Location location) {
            try {
                Class<?> actionTypeClass = Class.forName("com.palmergames.bukkit.towny.object.TownyPermission$ActionType");
                Object actionType = Enum.valueOf((Class<Enum>) actionTypeClass.asSubclass(Enum.class), "BUILD");
                Class<?> cacheUtilClass = Class.forName("com.palmergames.bukkit.towny.utils.PlayerCacheUtil");
                Method method = cacheUtilClass.getMethod("getCachePermission", Player.class, Location.class, Material.class, actionTypeClass);
                boolean allowed = (boolean) method.invoke(null, player, location, Material.STONE, actionType);
                return allowed ? Optional.empty() : Optional.of(name());
            } catch (ReflectiveOperationException ignored) {
                return Optional.empty();
            }
        }
    }

    private static final class LandsHook implements ProtectionHook {

        private final VortexBlueprintsPlugin plugin;

        private LandsHook(VortexBlueprintsPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public String name() {
            return "Lands";
        }

        @Override
        public Optional<String> deniedBy(Player player, Location location) {
            try {
                Class<?> integrationClass = Class.forName("me.angeschossen.lands.api.integration.LandsIntegration");
                Object integration;
                try {
                    integration = integrationClass.getConstructor(Plugin.class).newInstance(plugin);
                } catch (NoSuchMethodException exception) {
                    integration = integrationClass.getMethod("of", Plugin.class).invoke(null, plugin);
                }
                Object area = integrationClass.getMethod("getArea", Location.class).invoke(integration, location);
                if (area == null) {
                    return Optional.empty();
                }
                Object result = invokeCompatibleMethod(area, "hasRoleFlag", player.getUniqueId(), "BLOCK_PLACE");
                if (result instanceof Boolean allowed && !allowed) {
                    return Optional.of(name());
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return Optional.empty();
        }
    }

    private static final class GriefPreventionHook implements ProtectionHook {

        @Override
        public String name() {
            return "GriefPrevention";
        }

        @Override
        public Optional<String> deniedBy(Player player, Location location) {
            try {
                Class<?> griefPreventionClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
                Field instanceField = griefPreventionClass.getField("instance");
                Object griefPrevention = instanceField.get(null);
                Field dataStoreField = griefPreventionClass.getField("dataStore");
                Object dataStore = dataStoreField.get(griefPrevention);
                Object claim = invokeCompatibleMethod(dataStore, "getClaimAt", location, true, null);
                if (claim == null) {
                    return Optional.empty();
                }
                Object result = invokeCompatibleMethod(claim, "allowBuild", player, Material.STONE);
                if (result instanceof String denialReason && !denialReason.isBlank()) {
                    return Optional.of(name());
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return Optional.empty();
        }
    }

    private static final class ResidenceHook implements ProtectionHook {

        @Override
        public String name() {
            return "Residence";
        }

        @Override
        public Optional<String> deniedBy(Player player, Location location) {
            try {
                Class<?> apiClass = Class.forName("com.bekvon.bukkit.residence.api.ResidenceApi");
                Object manager = apiClass.getMethod("getResidenceManager").invoke(null);
                Object residence = invokeCompatibleMethod(manager, "getByLoc", location);
                if (residence == null) {
                    return Optional.empty();
                }
                Object permissions = invokeCompatibleMethod(residence, "getPermissions");
                if (permissions == null) {
                    return Optional.empty();
                }
                Object result = invokeCompatibleMethod(permissions, "playerHas", player, "build", true);
                if (result instanceof Boolean allowed && !allowed) {
                    return Optional.of(name());
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return Optional.empty();
        }
    }

    private static final class PlotSquaredHook implements ProtectionHook {

        @Override
        public String name() {
            return "PlotSquared";
        }

        @Override
        public Optional<String> deniedBy(Player player, Location location) {
            try {
                Class<?> plotPlayerClass = Class.forName("com.plotsquared.core.player.PlotPlayer");
                Object plotPlayer = invokeCompatibleMethod(plotPlayerClass, "from", player);
                Object currentPlot = invokeCompatibleMethod(plotPlayer, "getCurrentPlot");
                if (currentPlot == null) {
                    return Optional.empty();
                }
                Object allowed = invokeCompatibleMethod(currentPlot, "isAdded", player.getUniqueId());
                if (allowed instanceof Boolean added && !added) {
                    return Optional.of(name());
                }
            } catch (ReflectiveOperationException ignored) {
            }
            return Optional.empty();
        }
    }

    private static Object invokeCompatibleMethod(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Class<?> searchType = target instanceof Class<?> clazz ? clazz : target.getClass();
        for (Method method : searchType.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int index = 0; index < parameterTypes.length; index++) {
                Object argument = args[index];
                if (argument == null) {
                    continue;
                }
                Class<?> wrappedParameter = wrap(parameterTypes[index]);
                if (!wrappedParameter.isAssignableFrom(argument.getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) {
                continue;
            }
            return method.invoke(target instanceof Class<?> ? null : target, args);
        }
        throw new NoSuchMethodException(methodName);
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        return type;
    }
}