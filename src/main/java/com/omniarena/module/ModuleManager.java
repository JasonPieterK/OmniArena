package com.omniarena.module;

import com.omniarena.OmniArena;

public class ModuleManager {

    private final KillstreakModule killstreakModule;
    private final PowerUpModule    powerUpModule;
    private final ComboModule      comboModule;

    public ModuleManager(OmniArena plugin) {
        this.killstreakModule = new KillstreakModule(plugin);
        this.powerUpModule    = new PowerUpModule(plugin);
        this.comboModule      = new ComboModule(plugin);
    }

    public KillstreakModule getKillstreakModule() { return killstreakModule; }
    public PowerUpModule getPowerUpModule() { return powerUpModule; }
    public ComboModule getComboModule() { return comboModule; }

    public void reload() {
        killstreakModule.reload();
    }

    public void shutdown() {
        comboModule.cancelAll();
    }
}
