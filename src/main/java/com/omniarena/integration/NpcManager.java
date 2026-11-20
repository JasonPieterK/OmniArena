package com.omniarena.integration;

import com.omniarena.OmniArena;
import com.omniarena.util.YamlUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Citizens NPCs bound to OmniArena actions (menu, join, queue, spectate), persisted in npcs.yml as
 * {@code npcs.<citizensId>: {action, arg}}. Citizens itself persists the NPC entities; this class only
 * remembers what a right-click should do. All Citizens calls go through {@link IntegrationManager#safe}.
 * {@link #start()}/{@link #stop()} are idempotent and wired into onEnable/onDisable and /oa plugin stop|start.
 */
public final class NpcManager {

    public record Binding(NpcAction action, String arg) {}

    public record Entry(int id, NpcAction action, String arg, boolean npcExists) {}

    public enum CreateStatus { OK, NO_CITIZENS, FAILED }

    public enum RemoveStatus { REMOVED, NOT_BOUND, NO_CITIZENS }

    public record CreateResult(CreateStatus status, int id) {}

    private static final long CLICK_COOLDOWN_MS = 750;

    private final OmniArena plugin;
    private final IntegrationManager im;
    private final File file;
    private final Map<Integer, Binding> bindings = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public NpcManager(OmniArena plugin, IntegrationManager im) {
        this.plugin = plugin;
        this.im = im;
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    // ── lifecycle ────────────────────────────────────────────────

    public void start() {
        stop();
        loadFile();
        attach();
    }

    public void stop() {
        CitizensIntegration c = im.citizens();
        if (c != null) im.safe("citizens", c::unregister);
        bindings.clear();
        lastClick.clear();
    }

    /** (Re)registers the right-click handler when the Citizens hook is live; called when the hook (re)activates. */
    public void attach() {
        CitizensIntegration c = im.citizens();
        if (c == null) return;
        im.safe("citizens", () -> c.registerClick(this::onRawClick));
    }

    private void loadFile() {
        bindings.clear();
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("npcs");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            int id;
            try { id = Integer.parseInt(key); } catch (NumberFormatException e) { continue; }
            var action = NpcAction.parse(s.getString("action"));
            if (action.isEmpty()) {
                plugin.getLogger().warning("npcs.yml: NPC " + key + " has an unknown action '" + s.getString("action") + "' and was ignored. Fix: edit plugins/OmniArena/npcs.yml (menu, join, queue or spectate) or remove it with /oa npc remove " + key + ".");
                continue;
            }
            String arg = s.getString("arg", "");
            bindings.put(id, new Binding(action.get(), arg == null || arg.isBlank() ? null : arg));
        }
    }

    private void saveFile() {
        FileConfiguration cfg = new YamlConfiguration();
        new TreeMap<>(bindings).forEach((id, b) -> {
            cfg.set("npcs." + id + ".action", b.action().id());
            if (b.arg() != null) cfg.set("npcs." + id + ".arg", b.arg());
        });
        YamlUtil.save(cfg, file, plugin.getLogger());
    }

    // ── operations ───────────────────────────────────────────────

    public boolean available() { return im.citizens() != null; }

    /** Spawns an NPC at {@code where} and binds it. Main thread. */
    public CreateResult create(Location where, NpcAction action, String arg) {
        CitizensIntegration c = im.citizens();
        if (c == null) return new CreateResult(CreateStatus.NO_CITIZENS, -1);
        String name = "&6" + action.defaultName(arg);
        int id = im.safe("citizens", () -> c.createNpc(name, where), -1);
        if (id < 0) return new CreateResult(CreateStatus.FAILED, -1);
        bindings.put(id, new Binding(action, arg == null || arg.isBlank() ? null : arg.trim()));
        saveFile();
        return new CreateResult(CreateStatus.OK, id);
    }

    public RemoveStatus remove(int id) {
        if (!bindings.containsKey(id)) return RemoveStatus.NOT_BOUND;
        CitizensIntegration c = im.citizens();
        if (c == null) return RemoveStatus.NO_CITIZENS;
        im.safe("citizens", () -> c.removeNpc(id), false);
        bindings.remove(id);
        saveFile();
        return RemoveStatus.REMOVED;
    }

    public List<Entry> list() {
        CitizensIntegration c = im.citizens();
        List<Entry> out = new ArrayList<>();
        for (var e : bindings.entrySet()) {
            boolean exists = c != null && im.safe("citizens", () -> c.exists(e.getKey()), false);
            out.add(new Entry(e.getKey(), e.getValue().action(), e.getValue().arg(), exists));
        }
        out.sort(Comparator.comparingInt(Entry::id));
        return out;
    }

    public Binding binding(int id) { return bindings.get(id); }

    // ── click handling ───────────────────────────────────────────

    private void onRawClick(Object event) {
        CitizensIntegration c = im.citizens();
        if (c == null || plugin.isStopped()) return;
        CitizensIntegration.Click click = im.safe("citizens", () -> c.extract(event), null);
        if (click == null || click.player() == null) return;
        handleClick(click.player(), click.npcId());
    }

    /** Runs the bound action for a click (the command goes through the normal /oa dispatcher). */
    void handleClick(Player player, int npcId) {
        Binding b = bindings.get(npcId);
        if (b == null) return;
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < CLICK_COOLDOWN_MS) return; // Citizens can fire both hands / repeat
        if (lastClick.size() > 256) lastClick.values().removeIf(t -> now - t > CLICK_COOLDOWN_MS);
        player.performCommand(b.action().command(b.arg()));
    }
}
