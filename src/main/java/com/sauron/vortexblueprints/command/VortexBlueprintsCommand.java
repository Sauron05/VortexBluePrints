package com.sauron.vortexblueprints.command;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.gui.CreatorAnalyticsGui;
import com.sauron.vortexblueprints.gui.DisputeQueueGui;
import com.sauron.vortexblueprints.gui.MarketplaceGui;
import com.sauron.vortexblueprints.gui.RatingGui;
import com.sauron.vortexblueprints.gui.ReviewQueueGui;
import com.sauron.vortexblueprints.gui.TeamOwnershipGui;
import com.sauron.vortexblueprints.listener.SelectionListener;
import com.sauron.vortexblueprints.model.BlueprintCategory;
import com.sauron.vortexblueprints.model.BlueprintLicenseType;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.BuildStyle;
import com.sauron.vortexblueprints.model.DisputeRecord;
import com.sauron.vortexblueprints.model.MarketView;
import com.sauron.vortexblueprints.model.ReviewTicket;
import com.sauron.vortexblueprints.util.ItemBuilder;
import com.sauron.vortexblueprints.util.MessageUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class VortexBlueprintsCommand implements CommandExecutor, TabCompleter {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{3,32}");
    private static final List<String> ROOT_COMMANDS = List.of(
        "help", "wand", "pos1", "pos2", "save", "market", "build", "preview", "info", "list", "analytics", "rate", "review", "dispute", "team", "curate", "panel", "probe", "delete", "balance", "credits", "reload"
    );

    private final VortexBlueprintsPlugin plugin;

    public VortexBlueprintsCommand(VortexBlueprintsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "wand" -> handleWand(sender);
            case "pos1" -> handlePosition(sender, true);
            case "pos2" -> handlePosition(sender, false);
            case "save" -> handleSave(sender, args);
            case "market" -> handleMarket(sender, args);
            case "build" -> handleBuild(sender, args);
            case "preview" -> handlePreview(sender, args);
            case "info" -> handleInfo(sender, args);
            case "list" -> handleList(sender);
            case "analytics" -> handleAnalytics(sender, args);
            case "rate" -> handleRate(sender, args);
            case "review" -> handleReview(sender, args);
            case "dispute" -> handleDispute(sender, args);
            case "team" -> handleTeam(sender, args);
            case "curate" -> handleCurate(sender, args);
            case "panel" -> handlePanel(sender);
            case "probe" -> handleProbe(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "balance" -> handleBalance(sender);
            case "credits" -> handleCredits(sender, args);
            case "reload" -> handleReload(sender);
            default -> MessageUtil.send(sender, plugin.getConfigManager().message("unknown-command"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(ROOT_COMMANDS, args[0]);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("build", "preview", "info", "delete", "rate", "curate").contains(subcommand)) {
            return filter(plugin.getDataManager().getBlueprints().stream().map(BlueprintListing::getId).toList(), args[1]);
        }
        if (args.length == 2 && subcommand.equals("market")) {
            return filter(List.of("featured", "trending", "newest", "cheapest", "profitable", "creator_top"), args[1]);
        }
        if (args.length == 3 && subcommand.equals("market")) {
            return filter(List.of("house", "spawn", "farm", "pvp", "redstone", "shop", "dungeon", "medieval", "scifi", "decor", "other"), args[2]);
        }
        if (args.length == 5 && subcommand.equals("save")) {
            return filter(List.of("house", "spawn", "farm", "pvp", "redstone", "shop", "dungeon", "medieval", "scifi", "decor", "other"), args[4]);
        }
        if (args.length == 6 && subcommand.equals("save")) {
            return filter(List.of("single_use", "pack_five", "pack_twenty", "showcase", "exclusive"), args[5]);
        }
        if (args.length == 7 && subcommand.equals("save")) {
            return filter(List.of("instant", "animated", "drone", "crate"), args[6]);
        }
        if (args.length == 2 && subcommand.equals("review")) {
            return filter(List.of("gui", "list", "approve", "reject"), args[1]);
        }
        if (args.length == 3 && subcommand.equals("review") && !args[1].equalsIgnoreCase("list")) {
            return filter(plugin.getDataManager().getReviewTickets().stream().map(ReviewTicket::getId).toList(), args[2]);
        }
        if (args.length == 2 && subcommand.equals("dispute")) {
            return filter(List.of("gui", "open", "resolve", "list"), args[1]);
        }
        if (args.length == 2 && subcommand.equals("team")) {
            return filter(List.of("gui", "add", "remove", "list"), args[1]);
        }
        if (args.length == 3 && subcommand.equals("team") && args[1].equalsIgnoreCase("gui")) {
            return filter(plugin.getDataManager().getBlueprints().stream().map(BlueprintListing::getId).toList(), args[2]);
        }
        if (args.length == 4 && subcommand.equals("team") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        if (args.length == 2 && subcommand.equals("curate")) {
            return filter(List.of("feature", "staffpick"), args[1]);
        }
        if (args.length == 4 && subcommand.equals("curate")) {
            return filter(List.of("true", "false"), args[3]);
        }
        if (args.length == 2 && subcommand.equals("credits")) {
            return filter(List.of("give"), args[1]);
        }
        if (args.length == 3 && subcommand.equals("credits") && args[1].equalsIgnoreCase("give")) {
            return null;
        }
        if (args.length == 2 && subcommand.equals("probe")) {
            return filter(List.of("protection"), args[1]);
        }
        return List.of();
    }

    private void handleWand(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "vortexblueprints.create")) {
            return;
        }
        ItemStack wand = new ItemBuilder(plugin.getConfigManager().wandMaterial())
            .name("<gradient:#38bdf8:#22c55e>Blueprint Wand</gradient>")
            .lore("<gray>Left-click: <white>position 1", "<gray>Right-click: <white>position 2")
            .pdc(plugin, SelectionListener.WAND_KEY, "true")
            .hideAttributes()
            .build();
        player.getInventory().addItem(wand).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        MessageUtil.send(player, plugin.getConfigManager().message("wand-given"));
    }

    private void handlePosition(CommandSender sender, boolean firstCorner) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "vortexblueprints.create")) {
            return;
        }
        if (firstCorner) {
            plugin.getSelectionManager().setFirstCorner(player, player.getLocation());
            MessageUtil.send(player, plugin.getConfigManager().message("selection-pos1"),
                "location", MessageUtil.formatLocation(player.getLocation()));
            return;
        }
        plugin.getSelectionManager().setSecondCorner(player, player.getLocation());
        MessageUtil.send(player, plugin.getConfigManager().message("selection-pos2"),
            "location", MessageUtil.formatLocation(player.getLocation()));
    }

    private void handleSave(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "vortexblueprints.create")) {
            return;
        }
        if (args.length < 3) {
            MessageUtil.send(player, plugin.getConfigManager().message("save-usage"));
            return;
        }
        Optional<String> idOptional = normalizeId(args[1]);
        if (idOptional.isEmpty()) {
            MessageUtil.send(player, "<prefix><red>Blueprint ids must be 3-32 characters: lowercase letters, numbers, underscores, or hyphens.");
            return;
        }
        Double price = parseDouble(args[2]);
        if (price == null || price < plugin.getConfigManager().minPrice() || price > plugin.getConfigManager().maxPrice()) {
            MessageUtil.send(player, "<prefix><red>Price must be between <white><min></white> and <white><max></white>.",
                "min", MessageUtil.number(plugin.getConfigManager().minPrice()),
                "max", MessageUtil.number(plugin.getConfigManager().maxPrice()));
            return;
        }
        double royaltyPercent = plugin.getConfigManager().defaultRoyaltyPercent();
        if (args.length >= 4) {
            Double parsedRoyalty = parseDouble(args[3]);
            if (parsedRoyalty == null) {
                MessageUtil.send(player, "<prefix><red>Royalty percent must be a number.");
                return;
            }
            royaltyPercent = parsedRoyalty;
        }
        if (royaltyPercent < 0.0D || royaltyPercent > plugin.getConfigManager().maxRoyaltyPercent()) {
            MessageUtil.send(player, "<prefix><red>Royalty percent must be between <white>0</white> and <white><max></white>.",
                "max", MessageUtil.number(plugin.getConfigManager().maxRoyaltyPercent()));
            return;
        }
        BlueprintCategory category = args.length >= 5 ? parseCategory(args[4]) : plugin.getConfigManager().defaultCategory();
        BlueprintLicenseType licenseType = args.length >= 6 ? parseLicense(args[5]) : plugin.getConfigManager().defaultLicenseType();
        BuildStyle buildStyle = args.length >= 7 ? parseBuildStyle(args[6]) : plugin.getConfigManager().defaultBuildStyle();
        if (category == null || licenseType == null || buildStyle == null) {
            MessageUtil.send(player, "<prefix><red>Usage: <white>/vbp save <id> <price> [royalty] [category] [license] [style] [description...]</white>");
            return;
        }
        String description = args.length >= 8 ? joinTail(args, 7) : "";
        plugin.getCaptureService().captureAndPublish(player, idOptional.get(), price, royaltyPercent, category, licenseType, buildStyle, description);
    }

    private void handleMarket(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "vortexblueprints.use")) {
            return;
        }
        int page = 0;
        MarketView view = MarketView.TRENDING;
        BlueprintCategory category = null;
        for (int index = 1; index < args.length; index++) {
            Integer parsedPage = parseInteger(args[index]);
            if (parsedPage != null) {
                page = Math.max(0, parsedPage - 1);
                continue;
            }
            MarketView parsedView = parseMarketView(args[index]);
            if (parsedView != null) {
                view = parsedView;
                continue;
            }
            BlueprintCategory parsedCategory = parseCategory(args[index]);
            if (parsedCategory != null) {
                category = parsedCategory;
            }
        }
        new MarketplaceGui(plugin, player, page, view, category).open(player);
    }

    private void handleBuild(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "vortexblueprints.build")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp build <id></white>");
            return;
        }
        plugin.getBuildService().build(player, args[1]);
    }

    private void handlePreview(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "vortexblueprints.build")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp preview <id></white>");
            return;
        }
        plugin.getBuildService().preview(player, args[1]);
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "vortexblueprints.use")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(sender, "<prefix><yellow>Usage: <white>/vbp info <id></white>");
            return;
        }
        BlueprintListing listing = plugin.getDataManager().getBlueprint(args[1]).orElse(null);
        if (listing == null) {
            MessageUtil.send(sender, plugin.getConfigManager().message("not-found"), "id", args[1]);
            return;
        }
        MessageUtil.send(sender, "<prefix><aqua><id></aqua> <gray>by <white><owner></white>",
            "id", listing.getId(), "owner", listing.getOwnerName());
        MessageUtil.send(sender, "<gray>Price: <green><price></green> <dark_gray>|</dark_gray> Royalty: <white><royalty>%</white> <dark_gray>|</dark_gray> Rating: <white><rating></white>",
            "price", plugin.getEconomyService().format(listing.getPrice()),
            "royalty", MessageUtil.number(listing.getCurrentRoyaltyPercent()),
            "rating", MessageUtil.number(listing.getAverageRating()));
        MessageUtil.send(sender, "<gray>Category: <white><category></white> <dark_gray>|</dark_gray> License: <white><license></white> <dark_gray>|</dark_gray> Style: <white><style></white>",
            "category", listing.getCategory().getDisplayName(),
            "license", listing.getLicenseType().getDisplayName(),
            "style", listing.getBuildStyle().getDisplayName());
        MessageUtil.send(sender, "<gray>Size: <white><size></white> <dark_gray>|</dark_gray> Builds: <white><builds></white> <dark_gray>|</dark_gray> Originality: <white><score>%</white> <dark_gray>|</dark_gray> Status: <white><status></white>",
            "size", listing.getWidth() + "x" + listing.getHeight() + "x" + listing.getDepth(),
            "builds", String.valueOf(listing.getBuilds()),
            "score", MessageUtil.percent(listing.getOriginalityScore()),
            "status", listing.getStatus().name());
        MessageUtil.send(sender, "<gray>Team: <white><team></white> <dark_gray>|</dark_gray> Revision: <white><revision></white> <dark_gray>|</dark_gray> Provenance entries: <white><timeline></white>",
            "team", listing.getTeamKey().isBlank() ? "solo" : listing.getTeamKey(),
            "revision", String.valueOf(listing.getRevision()),
            "timeline", String.valueOf(listing.getTimeline().size()));
    }

    private void handleList(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "vortexblueprints.use")) {
            return;
        }
        List<String> ownedIds = plugin.getDataManager().getBlueprints().stream()
            .filter(listing -> listing.isOwner(player.getUniqueId()))
            .map(BlueprintListing::getId)
            .sorted()
            .toList();
        if (ownedIds.isEmpty()) {
            MessageUtil.send(player, "<prefix><yellow>You have not published any blueprints yet.");
            return;
        }
        MessageUtil.send(player, "<prefix><green>Your blueprints: <white><ids></white>", "ids", String.join(", ", ownedIds));
    }

    private void handleAnalytics(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "vortexblueprints.use")) {
            return;
        }
        new CreatorAnalyticsGui(plugin, player).open(player);
    }

    private void handleRate(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "vortexblueprints.use")) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp rate <id></white>");
            return;
        }
        BlueprintListing listing = plugin.getDataManager().getBlueprint(args[1]).orElse(null);
        if (listing == null) {
            MessageUtil.send(player, plugin.getConfigManager().message("not-found"), "id", args[1]);
            return;
        }
        if (!listing.isOwner(player.getUniqueId()) && plugin.getDataManager().getPurchase(listing.getId(), player.getUniqueId()).isEmpty()) {
            MessageUtil.send(player, "<prefix><red>You need to own or purchase a blueprint before rating it.");
            return;
        }
        if (args.length == 2) {
            new RatingGui(plugin, player, listing).open(player);
            return;
        }
        if (args.length < 5) {
            MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp rate <id></white> <gray>or</gray> <white>/vbp rate <id> <quality> <accuracy> <usefulness></white>");
            return;
        }
        Integer quality = parseInteger(args[2]);
        Integer accuracy = parseInteger(args[3]);
        Integer usefulness = parseInteger(args[4]);
        if (!validRating(quality) || !validRating(accuracy) || !validRating(usefulness)) {
            MessageUtil.send(player, "<prefix><red>Ratings must be whole numbers from 1 to 5.");
            return;
        }
        listing.recordRating(player.getUniqueId(), quality, accuracy, usefulness, player.getName());
        plugin.getDataManager().saveBlueprintAsync(listing);
        MessageUtil.send(player, plugin.getConfigManager().message("rating-recorded"),
            "id", listing.getId(),
            "rating", MessageUtil.number(listing.getAverageRating()));
    }

    private void handleReview(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "vortexblueprints.admin")) {
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("gui")) {
            Player player = requirePlayer(sender);
            if (player == null) {
                return;
            }
            new ReviewQueueGui(plugin, player, 0).open(player);
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            List<ReviewTicket> openTickets = plugin.getDataManager().getReviewTickets().stream()
                .filter(ticket -> ticket.getStatus() == ReviewTicket.Status.OPEN)
                .sorted(Comparator.comparingLong(ReviewTicket::getCreatedAt))
                .toList();
            if (openTickets.isEmpty()) {
                MessageUtil.send(sender, "<prefix><yellow>No open review tickets.");
                return;
            }
            for (ReviewTicket ticket : openTickets) {
                MessageUtil.send(sender, "<gray><id></gray> <dark_gray>-</dark_gray> <white><blueprint></white> <dark_gray>|</dark_gray> <aqua><classification></aqua> <dark_gray>|</dark_gray> <white><score>%</white>",
                    "id", ticket.getId(),
                    "blueprint", ticket.getBlueprintId(),
                    "classification", ticket.getClassification().name(),
                    "score", MessageUtil.percent(ticket.getSimilarity()));
            }
            return;
        }
        if (args.length < 3) {
            MessageUtil.send(sender, "<prefix><yellow>Usage: <white>/vbp review <approve|reject> <ticketId> [notes...]</white>");
            return;
        }
        String notes = args.length >= 4 ? joinTail(args, 3) : "";
        Optional<String> failure;
        if (args[1].equalsIgnoreCase("approve")) {
            failure = plugin.getModerationService().approveReview(args[2], sender.getName(), notes);
        } else if (args[1].equalsIgnoreCase("reject")) {
            failure = plugin.getModerationService().rejectReview(args[2], sender.getName(), notes);
        } else {
            MessageUtil.send(sender, "<prefix><yellow>Usage: <white>/vbp review <approve|reject> <ticketId> [notes...]</white>");
            return;
        }
        if (failure.isPresent()) {
            MessageUtil.send(sender, "<prefix><red>" + failure.get());
            return;
        }
        if (args[1].equalsIgnoreCase("approve")) {
            MessageUtil.send(sender, "<prefix><green>Approved review ticket <white><id></white>.", "id", args[2]);
        } else {
            MessageUtil.send(sender, "<prefix><yellow>Rejected review ticket <white><id></white>.", "id", args[2]);
        }
    }

    private void handleDispute(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("gui")) {
            if (!requirePermission(sender, "vortexblueprints.admin")) {
                return;
            }
            Player player = requirePlayer(sender);
            if (player == null) {
                return;
            }
            new DisputeQueueGui(plugin, player, 0).open(player);
            return;
        }
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            if (!requirePermission(sender, "vortexblueprints.admin")) {
                return;
            }
            List<DisputeRecord> disputes = plugin.getDataManager().getDisputes().stream()
                .sorted(Comparator.comparingLong(DisputeRecord::getCreatedAt).reversed())
                .limit(10)
                .toList();
            if (disputes.isEmpty()) {
                MessageUtil.send(sender, "<prefix><yellow>No disputes recorded.");
                return;
            }
            for (DisputeRecord dispute : disputes) {
                MessageUtil.send(sender, "<gray><id></gray> <dark_gray>-</dark_gray> <white><blueprint></white> vs <white><against></white> <dark_gray>|</dark_gray> <aqua><status></aqua>",
                    "id", dispute.getId(),
                    "blueprint", dispute.getBlueprintId(),
                    "against", dispute.getAgainstBlueprintId(),
                    "status", dispute.getStatus().name());
            }
            return;
        }
        if (args[1].equalsIgnoreCase("open")) {
            Player player = requirePlayer(sender);
            if (player == null || !requirePermission(player, "vortexblueprints.use")) {
                return;
            }
            if (args.length < 5) {
                MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp dispute open <id> <againstId> <evidence...></white>");
                return;
            }
            BlueprintListing listing = plugin.getDataManager().getBlueprint(args[2]).orElse(null);
            BlueprintListing against = plugin.getDataManager().getBlueprint(args[3]).orElse(null);
            if (listing == null || against == null) {
                MessageUtil.send(player, "<prefix><red>Both blueprint ids must exist.");
                return;
            }
            DisputeRecord dispute = plugin.getModerationService().openDispute(listing, against, player.getUniqueId(), player.getName(), joinTail(args, 4));
            MessageUtil.send(player, plugin.getConfigManager().message("dispute-opened"), "id", dispute.getId());
            return;
        }
        if (!requirePermission(sender, "vortexblueprints.admin")) {
            return;
        }
        if (args.length < 4 || !args[1].equalsIgnoreCase("resolve")) {
            MessageUtil.send(sender, "<prefix><yellow>Usage: <white>/vbp dispute resolve <disputeId> <resolved|rejected> [notes...]</white>");
            return;
        }
        Optional<String> failure;
        if (args[3].equalsIgnoreCase("resolved")) {
            failure = plugin.getModerationService().resolveDispute(args[2], sender.getName(), args.length >= 5 ? joinTail(args, 4) : "");
        } else if (args[3].equalsIgnoreCase("rejected")) {
            failure = plugin.getModerationService().rejectDispute(args[2], sender.getName(), args.length >= 5 ? joinTail(args, 4) : "");
        } else {
            MessageUtil.send(sender, "<prefix><red>Status must be resolved or rejected.");
            return;
        }
        if (failure.isPresent()) {
            MessageUtil.send(sender, "<prefix><red>" + failure.get());
            return;
        }
        MessageUtil.send(sender, plugin.getConfigManager().message("dispute-updated"),
            "id", args[2],
            "status", args[3].toUpperCase(Locale.ROOT));
    }

    private void handleTeam(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "vortexblueprints.create")) {
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("gui")) {
            if (args.length < 3) {
                MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp team gui <id></white>");
                return;
            }
            BlueprintListing listing = plugin.getDataManager().getBlueprint(args[2]).orElse(null);
            if (listing == null) {
                MessageUtil.send(player, plugin.getConfigManager().message("not-found"), "id", args[2]);
                return;
            }
            if (!listing.isOwner(player.getUniqueId()) && !player.hasPermission("vortexblueprints.admin")) {
                MessageUtil.send(player, plugin.getConfigManager().message("no-permission"));
                return;
            }
            new TeamOwnershipGui(plugin, player, listing.getId()).open(player);
            return;
        }
        if (args.length < 3) {
            MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp team <add|remove|list> <id> [player] [share]</white>");
            return;
        }
        BlueprintListing listing = plugin.getDataManager().getBlueprint(args[2]).orElse(null);
        if (listing == null) {
            MessageUtil.send(player, plugin.getConfigManager().message("not-found"), "id", args[2]);
            return;
        }
        if (!listing.isOwner(player.getUniqueId()) && !player.hasPermission("vortexblueprints.admin")) {
            MessageUtil.send(player, plugin.getConfigManager().message("no-permission"));
            return;
        }
        if (args[1].equalsIgnoreCase("list")) {
            String shares = listing.getOwnerShares().stream().map(share -> share.name() + " " + MessageUtil.number(share.percent()) + "%").reduce((first, second) -> first + ", " + second).orElse("solo");
            MessageUtil.send(player, "<prefix><aqua><id></aqua> team: <white><shares></white>", "id", listing.getId(), "shares", shares);
            return;
        }
        if (args.length < 4) {
            MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp team <add|remove> <id> <player> [share]</white>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[3]);
        if (target == null) {
            MessageUtil.send(player, "<prefix><red>That player must be online for team edits.");
            return;
        }
        if (args[1].equalsIgnoreCase("add")) {
            if (args.length < 5) {
                MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp team add <id> <player> <share></white>");
                return;
            }
            Double shareValue = parseDouble(args[4]);
            if (shareValue == null || shareValue <= 0.0D || shareValue >= 100.0D) {
                MessageUtil.send(player, "<prefix><red>Share must be greater than 0 and less than 100.");
                return;
            }
            if (!plugin.getModerationService().addCoOwner(listing, target.getUniqueId(), target.getName(), shareValue)) {
                MessageUtil.send(player, "<prefix><red>The owner does not have enough remaining share for that transfer.");
                return;
            }
            MessageUtil.send(player, "<prefix><green>Added <white><player></white> to <white><id></white> for <white><share>%</white>.",
                "player", target.getName(),
                "id", listing.getId(),
                "share", MessageUtil.number(shareValue));
            return;
        }
        if (args[1].equalsIgnoreCase("remove")) {
            if (plugin.getModerationService().removeCoOwner(listing, target.getUniqueId())) {
                MessageUtil.send(player, "<prefix><yellow>Removed <white><player></white> from <white><id></white> team ownership.",
                    "player", target.getName(),
                    "id", listing.getId());
                return;
            }
            MessageUtil.send(player, "<prefix><red>That player is not a co-owner on this blueprint.");
            return;
        }
        MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp team <add|remove|list> <id> [player] [share]</white>");
    }

    private void handleCurate(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "vortexblueprints.admin")) {
            return;
        }
        if (args.length < 4) {
            MessageUtil.send(sender, "<prefix><yellow>Usage: <white>/vbp curate <feature|staffpick> <id> <true|false></white>");
            return;
        }
        BlueprintListing listing = plugin.getDataManager().getBlueprint(args[2]).orElse(null);
        if (listing == null) {
            MessageUtil.send(sender, plugin.getConfigManager().message("not-found"), "id", args[2]);
            return;
        }
        boolean enabled = Boolean.parseBoolean(args[3]);
        if (args[1].equalsIgnoreCase("feature")) {
            listing.setFeatured(enabled);
        } else if (args[1].equalsIgnoreCase("staffpick")) {
            listing.setStaffPick(enabled);
        } else {
            MessageUtil.send(sender, "<prefix><yellow>Usage: <white>/vbp curate <feature|staffpick> <id> <true|false></white>");
            return;
        }
        plugin.getDataManager().saveBlueprintAsync(listing);
        MessageUtil.send(sender, "<prefix><green>Updated curation flags for <white><id></white>.", "id", listing.getId());
    }

    private void handlePanel(CommandSender sender) {
        if (!requirePermission(sender, "vortexblueprints.admin")) {
            return;
        }
        if (!plugin.getConfigManager().webPanelEnabled()) {
            MessageUtil.send(sender, "<prefix><yellow>The web panel is disabled in config.yml.");
            return;
        }
        MessageUtil.send(sender, plugin.getConfigManager().message("web-panel"), "url", plugin.getExternalBridgeService().panelUrl());
    }

    private void handleProbe(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "vortexblueprints.admin")) {
            return;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("protection")) {
            MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp probe protection [x] [y] [z]</white>");
            return;
        }
        Location location = player.getLocation();
        if (args.length >= 5) {
            Double x = parseDouble(args[2]);
            Double y = parseDouble(args[3]);
            Double z = parseDouble(args[4]);
            if (x == null || y == null || z == null) {
                MessageUtil.send(player, "<prefix><red>Coordinates must be valid numbers.");
                return;
            }
            location = new Location(player.getWorld(), x, y, z);
        }
        MessageUtil.send(player, "<prefix><aqua>Protection probe at <white><location></white>", "location", MessageUtil.formatLocation(location));
        for (com.sauron.vortexblueprints.service.ProtectionService.ProtectionDiagnosis diagnosis : plugin.getProtectionService().diagnose(player, location)) {
            String state = diagnosis.active() ? (diagnosis.denied() ? "<red>DENY" : "<green>ALLOW") : "<gray>INACTIVE";
            MessageUtil.send(player, "<gray><hook></gray> <dark_gray>-</dark_gray> " + state + " <dark_gray>|</dark_gray> <white><detail></white>",
                "hook", diagnosis.hookName(),
                "detail", diagnosis.detail());
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            MessageUtil.send(player, "<prefix><yellow>Usage: <white>/vbp delete <id></white>");
            return;
        }
        BlueprintListing listing = plugin.getDataManager().getBlueprint(args[1]).orElse(null);
        if (listing == null) {
            MessageUtil.send(player, plugin.getConfigManager().message("not-found"), "id", args[1]);
            return;
        }
        if (!listing.isOwner(player.getUniqueId()) && !player.hasPermission("vortexblueprints.admin")) {
            MessageUtil.send(player, plugin.getConfigManager().message("delete-denied"));
            return;
        }
        plugin.getDataManager().deleteBlueprint(listing.getId());
        MessageUtil.send(player, plugin.getConfigManager().message("deleted"), "id", listing.getId());
    }

    private void handleBalance(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null || !requirePermission(player, "vortexblueprints.use")) {
            return;
        }
        MessageUtil.send(player, plugin.getConfigManager().message("balance"),
            "balance", plugin.getEconomyService().format(plugin.getEconomyService().balance(player)));
    }

    private void handleCredits(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "vortexblueprints.admin")) {
            return;
        }
        if (args.length < 4 || !args[1].equalsIgnoreCase("give")) {
            MessageUtil.send(sender, "<prefix><yellow>Usage: <white>/vbp credits give <onlinePlayer> <amount></white>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        Double amount = parseDouble(args[3]);
        if (target == null || amount == null || amount <= 0.0D) {
            MessageUtil.send(sender, "<prefix><red>Provide an online player and a positive amount.");
            return;
        }
        plugin.getEconomyService().deposit(target.getUniqueId(), target.getName(), amount);
        MessageUtil.send(sender, plugin.getConfigManager().message("credits-given"),
            "amount", plugin.getEconomyService().format(amount),
            "player", target.getName());
    }

    private void handleReload(CommandSender sender) {
        if (!requirePermission(sender, "vortexblueprints.admin")) {
            return;
        }
        plugin.getConfigManager().load();
        MessageUtil.send(sender, plugin.getConfigManager().message("reload-success"));
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.send(sender, "<gradient:#38bdf8:#22c55e><bold>VortexBlueprints</bold></gradient> <gray>Commands</gray>");
        MessageUtil.send(sender, "<gray>/vbp wand</gray> <dark_gray>-</dark_gray> <white>Get the selection wand</white>");
        MessageUtil.send(sender, "<gray>/vbp save <id> <price> [royalty] [category] [license] [style] [description]</gray> <dark_gray>-</dark_gray> <white>Publish a categorized blueprint</white>");
        MessageUtil.send(sender, "<gray>/vbp market [view] [category] [page]</gray> <dark_gray>-</dark_gray> <white>Browse curated market slices</white>");
        MessageUtil.send(sender, "<gray>/vbp build <id></gray> <dark_gray>-</dark_gray> <white>Build at your feet</white>");
        MessageUtil.send(sender, "<gray>/vbp preview <id></gray> <dark_gray>-</dark_gray> <white>Preview the footprint</white>");
        MessageUtil.send(sender, "<gray>/vbp analytics</gray> <dark_gray>-</dark_gray> <white>Open your creator analytics</white>");
        MessageUtil.send(sender, "<gray>/vbp rate <id></gray> <dark_gray>-</dark_gray> <white>Open the rating GUI for a purchased blueprint</white>");
        MessageUtil.send(sender, "<gray>/vbp review gui|list|approve|reject</gray> <dark_gray>-</dark_gray> <white>Moderate originality review queue</white>");
        MessageUtil.send(sender, "<gray>/vbp dispute gui|open|resolve</gray> <dark_gray>-</dark_gray> <white>Manage originality disputes</white>");
        MessageUtil.send(sender, "<gray>/vbp team gui|add|remove|list</gray> <dark_gray>-</dark_gray> <white>Manage co-owner royalty splits</white>");
        MessageUtil.send(sender, "<gray>/vbp curate feature|staffpick</gray> <dark_gray>-</dark_gray> <white>Set marketplace curation flags</white>");
        MessageUtil.send(sender, "<gray>/vbp probe protection [x] [y] [z]</gray> <dark_gray>-</dark_gray> <white>Diagnose claim-hook results at a location</white>");
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        MessageUtil.send(sender, plugin.getConfigManager().message("player-only"));
        return null;
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("vortexblueprints.admin")) {
            return true;
        }
        MessageUtil.send(sender, plugin.getConfigManager().message("no-permission"));
        return false;
    }

    private Optional<String> normalizeId(String rawId) {
        String normalized = rawId.toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(normalized).matches()) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private Double parseDouble(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer parseInteger(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private BlueprintCategory parseCategory(String input) {
        for (BlueprintCategory category : BlueprintCategory.values()) {
            if (category.name().equalsIgnoreCase(input) || category.getDisplayName().equalsIgnoreCase(input.replace('_', ' '))) {
                return category;
            }
        }
        return null;
    }

    private BlueprintLicenseType parseLicense(String input) {
        for (BlueprintLicenseType type : BlueprintLicenseType.values()) {
            if (type.name().equalsIgnoreCase(input) || type.getDisplayName().equalsIgnoreCase(input.replace('_', ' '))) {
                return type;
            }
        }
        return null;
    }

    private BuildStyle parseBuildStyle(String input) {
        for (BuildStyle style : BuildStyle.values()) {
            if (style.name().equalsIgnoreCase(input) || style.getDisplayName().equalsIgnoreCase(input.replace('_', ' '))) {
                return style;
            }
        }
        return null;
    }

    private MarketView parseMarketView(String input) {
        for (MarketView view : MarketView.values()) {
            if (view.name().equalsIgnoreCase(input)) {
                return view;
            }
        }
        return null;
    }

    private boolean validRating(Integer rating) {
        return rating != null && rating >= 1 && rating <= 5;
    }

    private String joinTail(String[] args, int startIndex) {
        StringBuilder builder = new StringBuilder();
        for (int index = startIndex; index < args.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private List<String> filter(List<String> options, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerInput)) {
                results.add(option);
            }
        }
        return results;
    }
}