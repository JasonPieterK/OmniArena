package com.omniarena.health;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.config.ConfigManager;
import com.omniarena.integration.IntegrationStatus;
import com.omniarena.match.Match;
import com.omniarena.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds and prints the {@code /oa doctor} report. Line templates live in messages.yml (doctor-*). */
public final class Doctor {

    private static final String FIX_ALL = "/oa doctor fix";

    private final OmniArena plugin;

    public Doctor(OmniArena plugin) {
        this.plugin = plugin;
    }

    /** One finding: {@code fixCommand} is shown as a clickable [fix] button when non-null. */
    record Problem(String text, String fixCommand) {}

    public void report(CommandSender sender) {
        ConfigManager cm = ConfigManager.getInstance();
        MessageUtil.send(sender, cm.getRawMessage("doctor-header"));
        int problems = 0;

        problems += section(sender, "Config and data files", configProblems(), "No config or data file needed repair.");
        problems += section(sender, "Arenas", arenaProblems(), "All arenas have a region, battle spawns and a snapshot.");
        problems += section(sender, "Stuck state", stuckProblems(), "No stuck arenas, matches or players.");
        problems += section(sender, "Background tasks", taskProblems(), "All core tasks are running.");
        problems += section(sender, "Integrations", integrationLines(), null);
        problems += section(sender, "Recent errors", errorProblems(), "No errors since the last restart.");

        MessageUtil.send(sender, cm.getRawMessage(problems == 0 ? "doctor-footer-ok" : "doctor-footer-problems")
                .replace("{count}", String.valueOf(problems)).replace("{command}", FIX_ALL));
    }

    // ── Sections ──────────────────────────────────────────────────

    private int section(CommandSender sender, String title, List<Problem> items, String okText) {
        ConfigManager cm = ConfigManager.getInstance();
        MessageUtil.send(sender, cm.getRawMessage("doctor-section").replace("{title}", title));
        if (items.isEmpty()) {
            if (okText != null) MessageUtil.send(sender, cm.getRawMessage("doctor-ok").replace("{text}", okText));
            return 0;
        }
        int problems = 0;
        for (Problem p : items) {
            // Lines beginning with "i:" are informational (not counted as problems)
            boolean info = p.text().startsWith("i:");
            String text = esc(info ? p.text().substring(2) : p.text());
            String line = cm.getRawMessage(info ? "doctor-info" : "doctor-problem").replace("{text}", text);
            if (p.fixCommand() != null) {
                line += cm.getRawMessage("doctor-fix").replace("{command}", p.fixCommand());
            }
            MessageUtil.send(sender, line);
            if (!info) problems++;
        }
        return problems;
    }

    private List<Problem> configProblems() {
        List<Problem> out = new ArrayList<>();
        for (HealthReport.Entry e : HealthReport.get().recent(HealthReport.Kind.REPAIR, 100)) {
            if (e.category().equals("config") || e.category().equals("broken-file")) {
                out.add(new Problem("i:" + time(e.time()) + " " + e.message(), null));
            }
        }
        int broken = 0;
        broken += count(new File(plugin.getDataFolder(), "arenas/broken"));
        broken += count(new File(plugin.getDataFolder(), "broken"));
        if (broken > 0) {
            out.add(new Problem(broken + " corrupt file(s) are kept in plugins/OmniArena/arenas/broken and /broken. "
                    + "Repair the YAML and copy them back, or delete them.", null));
        }
        return out;
    }

    private static int count(File dir) {
        File[] files = dir.listFiles(File::isFile);
        return files == null ? 0 : files.length;
    }

    private List<Problem> arenaProblems() {
        List<Problem> out = new ArrayList<>();
        var restore = plugin.getBlockRestoreModule();
        for (Arena a : plugin.getArenaManager().getAll()) {
            String n = a.getName();
            if (!a.getRegion().isDefined()) {
                out.add(new Problem("Arena " + n + " has no region.", "/oa region wand"));
            }
            if (a.getBattleSpawns().isEmpty()) {
                out.add(new Problem("Arena " + n + " has no battle spawn.", "/oa spawn set " + n + " BATTLE"));
            }
            if (a.getRegion().isDefined() && restore != null && !restore.hasSnapshot(n)) {
                out.add(new Problem("Arena " + n + " has no snapshot, so it cannot regenerate.", "/oa regen snapshot " + n));
            }
        }
        return out;
    }

