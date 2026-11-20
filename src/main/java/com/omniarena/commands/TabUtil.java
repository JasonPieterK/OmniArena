package com.omniarena.commands;

import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.kit.AdminKit;
import com.omniarena.kit.KitManager;
import com.omniarena.kit.PlayerKit;
import com.omniarena.tournament.Tournament;
import com.omniarena.OmniArena;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Shared tab-completion helpers: every result is prefix-filtered
 * (case-insensitive via {@link StringUtil#copyPartialMatches}) and sorted.
 */
public final class TabUtil {

    public static final List<String> BOOLEANS = List.of("true", "false");

    private TabUtil() {}

    /** Prefix-filter + sort (case-insensitive). */
    public static List<String> filter(Collection<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        StringUtil.copyPartialMatches(prefix == null ? "" : prefix, options, out);
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static List<String> filter(String prefix, String... options) {
        return filter(List.of(options), prefix);
    }

    /** Filter enum constant names (upper-case, as the commands parse them). */
    public static List<String> enums(Class<? extends Enum<?>> type, String prefix) {
        List<String> names = new ArrayList<>();
        for (Enum<?> e : type.getEnumConstants()) names.add(e.name());
        return filter(names, prefix);
    }

    /** A single placeholder hint such as "&lt;amount&gt;" for free-form values. */
    public static List<String> hint(String placeholder, String typed) {
        if (typed == null || typed.isEmpty() || placeholder.toLowerCase(Locale.ROOT).startsWith(typed.toLowerCase(Locale.ROOT))) {
            return List.of(placeholder);
        }
        return List.of();
    }

    public static List<String> bools(String prefix) {
        return filter(BOOLEANS, prefix);
    }

    public static List<String> arenas(String prefix) {
        return filter(names(ArenaManager.getInstance().getAll(), Arena::getName), prefix);
    }

    public static List<String> enabledArenas(String prefix) {
        return filter(names(ArenaManager.getInstance().getEnabled(), Arena::getName), prefix);
    }

    public static List<String> players(CommandSender sender, String prefix) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player s && !s.canSee(p)) continue;
            names.add(p.getName());
        }
        return filter(names, prefix);
    }

    public static List<String> adminKits(String prefix) {
        return filter(names(KitManager.getInstance().getAllAdminKits(), AdminKit::getName), prefix);
    }

    public static List<String> playerKits(CommandSender sender, String prefix) {
        if (!(sender instanceof Player p)) return List.of();
        return filter(names(KitManager.getInstance().getPlayerKits(p.getUniqueId()), PlayerKit::getName), prefix);
    }

    public static List<String> tournaments(String prefix) {
        OmniArena plugin = OmniArena.getInstance();
        if (plugin == null || plugin.getTournamentManager() == null) return List.of();
        return filter(names(plugin.getTournamentManager().getAll(), Tournament::getName), prefix);
    }

    public static List<String> worlds(String prefix) {
        return filter(names(Bukkit.getWorlds(), org.bukkit.World::getName), prefix);
    }

    public static List<String> materials(String prefix) {
        List<String> out = new ArrayList<>();
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem()) continue;
            String n = m.name().toLowerCase(Locale.ROOT);
            if (n.startsWith(p)) out.add(n);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static <T> List<String> names(Collection<T> items, Function<T, String> f) {
        List<String> out = new ArrayList<>(items.size());
        for (T t : items) out.add(f.apply(t));
        return out;
    }

    /** Value suggestions for a schema-defined setting. */
    public static List<String> settingValue(com.omniarena.web.model.SettingDefinition def, String cur) {
        if (def == null) return hint("<value>", cur);
        if ("regen.style".equalsIgnoreCase(def.key())) {
            return enums(com.omniarena.arena.regen.RegenAnimationType.class, cur);
        }
        return switch (def.type()) {
            case BOOLEAN -> bools(cur);
            case INTEGER, DECIMAL -> hint("<number>", cur);
            case COLOR -> hint("<color>", cur);
            case LIST -> hint("<a,b,c>", cur);
            case SELECT -> hint("<option>", cur);
            default -> hint("<text>", cur);
        };
    }
}
