package com.omniarena.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.match.Match;
import com.omniarena.match.MatchTeam;
import com.omniarena.stats.PlayerStats;
import com.omniarena.web.model.ConfigSnapshot;
import com.omniarena.web.model.LiveMatchData;
import com.omniarena.web.model.LivePlayerStat;
import com.omniarena.web.model.LiveTeamStats;
import com.omniarena.web.model.PlayerStatsView;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class WebDashboardServer {

    private final OmniArena plugin;
    private final Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private final WebSessionManager sessions;
    private final ConfigSchemaService schemaService;
    private final ConfigApplyService applyService = new ConfigApplyService();
    private final DocsCatalog docsCatalog;
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private static final int MAX_BODY_BYTES = 1024 * 1024;
    private static final java.util.regex.Pattern ARENA_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{1,32}");

    @FunctionalInterface
    private interface Handler { void handle(HttpExchange exchange) throws Exception; }

    /** Wraps a handler so exceptions yield an error response instead of a dropped connection. */
    private void ctx(String path, Handler handler) {
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } catch (Throwable t) {
                plugin.getLogger().warning("[Web] Request failed for " + path + ": " + t);
                try { send(exchange, 400, "{\"ok\":false}", "application/json; charset=utf-8"); } catch (Throwable ignored) {}
            }
        });
    }

    /** Runs Bukkit API work on the main thread and waits for the result. */
    private <T> T onMain(java.util.concurrent.Callable<T> task) throws Exception {
        if (Bukkit.isPrimaryThread()) return task.call();
        return Bukkit.getScheduler().callSyncMethod(plugin, task).get(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new IOException("Request body too large");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public WebDashboardServer(OmniArena plugin) {
        this.plugin = plugin;
        long minutes = plugin.getConfigManager().getConfig().getLong("web.session-minutes", 30);
        int maxSessions = plugin.getConfigManager().getConfig().getInt("web.max-sessions", 1);
        this.sessions = new WebSessionManager(Duration.ofMinutes(Math.max(1, minutes)), Math.max(0, maxSessions));
        this.schemaService = new ConfigSchemaService(plugin);
        this.docsCatalog = new DocsCatalog(plugin);
    }

    public void start() {
        if (!plugin.getConfigManager().getConfig().getBoolean("web.enabled", true)) {
            plugin.getLogger().info("[Web] Dashboard disabled.");
            return;
        }

        try {
            String bindAddress = plugin.getConfigManager().getConfig().getString("web.bind-address", "127.0.0.1");
            int port = plugin.getConfigManager().getConfig().getInt("web.port", 8123);
            server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            ctx("/", this::handleRoot);
            ctx("/styles.css", exchange -> sendResource(exchange, "web/styles.css", "text/css; charset=utf-8"));
            ctx("/app.js", exchange -> sendResource(exchange, "web/app.js", "application/javascript; charset=utf-8"));
            // legacy paths kept for compatibility
            ctx("/web/styles.css", exchange -> sendResource(exchange, "web/styles.css", "text/css; charset=utf-8"));
            ctx("/web/app.js", exchange -> sendResource(exchange, "web/app.js", "application/javascript; charset=utf-8"));
            ctx("/api/state", this::handleState);
            ctx("/api/apply", this::handleApply);
            ctx("/api/create-arena", this::handleCreateArena);
            ctx("/api/delete-arena", this::handleDeleteArena);
            ctx("/api/docs", this::handleDocs);
            ctx("/api/live-matches", this::handleLiveMatches);
            ctx("/api/players", this::handlePlayers);
            executor = java.util.concurrent.Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "OmniArena-Web");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            server.start();
            plugin.getLogger().info("[Web] Dashboard running on " + bindAddress + ":" + port);
        } catch (IOException e) {
            plugin.getLogger().severe("[Web] Failed to start dashboard: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** @return URL string, or null if session limit reached */
    public String createEditorUrl() {
        String token = sessions.createSession();
        if (token == null) return null;
        return dashboardUrl(token);
    }

    public void killAllSessions() {
        sessions.killAll();
    }

    public int activeSessionCount() {
        return sessions.activeCount();
    }

    public WebSessionManager getSessions() {
        return sessions;
    }

    private String dashboardUrl(String token) {
        String base = plugin.getConfigManager().getConfig().getString("web.public-url", "").trim();
        if (base.isBlank()) {
            base = "http://127.0.0.1:" + plugin.getConfigManager().getConfig().getInt("web.port", 8123);
        }
        return base.replaceAll("/+$", "") + "/?token=" + token;
    }

    private void handleRoot(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }
        sendResource(exchange, "web/index.html", "text/html; charset=utf-8");
    }

    private void handleState(HttpExchange exchange) throws Exception {
        if (!authorize(exchange)) return;
        ConfigSnapshot snapshot = onMain(schemaService::snapshot);
        send(exchange, 200, gson.toJson(snapshot), "application/json; charset=utf-8");
    }

    private void handleDocs(HttpExchange exchange) throws Exception {
        if (!authorize(exchange)) return;
        send(exchange, 200, docsCatalog.loadMarkdown(), "text/markdown; charset=utf-8");
    }

    private void handleApply(HttpExchange exchange) throws Exception {
        if (!authorize(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }
        JsonObject payload = gson.fromJson(readBody(exchange), JsonObject.class);
        onMain(() -> { applyService.apply(payload, schemaService); return null; });
        send(exchange, 200, "{\"ok\":true}", "application/json; charset=utf-8");
    }

    private void handleCreateArena(HttpExchange exchange) throws Exception {
        if (!authorize(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }
        JsonObject payload = gson.fromJson(readBody(exchange), JsonObject.class);
        String name = payload != null && payload.has("name") ? payload.get("name").getAsString().trim() : "";
        final String arenaName = name;
        boolean created = ARENA_NAME.matcher(arenaName).matches() && onMain(() -> {
            if (plugin.getArenaManager().exists(arenaName)) return false;
            plugin.getArenaManager().create(arenaName);
            return true;
        });
        if (!created) {
            send(exchange, 400, "{\"ok\":false}", "application/json; charset=utf-8");
            return;
        }
        send(exchange, 200, "{\"ok\":true}", "application/json; charset=utf-8");
    }

    private void handleDeleteArena(HttpExchange exchange) throws Exception {
        if (!authorize(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            send(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
            return;
        }
        JsonObject payload = gson.fromJson(readBody(exchange), JsonObject.class);
        String name = payload != null && payload.has("name") ? payload.get("name").getAsString().trim() : "";
        final String arenaName = name;
        boolean deleted = ARENA_NAME.matcher(arenaName).matches() && onMain(() -> plugin.getArenaManager().delete(arenaName));
        send(exchange, deleted ? 200 : 404, deleted ? "{\"ok\":true}" : "{\"ok\":false}", "application/json; charset=utf-8");
    }

    private void handleLiveMatches(HttpExchange exchange) throws Exception {
        if (!authorize(exchange)) return;
        List<LiveMatchData> matches = onMain(() -> {
        List<LiveMatchData> result = new ArrayList<>();
        var matchMgr = com.omniarena.match.MatchManager.getInstance();
        if (matchMgr != null) {
            for (Arena arena : plugin.getArenaManager().getAll()) {
                var matchOpt = matchMgr.getMatch(arena);
                if (matchOpt.isEmpty()) continue;
                result.add(buildLiveMatchData(arena, matchOpt.get()));
            }
        }
        return result;
        });
        send(exchange, 200, gson.toJson(matches), "application/json; charset=utf-8");
    }

    private void handlePlayers(HttpExchange exchange) throws Exception {
        if (!authorize(exchange)) return;
        String search = queryParams(exchange.getRequestURI()).get("search");
        List<PlayerStatsView> players = onMain(() -> {
        List<PlayerStatsView> result = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (search != null && !search.isBlank() && !online.getName().toLowerCase().contains(search.toLowerCase())) continue;
            result.add(buildPlayerStats(online));
        }
        return result;
        });
        players.sort((a, b) -> Integer.compare(b.elo(), a.elo()));
        send(exchange, 200, gson.toJson(players.stream().limit(100).collect(Collectors.toList())), "application/json; charset=utf-8");
    }

    private LiveMatchData buildLiveMatchData(Arena arena, Match match) {
        int elapsedSeconds = match.getElapsedSeconds();
        String timeStr = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
        String status = match.getState().toString();

        List<LivePlayerStat> leaderboard = buildLeaderboard(match);
        int aliveCount = (int) match.getAlivePlayers().stream().filter(uuid -> match.getPlayers().contains(uuid)).count();

        LiveTeamStats team1 = buildTeamStats(match.getTeams().isEmpty() ? null : match.getTeams().get(0));
        LiveTeamStats team2 = buildTeamStats(match.getTeams().size() > 1 ? match.getTeams().get(1) : null);

        return new LiveMatchData(
            arena.getName(),
            status,
            arena.getGameMode().getDisplayName(),
            timeStr,
            elapsedSeconds,
            match.getCountdownSeconds(),
            team1,
            team2,
            leaderboard,
            aliveCount,
            match.getPlayers().size()
        );
    }

    private List<LivePlayerStat> buildLeaderboard(Match match) {
        return match.getPlayers().stream()
            .map(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) return null;
                MatchTeam team = match.getTeamMap().get(uuid);
                return new LivePlayerStat(
                    p.getName(),
                    uuid.toString(),
                    match.getKillsMap().getOrDefault(uuid, 0),
                    match.getDeathsMap().getOrDefault(uuid, 0),
                    match.getStreakMap().getOrDefault(uuid, 0),
                    match.getComboMap().getOrDefault(uuid, 0),
                    team != null ? team.getColor().toString() : "SPECTATOR",
                    match.getAlivePlayers().contains(uuid)
                );
            })
            .filter(Objects::nonNull)
            .sorted((a, b) -> {
                int kcmp = Integer.compare(b.kills(), a.kills());
                return kcmp != 0 ? kcmp : Integer.compare(b.streak(), a.streak());
            })
            .collect(Collectors.toList());
    }

    private LiveTeamStats buildTeamStats(MatchTeam team) {
        if (team == null) {
            return new LiveTeamStats("", 0, 0, 0, 0);
        }
        return new LiveTeamStats(team.getColor().toString(), team.getScore(), team.getKills(), 0, team.getMembers().size());
    }

    private PlayerStatsView buildPlayerStats(Player player) {
        var eloMgr = plugin.getEloManager();
        var eloRating = eloMgr != null ? eloMgr.getRating(player.getUniqueId()) : null;
        int elo = eloRating != null ? eloRating.getRating() : 1000;
        int wins = eloRating != null ? eloRating.getWins() : 0;
        int losses = eloRating != null ? eloRating.getLosses() : 0;
        int matches = wins + losses;
        double kd = 0.0;
        return new PlayerStatsView(
            player.getName(),
            player.getUniqueId().toString(),
            elo,
            wins,
            losses,
            kd,
            matches,
            0,
            0,
            0,
            "Now"
        );
    }

    private String formatTime(long millis) {
        long minutes = millis / 60000;
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        return days + "d ago";
    }

    private boolean authorize(HttpExchange exchange) throws IOException {
        String token = queryParams(exchange.getRequestURI()).get("token");
        if (sessions.isValid(token)) return true;
        send(exchange, 401, "Invalid or expired session.", "text/plain; charset=utf-8");
        return false;
    }

    private void sendResource(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                send(exchange, 404, "Missing resource: " + resourcePath, "text/plain; charset=utf-8");
                return;
            }
            byte[] bytes = in.readAllBytes();
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType);
            headers.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send resource " + resourcePath + ": " + e.getMessage());
            try { send(exchange, 500, "Internal Server Error", "text/plain; charset=utf-8"); } catch (Exception ignored) {}
        } finally {
            exchange.close();
        }
    }

    private void send(HttpExchange exchange, int code, String body, String contentType) throws IOException {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType);
            headers.set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(code, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send response: " + e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private Map<String, String> queryParams(URI uri) {
        Map<String, String> params = new LinkedHashMap<>();
        String query = uri.getQuery();
        if (query == null || query.isBlank()) return params;
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            String key = decode(pair[0]);
            String value = pair.length > 1 ? decode(pair[1]) : "";
            params.put(key, value);
        }
        return params;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
