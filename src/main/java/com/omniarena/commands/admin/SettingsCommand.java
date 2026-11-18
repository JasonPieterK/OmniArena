package com.omniarena.commands.admin;

import com.omniarena.commands.TabUtil;
import com.omniarena.arena.regen.RegenAnimationType;
import com.omniarena.commands.SubCommand;
import com.omniarena.config.ConfigManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.ColorUtil;
import com.omniarena.util.MessageUtil;
import com.omniarena.web.ConfigSchemaService;
import com.omniarena.web.model.SettingDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SettingsCommand extends SubCommand {

    private final ConfigSchemaService schema;
    private final List<SettingDefinition> settings;
    private final Map<String, SettingDefinition> byKey = new LinkedHashMap<>();

    public SettingsCommand() {
        super(PermissionChecker.ADMIN_RELOAD, "/oa settings [set <key> <value>]", "Open clickable plugin settings");
        ConfigSchemaService tmpSchema = null;
        List<SettingDefinition> tmpSettings = new ArrayList<>();
        try {
            tmpSchema = new ConfigSchemaService(com.omniarena.OmniArena.getInstance());
            tmpSettings = new ArrayList<>(tmpSchema.settings());
            tmpSettings.forEach(setting -> byKey.put(setting.key().toLowerCase(Locale.ROOT), setting));
        } catch (Exception e) {
            com.omniarena.OmniArena.getInstance().getLogger().log(java.util.logging.Level.SEVERE, "Failed to initialize /oa settings (" + e + "): the settings schema could not be built, so /oa settings will show no keys. Fix: check config.yml for YAML errors and run /oa reload.", e);
        }
        this.schema = tmpSchema;
        this.settings = tmpSettings;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Players get the interactive chest GUI; console keeps the text list.
            if (sender instanceof org.bukkit.entity.Player player) {
                com.omniarena.settings.SettingsGUI.openCategories(player);
            } else {
                showSettings(sender);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("help")) {
            showSettings(sender);
            return;
        }
        if (args[0].equalsIgnoreCase("set")) {
            setValue(sender, args);
            return;
        }
        MessageUtil.send(sender, "<red>Unknown action <yellow>" + args[0] + "</yellow>. <gray>Usage: <white>/oa settings</white> (open the list) or <white>/oa settings set <key> <value></white>.");
    }

    private void showSettings(CommandSender sender) {
        MessageUtil.send(sender, "<gold>=== OmniArena Settings ===");
        MessageUtil.send(sender, "<gray>Hover setting names for descriptions. Click values to change.");
        String group = "";
        for (SettingDefinition entry : settings) {
            if (!entry.group().equals(group)) {
                group = entry.group();
                MessageUtil.send(sender, "");
                MessageUtil.send(sender, "<aqua>" + group);
            }
            com.omniarena.util.MessageUtil.send(sender, settingLine(entry));
        }
    }

    private Component settingLine(SettingDefinition entry) {
        Object current = currentValue(entry);
        Component name = ColorUtil.parse("<gray>- <yellow>" + entry.label() + "</yellow> <dark_gray>(" + entry.key() + ")")
                .hoverEvent(HoverEvent.showText(ColorUtil.parse("<white>" + entry.description())));
        return switch (entry.type()) {
            case BOOLEAN -> name.append(booleanButtons(entry, toBoolean(current, entry.defaultValue())));
            case INTEGER, DECIMAL, STRING, COLOR, LIST, SELECT -> {
                if ("regen.animation.type".equalsIgnoreCase(entry.key())) {
                    yield name.append(styleButton(entry, String.valueOf(current != null ? current : entry.defaultValue())));
                }
                yield name.append(suggestButton(entry, stringifyCurrent(current)));
            }
        };
    }

    private Component booleanButtons(SettingDefinition entry, boolean current) {
        Component yes = ColorUtil.parse(current ? "<green>[YES]" : "<dark_gray>[YES]")
                .hoverEvent(HoverEvent.showText(ColorUtil.parse("<gray>Click to enable " + entry.key())))
                .clickEvent(ClickEvent.runCommand("/oa settings set " + entry.key() + " true"));
        Component no = ColorUtil.parse(!current ? " <red>[NO]" : " <dark_gray>[NO]")
                .hoverEvent(HoverEvent.showText(ColorUtil.parse("<gray>Click to disable " + entry.key())))
                .clickEvent(ClickEvent.runCommand("/oa settings set " + entry.key() + " false"));
        return yes.append(no);
    }

    private Component styleButton(SettingDefinition entry, String current) {
        RegenAnimationType type = RegenAnimationType.fromConfig(current);
        RegenAnimationType[] values = RegenAnimationType.values();
        RegenAnimationType next = values[(type.ordinal() + 1) % values.length];
        return ColorUtil.parse("<white>" + type.name() + " <dark_gray>[cycle]")
                .hoverEvent(HoverEvent.showText(ColorUtil.parse("<gray>Click to change to <yellow>" + next.name())))
                .clickEvent(ClickEvent.runCommand("/oa settings set " + entry.key() + " " + next.name()));
    }

    private Component suggestButton(SettingDefinition entry, String current) {
        return ColorUtil.parse("<white>" + current + " <dark_gray>[edit]")
                .hoverEvent(HoverEvent.showText(ColorUtil.parse("<gray>Click, then type new value.")))
                .clickEvent(ClickEvent.suggestCommand("/oa settings set " + entry.key() + " " + current));
    }

    private void setValue(CommandSender sender, String[] args) {
        if (args.length < 3) {
            MessageUtil.send(sender, "<red>Missing key or value. <gray>Usage: <yellow>/oa settings set <key> <value></yellow> <gray>- click a setting in <white>/oa settings</white> to pre-fill it.");
            return;
        }
        String key = args[1];
        SettingDefinition entry = byKey.get(key.toLowerCase(Locale.ROOT));
        if (entry == null) {
            MessageUtil.send(sender, "<red>Unknown setting <yellow>" + key + "</yellow>. <gray>See all keys with <white>/oa settings</white> or press TAB after <white>/oa settings set</white>.");
            return;
        }
        String raw = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Object value = parseValue(entry, raw, sender);
        if (value == null) return;
        ConfigManager.getInstance().setValue(entry.key(), value);
        MessageUtil.send(sender, "<green>Set <yellow>" + entry.key() + "</yellow> to <white>" + stringifyCurrent(value) + "</white>.");
        com.omniarena.util.MessageUtil.send(sender, settingLine(entry));
    }

    private Object parseValue(SettingDefinition entry, String raw, CommandSender sender) {
        try {
            return switch (entry.type()) {
                case BOOLEAN -> {
                    String v = raw.trim().toLowerCase(Locale.ROOT);
                    if (v.equals("true") || v.equals("yes") || v.equals("on")) yield Boolean.TRUE;
                    if (v.equals("false") || v.equals("no") || v.equals("off")) yield Boolean.FALSE;
                    MessageUtil.send(sender, "<red>Expected true or false for <yellow>" + entry.key() + "</yellow>, got <yellow>" + raw + "</yellow>. <gray>Use true/false (or yes/no, on/off): <white>/oa settings set " + entry.key() + " true</white>");
                    yield null;
                }
                case INTEGER -> Integer.parseInt(raw.trim());
                case DECIMAL -> Double.parseDouble(raw.trim());
                case LIST -> Arrays.stream(raw.split("\\R|,"))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
                case COLOR, SELECT, STRING -> {
                    if ("regen.animation.type".equalsIgnoreCase(entry.key())) {
                        RegenAnimationType parsed = RegenAnimationType.parse(raw);
                        if (parsed != null) yield parsed.name();
                        {
                            MessageUtil.send(sender, "<red>Unknown regen style <yellow>" + raw + "</yellow>. <gray>Valid: <white>" + Arrays.stream(RegenAnimationType.values()).map(Enum::name).collect(java.util.stream.Collectors.joining(", ")) + "</white> (see <white>/oa help regenstyles</white>).");
                            yield null;
                        }
                    }
                    yield raw;
                }
            };
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "<red>Expected a number for <yellow>" + entry.key() + "</yellow>, got <yellow>" + raw + "</yellow>. <gray>Use digits only, e.g. <white>/oa settings set " + entry.key() + " 10</white>.");
            return null;
        }
    }

    private Object currentValue(SettingDefinition entry) {
        Object value = ConfigManager.getInstance().getValue(entry.key());
        return value != null ? value : entry.defaultValue();
    }

    private boolean toBoolean(Object value, String fallback) {
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value != null ? String.valueOf(value) : fallback);
    }

    private String stringifyCurrent(Object value) {
        if (value == null) return "";
        if (value instanceof List<?> list) return String.join(", ", list.stream().map(String::valueOf).toList());
        return String.valueOf(value);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(List.of("set", "help"), cur);
        if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
            if (args.length == 2) return TabUtil.filter(settings.stream().map(SettingDefinition::key).toList(), cur);
            if (args.length == 3) return TabUtil.settingValue(byKey.get(args[1].toLowerCase(Locale.ROOT)), cur);
        }
        return List.of();
    }
}
