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

    private static final class HttpError extends RuntimeException {
        final int status;
        HttpError(int status, String message) { super(message); this.status = status; }
    }

    @FunctionalInterface
    private interface Handler { void handle(HttpExchange exchange) throws Exception; }

    /** Wraps a handler so exceptions yield an error response instead of a dropped connection. */
    private void ctx(String path, Handler handler) {
        server.createContext(path, exchange -> {
            try {
                handler.handle(exchange);
            } catch (HttpError e) {
                try { err(exchange, e.status, e.getMessage()); } catch (Throwable ignored) {}
            } catch (com.google.gson.JsonParseException | IllegalStateException | UnsupportedOperationException | ClassCastException e) {
                // malformed / wrongly shaped JSON from the client
                try { err(exchange, 400, "Invalid request body (expected a JSON object)."); } catch (Throwable ignored) {}
            } catch (Throwable t) {
                Throwable root = t instanceof java.util.concurrent.ExecutionException && t.getCause() != null ? t.getCause() : t;
                plugin.getLogger().warning("[Web] Request failed for " + path + ": " + root);
                boolean busy = root instanceof java.util.concurrent.TimeoutException;
                try { err(exchange, busy ? 503 : 500, busy ? "Server is busy, try again." : "Internal error while handling this request. Reload and retry; if it persists an admin should check the server console for the '[Web] Request failed' line."); } catch (Throwable ignored) {}
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
        if (bytes.length > MAX_BODY_BYTES) throw new HttpError(413, "Request body too large.");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private JsonObject readJson(HttpExchange exchange) throws IOException {
        JsonObject obj = gson.fromJson(readBody(exchange), JsonObject.class);
        if (obj == null) throw new HttpError(400, "Request body must be a JSON object.");
        return obj;
    }

    private String stringField(JsonObject o, String key) {
        var e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return "";
        return e.getAsString().trim();
    }

    /** @return true when the method matches; otherwise sends 405 and returns false. */
    private boolean requireMethod(HttpExchange exchange, String method) throws IOException {
        if (method.equalsIgnoreCase(exchange.getRequestMethod())) return true;
        exchange.getResponseHeaders().set("Allow", method);
        err(exchange, 405, "Method not allowed; use " + method + ".");
        return false;
    }

    private void err(HttpExchange exchange, int code, String message) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("ok", false);
        body.addProperty("error", message);
        send(exchange, code, gson.toJson(body), "application/json; charset=utf-8");
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

        if (server != null) {
            plugin.getLogger().info("[Web] Dashboard already running.");
            return;
        }
        String bindAddress = plugin.getConfigManager().getConfig().getString("web.bind-address", "127.0.0.1");
        int port = plugin.getConfigManager().getConfig().getInt("web.port", 8123);
        try {
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
            ctx("/api/logout", this::handleLogout);
            executor = java.util.concurrent.Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "OmniArena-Web");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            server.start();
            plugin.getLogger().info("[Web] Dashboard running on " + bindAddress + ":" + port);
        } catch (java.net.BindException e) {
            plugin.getLogger().severe("[Web] Cannot start dashboard: " + bindAddress + ":" + port + " is unavailable ("
                    + e.getMessage() + "). Another program (or another server) is using that port. "
                    + "Fix: set web.port in config.yml to a free port (or web.bind-address to a local address) and run /oa restart.");
            abortStart();
        } catch (IOException | IllegalArgumentException | SecurityException e) {
            plugin.getLogger().severe("[Web] Failed to start dashboard on " + bindAddress + ":" + port + ": " + e
                    + ". Check web.port / web.bind-address in config.yml.");
            abortStart();
        }
    }

    private void abortStart() {
        if (server != null) { try { server.stop(0); } catch (Throwable ignored) {} server = null; }
        if (executor != null) { executor.shutdownNow(); executor = null; }
    }

    public void stop() {
        sessions.killAll();
        abortStart(); // stops the server (releases the port immediately) and the worker pool
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
        if (!requireMethod(exchange, "GET")) return;
        String path = exchange.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) {
            err(exchange, 404, "Unknown dashboard endpoint. Reload the page; if it persists the dashboard and plugin versions may differ.");
            return;
        }
        sendResource(exchange, "web/index.html", "text/html; charset=utf-8");
    }

    private void handleLogout(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) return;
        if (!authorize(exchange)) return;
        sessions.revoke(extractToken(exchange));
        send(exchange, 200, "{\"ok\":true}", "application/json; charset=utf-8");
    }

    private void handleState(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) return;
        if (!authorize(exchange)) return;
        ConfigSnapshot snapshot = onMain(schemaService::snapshot);
        send(exchange, 200, gson.toJson(snapshot), "application/json; charset=utf-8");
    }

    private void handleDocs(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) return;
        if (!authorize(exchange)) return;
        send(exchange, 200, docsCatalog.loadMarkdown(), "text/markdown; charset=utf-8");
    }

    private void handleApply(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) return;
        if (!authorize(exchange)) return;
        JsonObject payload = readJson(exchange);
        List<String> errors = onMain(() -> applyService.apply(payload, schemaService));
        if (!errors.isEmpty()) {
            JsonObject body = new JsonObject();
            body.addProperty("ok", false);
            body.addProperty("error", String.join("; ", errors));
            body.add("errors", gson.toJsonTree(errors));
            send(exchange, 400, gson.toJson(body), "application/json; charset=utf-8");
            return;
        }
        send(exchange, 200, "{\"ok\":true}", "application/json; charset=utf-8");
    }

    private void handleCreateArena(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) return;
        if (!authorize(exchange)) return;
        final String arenaName = stringField(readJson(exchange), "name");
        if (!ARENA_NAME.matcher(arenaName).matches()) {
            err(exchange, 400, "Invalid arena name: use 1-32 letters, digits, '_' or '-'.");
            return;
        }
        boolean created = onMain(() -> {
            if (plugin.getArenaManager().exists(arenaName)) return false;
            plugin.getArenaManager().create(arenaName);
            return true;
        });
        if (!created) {
            err(exchange, 409, "An arena named '" + arenaName + "' already exists.");
            return;
        }
        send(exchange, 200, "{\"ok\":true}", "application/json; charset=utf-8");
    }

    private void handleDeleteArena(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "POST")) return;
        if (!authorize(exchange)) return;
        final String arenaName = stringField(readJson(exchange), "name");
        if (!ARENA_NAME.matcher(arenaName).matches()) {
            err(exchange, 400, "Invalid arena name.");
            return;
        }
        String problem = onMain(() -> {
            var arena = plugin.getArenaManager().get(arenaName).orElse(null);
            if (arena == null) return "No arena named '" + arenaName + "'.";
            var mm = com.omniarena.match.MatchManager.getInstance();
            if (mm != null && mm.getMatch(arena).isPresent())
                return "Arena '" + arenaName + "' has a match in progress; end it first (/oa match stop " + arenaName + ").";
            return plugin.getArenaManager().delete(arenaName) ? null : "Could not delete '" + arenaName + "'.";
        });
        if (problem != null) { err(exchange, problem.startsWith("No arena") ? 404 : 409, problem); return; }
        send(exchange, 200, "{\"ok\":true}", "application/json; charset=utf-8");
    }

    private void handleLiveMatches(HttpExchange exchange) throws Exception {
        if (!requireMethod(exchange, "GET")) return;
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
        if (!requireMethod(exchange, "GET")) return;
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
        var eloRating = eloMgr != null ? eloMgr.peekRating(player.getUniqueId()) : null;
        int elo = eloRating != null ? eloRating.getRating() : 1000;
        int wins = eloRating != null ? eloRating.getWins() : 0;
        int losses = eloRating != null ? eloRating.getLosses() : 0;
        int matches = wins + losses;
        var statsMgr = plugin.getStatsManager();
        var ps = statsMgr != null ? statsMgr.peekStats(player.getUniqueId()) : null;
        double kd = ps != null ? ps.getKDRatio() : 0.0;
        if (ps != null) {
            wins = Math.max(wins, ps.getWins());
            losses = Math.max(losses, ps.getLosses());
            matches = Math.max(matches, ps.getWins() + ps.getLosses() + ps.getDraws());
        }
        return new PlayerStatsView(
            player.getName(),
            player.getUniqueId().toString(),
            elo,
            wins,
            losses,
            kd,
            matches,
            ps != null ? ps.getKills() : 0,
            ps != null ? ps.getDeaths() : 0,
            0,
            "Online"
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
        if (sessions.isValid(extractToken(exchange))) return true;
        err(exchange, 401, "Invalid or expired session. Run /oa website in-game for a new link.");
        return false;
    }

    /** Token from "Authorization: Bearer", "X-OmniArena-Token", or (legacy) the ?token= query parameter. */
    private String extractToken(HttpExchange exchange) {
        Headers h = exchange.getRequestHeaders();
        String auth = h.getFirst("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) return auth.substring(7).trim();
        String custom = h.getFirst("X-OmniArena-Token");
        if (custom != null && !custom.isBlank()) return custom.trim();
        return queryParams(exchange.getRequestURI()).get("token");
    }

    private void secureHeaders(Headers headers) {
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer"); // keep ?token= from leaking via Referer
        headers.set("Cross-Origin-Resource-Policy", "same-origin"); // no CORS headers are ever sent: same-origin only
        headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self'; connect-src 'self'; "
                + "style-src 'self' 'unsafe-inline' https://unpkg.com https://fonts.googleapis.com; "
                + "font-src 'self' https://unpkg.com https://fonts.gstatic.com; img-src 'self' data:; "
                + "frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
    }

    private void sendResource(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            err(exchange, 405, "Method not allowed; use GET.");
            return;
        }
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) {
                send(exchange, 404, "Missing resource: " + resourcePath, "text/plain; charset=utf-8");
                return;
            }
            byte[] bytes = in.readAllBytes();
            if ("web/index.html".equals(resourcePath)) {
                // web.title customises the browser tab title
                String title = plugin.getConfig().getString("web.title", "OmniArena Control");
                String safe = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                bytes = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("<title>OmniArena Control</title>", "<title>" + safe + "</title>")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType);
            secureHeaders(headers);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send dashboard resource " + resourcePath + " (" + e + "). The client likely disconnected; reload the page, or check the plugin jar if it persists.");
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
            secureHeaders(headers);
            exchange.sendResponseHeaders(code, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send dashboard response (" + e + "); the browser probably closed the connection, reload the page.");
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
