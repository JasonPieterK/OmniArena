package com.omniarena.match;

import com.omniarena.arena.TeamColor;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class MatchTeam {
    private final String name;
    private final TeamColor color;
    private final Set<UUID> members = new HashSet<>();
    private int kills;
    private int score;

    public MatchTeam(String name, TeamColor color) {
        this.name = name;
        this.color = color;
    }

    public String getName() { return name; }
    public TeamColor getColor() { return color; }
    public Set<UUID> getMembers() { return members; }
    public int getKills() { return kills; }
    public void setKills(int kills) { this.kills = kills; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public void addMember(UUID uuid)    { members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }
    public boolean hasMember(UUID uuid) { return members.contains(uuid); }
    public int size() { return members.size(); }
    public void addKill()  { kills++; score++; }
    public void addScore() { score++; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchTeam)) return false;
        MatchTeam that = (MatchTeam) o;
        return kills == that.kills && score == that.score
                && Objects.equals(name, that.name)
                && color == that.color
                && Objects.equals(members, that.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, color, members, kills, score);
    }

    @Override
    public String toString() {
        return "MatchTeam(name=" + name + ", color=" + color + ", kills=" + kills + ", score=" + score + ")";
    }
}
