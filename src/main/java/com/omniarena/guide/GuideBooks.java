package com.omniarena.guide;

import com.omniarena.OmniArena;
import com.omniarena.health.SafeYaml;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.platform.Platform;
import com.omniarena.util.ColorUtil;
import com.omniarena.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * In-game guide books ({@code /oa guide [topic]}). The text lives in {@code guides.yml}: the bundled file is copied to
 * the data folder so owners can edit it, and topics (or keys) missing from the edited file are back-filled from the
 * jar in memory. Pages come from {@link GuidePaginator}; clickable commands use the {@link GuideMarkup} shorthand.
 */
public final class GuideBooks {

    public static final String FILE = "guides.yml";
    public static final String AUTHOR = "OmniArena";

    /** One guide. {@code text} is MiniMessage plus the {@link GuideMarkup} shorthand. */
    public record Topic(String id, String title, boolean admin, String summary, String text) {

        /** Book pages for this topic: expanded, paginated, with a trailing "all guides" link. */
        public List<String> pages() {
            String body = GuideMarkup.expand(text).stripTrailing()
                    + "\n\n<click:run_command:'/oa guide'><hover:show_text:'Back to the list of guides'><dark_gray>"
                    + "« All guides</dark_gray></hover></click>";
            return GuidePaginator.paginate(body);
        }
    }

    private static volatile Map<String, Topic> cache;
    private static volatile long stamp = Long.MIN_VALUE;

    private GuideBooks() {}

    // ── Loading ──────────────────────────────────────────────────────

    /** Copies the bundled guides.yml to the data folder when it is not there yet. */
    public static void install(OmniArena plugin) {
        try {
            if (!new File(plugin.getDataFolder(), FILE).exists()) plugin.saveResource(FILE, false);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not copy " + FILE + " to the data folder (" + e.getMessage()
                    + "). The bundled guides are used. Fix: check that plugins/OmniArena is writable.");
        }
    }

    /** Drops the cache so the next use re-reads guides.yml (called by {@code /oa reload}). */
    public static void reload() {
        cache = null;
        stamp = Long.MIN_VALUE;
    }

    /** All topics in book order: bundled topics first (with edits applied), then topics the owner added. */
    public static Map<String, Topic> all() {
        OmniArena plugin = OmniArena.getInstance();
        File file = plugin == null ? null : new File(plugin.getDataFolder(), FILE);
        long now = file != null && file.isFile() ? file.lastModified() : -1L;
        Map<String, Topic> c = cache;
        if (c != null && stamp == now) return c;
        YamlConfiguration user = file == null ? new YamlConfiguration() : SafeYaml.loadData(file);
        Map<String, Topic> loaded = parse(bundled(plugin), user);
        cache = loaded;
        stamp = now;
        return loaded;
    }

    private static YamlConfiguration bundled(OmniArena plugin) {
        InputStream in = plugin == null ? GuideBooks.class.getResourceAsStream("/" + FILE) : plugin.getResource(FILE);
        if (in == null) return new YamlConfiguration();
        try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }

    /** Pure merge of the bundled guides with the owner's file (testable without a server). */
    public static Map<String, Topic> parse(ConfigurationSection bundled, ConfigurationSection user) {
        Map<String, Topic> out = new LinkedHashMap<>();
        ConfigurationSection bt = bundled == null ? null : bundled.getConfigurationSection("topics");
        ConfigurationSection ut = user == null ? null : user.getConfigurationSection("topics");
        if (bt != null) {
            for (String id : bt.getKeys(false)) {
                ConfigurationSection b = bt.getConfigurationSection(id);
                ConfigurationSection u = ut == null ? null : ut.getConfigurationSection(id);
                Topic t = topic(id.toLowerCase(Locale.ROOT), b, u);
                if (t != null) out.put(t.id(), t);
            }
        }
        if (ut != null) {
            for (String id : ut.getKeys(false)) {
                String key = id.toLowerCase(Locale.ROOT);
                if (out.containsKey(key)) continue;
                Topic t = topic(key, null, ut.getConfigurationSection(id));
                if (t != null) out.put(key, t);
            }
        }
        return out;
    }

