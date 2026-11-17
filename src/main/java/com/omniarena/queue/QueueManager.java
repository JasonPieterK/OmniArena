package com.omniarena.queue;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Queue system for automatic matchmaking
 * Supports ELO-based matchmaking, kit-specific queues, and party queues
 */
public class QueueManager {

    private static QueueManager instance;

    private final OmniArena plugin;
    private final Map<String, Queue> queues = new ConcurrentHashMap<>(); // kitName -> Queue
    private final Map<UUID, QueuedPlayer> queuedPlayers = new ConcurrentHashMap<>();
    private BukkitTask matchmakingTask;

    public QueueManager(OmniArena plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static QueueManager getInstance() { return instance; }

    public void start() {
        stop();
        matchmakingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processMatchmaking, 20L, 20L);
    }

    public void stop() {
        if (matchmakingTask != null) {
            matchmakingTask.cancel();
            matchmakingTask = null;
        }
    }

    /**
     * Add a player to a queue. When the player leads a party of 2+, the whole party is queued
     * as one unit; party members who are not the leader cannot queue on their own.
     */
    public boolean joinQueue(Player player, String kitName) {
        if (isQueued(player)) {
            MessageUtil.send(player, "<red>You are already in a queue.");
            return false;
        }
        if (plugin.getMatchManager().isInArena(player)) {
            MessageUtil.send(player, "<red>You cannot queue while in an arena.");
            return false;
        }

        List<Player> members = new ArrayList<>();
        members.add(player);
        var pm = com.omniarena.party.PartyManager.getInstance();
        var party = pm != null ? pm.getParty(player) : null;
        if (party != null && party.getSize() > 1) {
            if (!party.isLeader(player.getUniqueId())) {
                MessageUtil.send(player, "<red>Only the party leader can queue your party. Ask <yellow>"
                        + party.getLeaderName() + "</yellow> to queue, or <yellow>/party leave</yellow> to queue alone.");
                return false;
            }
            members.clear();
            members.add(player); // leader first
            for (UUID id : party.getMembers()) {
                if (id.equals(player.getUniqueId())) continue;
                Player m = Bukkit.getPlayer(id);
                if (m == null || !m.isOnline()) {
                    MessageUtil.send(player, "<red>Everyone in your party must be online to queue.");
                    return false;
                }
                members.add(m);
            }
            for (Player m : members) {
                if (plugin.getMatchManager().isInArena(m)) {
                    MessageUtil.send(player, "<red>" + m.getName() + " is in an arena. Your whole party must be free to queue.");
                    return false;
                }
                if (isQueued(m)) {
                    MessageUtil.send(player, "<red>" + m.getName() + " is already in a queue.");
                    return false;
                }
            }
            if (!canHost(members.size())) {
                MessageUtil.send(player, "<red>No enabled arena supports teams of <yellow>" + members.size()
                        + "</yellow> (needs team mode with 2 teams and enough player slots).");
                return false;
            }
        }

        Queue queue = queues.computeIfAbsent(kitName.toLowerCase(), k -> new Queue(k));
        QueuedPlayer qp = members.size() > 1
                ? new QueuedPlayer(members.stream().map(Player::getUniqueId).toList(),
                        members.stream().map(Player::getName).toList(), kitName)
                : new QueuedPlayer(player.getUniqueId(), player.getName(), kitName);

        queue.addPlayer(qp);
        for (UUID id : qp.getMembers()) queuedPlayers.put(id, qp);

        MessageUtil.send(player, "<green>Joined queue for <yellow>" + kitName + "</yellow>"
                + (members.size() > 1 ? " <gray>with your party of " + members.size() : "")
                + "<green>. <gray>(Position: " + queue.getPosition(qp) + ")");
        for (Player m : members) {
            if (!m.equals(player)) {
                MessageUtil.send(m, "<green>Your party leader <yellow>" + player.getName()
                        + "</yellow> queued the party for <yellow>" + kitName + "</yellow>.");
            }
        }

        return true; // paired on the next matchmaking tick (1s)
    }

    /**
     * Remove a player (and, for a party unit, the whole party) from queue.
     */
    public boolean leaveQueue(Player player) {
        QueuedPlayer qp = queuedPlayers.get(player.getUniqueId());
        if (qp == null) {
            MessageUtil.send(player, "<red>You are not in a queue.");
            return false;
        }
        removeEntry(qp, qp.isParty() ? player.getName() + " left the queue" : null);
        MessageUtil.send(player, "<green>Left the queue.");
        return true;
    }

