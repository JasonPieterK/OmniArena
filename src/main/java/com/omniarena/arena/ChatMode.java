package com.omniarena.arena;

public enum ChatMode {
    ARENA,
    TEAM,
    GLOBAL;

    public static ChatMode fromString(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ARENA;
        }
    }
}
