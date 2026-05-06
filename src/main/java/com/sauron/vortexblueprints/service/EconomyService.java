package com.sauron.vortexblueprints.service;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.manager.ConfigManager;
import com.sauron.vortexblueprints.manager.DataManager;
import com.sauron.vortexblueprints.model.Account;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class EconomyService {

    private final DataManager dataManager;
    private final Provider provider;

    public EconomyService(VortexBlueprintsPlugin plugin, ConfigManager configManager, DataManager dataManager) {
        this.dataManager = dataManager;
        Provider hookedProvider = null;
        if (configManager.preferVault()) {
            hookedProvider = VaultProvider.tryCreate(plugin).orElse(null);
        }
        this.provider = hookedProvider == null ? new InternalProvider(dataManager, configManager) : hookedProvider;
        plugin.getLogger().info("Economy provider: " + provider.name());
    }

    public boolean withdraw(Player player, double amount) {
        return provider.withdraw(player, amount);
    }

    public void deposit(UUID uuid, String name, double amount) {
        provider.deposit(Bukkit.getOfflinePlayer(uuid), name, amount);
    }

    public double balance(Player player) {
        return provider.balance(player);
    }

    public String format(double amount) {
        return provider.format(amount);
    }

    public boolean isInternal() {
        return provider instanceof InternalProvider;
    }

    public void giveInternal(UUID uuid, String name, double amount) {
        if (!(provider instanceof InternalProvider)) {
            return;
        }
        dataManager.getOrCreateAccount(uuid, name).deposit(amount);
        dataManager.saveAccountsAsync();
    }

    private interface Provider {
        String name();

        double balance(Player player);

        boolean withdraw(Player player, double amount);

        void deposit(OfflinePlayer offlinePlayer, String name, double amount);

        String format(double amount);
    }

    private static final class InternalProvider implements Provider {

        private final DataManager dataManager;
        private final ConfigManager configManager;

        private InternalProvider(DataManager dataManager, ConfigManager configManager) {
            this.dataManager = dataManager;
            this.configManager = configManager;
        }

        @Override
        public String name() {
            return "internal credits";
        }

        @Override
        public double balance(Player player) {
            return dataManager.getOrCreateAccount(player.getUniqueId(), player.getName()).getBalance();
        }

        @Override
        public boolean withdraw(Player player, double amount) {
            Account account = dataManager.getOrCreateAccount(player.getUniqueId(), player.getName());
            boolean success = account.withdraw(amount);
            if (success) {
                dataManager.saveAccountsAsync();
            }
            return success;
        }

        @Override
        public void deposit(OfflinePlayer offlinePlayer, String name, double amount) {
            Account account = dataManager.getOrCreateAccount(offlinePlayer.getUniqueId(), name);
            account.deposit(amount);
            dataManager.saveAccountsAsync();
        }

        @Override
        public String format(double amount) {
            String currency = Math.abs(amount - 1.0D) < 0.0001D ? configManager.currencySingular() : configManager.currencyPlural();
            return MessageUtil.number(amount) + " " + currency;
        }
    }

    private static final class VaultProvider implements Provider {

        private final Object economy;
        private final Class<?> economyClass;

        private VaultProvider(Object economy, Class<?> economyClass) {
            this.economy = economy;
            this.economyClass = economyClass;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Optional<Provider> tryCreate(VortexBlueprintsPlugin plugin) {
            if (!plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
                return Optional.empty();
            }
            try {
                Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider registration = Bukkit.getServicesManager().getRegistration((Class) economyClass);
                if (registration == null || registration.getProvider() == null) {
                    return Optional.empty();
                }
                return Optional.of(new VaultProvider(registration.getProvider(), economyClass));
            } catch (ReflectiveOperationException exception) {
                plugin.getLogger().warning("Vault was found but could not be hooked: " + exception.getMessage());
                return Optional.empty();
            }
        }

        @Override
        public String name() {
            return "Vault";
        }

        @Override
        public double balance(Player player) {
            try {
                Method method = economyClass.getMethod("getBalance", OfflinePlayer.class);
                return ((Number) method.invoke(economy, player)).doubleValue();
            } catch (ReflectiveOperationException exception) {
                return 0.0D;
            }
        }

        @Override
        public boolean withdraw(Player player, double amount) {
            if (amount <= 0.0D) {
                return true;
            }
            return transaction("withdrawPlayer", player, amount);
        }

        @Override
        public void deposit(OfflinePlayer offlinePlayer, String name, double amount) {
            if (amount <= 0.0D) {
                return;
            }
            transaction("depositPlayer", offlinePlayer, amount);
        }

        @Override
        public String format(double amount) {
            try {
                Method method = economyClass.getMethod("format", double.class);
                return String.valueOf(method.invoke(economy, amount));
            } catch (ReflectiveOperationException exception) {
                return MessageUtil.number(amount);
            }
        }

        private boolean transaction(String methodName, OfflinePlayer offlinePlayer, double amount) {
            try {
                Method method = economyClass.getMethod(methodName, OfflinePlayer.class, double.class);
                Object response = method.invoke(economy, offlinePlayer, amount);
                Field successField = response.getClass().getField("transactionSuccess");
                return successField.getBoolean(response);
            } catch (ReflectiveOperationException exception) {
                return false;
            }
        }
    }
}