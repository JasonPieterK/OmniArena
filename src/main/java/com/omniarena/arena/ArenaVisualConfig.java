package com.omniarena.arena;

public class ArenaVisualConfig {
    private boolean titles = true;
    private boolean sounds = true;
    private boolean particles = true;
    private boolean scoreboard = true;
    private boolean bossBar = true;
    private boolean actionBar = true;

    public boolean isTitles() { return titles; }
    public void setTitles(boolean titles) { this.titles = titles; }
    public boolean isSounds() { return sounds; }
    public void setSounds(boolean sounds) { this.sounds = sounds; }
    public boolean isParticles() { return particles; }
    public void setParticles(boolean particles) { this.particles = particles; }
    public boolean isScoreboard() { return scoreboard; }
    public void setScoreboard(boolean scoreboard) { this.scoreboard = scoreboard; }
    public boolean isBossBar() { return bossBar; }
    public void setBossBar(boolean bossBar) { this.bossBar = bossBar; }
    public boolean isActionBar() { return actionBar; }
    public void setActionBar(boolean actionBar) { this.actionBar = actionBar; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArenaVisualConfig)) return false;
        ArenaVisualConfig that = (ArenaVisualConfig) o;
        return titles == that.titles && sounds == that.sounds && particles == that.particles
                && scoreboard == that.scoreboard && bossBar == that.bossBar
                && actionBar == that.actionBar;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(titles, sounds, particles, scoreboard, bossBar, actionBar);
    }

    @Override
    public String toString() {
        return "ArenaVisualConfig(titles=" + titles + ", sounds=" + sounds + ", particles=" + particles
                + ", scoreboard=" + scoreboard + ", bossBar=" + bossBar
                + ", actionBar=" + actionBar + ")";
    }
}
