package com.omniarena.integration;

import org.bukkit.Location;

import java.util.List;

/**
 * A third-party hologram plugin OmniArena can drive for leaderboards. Implementations use reflection only.
 * They may throw: every call is made through {@link IntegrationManager#safe}, which isolates and counts failures.
 */
public interface HologramHook {

    /** Hook key, same as the {@code integrations.<key>} switch and the IntegrationManager hook name. */
    String key();

    /** Human readable plugin name for messages. */
    String displayName();

    /** True when text lines use MiniMessage; false for legacy {@code &} colour codes. */
    boolean usesMiniMessage();

    /** Creates a hologram; returns false when one with that name already exists or creation was refused. */
    boolean create(String name, Location location, List<String> lines);

    /** Replaces all lines; returns false when the hologram does not exist (e.g. deleted by hand). */
    boolean update(String name, List<String> lines);

    /** Deletes a hologram; returns false when it did not exist. */
    boolean remove(String name);
}
