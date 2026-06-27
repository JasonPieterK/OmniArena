package com.omniarena.arena;

public enum SpawnType {
    BATTLE,
    LOBBY,
    SPECTATOR;

    public static SpawnType fromString(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return BATTLE;
        }
    }
}
