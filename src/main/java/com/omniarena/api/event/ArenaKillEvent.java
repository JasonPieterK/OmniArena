package com.omniarena.api.event;

import com.omniarena.arena.Arena;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the main thread when a player kills another player inside a running match (after the kill
 * was recorded in the match, before rewards and streak handling). Not cancellable.
 */
public class ArenaKillEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player killer;
    private final Player victim;
    private final Arena arena;

    public ArenaKillEvent(Player killer, Player victim, Arena arena) {
        this.killer = killer;
        this.victim = victim;
        this.arena = arena;
    }

    public Player getKiller() { return killer; }

    public Player getVictim() { return victim; }

    public Arena getArena() { return arena; }

    public String getArenaName() { return arena.getName(); }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
