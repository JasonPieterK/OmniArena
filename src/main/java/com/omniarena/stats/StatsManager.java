package com.omniarena.stats;

import com.omniarena.OmniArena;
import com.omniarena.match.Match;
import com.omniarena.match.MatchResult;
import com.omniarena.util.AsyncUtil;
import com.omniarena.util.YamlUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StatsManager {

    private static StatsManager instance;

    private final OmniArena plugin;
    private final File statsFile;
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();

    public StatsManager(OmniArena plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        instance = this;
    }

    public static StatsManager getInstance() { return instance; }

    public void loadAll() {
        if (!statsFile.exists()) return;
        try {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(statsFile);
            if (!cfg.contains("stats")) return;
            for (String uuidStr : cfg.getConfigurationSection("stats").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String path = "stats." + uuidStr;
                    String name = cfg.getString(path + ".name", "Unknown");
                    PlayerStats stats = new PlayerStats(uuid, name);
                    stats.setKills(cfg.getInt(path + ".kills"));
                    stats.setDeaths(cfg.getInt(path + ".deaths"));
                    stats.setWins(cfg.getInt(path + ".wins"));
                    stats.setLosses(cfg.getInt(path + ".losses"));
                    stats.setAssists(cfg.getInt(path + ".assists"));
                    stats.setDraws(cfg.getInt(path + ".draws"));
                    stats.setTimePlayed(cfg.getLong(path + ".time-played"));
                    statsCache.put(uuid, stats);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load stats for " + uuidStr + ": " + e.getMessage());
                }
            }
            plugin.getLogger().info("Loaded stats for " + statsCache.size() + " player(s).");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load stats file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveAll() {
        // During onDisable the plugin is already disabled, so the scheduler is unusable: save inline.
        if (!plugin.isEnabled()) {
            writeStats();
            return;
        }
        AsyncUtil.runAsync(this::writeStats);
    }

    private synchronized void writeStats() {
        {
            FileConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<UUID, PlayerStats> entry : statsCache.entrySet()) {
                String path = "stats." + entry.getKey();
                PlayerStats s = entry.getValue();
                cfg.set(path + ".name", s.getPlayerName());
                cfg.set(path + ".kills", s.getKills());
                cfg.set(path + ".deaths", s.getDeaths());
                cfg.set(path + ".wins", s.getWins());
                cfg.set(path + ".losses", s.getLosses());
                cfg.set(path + ".assists", s.getAssists());
                cfg.set(path + ".draws", s.getDraws());
                cfg.set(path + ".time-played", s.getTimePlayed());
            }
            YamlUtil.save(cfg, statsFile, plugin.getLogger());
        }
    }

    public PlayerStats getStats(UUID uuid) {
        return statsCache.computeIfAbsent(uuid, id -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            return new PlayerStats(id, op.getName() != null ? op.getName() : "Unknown");
        });
    }

    public void recordResult(Match match, MatchResult result) {
        long duration = result.getDurationSeconds();

        for (UUID uuid : result.getParticipants()) {
            PlayerStats stats = getStats(uuid);
            stats.addTime(duration);
            stats.setKills(stats.getKills() + match.getKills(uuid));
            stats.setDeaths(stats.getDeaths() + match.getDeaths(uuid));

            if (result.isDraw()) {
                stats.addDraw();
            } else if (uuid.equals(result.getWinnerId())) {
                stats.addWin();
            } else {
                stats.addLoss();
            }
        }

        saveAll();
    }

    public void resetStats(UUID uuid) {
        statsCache.put(uuid, new PlayerStats(uuid,
                Bukkit.getOfflinePlayer(uuid).getName() != null
                        ? Bukkit.getOfflinePlayer(uuid).getName() : "Unknown"));
        saveAll();
    }

    public List<PlayerStats> getTopKills(int limit) {
        return statsCache.values().stream()
                .sorted(Comparator.comparingInt(PlayerStats::getKills).reversed())
                .limit(limit)
                .toList();
    }

    public List<PlayerStats> getTopWins(int limit) {
        return statsCache.values().stream()
                .sorted(Comparator.comparingInt(PlayerStats::getWins).reversed())
                .limit(limit)
                .toList();
    }

    public List<PlayerStats> getTopKD(int limit) {
        return statsCache.values().stream()
                .sorted(Comparator.comparingDouble(PlayerStats::getKDRatio).reversed())
                .limit(limit)
                .toList();
    }
}
