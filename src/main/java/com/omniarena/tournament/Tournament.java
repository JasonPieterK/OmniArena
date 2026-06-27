package com.omniarena.tournament;

import java.util.*;

public class Tournament {

    private final String name;
    private final String arenaName;
    private final int maxPlayers;
    private final UUID creatorUuid;
    private final List<UUID> participants = new ArrayList<>();
    private TournamentState state = TournamentState.REGISTRATION;
    private TournamentBracket bracket;
    private double prizeFirst  = 0;
    private double prizeSecond = 0;
    private double prizeThird  = 0;
    private final long createdAt = System.currentTimeMillis();

    public Tournament(String name, String arenaName, int maxPlayers, UUID creatorUuid) {
        this.name = name;
        this.arenaName = arenaName;
        this.maxPlayers = maxPlayers;
        this.creatorUuid = creatorUuid;
    }

    public String getName()       { return name; }
    public String getArenaName()  { return arenaName; }
    public int getMaxPlayers()    { return maxPlayers; }
    public UUID getCreatorUuid()  { return creatorUuid; }
    public List<UUID> getParticipants() { return participants; }
    public TournamentState getState()   { return state; }
    public void setState(TournamentState state) { this.state = state; }
    public TournamentBracket getBracket() { return bracket; }
    public void setBracket(TournamentBracket bracket) { this.bracket = bracket; }
    public double getPrizeFirst()  { return prizeFirst; }
    public double getPrizeSecond() { return prizeSecond; }
    public double getPrizeThird()  { return prizeThird; }
    public void setPrizeFirst(double v)  { prizeFirst = v; }
    public void setPrizeSecond(double v) { prizeSecond = v; }
    public void setPrizeThird(double v)  { prizeThird = v; }
    public long getCreatedAt()     { return createdAt; }

    public boolean isFull()   { return participants.size() >= maxPlayers; }
    public boolean hasPlayer(UUID uuid) { return participants.contains(uuid); }

    public boolean addParticipant(UUID uuid) {
        if (state != TournamentState.REGISTRATION) return false;
        if (isFull()) return false;
        if (hasPlayer(uuid)) return false;
        participants.add(uuid);
        return true;
    }

    public boolean removeParticipant(UUID uuid) {
        if (state != TournamentState.REGISTRATION) return false;
        return participants.remove(uuid);
    }
}
