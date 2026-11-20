package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ArenaRegion;
import com.omniarena.arena.SpawnType;
import com.omniarena.commands.SmartErrors;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.gui.ArenaSetupMenu;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.ItemUtil;
import com.omniarena.util.MessageUtil;
import com.omniarena.util.Suggest;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * /oa setup &lt;arena&gt;: creates the arena when missing, hands out the wand and opens the setup menu.
 * Text shortcuts: region, lobby, spectator, spawn, clearspawns, finish.
 */
public class SetupCommand extends SubCommand {

    private static final List<String> STEPS = List.of("region", "lobby", "spectator", "spawn", "clearspawns", "finish");
    private final OmniArena plugin;

    public SetupCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_ARENA, "/oa setup <arena> [region|lobby|spectator|spawn|clearspawns|finish]",
                "Create and set up an arena (wand + menu)");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("player-only"));
            return;
        }
        if (args.length < 1) {
            SmartErrors.wrongArgs(sender, getUsage(), "/oa setup duel_1");
            return;
        }
        String name = args[0];
        ArenaManager am = ArenaManager.getInstance();
        Optional<Arena> existing = am.get(name);
        if (args.length >= 2) {
            // shortcuts never create arenas: a typo would silently make a new one
            if (existing.isEmpty()) { SmartErrors.unknownArena(sender, name, "/oa setup "); return; }
            SmartErrors.ensureWorldsOrExplain(player, existing.get());
            step(player, existing.get(), args[1].toLowerCase(Locale.ROOT));
            return;
        }
        Arena arena;
        if (existing.isPresent()) {
            arena = existing.get();
            SmartErrors.ensureWorldsOrExplain(player, arena);
        } else {
            if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
                MessageUtil.send(sender, "<red>Invalid arena name <yellow>" + Suggest.escape(name)
                        + "</yellow>: use 1-32 letters, numbers, '_' or '-' (no spaces). <gray>Example: " + Suggest.click("/oa setup duel_1"));
                return;
            }
            arena = am.create(name);
            MessageUtil.send(sender, ConfigManager.getInstance().getMessage("arena-created").replace("{arena}", arena.getName()));
        }
        giveWand(player);
        for (String line : SmartErrors.setupChecklist(arena)) MessageUtil.send(player, line);
        ArenaSetupMenu.open(player, arena);
    }

    private void step(Player player, Arena arena, String step) {
        switch (step) {
            case "region" -> region(player, arena);
            case "lobby" -> single(player, arena, SpawnType.LOBBY);
            case "spectator" -> single(player, arena, SpawnType.SPECTATOR);
            case "spawn" -> {
                arena.addSpawn(SpawnType.BATTLE, player.getLocation());
                ArenaManager.getInstance().save(arena);
                int n = arena.getBattleSpawns().size();
                MessageUtil.send(player, "<green>Added <yellow>battle spawn #" + n + "</yellow> to <yellow>" + arena.getName()
                        + "</yellow>. <gray>Players fill spawns 1 to " + n + " in order. Add more with " + Suggest.click("/oa setup " + arena.getName() + " spawn")
                        + "<gray>, or finish with " + Suggest.click("/oa setup " + arena.getName() + " finish") + "<gray>.");
            }
            case "clearspawns" -> {
                arena.clearSpawns(SpawnType.BATTLE);
                ArenaManager.getInstance().save(arena);
                MessageUtil.send(player, "<yellow>Cleared all battle spawns of " + arena.getName() + ".</yellow> <gray>Add new ones with "
                        + Suggest.click("/oa setup " + arena.getName() + " spawn") + "<gray>.");
            }
            case "finish" -> finish(player, arena);
            default -> SmartErrors.unknownSubcommand(player, "/oa setup " + arena.getName(), step, STEPS);
        }
    }

    private void single(Player player, Arena arena, SpawnType type) {
        arena.clearSpawns(type);
        arena.addSpawn(type, player.getLocation());
        ArenaManager.getInstance().save(arena);
        MessageUtil.send(player, "<green>Set the <yellow>" + type.name().toLowerCase(Locale.ROOT) + " spawn</yellow> of <yellow>" + arena.getName() + "</yellow> to your position.");
    }

    private void region(Player player, Arena arena) {
        ArenaManager am = ArenaManager.getInstance();
        UUID uid = player.getUniqueId();
        if (!am.hasFullSelection(uid)) {
            MessageUtil.send(player, "<red>You have not selected both corners yet. <gray>Hold the wand (get it with " + Suggest.click("/oa setup " + arena.getName())
                    + "<gray>), left-click corner 1, right-click corner 2, then run " + Suggest.click("/oa setup " + arena.getName() + " region") + "<gray> again.");
            return;
        }
        Location p1 = am.getPoint1(uid).get();
        Location p2 = am.getPoint2(uid).get();
        if (p1.getWorld() == null || p2.getWorld() == null || !p1.getWorld().equals(p2.getWorld())) {
            MessageUtil.send(player, "<red>Your two corners are in different worlds; a region must be in one world. <gray>Re-select both corners with the wand in the same world.");
            return;
        }
        arena.setRegion(new ArenaRegion(p1, p2));
        am.save(arena);
        am.clearSelection(uid);
        MessageUtil.send(player, ConfigManager.getInstance().getMessage("region-set").replace("{arena}", arena.getName()));
        final String an = arena.getName();
        plugin.getBlockRestoreModule().createSnapshot(arena,
                () -> MessageUtil.send(player, "<gray>Region snapshot saved for regeneration."),
                reason -> MessageUtil.send(player, "<red>Region saved, but the regen snapshot of <yellow>" + an + "</yellow> failed: "
                        + Suggest.escape(String.valueOf(reason)) + " <gray>Fix the cause, then run " + Suggest.click("/oa regen snapshot " + an) + "<gray>."));
    }

    private void finish(Player player, Arena arena) {
        List<String> missing = SmartErrors.setupProblems(arena);
        if (!missing.isEmpty()) {
            MessageUtil.send(player, "<red>Arena <yellow>" + arena.getName() + "</yellow> is not ready yet:");
            for (String line : SmartErrors.setupChecklist(arena)) MessageUtil.send(player, line);
            return;
        }
        if (!arena.isEnabled()) ArenaManager.getInstance().enable(arena);
        MessageUtil.send(player, "<green>Arena <yellow>" + arena.getName() + "</yellow> is set up and enabled. <gray>Players can join with "
                + Suggest.click("/oa join " + arena.getName()) + "<gray>.");
        if (!arena.getRegion().isDefined()) {
            MessageUtil.send(player, "<yellow>Tip:</yellow> <gray>no region yet, so there is no block protection or regen. Select corners with the wand and run "
                    + Suggest.click("/oa setup " + arena.getName() + " region") + "<gray>.");
        }
    }

    /** Gives the selection wand (shared with the setup menu). */
    public static void giveWand(Player player) {
        org.bukkit.Material mat = com.omniarena.listener.PlayerInteractListener.wandMaterial();
        if (player.getInventory().contains(mat)) {
            MessageUtil.send(player, "<gray>You already carry a <white>" + mat.name().toLowerCase(Locale.ROOT) + "</white>: that is your selection wand (left-click corner 1, right-click corner 2).");
            return;
        }
        ItemStack wand = ItemUtil.buildGuiItem(mat,
                "&6&lOmniArena Selection Wand",
                "&7Left-click a block: &eSet Position 1",
                "&7Right-click a block: &eSet Position 2",
                "&7Then run &b/oa setup <arena> region");
        player.getInventory().addItem(wand);
        MessageUtil.send(player, "<green>You received the OmniArena selection wand.");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (args.length == 1) {
            List<String> opts = new java.util.ArrayList<>(TabUtil.arenas(""));
            opts.addAll(TabUtil.hint("<new-name>", cur));
            return TabUtil.filter(opts, cur);
        }
        if (args.length == 2) return TabUtil.filter(STEPS, cur);
        return List.of();
    }
}
