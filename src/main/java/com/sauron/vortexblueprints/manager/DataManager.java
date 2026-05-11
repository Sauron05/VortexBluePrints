package com.sauron.vortexblueprints.manager;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.model.Account;
import com.sauron.vortexblueprints.model.BlockEntry;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintCollection;
import com.sauron.vortexblueprints.model.BlueprintLicenseType;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.BlueprintStatus;
import com.sauron.vortexblueprints.model.BuildStyle;
import com.sauron.vortexblueprints.model.CreatorProfile;
import com.sauron.vortexblueprints.model.CreatorAnalytics;
import com.sauron.vortexblueprints.model.CreatorBadge;
import com.sauron.vortexblueprints.model.DisputeRecord;
import com.sauron.vortexblueprints.model.LedgerEntry;
import com.sauron.vortexblueprints.model.OriginalityClassification;
import com.sauron.vortexblueprints.model.OwnerShare;
import com.sauron.vortexblueprints.model.PurchaseRecord;
import com.sauron.vortexblueprints.model.RatingSummary;
import com.sauron.vortexblueprints.model.ReviewTicket;
import com.sauron.vortexblueprints.model.RoyaltyTier;
import com.sauron.vortexblueprints.model.SocialState;
import com.sauron.vortexblueprints.model.TimelineEntry;
import com.sauron.vortexblueprints.service.MarketQuery;
import com.sauron.vortexblueprints.service.SearchIndexService;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DataManager {

    private final VortexBlueprintsPlugin plugin;
    private final Map<String, BlueprintListing> blueprints = new ConcurrentHashMap<>();
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
    private final Map<UUID, CreatorProfile> creatorProfiles = new ConcurrentHashMap<>();
    private final Map<String, BlueprintCollection> collections = new ConcurrentHashMap<>();
    private final Map<String, PurchaseRecord> purchases = new ConcurrentHashMap<>();
    private final Map<String, DisputeRecord> disputes = new ConcurrentHashMap<>();
    private final Map<String, ReviewTicket> reviewTickets = new ConcurrentHashMap<>();
    private final Map<UUID, SocialState> socialStates = new ConcurrentHashMap<>();
    private final List<LedgerEntry> ledgerEntries = java.util.Collections.synchronizedList(new ArrayList<>());
    private final SearchIndexService searchIndexService = new SearchIndexService();
    private final ExecutorService ioExecutor;
    private String jdbcUrl;
    private Properties jdbcProperties;

    public DataManager(VortexBlueprintsPlugin plugin) {
        this.plugin = plugin;
        this.ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, plugin.getName() + "-storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void initialize() {
        configureConnection();
        createTables();
        loadAll();
        rebuildIndex();
    }

    public Collection<BlueprintListing> getBlueprints() {
        return List.copyOf(blueprints.values());
    }

    public List<BlueprintListing> getMarketplaceListings() {
        return searchBlueprints(MarketQuery.defaultQuery());
    }

    public List<BlueprintListing> searchBlueprints(MarketQuery query) {
        return searchIndexService.search(query == null ? MarketQuery.defaultQuery() : query);
    }

    public Optional<BlueprintListing> getBlueprint(String id) {
        return Optional.ofNullable(blueprints.get(normalizeId(id)));
    }

    public void putBlueprint(BlueprintListing listing) {
        blueprints.put(normalizeId(listing.getId()), listing);
        CreatorProfile profile = getOrCreateCreatorProfile(listing.getOwnerId(), listing.getOwnerName());
        if (profile.getFeaturedBlueprintIds().isEmpty()) {
            profile.featureBlueprint(listing.getId());
            saveCreatorProfileAsync(profile);
        }
        rebuildIndex();
        ioExecutor.execute(() -> saveBlueprintNow(listing));
    }

    public void deleteBlueprint(String id) {
        String normalizedId = normalizeId(id);
        BlueprintListing removed = blueprints.remove(normalizedId);
        if (removed != null) {
            CreatorProfile profile = creatorProfiles.get(removed.getOwnerId());
            if (profile != null && profile.unfeatureBlueprint(normalizedId)) {
                saveCreatorProfileAsync(profile);
            }
            for (BlueprintCollection collection : collections.values()) {
                if (collection.removeBlueprint(normalizedId)) {
                    saveCollectionAsync(collection);
                }
            }
            for (SocialState state : socialStates.values()) {
                if (state.removeFromWishlist(normalizedId)) {
                    saveSocialStateAsync(state);
                }
            }
        }
        rebuildIndex();
        ioExecutor.execute(() -> {
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM blueprints WHERE id = ?")) {
                statement.setString(1, normalizedId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().warning("Failed to delete blueprint " + normalizedId + ": " + exception.getMessage());
            }
        });
    }

    public Account getOrCreateAccount(UUID uuid, String name) {
        return accounts.compute(uuid, (ignoredUuid, existingAccount) -> {
            if (existingAccount == null) {
                return new Account(uuid, name, plugin.getConfigManager().internalStartingBalance(), 0.0D, 0.0D);
            }
            existingAccount.setName(name);
            return existingAccount;
        });
    }

    public Optional<Account> getAccount(UUID uuid) {
        return Optional.ofNullable(accounts.get(uuid));
    }

    public CreatorProfile getOrCreateCreatorProfile(UUID creatorId, String creatorName) {
        return creatorProfiles.compute(creatorId, (ignoredUuid, existingProfile) -> {
            if (existingProfile == null) {
                return new CreatorProfile(creatorId, creatorName);
            }
            existingProfile.setCreatorName(creatorName);
            return existingProfile;
        });
    }

    public Optional<CreatorProfile> getCreatorProfile(UUID creatorId) {
        return Optional.ofNullable(creatorProfiles.get(creatorId));
    }

    public List<CreatorProfile> getCreatorProfiles() {
        for (BlueprintListing listing : blueprints.values()) {
            creatorProfiles.computeIfAbsent(listing.getOwnerId(), ignoredUuid -> new CreatorProfile(listing.getOwnerId(), listing.getOwnerName()));
        }
        return creatorProfiles.values().stream()
            .sorted(java.util.Comparator.comparingLong(CreatorProfile::getUpdatedAt).reversed())
            .toList();
    }

    public Optional<CreatorProfile> findCreatorProfileByName(String creatorName) {
        if (creatorName == null || creatorName.isBlank()) {
            return Optional.empty();
        }
        Optional<CreatorProfile> existing = creatorProfiles.values().stream()
            .filter(profile -> profile.getCreatorName().equalsIgnoreCase(creatorName))
            .findFirst();
        if (existing.isPresent()) {
            return existing;
        }
        return blueprints.values().stream()
            .filter(listing -> listing.getOwnerName().equalsIgnoreCase(creatorName))
            .findFirst()
            .map(listing -> getOrCreateCreatorProfile(listing.getOwnerId(), listing.getOwnerName()));
    }

    public List<BlueprintListing> getBlueprintsByOwner(UUID ownerId) {
        return blueprints.values().stream()
            .filter(listing -> listing.isOwner(ownerId))
            .sorted(java.util.Comparator.comparingLong(BlueprintListing::getUpdatedAt).reversed())
            .toList();
    }

    public void saveCreatorProfileAsync(CreatorProfile profile) {
        creatorProfiles.put(profile.getCreatorId(), profile);
        ioExecutor.execute(() -> saveCreatorProfileNow(profile));
    }

    public List<BlueprintCollection> getCollections() {
        return collections.values().stream()
            .sorted(java.util.Comparator.comparing(BlueprintCollection::isFeatured).reversed()
                .thenComparingLong(BlueprintCollection::getUpdatedAt).reversed())
            .toList();
    }

    public List<BlueprintCollection> getCollectionsByOwner(UUID ownerId) {
        return collections.values().stream()
            .filter(collection -> collection.getOwnerId().equals(ownerId))
            .sorted(java.util.Comparator.comparingLong(BlueprintCollection::getUpdatedAt).reversed())
            .toList();
    }

    public Optional<BlueprintCollection> getCollection(String id) {
        return Optional.ofNullable(collections.get(normalizeId(id)));
    }

    public List<BlueprintCollection> getCollectionsContaining(String blueprintId) {
        String normalizedId = normalizeId(blueprintId);
        return collections.values().stream()
            .filter(collection -> collection.getBlueprintIds().contains(normalizedId))
            .sorted(java.util.Comparator.comparing(BlueprintCollection::isFeatured).reversed()
                .thenComparingLong(BlueprintCollection::getUpdatedAt).reversed())
            .toList();
    }

    public void saveCollectionAsync(BlueprintCollection collection) {
        collections.put(normalizeId(collection.getId()), collection);
        ioExecutor.execute(() -> saveCollectionNow(collection));
    }

    public void deleteCollection(String id) {
        String normalizedId = normalizeId(id);
        collections.remove(normalizedId);
        for (CreatorProfile profile : creatorProfiles.values()) {
            if (profile.unpinCollection(normalizedId)) {
                saveCreatorProfileAsync(profile);
            }
        }
        ioExecutor.execute(() -> {
            try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM blueprint_collections WHERE id = ?")) {
                statement.setString(1, normalizedId);
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().warning("Failed to delete collection " + normalizedId + ": " + exception.getMessage());
            }
        });
    }

    public SocialState getOrCreateSocialState(UUID playerId, String playerName) {
        return socialStates.compute(playerId, (ignoredUuid, existingState) -> {
            if (existingState == null) {
                return new SocialState(playerId, playerName);
            }
            existingState.setPlayerName(playerName);
            return existingState;
        });
    }

    public Optional<SocialState> getSocialState(UUID playerId) {
        return Optional.ofNullable(socialStates.get(playerId));
    }

    public void saveSocialStateAsync(SocialState state) {
        socialStates.put(state.getPlayerId(), state);
        ioExecutor.execute(() -> saveSocialStateNow(state));
    }

    public boolean isWishlisted(UUID playerId, String blueprintId) {
        SocialState state = socialStates.get(playerId);
        return state != null && state.hasWishlisted(blueprintId);
    }

    public long getWishlistCount(String blueprintId) {
        String normalizedId = normalizeId(blueprintId);
        return socialStates.values().stream().filter(state -> state.hasWishlisted(normalizedId)).count();
    }

    public List<BlueprintListing> getWishlistListings(UUID playerId) {
        SocialState state = socialStates.get(playerId);
        if (state == null) {
            return List.of();
        }
        return state.getWishlistBlueprintIds().stream()
            .map(this::getBlueprint)
            .flatMap(Optional::stream)
            .filter(listing -> listing.getStatus() == BlueprintStatus.LIVE)
            .sorted(java.util.Comparator.comparingDouble(BlueprintListing::getAverageRating).reversed()
                .thenComparingLong(BlueprintListing::getBuilds).reversed())
            .toList();
    }

    public boolean isFollowing(UUID playerId, UUID creatorId) {
        SocialState state = socialStates.get(playerId);
        return state != null && state.isFollowing(creatorId);
    }

    public long getFollowerCount(UUID creatorId) {
        return socialStates.values().stream().filter(state -> state.isFollowing(creatorId)).count();
    }

    public List<CreatorProfile> getFollowedCreatorProfiles(UUID playerId) {
        SocialState state = socialStates.get(playerId);
        if (state == null) {
            return List.of();
        }
        return state.getFollowedCreators().stream()
            .map(creatorProfiles::get)
            .filter(java.util.Objects::nonNull)
            .sorted(java.util.Comparator.comparingLong(CreatorProfile::getUpdatedAt).reversed())
            .toList();
    }

    public Optional<PurchaseRecord> getPurchase(String blueprintId, UUID buyerId) {
        return Optional.ofNullable(purchases.get(purchaseKey(blueprintId, buyerId)));
    }

    public List<PurchaseRecord> getPurchasesForBlueprint(String blueprintId) {
        String normalizedId = normalizeId(blueprintId);
        return purchases.values().stream()
            .filter(record -> record.getBlueprintId().equalsIgnoreCase(normalizedId))
            .toList();
    }

    public List<PurchaseRecord> getPurchasesForBuyer(UUID buyerId) {
        return purchases.values().stream()
            .filter(record -> record.getBuyerId().equals(buyerId))
            .toList();
    }

    public void savePurchaseAsync(PurchaseRecord purchaseRecord) {
        purchases.put(purchaseKey(purchaseRecord.getBlueprintId(), purchaseRecord.getBuyerId()), purchaseRecord);
        ioExecutor.execute(() -> savePurchaseNow(purchaseRecord));
    }

    public List<ReviewTicket> getReviewTickets() {
        return reviewTickets.values().stream()
            .sorted(java.util.Comparator.comparingLong(ReviewTicket::getCreatedAt).reversed())
            .toList();
    }

    public Optional<ReviewTicket> getReviewTicket(String id) {
        return Optional.ofNullable(reviewTickets.get(id));
    }

    public void saveReviewTicketAsync(ReviewTicket ticket) {
        reviewTickets.put(ticket.getId(), ticket);
        ioExecutor.execute(() -> saveReviewTicketNow(ticket));
    }

    public List<DisputeRecord> getDisputes() {
        return disputes.values().stream()
            .sorted(java.util.Comparator.comparingLong(DisputeRecord::getCreatedAt).reversed())
            .toList();
    }

    public Optional<DisputeRecord> getDispute(String id) {
        return Optional.ofNullable(disputes.get(id));
    }

    public void saveDisputeAsync(DisputeRecord disputeRecord) {
        disputes.put(disputeRecord.getId(), disputeRecord);
        ioExecutor.execute(() -> saveDisputeNow(disputeRecord));
    }

    public List<LedgerEntry> getLedgerEntries() {
        synchronized (ledgerEntries) {
            return List.copyOf(ledgerEntries);
        }
    }

    public List<LedgerEntry> getLedgerForBlueprint(String blueprintId) {
        synchronized (ledgerEntries) {
            return ledgerEntries.stream().filter(entry -> entry.blueprintId().equalsIgnoreCase(blueprintId)).toList();
        }
    }

    public void appendLedgerAsync(LedgerEntry ledgerEntry) {
        synchronized (ledgerEntries) {
            ledgerEntries.add(ledgerEntry);
        }
        ioExecutor.execute(() -> saveLedgerNow(ledgerEntry));
    }

    public void saveAccountsAsync() {
        ioExecutor.execute(this::saveAccountsNow);
    }

    public void saveBlueprintAsync(BlueprintListing listing) {
        ioExecutor.execute(() -> saveBlueprintNow(listing));
    }

    public void saveAllNow() {
        for (BlueprintListing listing : blueprints.values()) {
            saveBlueprintNow(listing);
        }
        saveAccountsNow();
        for (CreatorProfile profile : creatorProfiles.values()) {
            saveCreatorProfileNow(profile);
        }
        for (BlueprintCollection collection : collections.values()) {
            saveCollectionNow(collection);
        }
        for (PurchaseRecord purchaseRecord : purchases.values()) {
            savePurchaseNow(purchaseRecord);
        }
        for (DisputeRecord disputeRecord : disputes.values()) {
            saveDisputeNow(disputeRecord);
        }
        for (ReviewTicket ticket : reviewTickets.values()) {
            saveReviewTicketNow(ticket);
        }
        for (SocialState state : socialStates.values()) {
            saveSocialStateNow(state);
        }
        synchronized (ledgerEntries) {
            for (LedgerEntry ledgerEntry : ledgerEntries) {
                saveLedgerNow(ledgerEntry);
            }
        }
    }

    public CreatorAnalytics analyticsFor(UUID creatorId) {
        List<BlueprintListing> ownedListings = blueprints.values().stream()
            .filter(listing -> listing.isOwner(creatorId))
            .toList();
        long totalViews = ownedListings.stream().mapToLong(BlueprintListing::getViews).sum();
        long totalPurchases = ownedListings.stream().mapToLong(BlueprintListing::getPurchases).sum();
        long totalBuilds = ownedListings.stream().mapToLong(BlueprintListing::getBuilds).sum();
        long repeatBuyers = ownedListings.stream().mapToLong(BlueprintListing::getRepeatBuyers).sum();
        double totalRoyalties = ownedListings.stream().mapToDouble(BlueprintListing::getTotalRoyaltiesPaid).sum();
        double totalRevenue = ownedListings.stream().mapToDouble(BlueprintListing::getTotalRevenue).sum();
        double conversionRate = totalViews <= 0 ? 0.0D : (double) totalPurchases / totalViews;
        Map<BlueprintCategory, Long> topCategories = ownedListings.stream().collect(java.util.stream.Collectors.groupingBy(BlueprintListing::getCategory, java.util.stream.Collectors.counting()));
        Set<CreatorBadge> badges = java.util.EnumSet.noneOf(CreatorBadge.class);
        ownedListings.forEach(listing -> badges.addAll(listing.getBadges()));
        int milestoneLevel = ownedListings.stream().mapToInt(BlueprintListing::getMilestoneLevel).max().orElse(0);
        List<String> topBlueprints = ownedListings.stream()
            .sorted(java.util.Comparator.comparingLong(BlueprintListing::getBuilds).reversed())
            .limit(5)
            .map(BlueprintListing::getId)
            .toList();
        return new CreatorAnalytics(totalViews, totalPurchases, totalBuilds, repeatBuyers, totalRoyalties, totalRevenue, conversionRate, milestoneLevel, topCategories, badges, topBlueprints);
    }

    public void shutdown() {
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ioExecutor.shutdownNow();
        }
    }

    private void configureConnection() {
        String type = plugin.getConfig().getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
        jdbcProperties = new Properties();
        if (type.equals("mysql")) {
            String host = plugin.getConfig().getString("storage.mysql.host", "127.0.0.1");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String database = plugin.getConfig().getString("storage.mysql.database", "vortexblueprints");
            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            jdbcProperties.setProperty("user", plugin.getConfig().getString("storage.mysql.username", "root"));
            jdbcProperties.setProperty("password", plugin.getConfig().getString("storage.mysql.password", ""));
            return;
        }
        File sqliteFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("storage.sqlite.file", "marketplace.db"));
        if (sqliteFile.getParentFile() != null) {
            sqliteFile.getParentFile().mkdirs();
        }
        jdbcUrl = "jdbc:sqlite:" + sqliteFile.getAbsolutePath();
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, jdbcProperties);
    }

    private void createTables() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS blueprints (id TEXT PRIMARY KEY, owner_uuid TEXT NOT NULL, owner_name TEXT NOT NULL, status TEXT NOT NULL, category TEXT NOT NULL, price DOUBLE NOT NULL, featured INTEGER NOT NULL, staff_pick INTEGER NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL, views BIGINT NOT NULL, purchases BIGINT NOT NULL, builds BIGINT NOT NULL, total_revenue DOUBLE NOT NULL, total_royalties DOUBLE NOT NULL, payload TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS accounts (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, balance DOUBLE NOT NULL, earned DOUBLE NOT NULL, spent DOUBLE NOT NULL, purchases BIGINT NOT NULL, builds BIGINT NOT NULL, sales BIGINT NOT NULL, reputation DOUBLE NOT NULL, last_seen BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS creator_profiles (creator_uuid TEXT PRIMARY KEY, creator_name TEXT NOT NULL, updated_at BIGINT NOT NULL, payload TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS blueprint_collections (id TEXT PRIMARY KEY, owner_uuid TEXT NOT NULL, owner_name TEXT NOT NULL, featured INTEGER NOT NULL, updated_at BIGINT NOT NULL, payload TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS purchases (purchase_key TEXT PRIMARY KEY, blueprint_id TEXT NOT NULL, buyer_uuid TEXT NOT NULL, buyer_name TEXT NOT NULL, license_type TEXT NOT NULL, uses_remaining INTEGER NOT NULL, total_purchases INTEGER NOT NULL, total_builds INTEGER NOT NULL, prepaid_value DOUBLE NOT NULL, first_purchased_at BIGINT NOT NULL, last_purchase_at BIGINT NOT NULL, last_build_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS disputes (id TEXT PRIMARY KEY, blueprint_id TEXT NOT NULL, against_blueprint_id TEXT NOT NULL, reporter_uuid TEXT NOT NULL, reporter_name TEXT NOT NULL, evidence TEXT NOT NULL, notes TEXT NOT NULL, status TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS review_tickets (id TEXT PRIMARY KEY, blueprint_id TEXT NOT NULL, creator_uuid TEXT NOT NULL, creator_name TEXT NOT NULL, reason TEXT NOT NULL, classification TEXT NOT NULL, similarity DOUBLE NOT NULL, moderator_notes TEXT NOT NULL, status TEXT NOT NULL, created_at BIGINT NOT NULL, updated_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS social_states (player_uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, updated_at BIGINT NOT NULL, payload TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS ledger (id BIGINT PRIMARY KEY, created_at BIGINT NOT NULL, type TEXT NOT NULL, blueprint_id TEXT NOT NULL, actor_uuid TEXT NOT NULL, actor_name TEXT NOT NULL, amount DOUBLE NOT NULL, payload TEXT NOT NULL)");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize database", exception);
        }
    }

    private void loadAll() {
        loadBlueprints();
        loadAccounts();
        loadCreatorProfiles();
        loadCollections();
        loadPurchases();
        loadDisputes();
        loadReviewTickets();
        loadSocialStates();
        loadLedger();
    }

    private void loadBlueprints() {
        blueprints.clear();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT payload FROM blueprints")) {
            while (resultSet.next()) {
                deserializeBlueprint(resultSet.getString("payload")).ifPresent(listing -> blueprints.put(normalizeId(listing.getId()), listing));
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to load blueprints: " + exception.getMessage());
        }
    }

    private void loadAccounts() {
        accounts.clear();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT * FROM accounts")) {
            while (resultSet.next()) {
                UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                Account account = new Account(uuid, resultSet.getString("name"), resultSet.getDouble("balance"), resultSet.getDouble("earned"), resultSet.getDouble("spent"));
                account.load(resultSet.getLong("purchases"), resultSet.getLong("builds"), resultSet.getLong("sales"), resultSet.getDouble("reputation"), resultSet.getLong("last_seen"));
                accounts.put(uuid, account);
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to load accounts: " + exception.getMessage());
        }
    }

    private void loadCreatorProfiles() {
        creatorProfiles.clear();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT payload FROM creator_profiles")) {
            while (resultSet.next()) {
                deserializeCreatorProfile(resultSet.getString("payload")).ifPresent(profile -> creatorProfiles.put(profile.getCreatorId(), profile));
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to load creator profiles: " + exception.getMessage());
        }
    }

    private void loadCollections() {
        collections.clear();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT payload FROM blueprint_collections")) {
            while (resultSet.next()) {
                deserializeCollection(resultSet.getString("payload")).ifPresent(collection -> collections.put(normalizeId(collection.getId()), collection));
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to load collections: " + exception.getMessage());
        }
    }

    private void loadPurchases() {
        purchases.clear();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT * FROM purchases")) {
            while (resultSet.next()) {
                PurchaseRecord purchaseRecord = new PurchaseRecord(
                    resultSet.getString("blueprint_id"),
                    UUID.fromString(resultSet.getString("buyer_uuid")),
                    resultSet.getString("buyer_name"),
                    BlueprintLicenseType.from(resultSet.getString("license_type")),
                    resultSet.getLong("first_purchased_at")
                );
                purchaseRecord.load(
                    resultSet.getInt("uses_remaining"),
                    resultSet.getInt("total_purchases"),
                    resultSet.getInt("total_builds"),
                    resultSet.getDouble("prepaid_value"),
                    resultSet.getLong("first_purchased_at"),
                    resultSet.getLong("last_purchase_at"),
                    resultSet.getLong("last_build_at")
                );
                purchases.put(resultSet.getString("purchase_key"), purchaseRecord);
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to load purchases: " + exception.getMessage());
        }
    }

    private void loadDisputes() {
        disputes.clear();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT * FROM disputes")) {
            while (resultSet.next()) {
                DisputeRecord disputeRecord = new DisputeRecord(
                    resultSet.getString("id"),
                    resultSet.getString("blueprint_id"),
                    resultSet.getString("against_blueprint_id"),
                    UUID.fromString(resultSet.getString("reporter_uuid")),
                    resultSet.getString("reporter_name"),
                    resultSet.getString("evidence"),
                    resultSet.getLong("created_at")
                );
                disputeRecord.load(DisputeRecord.Status.valueOf(resultSet.getString("status")), resultSet.getString("notes"), resultSet.getLong("updated_at"));
                disputes.put(disputeRecord.getId(), disputeRecord);
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to load disputes: " + exception.getMessage());
        }
    }

    private void loadReviewTickets() {
        reviewTickets.clear();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT * FROM review_tickets")) {
            while (resultSet.next()) {
                ReviewTicket ticket = new ReviewTicket(
                    resultSet.getString("id"),
                    resultSet.getString("blueprint_id"),
                    UUID.fromString(resultSet.getString("creator_uuid")),
                    resultSet.getString("creator_name"),
                    resultSet.getString("reason"),
                    OriginalityClassification.valueOf(resultSet.getString("classification")),
                    resultSet.getDouble("similarity"),
                    resultSet.getLong("created_at")
                );
                ticket.load(ReviewTicket.Status.valueOf(resultSet.getString("status")), resultSet.getString("moderator_notes"), resultSet.getLong("updated_at"));
                reviewTickets.put(ticket.getId(), ticket);
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to load review tickets: " + exception.getMessage());
        }
    }

    private void loadSocialStates() {
        socialStates.clear();
        try (Connection connection = openConnection(); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT payload FROM social_states")) {
            while (resultSet.next()) {
                deserializeSocialState(resultSet.getString("payload")).ifPresent(state -> socialStates.put(state.getPlayerId(), state));
            }
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to load social states: " + exception.getMessage());
        }
    }

    private void loadLedger() {
        synchronized (ledgerEntries) {
            ledgerEntries.clear();
            try (Connection connection = openConnection(); Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT * FROM ledger ORDER BY created_at ASC")) {
                while (resultSet.next()) {
                    ledgerEntries.add(new LedgerEntry(
                        resultSet.getLong("id"),
                        resultSet.getLong("created_at"),
                        resultSet.getString("type"),
                        resultSet.getString("blueprint_id"),
                        UUID.fromString(resultSet.getString("actor_uuid")),
                        resultSet.getString("actor_name"),
                        resultSet.getDouble("amount"),
                        resultSet.getString("payload")
                    ));
                }
            } catch (SQLException exception) {
                plugin.getLogger().warning("Failed to load ledger: " + exception.getMessage());
            }
        }
    }

    private void saveBlueprintNow(BlueprintListing listing) {
        String payload = serializeBlueprint(listing);
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("REPLACE INTO blueprints (id, owner_uuid, owner_name, status, category, price, featured, staff_pick, created_at, updated_at, views, purchases, builds, total_revenue, total_royalties, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)") ) {
            statement.setString(1, normalizeId(listing.getId()));
            statement.setString(2, listing.getOwnerId().toString());
            statement.setString(3, listing.getOwnerName());
            statement.setString(4, listing.getStatus().name());
            statement.setString(5, listing.getCategory().name());
            statement.setDouble(6, listing.getPrice());
            statement.setInt(7, listing.isFeatured() ? 1 : 0);
            statement.setInt(8, listing.isStaffPick() ? 1 : 0);
            statement.setLong(9, listing.getCreatedAt());
            statement.setLong(10, listing.getUpdatedAt());
            statement.setLong(11, listing.getViews());
            statement.setLong(12, listing.getPurchases());
            statement.setLong(13, listing.getBuilds());
            statement.setDouble(14, listing.getTotalRevenue());
            statement.setDouble(15, listing.getTotalRoyaltiesPaid());
            statement.setString(16, payload);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save blueprint " + listing.getId() + ": " + exception.getMessage());
        }
    }

    private void saveAccountsNow() {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("REPLACE INTO accounts (uuid, name, balance, earned, spent, purchases, builds, sales, reputation, last_seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)") ) {
            for (Account account : accounts.values()) {
                statement.setString(1, account.getUuid().toString());
                statement.setString(2, account.getName());
                statement.setDouble(3, account.getBalance());
                statement.setDouble(4, account.getEarned());
                statement.setDouble(5, account.getSpent());
                statement.setLong(6, account.getPurchases());
                statement.setLong(7, account.getBuilds());
                statement.setLong(8, account.getSales());
                statement.setDouble(9, account.getReputation());
                statement.setLong(10, account.getLastSeen());
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save accounts: " + exception.getMessage());
        }
    }

    private void saveCreatorProfileNow(CreatorProfile profile) {
        String payload = serializeCreatorProfile(profile);
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("REPLACE INTO creator_profiles (creator_uuid, creator_name, updated_at, payload) VALUES (?, ?, ?, ?)") ) {
            statement.setString(1, profile.getCreatorId().toString());
            statement.setString(2, profile.getCreatorName());
            statement.setLong(3, profile.getUpdatedAt());
            statement.setString(4, payload);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save creator profile " + profile.getCreatorId() + ": " + exception.getMessage());
        }
    }

    private void saveCollectionNow(BlueprintCollection collection) {
        String payload = serializeCollection(collection);
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("REPLACE INTO blueprint_collections (id, owner_uuid, owner_name, featured, updated_at, payload) VALUES (?, ?, ?, ?, ?, ?)") ) {
            statement.setString(1, normalizeId(collection.getId()));
            statement.setString(2, collection.getOwnerId().toString());
            statement.setString(3, collection.getOwnerName());
            statement.setInt(4, collection.isFeatured() ? 1 : 0);
            statement.setLong(5, collection.getUpdatedAt());
            statement.setString(6, payload);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save collection " + collection.getId() + ": " + exception.getMessage());
        }
    }

    private void savePurchaseNow(PurchaseRecord purchaseRecord) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("REPLACE INTO purchases (purchase_key, blueprint_id, buyer_uuid, buyer_name, license_type, uses_remaining, total_purchases, total_builds, prepaid_value, first_purchased_at, last_purchase_at, last_build_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)") ) {
            statement.setString(1, purchaseKey(purchaseRecord.getBlueprintId(), purchaseRecord.getBuyerId()));
            statement.setString(2, normalizeId(purchaseRecord.getBlueprintId()));
            statement.setString(3, purchaseRecord.getBuyerId().toString());
            statement.setString(4, purchaseRecord.getBuyerName());
            statement.setString(5, purchaseRecord.getLicenseType().name());
            statement.setInt(6, purchaseRecord.getUsesRemaining());
            statement.setInt(7, purchaseRecord.getTotalPurchases());
            statement.setInt(8, purchaseRecord.getTotalBuilds());
            statement.setDouble(9, purchaseRecord.getPrepaidValueRemaining());
            statement.setLong(10, purchaseRecord.getFirstPurchasedAt());
            statement.setLong(11, purchaseRecord.getLastPurchaseAt());
            statement.setLong(12, purchaseRecord.getLastBuildAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save purchase " + purchaseRecord.getBlueprintId() + ": " + exception.getMessage());
        }
    }

    private void saveDisputeNow(DisputeRecord disputeRecord) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("REPLACE INTO disputes (id, blueprint_id, against_blueprint_id, reporter_uuid, reporter_name, evidence, notes, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)") ) {
            statement.setString(1, disputeRecord.getId());
            statement.setString(2, disputeRecord.getBlueprintId());
            statement.setString(3, disputeRecord.getAgainstBlueprintId());
            statement.setString(4, disputeRecord.getReporterId().toString());
            statement.setString(5, disputeRecord.getReporterName());
            statement.setString(6, disputeRecord.getEvidence());
            statement.setString(7, disputeRecord.getNotes());
            statement.setString(8, disputeRecord.getStatus().name());
            statement.setLong(9, disputeRecord.getCreatedAt());
            statement.setLong(10, disputeRecord.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save dispute " + disputeRecord.getId() + ": " + exception.getMessage());
        }
    }

    private void saveReviewTicketNow(ReviewTicket ticket) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("REPLACE INTO review_tickets (id, blueprint_id, creator_uuid, creator_name, reason, classification, similarity, moderator_notes, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)") ) {
            statement.setString(1, ticket.getId());
            statement.setString(2, ticket.getBlueprintId());
            statement.setString(3, ticket.getCreatorId().toString());
            statement.setString(4, ticket.getCreatorName());
            statement.setString(5, ticket.getReason());
            statement.setString(6, ticket.getClassification().name());
            statement.setDouble(7, ticket.getSimilarity());
            statement.setString(8, ticket.getModeratorNotes());
            statement.setString(9, ticket.getStatus().name());
            statement.setLong(10, ticket.getCreatedAt());
            statement.setLong(11, ticket.getUpdatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save review ticket " + ticket.getId() + ": " + exception.getMessage());
        }
    }

    private void saveSocialStateNow(SocialState state) {
        String payload = serializeSocialState(state);
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("REPLACE INTO social_states (player_uuid, player_name, updated_at, payload) VALUES (?, ?, ?, ?)") ) {
            statement.setString(1, state.getPlayerId().toString());
            statement.setString(2, state.getPlayerName());
            statement.setLong(3, state.getUpdatedAt());
            statement.setString(4, payload);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save social state " + state.getPlayerId() + ": " + exception.getMessage());
        }
    }

    private void saveLedgerNow(LedgerEntry ledgerEntry) {
        try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement("REPLACE INTO ledger (id, created_at, type, blueprint_id, actor_uuid, actor_name, amount, payload) VALUES (?, ?, ?, ?, ?, ?, ?, ?)") ) {
            statement.setLong(1, ledgerEntry.id());
            statement.setLong(2, ledgerEntry.createdAt());
            statement.setString(3, ledgerEntry.type());
            statement.setString(4, ledgerEntry.blueprintId());
            statement.setString(5, ledgerEntry.actorId().toString());
            statement.setString(6, ledgerEntry.actorName());
            statement.setDouble(7, ledgerEntry.amount());
            statement.setString(8, ledgerEntry.payload());
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Failed to save ledger entry " + ledgerEntry.id() + ": " + exception.getMessage());
        }
    }

    private Optional<BlueprintListing> deserializeBlueprint(String payload) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(payload);
            String id = yaml.getString("id");
            if (id == null || id.isBlank()) {
                return Optional.empty();
            }
            UUID ownerId = UUID.fromString(yaml.getString("owner.uuid", ""));
            String ownerName = yaml.getString("owner.name", "Unknown");
            List<BlockEntry> entries = new ArrayList<>();
            for (String line : yaml.getStringList("blocks")) {
                BlockEntry.deserialize(line).ifPresent(entries::add);
            }
            Map<String, Integer> materialCounts = readCountMap(yaml.getConfigurationSection("counts.materials"));
            Map<String, Integer> blockDataCounts = readCountMap(yaml.getConfigurationSection("counts.block-data"));
            BlueprintListing listing = new BlueprintListing(
                normalizeId(id),
                ownerId,
                ownerName,
                yaml.getInt("size.width"),
                yaml.getInt("size.height"),
                yaml.getInt("size.depth"),
                entries,
                materialCounts,
                blockDataCounts,
                yaml.getString("fingerprint.exact-hash", ""),
                yaml.getString("fingerprint.canonical-hash", ""),
                yaml.getStringList("fingerprint.variant-hashes"),
                yaml.getLong("created-at", System.currentTimeMillis())
            );
            listing.loadUpdatedAt(yaml.getLong("updated-at", System.currentTimeMillis()));
            listing.setPrice(yaml.getDouble("price", 0.0D));
            listing.setBaseRoyaltyPercent(yaml.getDouble("royalty.base", 90.0D));
            listing.setOriginality(
                yaml.getDouble("originality.score", 1.0D),
                OriginalityClassification.valueOf(yaml.getString("originality.classification", OriginalityClassification.ORIGINAL.name())),
                yaml.getString("originality.best-match-id", ""),
                yaml.getString("originality.best-match-owner", "")
            );
            listing.setCategory(BlueprintCategory.from(yaml.getString("category")));
            listing.setLicenseType(BlueprintLicenseType.from(yaml.getString("license-type")));
            listing.setBuildStyle(BuildStyle.from(yaml.getString("build-style")));
            listing.setStatus(BlueprintStatus.valueOf(yaml.getString("status", BlueprintStatus.LIVE.name())));
            listing.setDescription(yaml.getString("description", ""));
            listing.setFeatured(yaml.getBoolean("curation.featured", false));
            listing.setStaffPick(yaml.getBoolean("curation.staff-pick", false));
            listing.setRevisionLocked(yaml.getBoolean("revision.locked", false));
            listing.setRevision(yaml.getInt("revision.number", 1));
            listing.setMilestoneLevel(yaml.getInt("milestone.level", 0));
            listing.setUpgradeChain(yaml.getString("upgrade.parent", ""), yaml.getDouble("upgrade.discount-percent", 0.0D));
            listing.setTeamKey(yaml.getString("team.key", ""));
            listing.setDerivedFromBlueprintId(yaml.getString("originality.derived-from", ""));
            listing.setOwnerShares(readOwnerShares(yaml.getConfigurationSection("owners")));
            listing.setRoyaltyTiers(readRoyaltyTiers(yaml.getConfigurationSection("royalty.tiers")));
            listing.setTags(yaml.getStringList("tags"));
            listing.setTimeline(readTimeline(yaml.getConfigurationSection("timeline")));
            listing.setBadges(readBadges(yaml.getStringList("badges")));
            listing.loadStats(
                yaml.getLong("stats.views", 0L),
                yaml.getLong("stats.purchases", 0L),
                yaml.getLong("stats.builds", 0L),
                yaml.getLong("stats.repeat-buyers", 0L),
                yaml.getDouble("stats.total-royalties", 0.0D),
                yaml.getDouble("stats.total-revenue", 0.0D)
            );
            listing.getRatingSummary().load(readRatings(yaml.getConfigurationSection("ratings.reviews")));
            listing.refreshBadges();
            return Optional.of(listing);
        } catch (InvalidConfigurationException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Failed to parse blueprint payload: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private String serializeBlueprint(BlueprintListing listing) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", listing.getId());
        yaml.set("owner.uuid", listing.getOwnerId().toString());
        yaml.set("owner.name", listing.getOwnerName());
        yaml.set("size.width", listing.getWidth());
        yaml.set("size.height", listing.getHeight());
        yaml.set("size.depth", listing.getDepth());
        yaml.set("blocks", listing.getEntries().stream().map(BlockEntry::serialize).toList());
        writeCountMap(yaml, "counts.materials", listing.getMaterialCounts());
        writeCountMap(yaml, "counts.block-data", listing.getBlockDataCounts());
        yaml.set("fingerprint.exact-hash", listing.getExactHash());
        yaml.set("fingerprint.canonical-hash", listing.getCanonicalHash());
        yaml.set("fingerprint.variant-hashes", listing.getVariantHashes());
        yaml.set("created-at", listing.getCreatedAt());
        yaml.set("updated-at", listing.getUpdatedAt());
        yaml.set("price", listing.getPrice());
        yaml.set("royalty.base", listing.getBaseRoyaltyPercent());
        yaml.set("originality.score", listing.getOriginalityScore());
        yaml.set("originality.classification", listing.getOriginalityClassification().name());
        yaml.set("originality.best-match-id", listing.getBestMatchBlueprintId());
        yaml.set("originality.best-match-owner", listing.getBestMatchOwnerName());
        yaml.set("originality.derived-from", listing.getDerivedFromBlueprintId());
        yaml.set("category", listing.getCategory().name());
        yaml.set("license-type", listing.getLicenseType().name());
        yaml.set("build-style", listing.getBuildStyle().name());
        yaml.set("status", listing.getStatus().name());
        yaml.set("description", listing.getDescription());
        yaml.set("curation.featured", listing.isFeatured());
        yaml.set("curation.staff-pick", listing.isStaffPick());
        yaml.set("revision.locked", listing.isRevisionLocked());
        yaml.set("revision.number", listing.getRevision());
        yaml.set("milestone.level", listing.getMilestoneLevel());
        yaml.set("upgrade.parent", listing.getParentBlueprintId());
        yaml.set("upgrade.discount-percent", listing.getUpgradeDiscountPercent());
        yaml.set("team.key", listing.getTeamKey());
        yaml.set("tags", List.copyOf(listing.getTags()));
        yaml.set("badges", listing.getBadges().stream().map(Enum::name).toList());
        yaml.set("stats.views", listing.getViews());
        yaml.set("stats.purchases", listing.getPurchases());
        yaml.set("stats.builds", listing.getBuilds());
        yaml.set("stats.repeat-buyers", listing.getRepeatBuyers());
        yaml.set("stats.total-royalties", listing.getTotalRoyaltiesPaid());
        yaml.set("stats.total-revenue", listing.getTotalRevenue());
        writeOwners(yaml, listing.getOwnerShares());
        writeRoyaltyTiers(yaml, listing.getRoyaltyTiers());
        writeTimeline(yaml, listing.getTimeline());
        writeRatings(yaml, listing.getRatingSummary());
        return yaml.saveToString();
    }

    private Optional<CreatorProfile> deserializeCreatorProfile(String payload) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(payload);
            UUID creatorId = UUID.fromString(yaml.getString("creator.uuid", ""));
            CreatorProfile profile = new CreatorProfile(creatorId, yaml.getString("creator.name", "Unknown"));
            profile.setHeadline(yaml.getString("headline", "Creator storefront"));
            profile.setAccentColor(yaml.getString("accent-color", "#38bdf8"));
            profile.setBio(yaml.getString("bio", ""));
            profile.setFeaturedBlueprintIds(yaml.getStringList("featured-blueprints"));
            profile.setPinnedCollectionIds(yaml.getStringList("pinned-collections"));
            profile.loadUpdatedAt(yaml.getLong("updated-at", System.currentTimeMillis()));
            return Optional.of(profile);
        } catch (InvalidConfigurationException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Failed to parse creator profile payload: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private String serializeCreatorProfile(CreatorProfile profile) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("creator.uuid", profile.getCreatorId().toString());
        yaml.set("creator.name", profile.getCreatorName());
        yaml.set("headline", profile.getHeadline());
        yaml.set("accent-color", profile.getAccentColor());
        yaml.set("bio", profile.getBio());
        yaml.set("featured-blueprints", profile.getFeaturedBlueprintIds());
        yaml.set("pinned-collections", profile.getPinnedCollectionIds());
        yaml.set("updated-at", profile.getUpdatedAt());
        return yaml.saveToString();
    }

    private Optional<BlueprintCollection> deserializeCollection(String payload) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(payload);
            String id = yaml.getString("id", "");
            if (id.isBlank()) {
                return Optional.empty();
            }
            BlueprintCollection collection = new BlueprintCollection(
                normalizeId(id),
                UUID.fromString(yaml.getString("owner.uuid", "")),
                yaml.getString("owner.name", "Unknown"),
                yaml.getString("title", id),
                yaml.getString("description", ""),
                yaml.getLong("created-at", System.currentTimeMillis())
            );
            collection.setFeatured(yaml.getBoolean("featured", false));
            collection.setBlueprintIds(yaml.getStringList("blueprints"));
            collection.loadTimestamps(yaml.getLong("created-at", System.currentTimeMillis()), yaml.getLong("updated-at", System.currentTimeMillis()));
            return Optional.of(collection);
        } catch (InvalidConfigurationException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Failed to parse collection payload: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private String serializeCollection(BlueprintCollection collection) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", collection.getId());
        yaml.set("owner.uuid", collection.getOwnerId().toString());
        yaml.set("owner.name", collection.getOwnerName());
        yaml.set("title", collection.getTitle());
        yaml.set("description", collection.getDescription());
        yaml.set("featured", collection.isFeatured());
        yaml.set("created-at", collection.getCreatedAt());
        yaml.set("updated-at", collection.getUpdatedAt());
        yaml.set("blueprints", collection.getBlueprintIds());
        return yaml.saveToString();
    }

    private Optional<SocialState> deserializeSocialState(String payload) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(payload);
            SocialState state = new SocialState(UUID.fromString(yaml.getString("player.uuid", "")), yaml.getString("player.name", "Unknown"));
            state.setWishlistBlueprintIds(yaml.getStringList("wishlist"));
            List<UUID> followedCreators = new ArrayList<>();
            for (String raw : yaml.getStringList("following")) {
                try {
                    followedCreators.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                }
            }
            state.setFollowedCreators(followedCreators);
            state.loadUpdatedAt(yaml.getLong("updated-at", System.currentTimeMillis()));
            return Optional.of(state);
        } catch (InvalidConfigurationException | IllegalArgumentException exception) {
            plugin.getLogger().warning("Failed to parse social state payload: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private String serializeSocialState(SocialState state) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player.uuid", state.getPlayerId().toString());
        yaml.set("player.name", state.getPlayerName());
        yaml.set("updated-at", state.getUpdatedAt());
        yaml.set("wishlist", state.getWishlistBlueprintIds());
        yaml.set("following", state.getFollowedCreators().stream().map(UUID::toString).toList());
        return yaml.saveToString();
    }

    private void rebuildIndex() {
        searchIndexService.rebuild(blueprints.values());
    }

    private String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }

    private String purchaseKey(String blueprintId, UUID buyerId) {
        return normalizeId(blueprintId) + "|" + buyerId;
    }

    private Map<String, Integer> readCountMap(ConfigurationSection section) {
        Map<String, Integer> counts = new HashMap<>();
        if (section == null) {
            return counts;
        }
        for (String key : section.getKeys(false)) {
            counts.put(key, section.getInt(key));
        }
        return counts;
    }

    private void writeCountMap(YamlConfiguration yaml, String path, Map<String, Integer> counts) {
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            yaml.set(path + "." + entry.getKey(), entry.getValue());
        }
    }

    private List<OwnerShare> readOwnerShares(ConfigurationSection section) {
        List<OwnerShare> shares = new ArrayList<>();
        if (section == null) {
            return shares;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            try {
                shares.add(new OwnerShare(
                    UUID.fromString(entry.getString("uuid", "")),
                    entry.getString("name", "Unknown"),
                    entry.getDouble("percent", 0.0D)
                ));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return shares;
    }

    private void writeOwners(YamlConfiguration yaml, List<OwnerShare> ownerShares) {
        for (int ownerIndex = 0; ownerIndex < ownerShares.size(); ownerIndex++) {
            OwnerShare ownerShare = ownerShares.get(ownerIndex);
            String path = "owners." + ownerIndex;
            yaml.set(path + ".uuid", ownerShare.uuid().toString());
            yaml.set(path + ".name", ownerShare.name());
            yaml.set(path + ".percent", ownerShare.percent());
        }
    }

    private List<RoyaltyTier> readRoyaltyTiers(ConfigurationSection section) {
        List<RoyaltyTier> tiers = new ArrayList<>();
        if (section == null) {
            return tiers;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            tiers.add(new RoyaltyTier(entry.getInt("min-builds", 0), entry.getDouble("royalty-percent", 90.0D)));
        }
        return tiers;
    }

    private void writeRoyaltyTiers(YamlConfiguration yaml, List<RoyaltyTier> royaltyTiers) {
        for (int tierIndex = 0; tierIndex < royaltyTiers.size(); tierIndex++) {
            RoyaltyTier royaltyTier = royaltyTiers.get(tierIndex);
            String path = "royalty.tiers." + tierIndex;
            yaml.set(path + ".min-builds", royaltyTier.minBuilds());
            yaml.set(path + ".royalty-percent", royaltyTier.royaltyPercent());
        }
    }

    private List<TimelineEntry> readTimeline(ConfigurationSection section) {
        List<TimelineEntry> timeline = new ArrayList<>();
        if (section == null) {
            return timeline;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            timeline.add(new TimelineEntry(entry.getLong("created-at"), entry.getString("type", "unknown"), entry.getString("actor", "system"), entry.getString("summary", "")));
        }
        return timeline;
    }

    private void writeTimeline(YamlConfiguration yaml, List<TimelineEntry> timeline) {
        for (int index = 0; index < timeline.size(); index++) {
            TimelineEntry entry = timeline.get(index);
            String path = "timeline." + index;
            yaml.set(path + ".created-at", entry.createdAt());
            yaml.set(path + ".type", entry.type());
            yaml.set(path + ".actor", entry.actor());
            yaml.set(path + ".summary", entry.summary());
        }
    }

    private Set<CreatorBadge> readBadges(List<String> badgeNames) {
        java.util.EnumSet<CreatorBadge> badges = java.util.EnumSet.noneOf(CreatorBadge.class);
        for (String badgeName : badgeNames) {
            try {
                badges.add(CreatorBadge.valueOf(badgeName));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return badges;
    }

    private Map<UUID, RatingSummary.Review> readRatings(ConfigurationSection section) {
        Map<UUID, RatingSummary.Review> ratings = new LinkedHashMap<>();
        if (section == null) {
            return ratings;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            try {
                ratings.put(UUID.fromString(key), new RatingSummary.Review(entry.getInt("quality"), entry.getInt("accuracy"), entry.getInt("usefulness")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return ratings;
    }

    private void writeRatings(YamlConfiguration yaml, RatingSummary ratingSummary) {
        for (Map.Entry<UUID, RatingSummary.Review> entry : ratingSummary.getReviews().entrySet()) {
            String path = "ratings.reviews." + entry.getKey();
            yaml.set(path + ".quality", entry.getValue().quality());
            yaml.set(path + ".accuracy", entry.getValue().accuracy());
            yaml.set(path + ".usefulness", entry.getValue().usefulness());
        }
    }
}