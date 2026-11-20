package com.omniarena.api.event;

import com.omniarena.OmniArena;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;

/**
 * Internal helper that fires the public API events. Bukkit already isolates exceptions thrown by listeners;
 * this additionally guards the call itself (wrong thread, server shutting down) so that an event can never
 * break a match. Always call it from the main thread.
 */
public final class ApiEvents {

    private ApiEvents() {}

    /**
     * Fires {@code event}; returns the event so callers can read a cancellation. Failures are logged and
     * swallowed, in which case the event counts as not handled.
     */
    public static <E extends Event> E fire(E event) {
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (Throwable t) {
            if (t instanceof VirtualMachineError vme) throw vme;
            OmniArena pl = OmniArena.getInstance();
            if (pl != null) {
                pl.getLogger().warning("API event " + event.getEventName() + " failed (" + t
                        + "); the match continues. A plugin listening to OmniArena events probably has a bug.");
            }
        }
        return event;
    }
}