    /** Takes a queue entry out entirely, telling its other online members why when it is a party. */
    private void removeEntry(QueuedPlayer qp, String reason) {
        for (UUID id : qp.getMembers()) queuedPlayers.remove(id, qp);
        String key = qp.getKitName().toLowerCase();
        Queue queue = queues.get(key);
        if (queue != null) {
            queue.removePlayer(qp);
            if (queue.isEmpty()) queues.remove(key);
        }
        if (qp.isParty() && reason != null) {
            for (UUID id : qp.getMembers()) {
                Player m = Bukkit.getPlayer(id);
                if (m != null && m.isOnline()) {
                    MessageUtil.send(m, "<red>Your party was removed from the queue: <gray>" + reason + ".");
                }
            }
        }
    }

    /**
     * Called by PartyManager when a party's makeup changes (leave, kick, disband, join):
     * a queued party unit is dropped from the queue and its members are told why.
     */
    public void removePartyFromQueue(Collection<UUID> members, String reason) {
        for (UUID id : members) {
            QueuedPlayer qp = queuedPlayers.get(id);
            if (qp != null) {
                removeEntry(qp, reason);
                return;
            }
        }
    }

    /** True if some enabled arena can host two sides of {@code perSide} players. */
    private boolean canHost(int perSide) {
        for (Arena a : plugin.getArenaManager().getEnabled()) {
            if (arenaFits(a, perSide)) return true;
        }
        return false;
    }

    private static boolean arenaFits(Arena a, int perSide) {
        if (a.getMaxPlayers() > 0 && a.getMaxPlayers() < perSide * 2) return false;
        if (perSide > 1) {
            var tc = a.getTeamConfig();
            return tc.isEnabled() && tc.getTeamCount() == 2;
        }
        return true;
    }

    /**
     * Check if player is queued
     */
    public boolean isQueued(Player player) {
        return queuedPlayers.containsKey(player.getUniqueId());
    }

    /**
     * Get player's queue info
     */
    public QueueInfo getQueueInfo(Player player) {
        QueuedPlayer qp = queuedPlayers.get(player.getUniqueId());
        if (qp == null) return null;
        
        Queue queue = queues.get(qp.getKitName().toLowerCase());
        if (queue == null) return null;
        
        return new QueueInfo(qp.getKitName(), queue.getPosition(qp), queue.getSize());
    }

    /** The queue entry (solo or party unit) containing this player, or null. */
    public QueuedPlayer getEntry(Player player) { return queuedPlayers.get(player.getUniqueId()); }

    /** Average ELO of an entry's members. */
    public int averageElo(QueuedPlayer qp) {
        long sum = 0;
        for (UUID id : qp.getMembers()) sum += plugin.getEloManager().peekElo(id);
        return (int) Math.round((double) sum / qp.size());
    }

    /** Current accepted rating gap (+/-) for an entry given its wait time. */
    public int currentRange(QueuedPlayer qp) {
        long steps = (System.currentTimeMillis() - qp.getQueueTime()) / (RANGE_STEP_SECONDS * 1000L);
        return (int) Math.min(MAX_RANGE, BASE_RANGE + steps * RANGE_STEP);
    }

    /** Names of queues that currently exist (for tab completion). */
    public java.util.Set<String> getQueueNames() { return new java.util.TreeSet<>(queues.keySet()); }

    /** Base allowed rating gap, widened over time so nobody waits forever. */
    private static final int BASE_RANGE = 200;
    private static final int RANGE_STEP = 50;          // added every RANGE_STEP_SECONDS of waiting
    private static final int RANGE_STEP_SECONDS = 10;
    private static final int MAX_RANGE = 1000;

    private static int allowedRange(QueuedPlayer a, QueuedPlayer b, long now) {
        long waitedMs = Math.max(now - a.getQueueTime(), now - b.getQueueTime());
        long steps = waitedMs / (RANGE_STEP_SECONDS * 1000L);
        return (int) Math.min(MAX_RANGE, BASE_RANGE + steps * RANGE_STEP);
    }

    /** Removes a player's queue entry without messaging (quit/disconnect); a party unit goes with them. */
    public void removeOnQuit(UUID uuid) {
        QueuedPlayer qp = queuedPlayers.get(uuid);
        if (qp == null) return;
        String who = Bukkit.getOfflinePlayer(uuid).getName();
        removeEntry(qp, (who != null ? who : "A party member") + " left or entered an arena");
    }

