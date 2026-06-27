package com.omniarena.tournament;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.match.Match;
import com.omniarena.match.MatchResult;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TournamentManager {

    private final OmniArena plugin;
    private final Map<String, Tournament> tournaments = new ConcurrentHashMap<>();
    private final File dataFile;

    public TournamentManager(OmniArena plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tournaments.yml");
    }

    public void start() {
        loadAll();
    }

    // ── Create / Join / Leave / Delete ────────────────────────────

    public boolean createTournament(Player creator, String name, String arenaName, int maxPlayers) {
        if (tournaments.containsKey(name.toLowerCase())) {
            MessageUtil.send(creator, "<red>Tournament '<yellow>" + name + "</yellow>' already exists.");
            return false;
        }
        Arena arena = plugin.getArenaManager().get(arenaName).orElse(null);
        if (arena == null) {
            MessageUtil.send(creator, "<red>Arena not found: <yellow>" + arenaName);
            return false;
        }
        if (maxPlayers < 2) {
            MessageUtil.send(creator, "<red>Tournament needs at least 2 players.");
            return false;
        }

        Tournament t = new Tournament(name, arenaName, maxPlayers, creator.getUniqueId());
        tournaments.put(name.toLowerCase(), t);
        saveAll();

        Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                "<gold>[Tournament] <yellow>" + creator.getName() +
                " created tournament <white>" + name +
                " <gray>(" + arenaName + ", max " + maxPlayers + "). Use /oa tournament join " + name));
        return true;
    }

    public boolean joinTournament(Player player, String name) {
        Tournament t = getTournament(name);
        if (t == null) {
            MessageUtil.send(player, "<red>Tournament not found: <yellow>" + name);
            return false;
        }
        if (t.getState() != TournamentState.REGISTRATION) {
            MessageUtil.send(player, "<red>Tournament is not accepting registrations.");
            return false;
        }
        if (!t.addParticipant(player.getUniqueId())) {
            if (t.hasPlayer(player.getUniqueId()))
                MessageUtil.send(player, "<red>Already registered.");
            else
                MessageUtil.send(player, "<red>Tournament is full.");
            return false;
        }

        MessageUtil.send(player, "<green>Joined tournament <yellow>" + t.getName() +
                "</yellow>. Players: " + t.getParticipants().size() + "/" + t.getMaxPlayers());

        if (t.isFull()) {
            startTournament(t, null);
        } else {
            saveAll();
        }
        return true;
    }

    public boolean leaveTournament(Player player, String name) {
        Tournament t = getTournament(name);
        if (t == null) {
            MessageUtil.send(player, "<red>Tournament not found: <yellow>" + name);
            return false;
        }
        if (!t.removeParticipant(player.getUniqueId())) {
            MessageUtil.send(player, "<red>Not registered or tournament already started.");
            return false;
        }
        MessageUtil.send(player, "<yellow>Left tournament <white>" + t.getName() + "</white>.");
        saveAll();
        return true;
    }

    public void startTournament(Tournament t, Player requester) {
        if (t.getState() != TournamentState.REGISTRATION) {
            if (requester != null) MessageUtil.send(requester, "<red>Tournament already started.");
            return;
        }
        if (t.getParticipants().size() < 2) {
            if (requester != null) MessageUtil.send(requester, "<red>Need at least 2 players.");
            return;
        }

        t.setState(TournamentState.IN_PROGRESS);
        t.setBracket(new TournamentBracket(t.getParticipants(), t.getArenaName()));

        Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                "<gold>[Tournament] <white>" + t.getName() + " <yellow>has started! " +
                t.getParticipants().size() + " players competing."));

        launchPendingMatches(t);
    }

    public boolean deleteTournament(String name, Player admin) {
        Tournament t = getTournament(name);
        if (t == null) {
            if (admin != null) MessageUtil.send(admin, "<red>Tournament not found: <yellow>" + name);
            return false;
        }
        t.setState(TournamentState.CANCELLED);
        tournaments.remove(name.toLowerCase());
        saveAll();
        if (admin != null) MessageUtil.send(admin, "<green>Tournament <yellow>" + name + "</yellow> deleted.");
        return true;
    }

    // ── Bracket progression ───────────────────────────────────────

    public void onMatchEnd(MatchResult result) {
        if (result.getWinnerId() == null) return; // draw/timeout — skip

        for (Tournament t : tournaments.values()) {
            if (t.getState() != TournamentState.IN_PROGRESS) continue;
            TournamentBracket bracket = t.getBracket();

            Optional<TournamentMatch> matchOpt = bracket.getActiveMatchForArena(result.getArenaName());
            if (matchOpt.isEmpty()) continue;

            TournamentMatch tm = matchOpt.get();
            if (!tm.involves(result.getWinnerId()) && (result.getLoserId() == null || !tm.involves(result.getLoserId()))) continue;

            bracket.recordWinner(tm, result.getWinnerId());
            String winnerName = Bukkit.getOfflinePlayer(result.getWinnerId()).getName();
            Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                    "<gold>[Tournament] <white>" + t.getName() +
                    " R" + bracket.getCurrentRoundNumber() +
                    ": <yellow>" + winnerName + " <gray>advances!"));

            if (bracket.isRoundComplete()) {
                if (bracket.isFinished()) {
                    finishTournament(t);
                } else {
                    bracket.advanceRound();
                    Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                            "<gold>[Tournament] <white>" + t.getName() +
                            " <yellow>Round " + bracket.getCurrentRoundNumber() + " begins!"));
                    launchPendingMatches(t);
                }
            } else {
                launchPendingMatches(t);
            }
            return;
        }
    }

    private void launchPendingMatches(Tournament t) {
        Arena arena = plugin.getArenaManager().get(t.getArenaName()).orElse(null);
        if (arena == null) return;

        for (TournamentMatch tm : t.getBracket().getPendingMatches()) {
            Player p1 = Bukkit.getPlayer(tm.getPlayer1());
            Player p2 = Bukkit.getPlayer(tm.getPlayer2());

            if (p1 == null || p2 == null) {
                // Offline player — award win to present player
                UUID winner = p1 != null ? tm.getPlayer1() : tm.getPlayer2();
                t.getBracket().recordWinner(tm, winner);
                String wName = Bukkit.getOfflinePlayer(winner).getName();
                Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                        "<gold>[Tournament] <yellow>" + wName + " <gray>wins by walkover (opponent offline)."));
                continue;
            }

            plugin.getMatchManager().joinArena(p1, arena);
            plugin.getMatchManager().joinArena(p2, arena);

            MessageUtil.send(p1, "<gold>[Tournament] <yellow>Your match vs <white>" + p2.getName() + " <yellow>begins!");
            MessageUtil.send(p2, "<gold>[Tournament] <yellow>Your match vs <white>" + p1.getName() + " <yellow>begins!");
        }
    }

    private void finishTournament(Tournament t) {
        UUID winner = t.getBracket().getFinalWinner();
        t.setState(TournamentState.FINISHED);

        String winnerName = winner != null ? Bukkit.getOfflinePlayer(winner).getName() : "Unknown";
        Bukkit.broadcast(com.omniarena.util.ColorUtil.parse(
                "<gold>★ [Tournament] <white>" + t.getName() +
                " <yellow>finished! Champion: <green>" + winnerName + " <yellow>★"));

        // Pay prizes via Vault (ponytail: deposit only if online; offline prize delivery not in scope)
        if (winner != null && t.getPrizeFirst() > 0) {
            Player winnerOnline = Bukkit.getPlayer(winner);
            if (winnerOnline != null) {
                plugin.getIntegrationManager().getVault().deposit(winnerOnline, t.getPrizeFirst());
                MessageUtil.send(winnerOnline,
                        "<green>You won <yellow>" + plugin.getIntegrationManager().getVault()
                                .format(t.getPrizeFirst()) + " <green>as tournament champion!");
            }
        }

        tournaments.remove(t.getName().toLowerCase());
        saveAll();
    }

    // ── Status ────────────────────────────────────────────────────

    public String getStatusText(Tournament t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<gold>=== Tournament: ").append(t.getName()).append(" ===\n");
        sb.append("<yellow>State: <white>").append(t.getState()).append("\n");
        sb.append("<yellow>Arena: <white>").append(t.getArenaName()).append("\n");
        sb.append("<yellow>Players: <white>").append(t.getParticipants().size())
                .append("/").append(t.getMaxPlayers()).append("\n");

        if (t.getState() == TournamentState.IN_PROGRESS && t.getBracket() != null) {
            sb.append("<yellow>Round: <white>").append(t.getBracket().getCurrentRoundNumber()).append("\n");
            sb.append("<yellow>Pending matches: <white>")
                    .append(t.getBracket().getPendingMatches().size()).append("\n");
        }
        if (t.getPrizeFirst() > 0) {
            sb.append("<yellow>Prizes: <green>1st ").append(t.getPrizeFirst())
                    .append(" <yellow>2nd ").append(t.getPrizeSecond())
                    .append(" <yellow>3rd ").append(t.getPrizeThird()).append("\n");
        }
        return sb.toString().trim();
    }

    // ── Persistence ───────────────────────────────────────────────

    public void loadAll() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        var section = cfg.getConfigurationSection("tournaments");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String arenaName  = section.getString(key + ".arena");
            int maxPlayers    = section.getInt(key + ".maxPlayers");
            UUID creatorUuid  = UUID.fromString(section.getString(key + ".creator"));
            String stateStr   = section.getString(key + ".state", "REGISTRATION");

            Tournament t = new Tournament(key, arenaName, maxPlayers, creatorUuid);
            try { t.setState(TournamentState.valueOf(stateStr)); }
            catch (IllegalArgumentException ignored) {}

            // Only restore REGISTRATION-state tournaments
            if (t.getState() != TournamentState.REGISTRATION) continue;

            for (String uuidStr : section.getStringList(key + ".participants")) {
                try { t.getParticipants().add(UUID.fromString(uuidStr)); }
                catch (IllegalArgumentException ignored) {}
            }

            t.setPrizeFirst(section.getDouble(key + ".prizes.first"));
            t.setPrizeSecond(section.getDouble(key + ".prizes.second"));
            t.setPrizeThird(section.getDouble(key + ".prizes.third"));

            tournaments.put(key.toLowerCase(), t);
        }
        plugin.getLogger().info("Loaded " + tournaments.size() + " tournament(s).");
    }

    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Tournament t : tournaments.values()) {
            if (t.getState() != TournamentState.REGISTRATION) continue; // only persist open ones
            String key = "tournaments." + t.getName();
            cfg.set(key + ".arena", t.getArenaName());
            cfg.set(key + ".maxPlayers", t.getMaxPlayers());
            cfg.set(key + ".creator", t.getCreatorUuid().toString());
            cfg.set(key + ".state", t.getState().name());
            cfg.set(key + ".participants", t.getParticipants().stream()
                    .map(UUID::toString).toList());
            cfg.set(key + ".prizes.first",  t.getPrizeFirst());
            cfg.set(key + ".prizes.second", t.getPrizeSecond());
            cfg.set(key + ".prizes.third",  t.getPrizeThird());
        }
        try { cfg.save(dataFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Failed to save tournaments.yml", e); }
    }

    // ── Lookup ────────────────────────────────────────────────────

    public Tournament getTournament(String name) {
        return tournaments.get(name.toLowerCase());
    }

    public Collection<Tournament> getAll() {
        return Collections.unmodifiableCollection(tournaments.values());
    }

    public Optional<Tournament> getTournamentForPlayer(UUID uuid) {
        return tournaments.values().stream()
                .filter(t -> t.hasPlayer(uuid))
                .findFirst();
    }
}