    private List<Problem> stuckProblems() {
        List<Problem> out = new ArrayList<>();
        Watchdog wd = Watchdog.getInstance();
        if (wd != null) {
            for (Arena a : wd.stuckArenas()) {
                out.add(new Problem("Arena " + a.getName() + " has been " + a.getState() + " for " + wd.transitionalSeconds(a)
                        + "s (limit " + wd.stuckThresholdSeconds() + "s).", FIX_ALL));
            }
        }
        for (Match m : plugin.getMatchManager().getActiveMatches()) {
            if (plugin.getArenaManager().get(m.getArena().getName()).orElse(null) != m.getArena()) {
                out.add(new Problem("A match is running in the deleted arena " + m.getArena().getName() + ".", FIX_ALL));
            }
        }
        int orphans = plugin.getMatchManager().findOrphanedPlayers().size();
        if (orphans > 0) {
            out.add(new Problem(orphans + " player(s) are mapped to a match that no longer exists.", FIX_ALL));
        }
        return out;
    }

    private List<Problem> taskProblems() {
        List<Problem> out = new ArrayList<>();
        boolean healthOn = ConfigManager.getInstance().getConfig().getBoolean("health.enabled", true);
        Watchdog wd = Watchdog.getInstance();
        if (healthOn && (wd == null || !wd.isRunning())) out.add(new Problem("The watchdog is not running.", FIX_ALL));
        check(out, "Matchmaking queue", plugin.getQueueManager() != null && plugin.getQueueManager().isRunning());
        check(out, "Party invite cleanup", plugin.getPartyManager() != null && plugin.getPartyManager().isRunning());
        check(out, "Tournament tick", plugin.getTournamentManager() != null && plugin.getTournamentManager().isRunning());
        check(out, "Sign refresh", plugin.isSignTaskRunning());
        check(out, "Combat tags", plugin.getCombatManager() != null && plugin.getCombatManager().isRunning());
        return out;
    }

    private static void check(List<Problem> out, String name, boolean running) {
        if (!running) out.add(new Problem(name + " task is not running.", FIX_ALL));
    }

    private List<Problem> integrationLines() {
        List<Problem> out = new ArrayList<>();
        var im = plugin.getIntegrationManager();
        if (im == null) return out;
        for (IntegrationStatus s : im.statuses()) {
            String state = !s.present() ? "not installed" : s.enabled() ? "hooked" : "installed but not hooked";
            String err = s.lastError() != null ? " (" + s.lastError() + ")" : "";
            boolean bad = s.present() && !s.enabled() && s.lastError() != null;
            out.add(new Problem((bad ? "" : "i:") + s.name() + ": " + state + err, null));
        }
        return out;
    }

    private List<Problem> errorProblems() {
        List<Problem> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Integer> e : HealthReport.get().tracker().hotComponents(now).entrySet()) {
            out.add(new Problem(e.getKey() + " threw " + e.getValue() + " errors in the last minute.", null));
        }
        for (HealthReport.Entry e : HealthReport.get().recent(HealthReport.Kind.ERROR, 5)) {
            out.add(new Problem("i:" + time(e.time()) + " [" + e.category() + "] " + e.message(), null));
        }
        for (Map.Entry<String, Long> e : HealthReport.get().tracker().totals().entrySet()) {
            out.add(new Problem("i:" + e.getKey() + ": " + e.getValue() + " error(s) since start", null));
        }
        return out;
    }

    private static String time(long millis) {
        return new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date(millis));
    }

    private static String esc(String s) {
        return MiniMessage.miniMessage().escapeTags(s);
    }
}
