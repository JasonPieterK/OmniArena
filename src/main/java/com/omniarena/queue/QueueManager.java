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
     * Add a player to a queue
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

        Queue queue = queues.computeIfAbsent(kitName.toLowerCase(), k -> new Queue(k));
        QueuedPlayer qp = new QueuedPlayer(player.getUniqueId(), player.getName(), kitName);
        
        queue.addPlayer(qp);
        queuedPlayers.put(player.getUniqueId(), qp);
        
        MessageUtil.send(player, "<green>Joined queue for <yellow>" + kitName + "</yellow>. <gray>(Position: " + queue.getPosition(qp) + ")");
        
        return true; // paired on the next matchmaking tick (1s)
    }

    /**
     * Remove a player from queue
     */
    public boolean leaveQueue(Player player) {
        QueuedPlayer qp = queuedPlayers.remove(player.getUniqueId());
        if (qp == null) {
            MessageUtil.send(player, "<red>You are not in a queue.");
            return false;
        }

        Queue queue = queues.get(qp.getKitName().toLowerCase());
        if (queue != null) {
            queue.removePlayer(qp);
            if (queue.isEmpty()) {
                queues.remove(qp.getKitName().toLowerCase());
            }
        }

        MessageUtil.send(player, "<green>Left the queue.");
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

    /** Removes a player's queue entry without messaging (quit/disconnect). */
    public void removeOnQuit(UUID uuid) {
        QueuedPlayer qp = queuedPlayers.remove(uuid);
        if (qp == null) return;
        String key = qp.getKitName().toLowerCase();
        Queue queue = queues.get(key);
        if (queue != null) {
            queue.removePlayer(qp);
            if (queue.isEmpty()) queues.remove(key);
        }
    }

    /**
     * Process matchmaking - pair players with similar ELO. The accepted rating gap
     * grows with time spent waiting.
     */
    private void processMatchmaking() {
        if (queues.isEmpty()) return;
        long now = System.currentTimeMillis();
        var mm = plugin.getMatchManager();

        for (Queue queue : new ArrayList<>(queues.values())) {
            // Drop players who disconnected or entered an arena by other means
            for (QueuedPlayer qp : new ArrayList<>(queue.getPlayers())) {
                Player online = Bukkit.getPlayer(qp.getUuid());
                if (online == null || !online.isOnline() || mm.isInArena(online)) {
                    removeOnQuit(qp.getUuid());
                }
            }
            if (queue.getSize() < 2) continue;

            // Look up each rating once (not per comparison)
            List<QueuedPlayer> players = new ArrayList<>(queue.getPlayers());
            Map<UUID, Integer> elo = new HashMap<>();
            for (QueuedPlayer qp : players) {
                elo.put(qp.getUuid(), plugin.getEloManager().peekElo(qp.getUuid()));
            }
            players.sort(Comparator.comparingInt(p -> elo.get(p.getUuid())));

            Set<UUID> used = new HashSet<>();
            for (int i = 0; i < players.size() - 1; i++) {
                QueuedPlayer p1 = players.get(i);
                if (used.contains(p1.getUuid())) continue;

                for (int j = i + 1; j < players.size(); j++) {
                    QueuedPlayer p2 = players.get(j);
                    if (used.contains(p2.getUuid())) continue;

                    int diff = elo.get(p2.getUuid()) - elo.get(p1.getUuid()); // sorted ascending: >= 0
                    if (diff > MAX_RANGE) break;
                    if (diff > allowedRange(p1, p2, now)) continue;

                    Arena arena = findFreeArena();
                    if (arena == null) return; // nothing free; keep everyone queued, retry next tick

                    // Take both out of the queue BEFORE creating the match so a failed
                    // attempt can put them back (previously they were silently lost).
                    removeFromQueue(queue, p1);
                    removeFromQueue(queue, p2);
                    used.add(p1.getUuid());
                    used.add(p2.getUuid());
                    createMatch(p1, p2, queue.getKitName(), arena);
                    break;
                }
            }
        }
    }

    private void removeFromQueue(Queue queue, QueuedPlayer qp) {
        queue.removePlayer(qp);
        queuedPlayers.remove(qp.getUuid());
        if (queue.isEmpty()) queues.remove(queue.getKitName().toLowerCase());
    }

    /** Put a player back, keeping their original wait time. Does not trigger matchmaking. */
    private void requeue(QueuedPlayer qp, String kitName) {
        Player p = Bukkit.getPlayer(qp.getUuid());
        if (p == null || !p.isOnline()) return;
        if (queuedPlayers.putIfAbsent(qp.getUuid(), qp) != null) return;
        queues.computeIfAbsent(kitName.toLowerCase(), Queue::new).addPlayer(qp);
    }

    private Arena findFreeArena() {
        var mm = plugin.getMatchManager();
        for (Arena a : plugin.getArenaManager().getEnabled()) {
            if (a.getState() == com.omniarena.arena.ArenaState.WAITING
                    && a.isJoinable()
                    && mm.getMatch(a).isEmpty()) {
                return a;
            }
        }
        return null;
    }

    /**
     * Create a match from queue
     */
    private void createMatch(QueuedPlayer p1, QueuedPlayer p2, String kitName, Arena arena) {
        Player player1 = Bukkit.getPlayer(p1.getUuid());
        Player player2 = Bukkit.getPlayer(p2.getUuid());

        if (player1 == null || player2 == null || !player1.isOnline() || !player2.isOnline()) {
            // One player went offline, re-queue the other
            requeue(p1, kitName);
            requeue(p2, kitName);
            return;
        }

        var mm = plugin.getMatchManager();
        boolean ok1 = mm.joinArena(player1, arena);
        if (ok1) {
            mm.getMatch(arena).ifPresent(m -> {
                m.setRanked(true);
                if (kitName != null && com.omniarena.kit.KitManager.getInstance().getAdminKit(kitName).isPresent()) {
                    m.setQueueKit(kitName);
                }
            });
        }
        boolean ok2 = ok1 && mm.joinArena(player2, arena);
        if (!ok2) {
            if (ok1) mm.leaveArena(player1);
            requeue(p1, kitName);
            requeue(p2, kitName);
            return;
        }

        MessageUtil.send(player1, "<green>Match found! vs <yellow>" + p2.getName());
        MessageUtil.send(player2, "<green>Match found! vs <yellow>" + p1.getName());
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

    public static class QueuedPlayer {
        private final UUID uuid;
        private final String name;
        private final String kitName;
        private final long queueTime;

        public QueuedPlayer(UUID uuid, String name, String kitName) {
            this.uuid = uuid;
            this.name = name;
            this.kitName = kitName;
            this.queueTime = System.currentTimeMillis();
        }

        public UUID getUuid() { return uuid; }
        public String getName() { return name; }
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
