package com.omniarena.visual;

import com.omniarena.OmniArena;

public class VisualController {

    private final TitleManager titleManager;
    private final SoundManager soundManager;
    private final ParticleManager particleManager;
    private final BoardManager scoreboardManager;
    private final BossBarManager bossBarManager;
    private final ActionBarManager actionBarManager;
    private final EffectManager effectManager;

    public VisualController(OmniArena plugin) {
        this.titleManager      = new TitleManager();
        this.soundManager      = new SoundManager();
        this.particleManager   = new ParticleManager();
        this.scoreboardManager = new BoardManager();
        this.bossBarManager    = new BossBarManager();
        this.actionBarManager  = new ActionBarManager();
        this.effectManager     = new EffectManager();
        // Built-in holograms were replaced by PlaceholderAPI leaderboard placeholders + /oa leaderboard.
        if (new java.io.File(plugin.getDataFolder(), "holograms.yml").exists()) {
            plugin.getLogger().info("Old holograms.yml found and ignored: the built-in holograms were removed. Use the %omniarena_top_<kills|wins|kd|elo>_<1-10>_name% / _value% placeholders in any hologram plugin, or /oa leaderboard create (DecentHolograms/FancyHolograms).");
        }
    }

    public TitleManager getTitleManager() { return titleManager; }
    public SoundManager getSoundManager() { return soundManager; }
    public ParticleManager getParticleManager() { return particleManager; }
    public BoardManager getScoreboardManager() { return scoreboardManager; }
    public BossBarManager getBossBarManager() { return bossBarManager; }
    public ActionBarManager getActionBarManager() { return actionBarManager; }
    public EffectManager getEffectManager() { return effectManager; }

    /** Re-enables the periodic visuals after {@link #shutdown()} (used by /oa start). Nothing periodic is owned here any more. */
    public void start() {
    }

    public void shutdown() {
        scoreboardManager.removeAllBoards();
        bossBarManager.removeAll();
    }
}
