package com.omniarena.api.event;

import com.omniarena.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/** Fired on the main thread when a match starts (players are teleported to their spawns, the match is RUNNING). */
public class ArenaMatchStartEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final List<UUID> players;
    private final boolean ranked;

    public ArenaMatchStartEvent(Arena arena, List<UUID> players, boolean ranked) {
        this.arena = arena;
        this.players = List.copyOf(players);
        this.ranked = ranked;
    }

    public Arena getArena() { return arena; }

    public String getArenaName() { return arena.getName(); }

    /** Ids of everyone who started the match. */
    public List<UUID> getPlayerIds() { return players; }

    /** The players of the match that are online right now. */
    public List<Player> getPlayers() {
        return players.stream().map(Bukkit::getPlayer).filter(p -> p != null).toList();
    }

    /** True for ranked (queue) matches, which affect ELO. */
    public boolean isRanked() { return ranked; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
