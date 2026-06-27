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
        matchmakingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::processMatchmaking, 20L, 20L);
    }

    public void stop() {
        if (matchmakingTask != null) {
            matchmakingTask.cancel();
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

        Queue queue = queues.computeIfAbsent(kitName.toLowerCase(), k -> new Queue(k));
        QueuedPlayer qp = new QueuedPlayer(player.getUniqueId(), player.getName(), kitName);
        
        queue.addPlayer(qp);
        queuedPlayers.put(player.getUniqueId(), qp);
        
        MessageUtil.send(player, "<green>Joined queue for <yellow>" + kitName + "</yellow>. <gray>(Position: " + queue.getPosition(qp) + ")");
        
        if (queue.getSize() >= 2) {
            processMatchmaking();
        }
        
        return true;
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

    /**
     * Process matchmaking - find matches based on ELO proximity
     */
    private void processMatchmaking() {
        for (Queue queue : new ArrayList<>(queues.values())) {
            if (queue.getSize() < 2) continue;

            List<QueuedPlayer> players = new ArrayList<>(queue.getPlayers());
            
            // Sort by ELO rating for better matchmaking
            players.sort(Comparator.comparingInt(p -> {
                return plugin.getEloManager().getRating(p.getUuid()).getRating();
            }));

            // Match players with similar ELO ratings
            for (int i = 0; i < players.size() - 1; i++) {
                QueuedPlayer p1 = players.get(i);
                if (!queuedPlayers.containsKey(p1.getUuid())) continue; // Player might have left

                for (int j = i + 1; j < players.size(); j++) {
                    QueuedPlayer p2 = players.get(j);
                    if (!queuedPlayers.containsKey(p2.getUuid())) continue;

                    // Check ELO difference (max 200 points difference for fair match)
                    int elo1 = plugin.getEloManager().getRating(p1.getUuid()).getRating();
                    int elo2 = plugin.getEloManager().getRating(p2.getUuid()).getRating();
                    
                    if (Math.abs(elo1 - elo2) <= 200) {
                        // Found a match!
                        createMatch(p1, p2, queue.getKitName());
                        queue.removePlayer(p1);
                        queue.removePlayer(p2);
                        queuedPlayers.remove(p1.getUuid());
                        queuedPlayers.remove(p2.getUuid());
                        
                        if (queue.isEmpty()) {
                            queues.remove(queue.getKitName().toLowerCase());
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * Create a match from queue
     */
    private void createMatch(QueuedPlayer p1, QueuedPlayer p2, String kitName) {
        Player player1 = Bukkit.getPlayer(p1.getUuid());
        Player player2 = Bukkit.getPlayer(p2.getUuid());

        if (player1 == null || player2 == null || !player1.isOnline() || !player2.isOnline()) {
            // One player went offline, re-queue the other
            if (player1 != null && player1.isOnline()) {
                joinQueue(player1, kitName);
            }
            if (player2 != null && player2.isOnline()) {
                joinQueue(player2, kitName);
            }
            return;
        }

        // Find available arena
        Arena arena = plugin.getArenaManager().getEnabled().stream()
                .filter(a -> a.getState() == com.omniarena.arena.ArenaState.WAITING)
                .findFirst()
                .orElse(null);

        if (arena == null) {
            MessageUtil.send(player1, "<red>No available arenas. Re-queued...");
            MessageUtil.send(player2, "<red>No available arenas. Re-queued...");
            joinQueue(player1, kitName);
            joinQueue(player2, kitName);
            return;
        }

        // Join both players to arena
        plugin.getMatchManager().joinArena(player1, arena);
        plugin.getMatchManager().joinArena(player2, arena);

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
