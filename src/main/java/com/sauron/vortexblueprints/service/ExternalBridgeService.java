package com.sauron.vortexblueprints.service;

import com.sauron.vortexblueprints.VortexBlueprintsPlugin;
import com.sauron.vortexblueprints.manager.ConfigManager;
import com.sauron.vortexblueprints.manager.DataManager;
import com.sauron.vortexblueprints.model.BlueprintListing;
import com.sauron.vortexblueprints.model.DisputeRecord;
import com.sauron.vortexblueprints.model.ReviewTicket;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExternalBridgeService {

    private final VortexBlueprintsPlugin plugin;
    private final ConfigManager configManager;
    private final DataManager dataManager;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private HttpServer server;

    public ExternalBridgeService(VortexBlueprintsPlugin plugin, ConfigManager configManager, DataManager dataManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.dataManager = dataManager;
    }

    public void start() {
        if (!configManager.webPanelEnabled()) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(configManager.webPanelPort()), 0);
            server.createContext("/", this::handleRoot);
            server.createContext("/api/blueprints", this::handleBlueprints);
            server.createContext("/api/reviews", this::handleReviews);
            server.createContext("/api/disputes", this::handleDisputes);
            server.start();
            plugin.getLogger().info("Web panel started on http://127.0.0.1:" + configManager.webPanelPort() + "/");
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to start web panel: " + exception.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public String panelUrl() {
        return "http://127.0.0.1:" + configManager.webPanelPort() + "/?token=" + configManager.webPanelToken();
    }

    public void notifyReview(ReviewTicket ticket) {
        if (configManager.discordWebhookUrl().isBlank() || !configManager.discordNotifyReviews()) {
            return;
        }
        sendDiscord("Review queue: " + ticket.getBlueprintId() + " | similarity " + String.format(java.util.Locale.ROOT, "%.2f", ticket.getSimilarity()));
    }

    public void notifyDispute(DisputeRecord dispute) {
        if (configManager.discordWebhookUrl().isBlank() || !configManager.discordNotifyDisputes()) {
            return;
        }
        sendDiscord("Dispute opened: " + dispute.getBlueprintId() + " vs " + dispute.getAgainstBlueprintId() + " by " + dispute.getReporterName());
    }

    public void notifyMilestone(BlueprintListing listing) {
        if (configManager.discordWebhookUrl().isBlank() || !configManager.discordNotifyMilestones()) {
            return;
        }
        sendDiscord("Milestone unlocked: " + listing.getId() + " reached level " + listing.getMilestoneLevel());
    }

    private void sendDiscord(String message) {
        String json = "{\"content\":\"" + escapeJson(message) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(configManager.discordWebhookUrl()))
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()).exceptionally(throwable -> null);
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            return;
        }
        List<BlueprintListing> topListings = dataManager.getMarketplaceListings().stream().sorted(Comparator.comparingLong(BlueprintListing::getBuilds).reversed()).limit(10).toList();
        List<ReviewTicket> openTickets = dataManager.getReviewTickets().stream().filter(ticket -> ticket.getStatus() == ReviewTicket.Status.OPEN).sorted(Comparator.comparingLong(ReviewTicket::getCreatedAt)).limit(10).toList();
        List<DisputeRecord> openDisputes = dataManager.getDisputes().stream().filter(dispute -> dispute.getStatus() == DisputeRecord.Status.OPEN || dispute.getStatus() == DisputeRecord.Status.UNDER_REVIEW).sorted(Comparator.comparingLong(DisputeRecord::getCreatedAt).reversed()).limit(10).toList();
        String tokenQuery = configManager.webPanelToken() == null || configManager.webPanelToken().isBlank() ? "" : "?token=" + escapeHtml(configManager.webPanelToken());
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>VortexBlueprints Panel</title><style>body{font-family:Segoe UI,Arial,sans-serif;background:#0f172a;color:#e2e8f0;padding:24px}table{width:100%;border-collapse:collapse;margin-top:18px}td,th{padding:10px;border-bottom:1px solid #334155;vertical-align:top}a{color:#38bdf8}input{background:#111827;color:#e2e8f0;border:1px solid #475569;border-radius:6px;padding:8px;width:220px}button{background:#0ea5e9;color:#04111d;border:0;border-radius:6px;padding:8px 12px;margin-right:6px;cursor:pointer}.danger{background:#f97316}.muted{color:#94a3b8}</style></head><body>");
        html.append("<h1>VortexBlueprints</h1>");
        html.append("<p>Listings: ").append(dataManager.getBlueprints().size()).append(" | Reviews: ").append(dataManager.getReviewTickets().size()).append(" | Disputes: ").append(dataManager.getDisputes().size()).append("</p>");
        html.append("<p><a href=\"/api/blueprints").append(tokenQuery).append("\">Blueprint JSON</a> <span class=\"muted\">Use POST on /api/reviews and /api/disputes for moderation.</span></p>");
        html.append("<table><tr><th>ID</th><th>Creator</th><th>Category</th><th>Price</th><th>Builds</th><th>Rating</th></tr>");
        for (BlueprintListing listing : topListings) {
            html.append("<tr><td>").append(escapeHtml(listing.getId())).append("</td><td>").append(escapeHtml(listing.getOwnerName())).append("</td><td>").append(escapeHtml(listing.getCategory().getDisplayName())).append("</td><td>").append(listing.getPrice()).append("</td><td>").append(listing.getBuilds()).append("</td><td>").append(String.format(java.util.Locale.ROOT, "%.2f", listing.getAverageRating())).append("</td></tr>");
        }
        html.append("</table>");
        html.append("<h2>Review Queue</h2><table><tr><th>Ticket</th><th>Blueprint</th><th>Creator</th><th>Similarity</th><th>Action</th></tr>");
        for (ReviewTicket ticket : openTickets) {
            html.append("<tr><td>").append(escapeHtml(ticket.getId())).append("</td><td>").append(escapeHtml(ticket.getBlueprintId())).append("</td><td>").append(escapeHtml(ticket.getCreatorName())).append("</td><td>").append(String.format(java.util.Locale.ROOT, "%.2f", ticket.getSimilarity() * 100.0D)).append("%</td><td>")
                .append(reviewForm(ticket, tokenQuery))
                .append("</td></tr>");
        }
        if (openTickets.isEmpty()) {
            html.append("<tr><td colspan=\"5\" class=\"muted\">No open review tickets.</td></tr>");
        }
        html.append("</table>");
        html.append("<h2>Disputes</h2><table><tr><th>ID</th><th>Blueprint</th><th>Against</th><th>Reporter</th><th>Action</th></tr>");
        for (DisputeRecord dispute : openDisputes) {
            html.append("<tr><td>").append(escapeHtml(dispute.getId())).append("</td><td>").append(escapeHtml(dispute.getBlueprintId())).append("</td><td>").append(escapeHtml(dispute.getAgainstBlueprintId())).append("</td><td>").append(escapeHtml(dispute.getReporterName())).append("</td><td>")
                .append(disputeForm(dispute, tokenQuery))
                .append("</td></tr>");
        }
        if (openDisputes.isEmpty()) {
            html.append("<tr><td colspan=\"5\" class=\"muted\">No active disputes.</td></tr>");
        }
        html.append("</table></body></html>");
        write(exchange, 200, "text/html; charset=utf-8", html.toString());
    }

    private void handleBlueprints(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            return;
        }
        StringBuilder json = new StringBuilder("[");
        List<BlueprintListing> listings = dataManager.getMarketplaceListings();
        for (int index = 0; index < listings.size(); index++) {
            BlueprintListing listing = listings.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"id\":\"").append(escapeJson(listing.getId())).append("\",\"creator\":\"").append(escapeJson(listing.getOwnerName())).append("\",\"category\":\"").append(listing.getCategory().name()).append("\",\"price\":").append(listing.getPrice()).append(",\"builds\":").append(listing.getBuilds()).append(",\"rating\":").append(String.format(java.util.Locale.ROOT, "%.2f", listing.getAverageRating())).append('}');
        }
        json.append(']');
        write(exchange, 200, "application/json; charset=utf-8", json.toString());
    }

    private void handleReviews(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            return;
        }
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            handleReviewMutation(exchange);
            return;
        }
        StringBuilder json = new StringBuilder("[");
        List<ReviewTicket> tickets = dataManager.getReviewTickets();
        for (int index = 0; index < tickets.size(); index++) {
            ReviewTicket ticket = tickets.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"id\":\"").append(escapeJson(ticket.getId())).append("\",\"blueprint\":\"").append(escapeJson(ticket.getBlueprintId())).append("\",\"creator\":\"").append(escapeJson(ticket.getCreatorName())).append("\",\"status\":\"").append(ticket.getStatus().name()).append("\",\"similarity\":").append(String.format(java.util.Locale.ROOT, "%.4f", ticket.getSimilarity())).append('}');
        }
        json.append(']');
        write(exchange, 200, "application/json; charset=utf-8", json.toString());
    }

    private void handleDisputes(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            return;
        }
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            handleDisputeMutation(exchange);
            return;
        }
        StringBuilder json = new StringBuilder("[");
        List<DisputeRecord> disputes = dataManager.getDisputes();
        for (int index = 0; index < disputes.size(); index++) {
            DisputeRecord dispute = disputes.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"id\":\"").append(escapeJson(dispute.getId())).append("\",\"blueprint\":\"").append(escapeJson(dispute.getBlueprintId())).append("\",\"against\":\"").append(escapeJson(dispute.getAgainstBlueprintId())).append("\",\"status\":\"").append(dispute.getStatus().name()).append("\"}");
        }
        json.append(']');
        write(exchange, 200, "application/json; charset=utf-8", json.toString());
    }

    private void handleReviewMutation(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        String action = form.getOrDefault("action", "").trim().toLowerCase(java.util.Locale.ROOT);
        String ticketId = form.getOrDefault("id", "").trim();
        String notes = form.getOrDefault("notes", "").trim();
        String actor = form.getOrDefault("actor", "web-panel").trim();
        if (ticketId.isBlank()) {
            write(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"Missing ticket id.\"}");
            return;
        }
        java.util.Optional<String> failure = switch (action) {
            case "approve" -> plugin.getModerationService().approveReview(ticketId, actor, notes);
            case "reject" -> plugin.getModerationService().rejectReview(ticketId, actor, notes);
            default -> java.util.Optional.of("Unsupported review action.");
        };
        if (failure.isPresent()) {
            write(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"" + escapeJson(failure.get()) + "\"}");
            return;
        }
        respondMutationSuccess(exchange, "review", ticketId, action);
    }

    private void handleDisputeMutation(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        String action = form.getOrDefault("action", "").trim().toLowerCase(java.util.Locale.ROOT);
        String disputeId = form.getOrDefault("id", "").trim();
        String notes = form.getOrDefault("notes", "").trim();
        String actor = form.getOrDefault("actor", "web-panel").trim();
        if (disputeId.isBlank()) {
            write(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"Missing dispute id.\"}");
            return;
        }
        java.util.Optional<String> failure = switch (action) {
            case "resolve", "resolved" -> plugin.getModerationService().resolveDispute(disputeId, actor, notes);
            case "reject", "rejected" -> plugin.getModerationService().rejectDispute(disputeId, actor, notes);
            default -> java.util.Optional.of("Unsupported dispute action.");
        };
        if (failure.isPresent()) {
            write(exchange, 400, "application/json; charset=utf-8", "{\"error\":\"" + escapeJson(failure.get()) + "\"}");
            return;
        }
        respondMutationSuccess(exchange, "dispute", disputeId, action);
    }

    private boolean authorized(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getRawQuery();
        String expectedToken = configManager.webPanelToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            return true;
        }
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.equals("token=" + expectedToken)) {
                    return true;
                }
            }
        }
        write(exchange, 401, "text/plain; charset=utf-8", "Unauthorized");
        return false;
    }

    private void respondMutationSuccess(HttpExchange exchange, String type, String id, String action) throws IOException {
        String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");
        boolean prefersHtml = acceptHeader != null && acceptHeader.toLowerCase(java.util.Locale.ROOT).contains("text/html");
        if (prefersHtml) {
            String location = panelUrl();
            exchange.getResponseHeaders().set("Location", location);
            exchange.sendResponseHeaders(303, -1L);
            exchange.close();
            return;
        }
        write(exchange, 200, "application/json; charset=utf-8", "{\"ok\":true,\"type\":\"" + escapeJson(type) + "\",\"id\":\"" + escapeJson(id) + "\",\"action\":\"" + escapeJson(action) + "\"}");
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        mergeForm(values, exchange.getRequestURI().getRawQuery());
        byte[] body = exchange.getRequestBody().readAllBytes();
        if (body.length > 0) {
            mergeForm(values, new String(body, StandardCharsets.UTF_8));
        }
        return values;
    }

    private void mergeForm(Map<String, String> values, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String pair : raw.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            values.put(key, value);
        }
    }

    private String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void write(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private String reviewForm(ReviewTicket ticket, String tokenQuery) {
        return "<form method=\"post\" action=\"/api/reviews" + tokenQuery + "\">"
            + "<input type=\"hidden\" name=\"id\" value=\"" + escapeHtml(ticket.getId()) + "\">"
            + "<input type=\"text\" name=\"notes\" placeholder=\"Moderator notes\">"
            + "<button name=\"action\" value=\"approve\">Approve</button>"
            + "<button class=\"danger\" name=\"action\" value=\"reject\">Reject</button>"
            + "</form>";
    }

    private String disputeForm(DisputeRecord dispute, String tokenQuery) {
        return "<form method=\"post\" action=\"/api/disputes" + tokenQuery + "\">"
            + "<input type=\"hidden\" name=\"id\" value=\"" + escapeHtml(dispute.getId()) + "\">"
            + "<input type=\"text\" name=\"notes\" placeholder=\"Moderator notes\">"
            + "<button name=\"action\" value=\"resolve\">Resolve</button>"
            + "<button class=\"danger\" name=\"action\" value=\"reject\">Reject</button>"
            + "</form>";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}