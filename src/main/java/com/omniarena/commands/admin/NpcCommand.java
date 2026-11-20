package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.commands.SmartErrors;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.integration.IntegrationManager;
import com.omniarena.integration.NpcAction;
import com.omniarena.integration.NpcManager;
import com.omniarena.kit.AdminKit;
import com.omniarena.kit.KitManager;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import com.omniarena.util.Suggest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * /oa npc &lt;create|remove|list&gt; ...: Citizens NPCs whose right-click opens the menu, joins an arena,
 * joins the queue or spectates. Needs the Citizens plugin (integrations.citizens).
 */
public class NpcCommand extends SubCommand {

    private static final List<String> ACTIONS = List.of("create", "remove", "list");
    private static final List<String> NPC_ACTIONS = Arrays.stream(NpcAction.values()).map(NpcAction::id).toList();

    private final OmniArena plugin;

    public NpcCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_NPC, "/oa npc <create|remove|list> <menu|join <arena>|queue [kit]|spectate [arena]>",
                "Citizens NPCs that open the menu, join, queue or spectate");
        this.plugin = plugin;
    }

    private static String msg(String key, String... kv) {
        String m = ConfigManager.getInstance().getMessage(key);
        for (int i = 0; i + 1 < kv.length; i += 2) m = m.replace("{" + kv[i] + "}", kv[i + 1]);
        return m;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        IntegrationManager im = plugin.getIntegrationManager();
        if (im == null) return;
        if (args.length < 1) {
            SmartErrors.wrongArgs(sender, getUsage(), "/oa npc create join duel_1");
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (!ACTIONS.contains(action)) {
            SmartErrors.unknownSubcommand(sender, "/oa npc", args[0], ACTIONS);
            return;
        }
        NpcManager nm = im.npcs();
        switch (action) {
            case "list" -> list(sender, nm);
            case "remove" -> remove(sender, nm, args);
            default -> create(sender, nm, args);
        }
    }

    private void create(CommandSender sender, NpcManager nm, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        if (args.length < 2) {
            SmartErrors.wrongArgs(sender, "/oa npc create <menu|join <arena>|queue [kit]|spectate [arena]>", "/oa npc create menu");
            return;
        }
        Optional<NpcAction> parsed = NpcAction.parse(args[1]);
        if (parsed.isEmpty()) {
            SmartErrors.unknownSubcommand(sender, "/oa npc create", args[1], NPC_ACTIONS);
            return;
        }
        NpcAction act = parsed.get();
        String arg = args.length >= 3 ? args[2] : null;
        if (!act.accepts(arg)) {
            SmartErrors.wrongArgs(sender, "/oa npc create " + act.id() + (act.takesArg() ? (act.argRequired() ? " <arena>" : " [arena|kit]") : ""),
                    act.argRequired() ? "/oa npc create " + act.id() + " duel_1" : "/oa npc create " + act.id());
            return;
        }
        if (!nm.available()) {
            MessageUtil.send(sender, msg("npc-no-citizens"));
            return;
        }
        if (arg != null && (act == NpcAction.JOIN || act == NpcAction.SPECTATE)) {
            if (SmartErrors.arena(sender, arg, "/oa npc create " + act.id() + " ").isEmpty()) return;
        }
        if (arg != null && act == NpcAction.QUEUE && !arg.equalsIgnoreCase("ranked")) {
            Optional<AdminKit> kit = KitManager.getInstance().getAdminKit(arg);
            if (kit.isEmpty()) {
                String kits = Stream.concat(Stream.of("ranked"), KitManager.getInstance().getAllAdminKits().stream().map(AdminKit::getName))
                        .collect(java.util.stream.Collectors.joining(", "));
                MessageUtil.send(sender, msg("npc-unknown-kit", "kit", Suggest.escape(arg), "kits", Suggest.escape(kits)));
                return;
            }
        }
        NpcManager.CreateResult r = nm.create(player.getLocation(), act, arg);
        switch (r.status()) {
            case OK -> MessageUtil.send(sender, msg("npc-created", "id", String.valueOf(r.id()), "action", act.id(),
                    "arg", arg == null ? "" : " " + Suggest.escape(arg)));
            case NO_CITIZENS -> MessageUtil.send(sender, msg("npc-no-citizens"));
            default -> MessageUtil.send(sender, msg("npc-failed"));
        }
    }

    private void remove(CommandSender sender, NpcManager nm, String[] args) {
        if (args.length < 2) {
            SmartErrors.wrongArgs(sender, "/oa npc remove <id>", "/oa npc list");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            SmartErrors.wrongArgs(sender, "/oa npc remove <id> (the number shown by /oa npc list)", "/oa npc list");
            return;
        }
        switch (nm.remove(id)) {
            case REMOVED -> MessageUtil.send(sender, msg("npc-removed", "id", String.valueOf(id)));
            case NO_CITIZENS -> MessageUtil.send(sender, msg("npc-no-citizens"));
            case NOT_BOUND -> MessageUtil.send(sender, msg("npc-not-found", "id", String.valueOf(id), "list", Suggest.click("/oa npc list")));
        }
    }

    private void list(CommandSender sender, NpcManager nm) {
        List<NpcManager.Entry> all = nm.list();
        if (all.isEmpty()) {
            MessageUtil.send(sender, msg("npc-list-empty", "create", Suggest.click("/oa npc create menu")));
            return;
        }
        MessageUtil.send(sender, msg("npc-list-header", "count", String.valueOf(all.size())));
        for (NpcManager.Entry e : all) {
            MessageUtil.send(sender, msg("npc-list-line", "id", String.valueOf(e.id()), "action", e.action().id(),
                    "arg", e.arg() == null ? "" : " " + Suggest.escape(e.arg()),
                    "state", e.npcExists() ? "<green>ok" : "<red>missing in Citizens"));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) return TabUtil.filter(ACTIONS, cur);
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) return TabUtil.filter(NPC_ACTIONS, cur);
        if (args.length == 2 && args[0].equalsIgnoreCase("remove") && plugin.getIntegrationManager() != null) {
            return TabUtil.filter(plugin.getIntegrationManager().npcs().list().stream().map(e -> String.valueOf(e.id())).toList(), cur);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            Optional<NpcAction> a = NpcAction.parse(args[1]);
            if (a.isPresent() && (a.get() == NpcAction.JOIN || a.get() == NpcAction.SPECTATE)) return TabUtil.arenas(cur);
            if (a.isPresent() && a.get() == NpcAction.QUEUE) {
                return TabUtil.filter(Stream.concat(Stream.of("ranked"),
                        KitManager.getInstance().getAllAdminKits().stream().map(AdminKit::getName)).toList(), cur);
            }
        }
        return List.of();
    }
}