    private static Topic topic(String id, ConfigurationSection base, ConfigurationSection over) {
        String text = str(over, "text", str(base, "text", null));
        if (text == null || text.isBlank()) return null;
        String title = str(over, "title", str(base, "title", id));
        String summary = str(over, "summary", str(base, "summary", ""));
        boolean admin = over != null && over.isSet("admin") ? over.getBoolean("admin")
                : base != null && base.getBoolean("admin", false);
        return new Topic(id, title, admin, summary, text);
    }

    private static String str(ConfigurationSection s, String key, String def) {
        if (s == null) return def;
        String v = s.getString(key);
        return v == null ? def : v;
    }

    // ── Queries ──────────────────────────────────────────────────────

    public static boolean isAdmin(CommandSender sender) {
        return PermissionChecker.hasAdminArena(sender);
    }

    /** Topics this sender may read (admin topics only for admins). */
    public static List<Topic> visible(CommandSender sender) {
        boolean admin = isAdmin(sender);
        List<Topic> out = new ArrayList<>();
        for (Topic t : all().values()) if (!t.admin() || admin) out.add(t);
        return out;
    }

    public static List<String> visibleIds(CommandSender sender) {
        List<String> ids = new ArrayList<>();
        for (Topic t : visible(sender)) ids.add(t.id());
        return ids;
    }

    /** The topic when it exists and the sender may read it. */
    public static Optional<Topic> find(CommandSender sender, String id) {
        if (id == null) return Optional.empty();
        Topic t = all().get(id.toLowerCase(Locale.ROOT));
        return t != null && (!t.admin() || isAdmin(sender)) ? Optional.of(t) : Optional.empty();
    }

    // ── Output ───────────────────────────────────────────────────────

    /** MiniMessage source of the index book: player topics, then (for admins) admin topics, each a clickable link. */
    public static String indexText(List<Topic> topics) {
        StringBuilder sb = new StringBuilder();
        sb.append("<bold><dark_blue>OmniArena guides</dark_blue></bold>\n");
        sb.append("<italic><gray>Click a topic to open its book.</gray></italic>\n\n");
        appendGroup(sb, topics, false);
        if (topics.stream().anyMatch(Topic::admin)) {
            sb.append("\n<bold><dark_red>For admins</dark_red></bold>\n");
            appendGroup(sb, topics, true);
        }
        sb.append("\n<italic><gray>Or type</gray></italic> {{/oa guide <topic>}}");
        return GuideMarkup.expand(sb.toString());
    }

    private static void appendGroup(StringBuilder sb, List<Topic> topics, boolean admin) {
        for (Topic t : topics) {
            if (t.admin() != admin) continue;
            sb.append("<click:run_command:'/oa guide ").append(t.id()).append("'><hover:show_text:'")
                    .append(quote(t.summary().isBlank() ? t.title() : t.summary())).append("'><dark_green>» ")
                    .append(escape(t.title())).append("</dark_green></hover></click>\n");
        }
    }

    public static void openIndex(Player player) {
        List<String> pages = GuidePaginator.paginate(indexText(visible(player)));
        Platform.get().openBook(player, "OmniArena guides", AUTHOR, components(pages));
    }

    public static void open(Player player, Topic topic) {
        Platform.get().openBook(player, topic.title(), AUTHOR, components(topic.pages()));
    }

    /** Console (and any non-player) output: the same pages as chat lines. */
    public static void sendText(CommandSender sender, Topic topic) {
        MessageUtil.send(sender, "<gold>=== " + escape(topic.title()) + " ===");
        for (String page : topic.pages()) MessageUtil.send(sender, page);
    }

    public static void sendIndexText(CommandSender sender) {
        for (String page : GuidePaginator.paginate(indexText(visible(sender)))) MessageUtil.send(sender, page);
    }

    private static List<Component> components(List<String> pages) {
        List<Component> out = new ArrayList<>(pages.size());
        for (String p : pages) out.add(ColorUtil.parse(p));
        return out;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("<", "\\<");
    }

    private static String quote(String s) {
        return escape(s).replace("'", "\\'");
    }
}
