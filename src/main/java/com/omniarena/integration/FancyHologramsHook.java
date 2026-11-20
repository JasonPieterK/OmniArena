package com.omniarena.integration;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

/**
 * FancyHolograms (v2 API) through reflection: {@code FancyHologramsPlugin.get().getHologramManager()},
 * {@code TextHologramData}, {@code manager.create/addHologram/removeHologram}. No compile-time dependency.
 */
public final class FancyHologramsHook implements HologramHook {

    private static final String PLUGIN_CLASS = "de.oliver.fancyholograms.api.FancyHologramsPlugin";
    private static final String TEXT_DATA = "de.oliver.fancyholograms.api.data.TextHologramData";

    private final Class<?> pluginClass;
    private final Class<?> textData;

    public FancyHologramsHook(Plugin fancyHolograms) throws ClassNotFoundException {
        ClassLoader cl = fancyHolograms.getClass().getClassLoader();
        this.pluginClass = Reflect.load(PLUGIN_CLASS, cl);
        this.textData = Reflect.load(TEXT_DATA, cl);
    }

    @Override public String key() { return "fancyholograms"; }
    @Override public String displayName() { return "FancyHolograms"; }
    @Override public boolean usesMiniMessage() { return true; }

    private Object manager() {
        Object api = Reflect.invokeStatic(pluginClass, "get");
        return Reflect.invoke(api, "getHologramManager");
    }

    private Object find(Object manager, String name) {
        Object r = Reflect.invoke(manager, "getHologram", name);
        if (r instanceof Optional<?> opt) return opt.orElse(null);
        return r;
    }

    @Override
    public boolean create(String name, Location location, List<String> lines) {
        Object manager = manager();
        if (find(manager, name) != null) return false;
        try {
            Constructor<?> c = textData.getConstructor(String.class, Location.class);
            Object data = c.newInstance(name, location);
            Reflect.invoke(data, "setText", lines);
            try { Reflect.invoke(data, "setPersistent", true); } catch (IllegalStateException ignored) { /* older API: persistent by default */ }
            Object hologram = Reflect.invoke(manager, "create", data);
            Reflect.invoke(manager, "addHologram", hologram);
            return true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("FancyHolograms API mismatch: " + e, e);
        }
    }

    @Override
    public boolean update(String name, List<String> lines) {
        Object manager = manager();
        Object hologram = find(manager, name);
        if (hologram == null) return false;
        Object data = Reflect.invoke(hologram, "getData");
        Reflect.invoke(data, "setText", lines);
        try { Reflect.invoke(hologram, "forceUpdate"); }
        catch (IllegalStateException e) { Reflect.invoke(hologram, "queueUpdate"); }
        return true;
    }

    @Override
    public boolean remove(String name) {
        Object manager = manager();
        Object hologram = find(manager, name);
        if (hologram == null) return false;
        Reflect.invoke(manager, "removeHologram", hologram);
        return true;
    }
}
