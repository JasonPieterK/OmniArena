package com.omniarena.commands.admin;

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
            com.omniarena.OmniArena.getInstance().getLogger().severe("Failed to initialize SettingsCommand: " + e.getMessage());
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
        MessageUtil.send(sender, "<red>Usage: <yellow>/oa settings</yellow> or <yellow>/oa settings set <key> <value></yellow>");
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
            sender.sendMessage(settingLine(entry));
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
            MessageUtil.send(sender, "<red>Usage: <yellow>/oa settings set <key> <value>");
            return;
        }
        String key = args[1];
        SettingDefinition entry = byKey.get(key.toLowerCase(Locale.ROOT));
        if (entry == null) {
            MessageUtil.send(sender, "<red>Unknown setting: <yellow>" + key);
            return;
        }
        String raw = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        Object value = parseValue(entry, raw, sender);
        if (value == null) return;
        ConfigManager.getInstance().setValue(entry.key(), value);
        MessageUtil.send(sender, "<green>Set <yellow>" + entry.key() + "</yellow> to <white>" + stringifyCurrent(value) + "</white>.");
        sender.sendMessage(settingLine(entry));
    }

    private Object parseValue(SettingDefinition entry, String raw, CommandSender sender) {
        try {
            return switch (entry.type()) {
                case BOOLEAN -> raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("yes") || raw.equalsIgnoreCase("on");
                case INTEGER -> Integer.parseInt(raw);
                case DECIMAL -> Double.parseDouble(raw);
                case LIST -> Arrays.stream(raw.split("\\R|,"))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
                case COLOR, SELECT, STRING -> raw;
            };
        } catch (NumberFormatException e) {
            MessageUtil.send(sender, "<red>Expected number for <yellow>" + entry.key() + "</yellow>.");
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
        if (args.length == 1) return List.of("set", "help").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return settings.stream().map(SettingDefinition::key).filter(s -> s.startsWith(args[1])).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            SettingDefinition entry = byKey.get(args[1].toLowerCase(Locale.ROOT));
            if (entry == null) return List.of();
            if (entry.type() == SettingDefinition.SettingType.BOOLEAN) {
                return List.of("true", "false").stream().filter(s -> s.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
            }
            if ("regen.animation.type".equalsIgnoreCase(entry.key())) {
                return Arrays.stream(RegenAnimationType.values()).map(Enum::name)
                        .filter(s -> s.startsWith(args[2].toUpperCase(Locale.ROOT))).toList();
            }
        }
        return List.of();
    }
}
