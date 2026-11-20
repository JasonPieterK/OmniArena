package com.omniarena.api;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.match.MatchManager;
import com.omniarena.rating.EloManager;
import com.omniarena.rating.EloRating;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Small, stable facade for other plugins. All methods must be called on the main thread and are safe
 * when OmniArena is stopped or not fully started (they return empty/false values instead of throwing).
 * Events live in {@code com.omniarena.api.event}.
 */
public final class OmniArenaAPI {

    private OmniArenaAPI() {}

    private static OmniArena plugin() {
        OmniArena p = OmniArena.getInstance();
        return p != null && p.isEnabled() && !p.isStopped() ? p : null;
    }

    /** Names of all arenas (original case), sorted case-insensitively. */
    public static List<String> getArenaNames() {
        ArenaManager am = ArenaManager.getInstance();
        if (am == null) return List.of();
        Collection<Arena> all = am.getAll();
        return all.stream().map(Arena::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    /** True when the player is playing in an arena (lobby or running match). Spectators are not "in a match". */
    public static boolean isInMatch(Player player) {
        MatchManager mm = MatchManager.getInstance();
        return player != null && mm != null && mm.isInArena(player) && !mm.isSpectating(player);
    }

    /** The arena the player is in, playing or spectating. */
    public static Optional<String> getArenaOf(Player player) {
        MatchManager mm = MatchManager.getInstance();
        if (player == null || mm == null) return Optional.empty();
        return mm.getArenaName(player);
    }

    /** Current ELO (default 1000 for unrated players). Read-only: never creates a rating entry. */
    public static int getElo(UUID uuid) {
        EloManager em = EloManager.getInstance();
        if (uuid == null || em == null) return EloRating.DEFAULT_RATING;
        return em.peekElo(uuid);
    }

    /**
     * Joins the player to the arena through the normal join path (all checks apply: enabled, whitelist,
     * reservations, full...). Returns false when the arena does not exist or the join was refused.
     */
    public static boolean joinArena(Player player, String arenaName) {
        if (player == null || arenaName == null || plugin() == null) return false;
        ArenaManager am = ArenaManager.getInstance();
        MatchManager mm = MatchManager.getInstance();
        if (am == null || mm == null) return false;
        Optional<Arena> arena = am.get(arenaName);
        return arena.isPresent() && mm.joinArena(player, arena.get());
    }
}
