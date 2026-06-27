package com.omniarena.party;

import com.omniarena.OmniArena;
import com.omniarena.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Party system for team-based PvP
 * Supports party creation, invitations, management, and party queues
 */
public class PartyManager {

    private static PartyManager instance;

    private final OmniArena plugin;
    private final Map<UUID, Party> parties = new ConcurrentHashMap<>(); // leader UUID -> Party
    private final Map<UUID, UUID> playerToParty = new ConcurrentHashMap<>(); // player UUID -> leader UUID
    private final Map<UUID, PartyInvite> pendingInvites = new ConcurrentHashMap<>();
    private BukkitTask inviteCleanupTask;

    public PartyManager(OmniArena plugin) {
        this.plugin = plugin;
        instance = this;
    }

    public static PartyManager getInstance() { return instance; }

    public void start() {
        // Clean up expired invites every 30 seconds
        inviteCleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredInvites, 600L, 600L);
    }

    public void stop() {
        if (inviteCleanupTask != null) {
            inviteCleanupTask.cancel();
        }
    }

    /**
     * Create a new party
     */
    public Party createParty(Player leader) {
        if (isInParty(leader)) {
            MessageUtil.send(leader, "<red>You are already in a party.");
            return null;
        }

        Party party = new Party(leader.getUniqueId(), leader.getName());
        parties.put(leader.getUniqueId(), party);
        playerToParty.put(leader.getUniqueId(), leader.getUniqueId());
        
        MessageUtil.send(leader, "<green>Party created! Use <yellow>/party invite <player></yellow> to invite members.");
        return party;
    }

    /**
     * Disband a party
     */
    public boolean disbandParty(Player leader) {
        Party party = getParty(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            MessageUtil.send(leader, "<red>You are not a party leader.");
            return false;
        }

        // Notify all members
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null && p.isOnline()) {
                MessageUtil.send(p, "<red>Your party has been disbanded.");
            }
            playerToParty.remove(member);
        }

        parties.remove(leader.getUniqueId());
        MessageUtil.send(leader, "<green>Party disbanded.");
        return true;
    }

    /**
     * Invite a player to the party
     */
    public boolean invitePlayer(Player leader, Player target) {
        Party party = getParty(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            MessageUtil.send(leader, "<red>You are not a party leader.");
            return false;
        }

        if (leader.equals(target)) {
            MessageUtil.send(leader, "<red>You cannot invite yourself.");
            return false;
        }

        if (isInParty(target)) {
            MessageUtil.send(leader, "<red>That player is already in a party.");
            return false;
        }

        if (party.isFull()) {
            MessageUtil.send(leader, "<red>Your party is full.");
            return false;
        }

        // Check for existing invite
        PartyInvite existing = pendingInvites.get(target.getUniqueId());
        if (existing != null && !existing.isExpired()) {
            MessageUtil.send(leader, "<red>That player already has a pending invite.");
            return false;
        }

        // Create invite (expires in 60 seconds)
        PartyInvite invite = new PartyInvite(leader.getUniqueId(), target.getUniqueId(), party.getKitName());
        pendingInvites.put(target.getUniqueId(), invite);

        MessageUtil.send(target, "<yellow>" + leader.getName() + " <gray>has invited you to join their party.");
        MessageUtil.send(target, "<gray>Type <yellow>/party accept</yellow> to join or <yellow>/party decline</yellow> to decline.");
        MessageUtil.send(leader, "<green>Invite sent to <yellow>" + target.getName() + "</yellow>.");
        
        return true;
    }

    /**
     * Accept a party invite
     */
    public boolean acceptInvite(Player player) {
        PartyInvite invite = pendingInvites.get(player.getUniqueId());
        if (invite == null || invite.isExpired()) {
            MessageUtil.send(player, "<red>You have no pending party invites.");
            return false;
        }

        Player leader = Bukkit.getPlayer(invite.getLeaderUuid());
        if (leader == null || !leader.isOnline()) {
            MessageUtil.send(player, "<red>The party leader is no longer online.");
            pendingInvites.remove(player.getUniqueId());
            return false;
        }

        Party party = parties.get(leader.getUniqueId());
        if (party == null || party.isFull()) {
            MessageUtil.send(player, "<red>The party is no longer available.");
            pendingInvites.remove(player.getUniqueId());
            return false;
        }

        // Add to party
        party.addMember(player.getUniqueId(), player.getName());
        playerToParty.put(player.getUniqueId(), leader.getUniqueId());
        pendingInvites.remove(player.getUniqueId());

        // Notify party
        MessageUtil.send(player, "<green>You joined <yellow>" + leader.getName() + "'s</yellow> party.");
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null && p.isOnline() && !p.equals(player)) {
                MessageUtil.send(p, "<green>" + player.getName() + " <gray>joined the party.");
            }
        }

        return true;
    }

    /**
     * Decline a party invite
     */
    public boolean declineInvite(Player player) {
        PartyInvite invite = pendingInvites.remove(player.getUniqueId());
        if (invite == null) {
            MessageUtil.send(player, "<red>You have no pending party invites.");
            return false;
        }

        Player leader = Bukkit.getPlayer(invite.getLeaderUuid());
        if (leader != null && leader.isOnline()) {
            MessageUtil.send(leader, "<red>" + player.getName() + " <gray>declined your party invite.");
        }

        MessageUtil.send(player, "<green>Invite declined.");
        return true;
    }

    /**
     * Leave a party
     */
    public boolean leaveParty(Player player) {
        UUID leaderUuid = playerToParty.get(player.getUniqueId());
        if (leaderUuid == null) {
            MessageUtil.send(player, "<red>You are not in a party.");
            return false;
        }

        Party party = parties.get(leaderUuid);
        if (party == null) {
            playerToParty.remove(player.getUniqueId());
            return false;
        }

        if (party.isLeader(player.getUniqueId())) {
            return disbandParty(player);
        }

        // Remove from party
        party.removeMember(player.getUniqueId());
        playerToParty.remove(player.getUniqueId());

        // Notify party
        MessageUtil.send(player, "<green>You left the party.");
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null && p.isOnline()) {
                MessageUtil.send(p, "<red>" + player.getName() + " <gray>left the party.");
            }
        }

        return true;
    }

    /**
     * Kick a player from the party
     */
    public boolean kickPlayer(Player leader, Player target) {
        Party party = getParty(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            MessageUtil.send(leader, "<red>You are not a party leader.");
            return false;
        }

        if (!party.isMember(target.getUniqueId())) {
            MessageUtil.send(leader, "<red>That player is not in your party.");
            return false;
        }

        party.removeMember(target.getUniqueId());
        playerToParty.remove(target.getUniqueId());

        MessageUtil.send(target, "<red>You were kicked from the party.");
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null && p.isOnline()) {
                MessageUtil.send(p, "<red>" + target.getName() + " <gray>was kicked from the party.");
            }
        }

        return true;
    }

    /**
     * Set party leader
     */
    public boolean setLeader(Player currentLeader, Player newLeader) {
        Party party = getParty(currentLeader);
        if (party == null || !party.isLeader(currentLeader.getUniqueId())) {
            MessageUtil.send(currentLeader, "<red>You are not a party leader.");
            return false;
        }

        if (!party.isMember(newLeader.getUniqueId())) {
            MessageUtil.send(currentLeader, "<red>That player is not in your party.");
            return false;
        }

        UUID oldLeader = party.getLeaderUuid();
        party.setLeader(newLeader.getUniqueId(), newLeader.getName());

        // Update mappings
        parties.remove(oldLeader);
        parties.put(newLeader.getUniqueId(), party);
        playerToParty.put(oldLeader, newLeader.getUniqueId());

        MessageUtil.send(currentLeader, "<green>You transferred leadership to <yellow>" + newLeader.getName() + "</yellow>.");
        MessageUtil.send(newLeader, "<green>You are now the party leader.");
        
        for (UUID member : party.getMembers()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null && p.isOnline() && !p.equals(currentLeader) && !p.equals(newLeader)) {
                MessageUtil.send(p, "<yellow>" + newLeader.getName() + " <gray>is now the party leader.");
            }
        }

        return true;
    }

    /**
     * Get party info
     */
    public Party getParty(Player player) {
        UUID leaderUuid = playerToParty.get(player.getUniqueId());
        if (leaderUuid == null) return null;
        return parties.get(leaderUuid);
    }

    /**
     * Check if player is in a party
     */
    public boolean isInParty(Player player) {
        return playerToParty.containsKey(player.getUniqueId());
    }

    /**
     * Get party members
     */
    public List<UUID> getPartyMembers(Player player) {
        Party party = getParty(player);
        return party != null ? new ArrayList<>(party.getMembers()) : Collections.emptyList();
    }

    /**
     * Get party leader
     */
    public Player getPartyLeader(Player player) {
        Party party = getParty(player);
        if (party == null) return null;
        return Bukkit.getPlayer(party.getLeaderUuid());
    }

    /**
     * Clean up expired invites
     */
    private void cleanupExpiredInvites() {
        Iterator<Map.Entry<UUID, PartyInvite>> it = pendingInvites.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, PartyInvite> entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
            }
        }
    }

    /**
     * Get total party count
     */
    public int getTotalParties() {
        return parties.size();
    }

    // Inner classes

    public static class Party {
        private UUID leaderUuid;
        private String leaderName;
        private final Set<UUID> members = new LinkedHashSet<>();
        private String kitName;
        private static final int MAX_MEMBERS = 10;

        public Party(UUID leaderUuid, String leaderName) {
            this.leaderUuid = leaderUuid;
            this.leaderName = leaderName;
            this.members.add(leaderUuid);
        }

        public UUID getLeaderUuid() { return leaderUuid; }
        public String getLeaderName() { return leaderName; }
        public Set<UUID> getMembers() { return members; }
        public String getKitName() { return kitName; }
        public void setKitName(String kitName) { this.kitName = kitName; }

        public boolean isLeader(UUID uuid) { return leaderUuid.equals(uuid); }
        public boolean isMember(UUID uuid) { return members.contains(uuid); }
        public boolean isFull() { return members.size() >= MAX_MEMBERS; }

        public void setLeader(UUID uuid, String name) {
            this.leaderUuid = uuid;
            this.leaderName = name;
        }

        public void addMember(UUID uuid, String name) {
            members.add(uuid);
        }

        public void removeMember(UUID uuid) {
            members.remove(uuid);
        }

        public int getSize() { return members.size(); }
    }

    public static class PartyInvite {
        private final UUID leaderUuid;
        private final UUID targetUuid;
        private final String kitName;
        private final long expireTime;

        public PartyInvite(UUID leaderUuid, UUID targetUuid, String kitName) {
            this.leaderUuid = leaderUuid;
            this.targetUuid = targetUuid;
            this.kitName = kitName;
            this.expireTime = System.currentTimeMillis() + 60000; // 60 seconds
        }

        public UUID getLeaderUuid() { return leaderUuid; }
        public UUID getTargetUuid() { return targetUuid; }
        public String getKitName() { return kitName; }
        public boolean isExpired() { return System.currentTimeMillis() > expireTime; }
    }
}
