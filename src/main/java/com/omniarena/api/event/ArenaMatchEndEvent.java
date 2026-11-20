package com.omniarena.api.event;

import com.omniarena.arena.Arena;
import com.omniarena.match.MatchResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Fired on the main thread when a match ends (ELO, betting, rewards and stats were already applied; the
 * arena reset follows after the end delay).
 */
public class ArenaMatchEndEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Arena arena;
    private final MatchResult result;
    private final List<UUID> winners;

    public ArenaMatchEndEvent(Arena arena, MatchResult result, List<UUID> winners) {
        this.arena = arena;
        this.result = result;
        this.winners = List.copyOf(winners);
    }

    public Arena getArena() { return arena; }

    public String getArenaName() { return arena.getName(); }

    public MatchResult getResult() { return result; }

    /** Winning player ids (the winner, or all members of the winning team); empty for a draw/timeout without a winner. */
    public List<UUID> getWinners() { return winners; }

    public boolean isDraw() { return result.isDraw(); }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
