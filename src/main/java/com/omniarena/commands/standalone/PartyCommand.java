package com.omniarena.commands.standalone;

import com.omniarena.OmniArena;
import com.omniarena.party.PartyManager;
import com.omniarena.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PartyCommand implements CommandExecutor, TabCompleter {

    private final OmniArena plugin;
    private final PartyManager partyManager;

    public PartyCommand(OmniArena plugin) {
        this.plugin = plugin;
        this.partyManager = plugin.getPartyManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (plugin.isStopped()) {
            com.omniarena.util.MessageUtil.send(sender, com.omniarena.config.ConfigManager.getInstance().getMessage("plugin-stopped"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "create" -> {
                partyManager.createParty(player); // PartyManager sends the feedback
            }
            case "disband" -> {
                partyManager.disbandParty(player);
            }
            case "invite" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "<red>Usage: /party invite <player>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    MessageUtil.send(player, "<red>Player not found.");
                    return true;
                }
                partyManager.invitePlayer(player, target);
            }
            case "accept" -> {
                partyManager.acceptInvite(player);
            }
            case "decline" -> {
                partyManager.declineInvite(player);
            }
            case "leave" -> {
                partyManager.leaveParty(player);
            }
            case "kick" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "<red>Usage: /party kick <player>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    MessageUtil.send(player, "<red>Player not found.");
                    return true;
                }
                partyManager.kickPlayer(player, target);
            }
            case "leader" -> {
                if (args.length < 2) {
                    MessageUtil.send(player, "<red>Usage: /party leader <player>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    MessageUtil.send(player, "<red>Player not found.");
                    return true;
                }
                partyManager.setLeader(player, target);
            }
            case "info" -> {
                com.omniarena.party.PartyManager.Party party = partyManager.getParty(player);
                if (party == null) {
                    MessageUtil.send(player, "<red>You are not in a party.");
                } else {
                    MessageUtil.send(player, "<yellow>Party Info:");
                    MessageUtil.send(player, "<gray>Leader: <yellow>" + party.getLeaderName());
                    MessageUtil.send(player, "<gray>Members: <yellow>" + party.getSize());
                }
            }
            default -> showHelp(player);
        }

        return true;
    }

    private void showHelp(Player player) {
        MessageUtil.send(player, "<gold>=== Party Commands ===");
        MessageUtil.send(player, "<yellow>/party create</yellow> <gray>- Create a new party");
        MessageUtil.send(player, "<yellow>/party disband</yellow> <gray>- Disband your party");
        MessageUtil.send(player, "<yellow>/party invite <player></yellow> <gray>- Invite a player");
        MessageUtil.send(player, "<yellow>/party accept</yellow> <gray>- Accept an invite");
        MessageUtil.send(player, "<yellow>/party decline</yellow> <gray>- Decline an invite");
        MessageUtil.send(player, "<yellow>/party leave</yellow> <gray>- Leave the party");
        MessageUtil.send(player, "<yellow>/party kick <player></yellow> <gray>- Kick a member");
        MessageUtil.send(player, "<yellow>/party leader <player></yellow> <gray>- Transfer leadership");
        MessageUtil.send(player, "<yellow>/party info</yellow> <gray>- Show party info");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cur = args.length == 0 ? "" : args[args.length - 1];
        if (!(sender instanceof Player player)) return List.of();
        if (args.length == 1) {
            return com.omniarena.commands.TabUtil.filter(
                    List.of("create", "disband", "invite", "accept", "decline", "leave", "kick", "leader", "info"), cur);
        }
        if (args.length == 2) {
            String a = args[0].toLowerCase(java.util.Locale.ROOT);
            PartyManager.Party party = partyManager.getParty(player);
            if (a.equals("invite")) {
                java.util.Set<java.util.UUID> inParty = party != null ? party.getMembers() : java.util.Set.of();
                List<String> names = new java.util.ArrayList<>();
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.equals(player) || inParty.contains(p.getUniqueId()) || !player.canSee(p)) continue;
                    names.add(p.getName());
                }
                return com.omniarena.commands.TabUtil.filter(names, cur);
            }
            if (a.equals("kick") || a.equals("leader")) {
                if (party == null) return List.of();
                List<String> names = new java.util.ArrayList<>();
                for (java.util.UUID id : party.getMembers()) {
                    if (id.equals(player.getUniqueId())) continue;
                    String n = org.bukkit.Bukkit.getOfflinePlayer(id).getName();
                    if (n != null) names.add(n);
                }
                return com.omniarena.commands.TabUtil.filter(names, cur);
            }
        }
        return List.of();
    }
}
