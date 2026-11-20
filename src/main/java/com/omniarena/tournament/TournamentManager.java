package com.omniarena.tournament;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.config.ConfigManager;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class TournamentManager {

    private final OmniArena plugin;
    private final Map<String, Tournament> tournaments = new ConcurrentHashMap<>();
    private final File dataFile;
    /** When a tournament match first found a player offline (for the walkover grace period). */
    private final Map<TournamentMatch, Long> waitingSince = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask tickTask;

    public TournamentManager(OmniArena plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tournaments.yml");
    }

    public void start() {
        loadAll();
        if (tickTask != null) tickTask.cancel();
        // Periodic safety net: launches matches that had to wait (arena busy, player busy/offline)
        // and recovers matches that were cancelled without producing a result.
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.isStopped() || tournaments.isEmpty()) return;
            for (Tournament t : new ArrayList<>(tournaments.values())) {
                if (t.getState() != TournamentState.IN_PROGRESS || t.getBracket() == null) continue;
                try { launchPendingMatches(t); }
                catch (Exception e) { plugin.getLogger().warning("Tournament tick failed for '" + t.getName() + "' (" + e + "). The tournament keeps running; if this repeats, delete it with /tournament delete " + t.getName() + " and recreate it."); }
            }
        }, 100L, 100L);
    }

    // ── Create / Join / Leave / Delete ────────────────────────────

    public boolean createTournament(Player creator, String name, String arenaName, int maxPlayers) {
        if (tournaments.containsKey(name.toLowerCase())) {
            MessageUtil.send(creator, "<red>A tournament named '<yellow>" + name + "</yellow>' already exists. <gray>Pick another name, or join it with <white>/tournament join " + name + "</white>.");
            return false;
        }
        Arena arena = plugin.getArenaManager().get(arenaName).orElse(null);
        if (arena == null) {
            MessageUtil.send(creator, com.omniarena.config.ConfigManager.getInstance().getMessage("arena-not-found").replace("{arena}", arenaName));
            return false;
        }
        if (maxPlayers < 2) {
            MessageUtil.send(creator, "<red>A tournament needs a max player count of at least 2 (you gave " + maxPlayers + "). <gray>Usage: <white>/tournament create <name> <arena> <maxPlayers></white>, e.g. <white>/tournament create " + name + " " + arenaName + " 8</white>.");
            return false;
        }

        Tournament t = new Tournament(name, arena.getName(), maxPlayers, creator.getUniqueId());
        tournaments.put(name.toLowerCase(), t);
        saveAll();

        com.omniarena.util.MessageUtil.broadcast(com.omniarena.util.ColorUtil.parse(
                "<gold>[Tournament] <yellow>" + creator.getName() +
                " created tournament <white>" + name +
                " <gray>(" + arena.getName() + ", max " + maxPlayers + "). Use /oa tournament join " + name));
        return true;
    }

    public boolean joinTournament(Player player, String name) {
        Tournament t = getTournament(name);
        if (t == null) {
            MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance().getMessage("tournament-not-found").replace("{name}", name));
            return false;
        }
        if (t.getState() != TournamentState.REGISTRATION) {
            MessageUtil.send(player, "<red>Tournament <yellow>" + t.getName() + "</yellow> is not accepting registrations (state: " + t.getState().name() + "). <gray>Only tournaments in REGISTRATION can be joined; see <white>/tournament list</white>.");
            return false;
        }
        if (!t.addParticipant(player.getUniqueId())) {
            if (t.hasPlayer(player.getUniqueId()))
                MessageUtil.send(player, "<red>You are already registered in <yellow>" + t.getName() + "</yellow>. <gray>Leave with <white>/tournament leave " + t.getName() + "</white> if you want out.");
            else
                MessageUtil.send(player, "<red>Tournament <yellow>" + t.getName() + "</yellow> is full (" + t.getMaxPlayers() + " players). <gray>Join another one (<white>/tournament list</white>) or ask the creator to create a bigger bracket.");
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
            MessageUtil.send(player, com.omniarena.config.ConfigManager.getInstance().getMessage("tournament-not-found").replace("{name}", name));
            return false;
        }
        if (!t.removeParticipant(player.getUniqueId())) {
            MessageUtil.send(player, "<red>You are not registered in <yellow>" + t.getName() + "</yellow>, or it already started (you can't leave mid-bracket). <gray>Check <white>/tournament status " + t.getName() + "</white>.");
            return false;
        }
        MessageUtil.send(player, "<yellow>Left tournament <white>" + t.getName() + "</white>.");
        saveAll();
        return true;
    }

    public void startTournament(Tournament t, Player requester) {
        if (t.getState() != TournamentState.REGISTRATION) {
            if (requester != null) MessageUtil.send(requester, "<red>Tournament <yellow>" + t.getName() + "</yellow> already started (state: " + t.getState().name() + "). <gray>See <white>/tournament status " + t.getName() + "</white>.");
            return;
        }
        // Drop players who went offline during registration so they don't distort the bracket
        t.getParticipants().removeIf(u -> Bukkit.getPlayer(u) == null);
        if (t.getParticipants().size() < 2) {
            if (requester != null) MessageUtil.send(requester, "<red>Can't start <yellow>" + t.getName() + "</yellow>: fewer than 2 registered players are online (offline players are dropped). <gray>Wait for more players to <white>/tournament join " + t.getName() + "</white>, then run <white>/tournament start " + t.getName() + "</white> again.");
            saveAll();
            return;
        }

        t.setState(TournamentState.IN_PROGRESS);
        t.setBracket(new TournamentBracket(t.getParticipants(), t.getArenaName()));

        com.omniarena.util.MessageUtil.broadcast(com.omniarena.util.ColorUtil.parse(
                "<gold>[Tournament] <white>" + t.getName() + " <yellow>has started! " +
                t.getParticipants().size() + " players competing."));

        launchPendingMatches(t);
        saveAll();
    }

    public boolean deleteTournament(String name, Player admin) {
        Tournament t = getTournament(name);
        if (t == null) {
            if (admin != null) MessageUtil.send(admin, com.omniarena.config.ConfigManager.getInstance().getMessage("tournament-not-found").replace("{name}", name));
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
        UUID resWinner = result.getWinnerId();
        List<UUID> parts = result.getParticipants() == null ? List.of() : result.getParticipants();

        for (Tournament t : tournaments.values()) {
            if (t.getState() != TournamentState.IN_PROGRESS || t.getBracket() == null) continue;
            if (result.getArenaName() != null && !result.getArenaName().equalsIgnoreCase(t.getArenaName())) continue;
            TournamentBracket bracket = t.getBracket();

            Optional<TournamentMatch> matchOpt = bracket.getCurrentRound().stream()
                    .filter(m -> !m.isCompleted() && !m.isBye() && m.isStarted()
                            && ((resWinner != null && m.involves(resWinner))
                            || (result.getLoserId() != null && m.involves(result.getLoserId()))
                            || (parts.contains(m.getPlayer1()) && parts.contains(m.getPlayer2()))))
                    .findFirst();
            if (matchOpt.isEmpty()) continue;

            TournamentMatch tm = matchOpt.get();

            // Draw/timeout: decide by rule so the bracket never stalls
            // (higher health advances; equal/unknown -> random).
            UUID winner = resWinner != null && tm.involves(resWinner) ? resWinner : decideDraw(tm);
            bracket.recordWinner(tm, winner);
            String winnerName = Bukkit.getOfflinePlayer(winner).getName();
            com.omniarena.util.MessageUtil.broadcast(com.omniarena.util.ColorUtil.parse(
                    "<gold>[Tournament] <white>" + t.getName() +
                    " R" + bracket.getCurrentRoundNumber() +
                    ": <yellow>" + winnerName + " <gray>advances!"));

            if (bracket.isRoundComplete()) {
                if (bracket.isFinished()) {
                    finishTournament(t);
                } else {
                    bracket.advanceRound();
                    com.omniarena.util.MessageUtil.broadcast(com.omniarena.util.ColorUtil.parse(
                            "<gold>[Tournament] <white>" + t.getName() +
                            " <yellow>Round " + bracket.getCurrentRoundNumber() + " begins!"));
                    scheduleLaunch(t);
                }
            } else {
                scheduleLaunch(t);
            }
            saveAll();
            return;
        }
    }

    /** Launch next match after the arena has finished resetting. */
    private void scheduleLaunch(Tournament t) {
        long delay = (com.omniarena.config.ConfigManager.getInstance().getConfig().getInt("match.end-delay", 5) + 2) * 20L;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.isStopped() && tournaments.get(t.getName().toLowerCase()) == t
                    && t.getState() == TournamentState.IN_PROGRESS) { launchPendingMatches(t); saveAll(); }
        }, delay);
    }

    private boolean inArena(Player p, String arenaName) {
        return p != null && !plugin.getMatchManager().isSpectating(p)
                && plugin.getMatchManager().getArenaName(p).map(n -> n.equalsIgnoreCase(arenaName)).orElse(false);
    }

    private UUID decideDraw(TournamentMatch tm) {
        Player p1 = Bukkit.getPlayer(tm.getPlayer1());
        Player p2 = Bukkit.getPlayer(tm.getPlayer2());
        // A player who left the arena (or went offline) loses against one who stayed
        boolean in1 = inArena(p1, tm.getArenaName());
        boolean in2 = inArena(p2, tm.getArenaName());
        if (in1 && !in2) return tm.getPlayer1();
        if (in2 && !in1) return tm.getPlayer2();
        if (p1 != null && p2 == null) return tm.getPlayer1();
        if (p2 != null && p1 == null) return tm.getPlayer2();
        if (p1 != null) {
            if (p1.getHealth() > p2.getHealth()) return tm.getPlayer1();
            if (p2.getHealth() > p1.getHealth()) return tm.getPlayer2();
        }
        return ThreadLocalRandom.current().nextBoolean() ? tm.getPlayer1() : tm.getPlayer2();
    }

    /**
     * Checks a launched match. Returns true while it is legitimately still in progress
     * (caller must wait), false if it was resolved or reset and may be re-launched.
     */
    private boolean checkLive(Tournament t, Arena arena, TournamentMatch tm) {
        var mm = plugin.getMatchManager();
        Match m = mm.getMatch(arena).orElse(null);
        if (m != null && (m.getState() == com.omniarena.match.MatchState.ENDING
                || m.getState() == com.omniarena.match.MatchState.RESETTING)) return true;
        Player p1 = Bukkit.getPlayer(tm.getPlayer1());
        Player p2 = Bukkit.getPlayer(tm.getPlayer2());
        boolean in1 = inArena(p1, arena.getName());
        boolean in2 = inArena(p2, arena.getName());
        if (m != null && in1 && in2) return true;
        if (m != null && (in1 || in2)) {
            UUID winner = in1 ? tm.getPlayer1() : tm.getPlayer2();
            if (m.getState() == com.omniarena.match.MatchState.RUNNING) {
                mm.forceWin(m, winner); // opponent left: onMatchEnd records the result
                return true;
            }
            // opponent left before the fight started: walkover
            t.getBracket().recordWinner(tm, winner);
            com.omniarena.util.MessageUtil.broadcast(com.omniarena.util.ColorUtil.parse(
                    "<gold>[Tournament] <yellow>" + Bukkit.getOfflinePlayer(winner).getName()
                    + " <gray>wins by walkover (opponent left)."));
            mm.leaveArena(in1 ? p1 : p2);
            return false;
        }
        tm.setStarted(false); // match vanished without a result: launch it again
        return false;
    }

    private void launchPendingMatches(Tournament t) {
        if (t.getState() != TournamentState.IN_PROGRESS || t.getBracket() == null) return;
        Arena arena = plugin.getArenaManager().get(t.getArenaName()).orElse(null);
        if (arena == null) return;
        var mm = plugin.getMatchManager();
        boolean changed = false;

        // One arena = one match at a time: if a launched match is still running, wait.
        for (TournamentMatch pm : t.getBracket().getPendingMatches()) {
            if (pm.isStarted() && checkLive(t, arena, pm)) return;
        }

        boolean busy = mm.getMatch(arena).isPresent() || !arena.isJoinable();
        long graceMs = ConfigManager.getInstance().getConfig().getInt("tournament.walkover-grace-seconds", 60) * 1000L;
        boolean launched = false;

        for (TournamentMatch tm : t.getBracket().getPendingMatches()) {
            if (tm.isCompleted()) { changed = true; continue; }
            Player p1 = Bukkit.getPlayer(tm.getPlayer1());
            Player p2 = Bukkit.getPlayer(tm.getPlayer2());

            if (p1 == null || p2 == null) {
                // Offline player: wait a grace period (reconnect / server restart), then walkover
                long now = System.currentTimeMillis();
                Long since = waitingSince.putIfAbsent(tm, now);
                if (since == null) {
                    Player present = p1 != null ? p1 : p2;
                    if (present != null) MessageUtil.send(present, "<gold>[Tournament] <yellow>Your opponent is offline; "
                            + "you win by walkover in " + (graceMs / 1000) + "s unless they return.");
                    continue;
                }
                if (now - since < graceMs) continue;
                waitingSince.remove(tm);
                UUID winner = p1 != null ? tm.getPlayer1() : p2 != null ? tm.getPlayer2() : tm.getPlayer1();
                t.getBracket().recordWinner(tm, winner);
                String wName = Bukkit.getOfflinePlayer(winner).getName();
                com.omniarena.util.MessageUtil.broadcast(com.omniarena.util.ColorUtil.parse(
                        "<gold>[Tournament] <yellow>" + wName + " <gray>wins by walkover (opponent offline)."));
                changed = true;
                continue;
            }
            waitingSince.remove(tm);

            if (launched || busy) continue;
            if (mm.isInArena(p1) || mm.isInArena(p2)) continue; // busy elsewhere: retry on next tick

            var qm = com.omniarena.queue.QueueManager.getInstance();
            if (qm != null) {
                qm.forceRemove(p1.getUniqueId(), "a member was called to a tournament match");
                qm.forceRemove(p2.getUniqueId(), "a member was called to a tournament match");
            }
            boolean ok1 = mm.joinArena(p1, arena);
            boolean ok2 = ok1 && mm.joinArena(p2, arena);
            if (!ok1 || !ok2) {
                if (ok1) mm.leaveArena(p1);
                continue;
            }
            tm.setStarted(true);
            launched = true;

            MessageUtil.send(p1, "<gold>[Tournament] <yellow>Your match vs <white>" + p2.getName() + " <yellow>begins!");
            MessageUtil.send(p2, "<gold>[Tournament] <yellow>Your match vs <white>" + p1.getName() + " <yellow>begins!");
        }

        if (t.getBracket().isRoundComplete()) {
            changed = true;
            if (t.getBracket().isFinished()) {
                finishTournament(t);
            } else {
                t.getBracket().advanceRound();
                com.omniarena.util.MessageUtil.broadcast(com.omniarena.util.ColorUtil.parse(
                        "<gold>[Tournament] <white>" + t.getName() +
                        " <yellow>Round " + t.getBracket().getCurrentRoundNumber() + " begins!"));
                launchPendingMatches(t);
            }
        }
        if (changed) saveAll();
    }

    private void finishTournament(Tournament t) {
        if (t.getState() == TournamentState.FINISHED) return;
        UUID winner = t.getBracket().getFinalWinner();
        t.setState(TournamentState.FINISHED);

        String winnerName = winner != null ? Bukkit.getOfflinePlayer(winner).getName() : "Unknown";
        com.omniarena.util.MessageUtil.broadcast(com.omniarena.util.ColorUtil.parse(
                "<gold>★ [Tournament] <white>" + t.getName() +
                " <yellow>finished! Champion: <green>" + winnerName + " <yellow>★"));

        pay(t, winner, t.getPrizeFirst(), "1st");
        pay(t, t.getBracket().getFinalLoser(), t.getPrizeSecond(), "2nd");
        List<UUID> thirds = t.getBracket().getSemifinalLosers();
        if (!thirds.isEmpty()) {
            double share = Math.floor(t.getPrizeThird() / thirds.size() * 100) / 100; // no 3rd-place match: split among semifinal losers
            for (UUID u : thirds) pay(t, u, share, "3rd");
        }

        tournaments.remove(t.getName().toLowerCase());
        saveAll();
    }

    private void pay(Tournament t, UUID uuid, double amount, String place) {
        if (uuid == null || amount <= 0) return;
        var im = plugin.getIntegrationManager();
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String paid = im != null ? im.depositOffline(op, amount) : null;
        if (paid == null) {
            plugin.getLogger().warning("Tournament '" + t.getName() + "': no working economy provider, so the " + place + " prize was not paid. Fix: install Vault + an economy plugin, or set the prize to 0 with /tournament setprize " + t.getName() + " <1st|2nd|3rd> 0.");
            return;
        }
        Player online = op.getPlayer();
        if (online != null) MessageUtil.send(online, "<green>You received <yellow>" + paid
                + " <green>for " + place + " place in tournament " + t.getName() + "!");
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
            try {
                String arenaName  = section.getString(key + ".arena");
                int maxPlayers    = section.getInt(key + ".maxPlayers");
                UUID creatorUuid  = UUID.fromString(section.getString(key + ".creator"));
                Tournament t = new Tournament(section.getString(key + ".name", key), arenaName, maxPlayers, creatorUuid);
                try { t.setState(TournamentState.valueOf(section.getString(key + ".state", "REGISTRATION"))); }
                catch (IllegalArgumentException ignored) {}
                if (t.getState() == TournamentState.FINISHED || t.getState() == TournamentState.CANCELLED) continue;

                for (String uuidStr : section.getStringList(key + ".participants")) {
                    try { t.getParticipants().add(UUID.fromString(uuidStr)); }
                    catch (IllegalArgumentException ignored) {}
                }
                t.setPrizeFirst(section.getDouble(key + ".prizes.first"));
                t.setPrizeSecond(section.getDouble(key + ".prizes.second"));
                t.setPrizeThird(section.getDouble(key + ".prizes.third"));

                if (t.getState() == TournamentState.IN_PROGRESS) {
                    TournamentBracket br = loadBracket(section.getConfigurationSection(key + ".bracket"), arenaName);
                    if (br == null) { // unusable bracket -> back to registration
                        t.setState(TournamentState.REGISTRATION);
                    } else {
                        t.setBracket(br);
                    }
                }
                tournaments.put(t.getName().toLowerCase(), t);
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping invalid tournament '" + key + "' in tournaments.yml (" + e + "). Fix: correct or remove the '" + key + "' section in plugins/OmniArena/tournaments.yml (arena name and participants must be valid), then /oa reload.");
            }
        }
        plugin.getLogger().info("Loaded " + tournaments.size() + " tournament(s).");

        // Resume in-progress brackets once the server finished starting
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Tournament t : tournaments.values()) {
                if (t.getState() == TournamentState.IN_PROGRESS) {
                    try { resume(t); } catch (Exception e) {
                        plugin.getLogger().warning("Failed to resume tournament '" + t.getName() + "' (" + e + "). Fix: check that its arena '" + t.getArenaName() + "' still exists and is enabled, or delete it with /tournament delete " + t.getName() + ".");
                    }
                }
            }
        });
    }

    private void resume(Tournament t) {
        TournamentBracket br = t.getBracket();
        if (br.isRoundComplete()) {
            if (br.isFinished()) { finishTournament(t); return; }
            br.advanceRound();
        }
        launchPendingMatches(t);
    }

    private TournamentBracket loadBracket(org.bukkit.configuration.ConfigurationSection sec, String arenaName) {
        if (sec == null) return null;
        List<List<TournamentMatch>> rounds = new ArrayList<>();
        int roundNum = 1;
        for (Object ro : sec.getList("rounds", List.of())) {
            List<TournamentMatch> round = new ArrayList<>();
            if (!(ro instanceof List<?> list)) return null;
            for (Object mo : list) {
                if (!(mo instanceof Map<?, ?> m)) return null;
                UUID p1 = UUID.fromString(String.valueOf(m.get("p1")));
                Object p2o = m.get("p2");
                UUID p2 = p2o == null ? null : UUID.fromString(String.valueOf(p2o));
                TournamentMatch tm = new TournamentMatch(p1, p2, arenaName, roundNum);
                Object w = m.get("winner");
                if (w != null) tm.restoreWinner(UUID.fromString(String.valueOf(w)));
                round.add(tm);
            }
            rounds.add(round);
            roundNum++;
        }
        if (rounds.isEmpty()) return null;
        return new TournamentBracket(arenaName, rounds, sec.getInt("currentRound", 0));
    }

    public void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Tournament t : tournaments.values()) {
            if (t.getState() == TournamentState.FINISHED || t.getState() == TournamentState.CANCELLED) continue;
            String key = "tournaments." + t.getName().replace(".", "_");
            cfg.set(key + ".name", t.getName());
            cfg.set(key + ".arena", t.getArenaName());
            cfg.set(key + ".maxPlayers", t.getMaxPlayers());
            cfg.set(key + ".creator", t.getCreatorUuid().toString());
            cfg.set(key + ".state", t.getState().name());
            cfg.set(key + ".participants", t.getParticipants().stream()
                    .map(UUID::toString).toList());
            cfg.set(key + ".prizes.first",  t.getPrizeFirst());
            cfg.set(key + ".prizes.second", t.getPrizeSecond());
            cfg.set(key + ".prizes.third",  t.getPrizeThird());
            if (t.getState() == TournamentState.IN_PROGRESS && t.getBracket() != null) {
                List<List<Map<String, Object>>> rounds = new ArrayList<>();
                for (List<TournamentMatch> round : t.getBracket().getAllRounds()) {
                    List<Map<String, Object>> rl = new ArrayList<>();
                    for (TournamentMatch m : round) {
                        Map<String, Object> mm = new LinkedHashMap<>();
                        mm.put("p1", m.getPlayer1().toString());
                        if (m.getPlayer2() != null) mm.put("p2", m.getPlayer2().toString());
                        if (m.getWinner() != null) mm.put("winner", m.getWinner().toString());
                        rl.add(mm);
                    }
                    rounds.add(rl);
                }
                cfg.set(key + ".bracket.rounds", rounds);
                cfg.set(key + ".bracket.currentRound", t.getBracket().getCurrentRoundIndex());
            }
        }
        try { cfg.save(dataFile); }
        catch (IOException e) { plugin.getLogger().log(Level.SEVERE, "Failed to save tournaments.yml", e); }
    }

    /** Persist state and drop in-memory tournaments (used by /oa stop and onDisable). */
    public void stop() {
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        saveAll();
        waitingSince.clear();
        tournaments.clear();
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
