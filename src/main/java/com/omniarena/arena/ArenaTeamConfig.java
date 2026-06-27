package com.omniarena.arena;

import java.util.ArrayList;
import java.util.List;

public class ArenaTeamConfig {
    private boolean enabled = false;
    private int teamCount = 2;
    private List<TeamColor> colors = new ArrayList<>(List.of(TeamColor.RED, TeamColor.BLUE));

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getTeamCount() { return teamCount; }
    public void setTeamCount(int teamCount) { this.teamCount = teamCount; }
    public List<TeamColor> getColors() { return colors; }
    public void setColors(List<TeamColor> colors) { this.colors = colors; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArenaTeamConfig)) return false;
        ArenaTeamConfig that = (ArenaTeamConfig) o;
        return enabled == that.enabled && teamCount == that.teamCount
                && java.util.Objects.equals(colors, that.colors);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(enabled, teamCount, colors);
    }

    @Override
    public String toString() {
        return "ArenaTeamConfig(enabled=" + enabled + ", teamCount=" + teamCount + ", colors=" + colors + ")";
    }
}
