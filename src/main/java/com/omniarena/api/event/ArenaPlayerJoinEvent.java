package com.omniarena.api.event;

import com.omniarena.arena.Arena;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the main thread after all OmniArena join checks passed, just before the player is moved into
 * the arena. Cancelling it keeps the player where they are and makes the join fail (tell the player why).
 */
public class ArenaPlayerJoinEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Arena arena;
    private boolean cancelled;

    public ArenaPlayerJoinEvent(Player player, Arena arena) {
        this.player = player;
        this.arena = arena;
    }

    public Player getPlayer() { return player; }

    public Arena getArena() { return arena; }

    public String getArenaName() { return arena.getName(); }

    @Override public boolean isCancelled() { return cancelled; }

    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