    /**
     * Process matchmaking: pair queue entries of equal size with a similar (average) ELO. A party
     * with no equal-size party waiting may instead be matched against enough solo players to fill
     * the other team. The accepted rating gap grows with time spent waiting.
     */
    private void processMatchmaking() {
        if (queues.isEmpty()) return;
        if (plugin.getMatchManager().isLockdown()) return;
        long now = System.currentTimeMillis();
        var mm = plugin.getMatchManager();

        for (Queue queue : new ArrayList<>(queues.values())) {
            // Drop entries whose members disconnected or entered an arena by other means
            for (QueuedPlayer qp : new ArrayList<>(queue.getPlayers())) {
                for (UUID id : qp.getMembers()) {
                    Player online = Bukkit.getPlayer(id);
                    if (online == null || !online.isOnline() || mm.isInArena(online)) {
                        removeOnQuit(id);
                        break;
                    }
                }
            }
            if (queue.getSize() < 2) continue;

            // Average rating per entry, looked up once (not per comparison)
            List<QueuedPlayer> entries = new ArrayList<>(queue.getPlayers());
            Map<QueuedPlayer, Integer> elo = new IdentityHashMap<>();
            for (QueuedPlayer qp : entries) {
                long sum = 0;
                for (UUID id : qp.getMembers()) sum += plugin.getEloManager().peekElo(id);
                elo.put(qp, (int) Math.round((double) sum / qp.size()));
            }
            entries.sort(Comparator.comparingInt(elo::get));

            Set<QueuedPlayer> used = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int i = 0; i < entries.size(); i++) {
                QueuedPlayer e1 = entries.get(i);
                if (used.contains(e1)) continue;
                int n = e1.size();

                List<QueuedPlayer> side2 = null;
                // 1) an entry of the same size within range
                for (int j = i + 1; j < entries.size(); j++) {
                    QueuedPlayer e2 = entries.get(j);
                    if (used.contains(e2) || e2.size() != n) continue;
                    int diff = elo.get(e2) - elo.get(e1); // ascending: >= 0
                    if (diff > MAX_RANGE) break;
                    if (diff <= allowedRange(e1, e2, now)) { side2 = List.of(e2); break; }
                }
                // 2) a party: fill the opposing team with the closest-rated solo players
                if (side2 == null && n > 1) {
                    List<QueuedPlayer> solos = new ArrayList<>();
                    for (QueuedPlayer e : entries) {
                        if (e != e1 && !used.contains(e) && e.size() == 1
                                && Math.abs(elo.get(e) - elo.get(e1)) <= allowedRange(e1, e, now)) solos.add(e);
                    }
                    if (solos.size() >= n) {
                        solos.sort(Comparator.comparingInt(e -> Math.abs(elo.get(e) - elo.get(e1))));
                        side2 = new ArrayList<>(solos.subList(0, n));
                    }
                }
                if (side2 == null) continue;

                Arena arena = findFreeArena(n);
                if (arena == null) continue; // nothing free for this size; keep everyone queued

                // Take everyone out of the queue BEFORE creating the match so a failed
                // attempt can put them back (previously they were silently lost).
                List<QueuedPlayer> side1 = List.of(e1);
                for (QueuedPlayer e : side1) { removeFromQueue(queue, e); used.add(e); }
                for (QueuedPlayer e : side2) { removeFromQueue(queue, e); used.add(e); }
                createMatch(side1, side2, queue.getKitName(), arena);
            }
        }
    }

    private void removeFromQueue(Queue queue, QueuedPlayer qp) {
        queue.removePlayer(qp);
        for (UUID id : qp.getMembers()) queuedPlayers.remove(id, qp);
        if (queue.isEmpty()) queues.remove(queue.getKitName().toLowerCase());
    }

    /** Put an entry back, keeping its original wait time. Does not trigger matchmaking. */
    private void requeue(QueuedPlayer qp, String kitName) {
        for (UUID id : qp.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) return; // a member is gone: the unit is dissolved
            if (queuedPlayers.containsKey(id)) return;
        }
        for (UUID id : qp.getMembers()) queuedPlayers.put(id, qp);
        queues.computeIfAbsent(kitName.toLowerCase(), Queue::new).addPlayer(qp);
    }

    /** A free arena that fits two sides of {@code perSide} players (teams required beyond 1v1). */
    private Arena findFreeArena(int perSide) {
        var mm = plugin.getMatchManager();
        for (Arena a : plugin.getArenaManager().getEnabled()) {
            if (a.getState() == com.omniarena.arena.ArenaState.WAITING
                    && a.isJoinable()
                    && arenaFits(a, perSide)
                    && !mm.isArenaReserved(a.getName(), new UUID(0, 0))
                    && mm.getMatch(a).isEmpty()) {
                return a;
            }
        }
        return null;
    }

    private static String describe(List<QueuedPlayer> side) {
        if (side.size() == 1 && !side.get(0).isParty()) return side.get(0).getName();
        List<String> names = new ArrayList<>();
        for (QueuedPlayer q : side) names.addAll(q.getNames());
        return String.join(", ", names);
    }

    /**
     * Create a match from queue entries. Each side is placed on its own team so parties stay together.
     */
    private void createMatch(List<QueuedPlayer> side1, List<QueuedPlayer> side2, String kitName, Arena arena) {
        List<QueuedPlayer> all = new ArrayList<>(side1);
        all.addAll(side2);
        List<List<Player>> sides = new ArrayList<>();
        for (List<QueuedPlayer> side : List.of(side1, side2)) {
            List<Player> ps = new ArrayList<>();
            for (QueuedPlayer q : side) {
                for (UUID id : q.getMembers()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) ps.add(p);
                }
            }
            sides.add(ps);
        }
        int expected = all.stream().mapToInt(QueuedPlayer::size).sum();
        if (sides.get(0).size() + sides.get(1).size() != expected) {
            // Someone went offline: re-queue the entries that are still whole
            for (QueuedPlayer q : all) requeue(q, kitName);
            return;
        }

        var mm = plugin.getMatchManager();
        List<Player> joined = new ArrayList<>();
        boolean ok = true;
        for (Player p : sides.get(0)) { if (!(ok = mm.joinArena(p, arena))) break; joined.add(p); if (joined.size() == 1) markRanked(arena, kitName); }
        if (ok) for (Player p : sides.get(1)) { if (!(ok = mm.joinArena(p, arena))) break; joined.add(p); }
        if (!ok) {
            for (Player p : joined) mm.leaveArena(p);
            for (QueuedPlayer q : all) requeue(q, kitName);
            return;
        }

        boolean teamed = sides.get(0).size() > 1;
        if (teamed) {
            mm.getMatch(arena).ifPresent(m -> {
                for (Player p : sides.get(0)) m.getPresetSides().put(p.getUniqueId(), 0);
                for (Player p : sides.get(1)) m.getPresetSides().put(p.getUniqueId(), 1);
            });
        }

        String d1 = describe(side1), d2 = describe(side2);
        for (Player p : sides.get(0)) MessageUtil.send(p, "<green>Match found! vs <yellow>" + d2);
        for (Player p : sides.get(1)) MessageUtil.send(p, "<green>Match found! vs <yellow>" + d1);
    }

    private void markRanked(Arena arena, String kitName) {
        plugin.getMatchManager().getMatch(arena).ifPresent(m -> {
            m.setRanked(true);
            m.setQueueName(kitName);
            if (kitName != null && com.omniarena.kit.KitManager.getInstance().getAdminKit(kitName).isPresent()) {
                m.setQueueKit(kitName);
            }
        });
    }

    /**
     * Get all queue sizes for display
     */
    public Map<String, Integer> getQueueSizes() {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (Map.Entry<String, Queue> entry : queues.entrySet()) {
            sizes.put(entry.getKey(), entry.getValue().getSize());
        }
        return sizes;
    }

    public int getTotalQueued() {
        return queuedPlayers.size();
    }

    // Inner classes

    public static class Queue {
        private final String kitName;
        private final List<QueuedPlayer> players = new ArrayList<>();

        public Queue(String kitName) {
            this.kitName = kitName;
        }

        public String getKitName() { return kitName; }
        public List<QueuedPlayer> getPlayers() { return players; }
        
        public void addPlayer(QueuedPlayer player) {
            if (!players.contains(player)) {
                players.add(player);
            }
        }

        public void removePlayer(QueuedPlayer player) {
            players.remove(player);
        }

        public int getSize() { return players.size(); }
        public boolean isEmpty() { return players.isEmpty(); }

        public int getPosition(QueuedPlayer player) {
            return players.indexOf(player) + 1;
        }
    }

    /** A queue entry: one solo player, or a whole party queued as a single unit (leader first). */
    public static class QueuedPlayer {
        private final List<UUID> members;
        private final List<String> names;
        private final String kitName;
        private final long queueTime;

        public QueuedPlayer(UUID uuid, String name, String kitName) {
            this(List.of(uuid), List.of(name), kitName);
        }

        public QueuedPlayer(List<UUID> members, List<String> names, String kitName) {
            this.members = List.copyOf(members);
            this.names = List.copyOf(names);
            this.kitName = kitName;
            this.queueTime = System.currentTimeMillis();
        }

        /** The unit's first member (the party leader for parties). */
        public UUID getUuid() { return members.get(0); }
        public String getName() { return isParty() ? names.get(0) + "'s party" : names.get(0); }
        public List<UUID> getMembers() { return members; }
        public List<String> getNames() { return names; }
        public int size() { return members.size(); }
        public boolean isParty() { return members.size() > 1; }
        public String getKitName() { return kitName; }
        public long getQueueTime() { return queueTime; }
    }

    public static class QueueInfo {
        private final String kitName;
        private final int position;
        private final int totalInQueue;

        public QueueInfo(String kitName, int position, int totalInQueue) {
            this.kitName = kitName;
            this.position = position;
            this.totalInQueue = totalInQueue;
        }

        public String getKitName() { return kitName; }
        public int getPosition() { return position; }
        public int getTotalInQueue() { return totalInQueue; }
    }
}
