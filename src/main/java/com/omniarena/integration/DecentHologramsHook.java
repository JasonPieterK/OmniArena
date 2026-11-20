package com.omniarena.integration;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** DecentHolograms through its static {@code DHAPI}, reflection only (no compile-time dependency). */
public final class DecentHologramsHook implements HologramHook {

    private static final String API = "eu.decentsoftware.holograms.api.DHAPI";

    private final Class<?> dhapi;

    /** @throws ClassNotFoundException when DecentHolograms' API class is not visible */
    public DecentHologramsHook(Plugin decentHolograms) throws ClassNotFoundException {
        this.dhapi = Reflect.load(API, decentHolograms.getClass().getClassLoader());
    }

    @Override public String key() { return "decentholograms"; }
    @Override public String displayName() { return "DecentHolograms"; }
    @Override public boolean usesMiniMessage() { return false; }

    @Override
    public boolean create(String name, Location location, List<String> lines) {
        if (Reflect.invokeStatic(dhapi, "getHologram", name) != null) return false;
        // createHologram(String name, Location loc, boolean saveToFile, List<String> lines)
        Object h = Reflect.invokeStatic(dhapi, "createHologram", name, location, true, lines);
        return h != null;
    }

    @Override
    public boolean update(String name, List<String> lines) {
        Object h = Reflect.invokeStatic(dhapi, "getHologram", name);
        if (h == null) return false;
        Reflect.invokeStatic(dhapi, "setHologramLines", h, lines);
        return true;
    }

    @Override
    public boolean remove(String name) {
        if (Reflect.invokeStatic(dhapi, "getHologram", name) == null) return false;
        Reflect.invokeStatic(dhapi, "removeHologram", name);
        return true;
    }
}
