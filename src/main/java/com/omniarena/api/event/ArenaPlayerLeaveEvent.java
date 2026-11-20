package com.omniarena.api.event;

import com.omniarena.arena.Arena;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the main thread after a player (or spectator) was removed from an arena and had their
 * pre-arena state restored. Not cancellable.
 */
public class ArenaPlayerLeaveEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Arena arena;
    private final boolean spectator;

    public ArenaPlayerLeaveEvent(Player player, Arena arena, boolean spectator) {
        this.player = player;
        this.arena = arena;
        this.spectator = spectator;
    }

    public Player getPlayer() { return player; }

    public Arena getArena() { return arena; }

    public String getArenaName() { return arena.getName(); }

    /** True when the player was only spectating. */
    public boolean wasSpectator() { return spectator; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
