package com.omniarena.visual;

import com.omniarena.OmniArena;

public class VisualController {

    private final TitleManager titleManager;
    private final SoundManager soundManager;
    private final ParticleManager particleManager;
    private final BoardManager scoreboardManager;
    private final BossBarManager bossBarManager;
    private final ActionBarManager actionBarManager;
    private final HologramManager hologramManager;
    private final EffectManager effectManager;

    public VisualController(OmniArena plugin) {
        this.titleManager      = new TitleManager();
        this.soundManager      = new SoundManager();
        this.particleManager   = new ParticleManager();
        this.scoreboardManager = new BoardManager();
        this.bossBarManager    = new BossBarManager();
        this.actionBarManager  = new ActionBarManager();
        this.hologramManager   = new HologramManager(plugin);
        this.effectManager     = new EffectManager();
        this.hologramManager.start();
    }

    public TitleManager getTitleManager() { return titleManager; }
    public SoundManager getSoundManager() { return soundManager; }
    public ParticleManager getParticleManager() { return particleManager; }
    public BoardManager getScoreboardManager() { return scoreboardManager; }
    public BossBarManager getBossBarManager() { return bossBarManager; }
    public ActionBarManager getActionBarManager() { return actionBarManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public EffectManager getEffectManager() { return effectManager; }

    public void shutdown() {
        hologramManager.removeAll();
        scoreboardManager.removeAllBoards();
        bossBarManager.removeAll();
    }
}
