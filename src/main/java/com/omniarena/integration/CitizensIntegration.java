package com.omniarena.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Citizens hook, reflection only (no compile-time dependency): create/remove NPCs and receive
 * right-click events. Every method may throw; callers go through {@link IntegrationManager#safe}.
 */
public final class CitizensIntegration {

    /** Who right-clicked which Citizens NPC. */
    public record Click(Player player, int npcId) {}

    private static final String API = "net.citizensnpcs.api.CitizensAPI";
    private static final String RIGHT_CLICK = "net.citizensnpcs.api.event.NPCRightClickEvent";

    private final Plugin citizens;
    private final Plugin owner;
    private final Class<?> api;
    private final Class<?> rightClick;
    private Listener listener;

    public CitizensIntegration(Plugin owner, Plugin citizens) throws ClassNotFoundException {
        this.owner = owner;
        this.citizens = citizens;
        ClassLoader cl = citizens.getClass().getClassLoader();
        this.api = Reflect.load(API, cl);
        this.rightClick = Reflect.load(RIGHT_CLICK, cl);
    }

    private Object registry() {
        return Reflect.invokeStatic(api, "getNPCRegistry");
    }

    /** Creates and spawns a player-type NPC; returns its Citizens id. */
    public int createNpc(String name, Location location) {
        Object npc = Reflect.invoke(registry(), "createNPC", EntityType.PLAYER, name);
        Reflect.invoke(npc, "spawn", location);
        return ((Number) Reflect.invoke(npc, "getId")).intValue();
    }

    /** True when an NPC with this Citizens id exists. */
    public boolean exists(int id) {
        return Reflect.invoke(registry(), "getById", id) != null;
    }

    /** Destroys the NPC; false when it did not exist. */
    public boolean removeNpc(int id) {
        Object npc = Reflect.invoke(registry(), "getById", id);
        if (npc == null) return false;
        Reflect.invoke(npc, "destroy");
        return true;
    }

    /** Registers one right-click handler; replaces an earlier one. The event is handed over raw, see {@link #extract}. */
    @SuppressWarnings("unchecked")
    public void registerClick(Consumer<Object> handler) {
        unregister();
        listener = new Listener() { };
        Bukkit.getPluginManager().registerEvent((Class<? extends Event>) rightClick, listener, EventPriority.NORMAL,
                (l, e) -> { if (rightClick.isInstance(e)) handler.accept(e); }, owner, true);
    }

    public void unregister() {
        if (listener != null) {
            HandlerList.unregisterAll(listener);
            listener = null;
        }
    }

    /** Reads the clicker and NPC id from a raw NPCRightClickEvent. */
    public Click extract(Object event) {
        Object npc = Reflect.invoke(event, "getNPC");
        Object clicker = Reflect.invoke(event, "getClicker");
        int id = ((Number) Reflect.invoke(npc, "getId")).intValue();
        return new Click(clicker instanceof Player p ? p : null, id);
    }

    public Plugin plugin() { return citizens; }
}
