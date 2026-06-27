package com.omniarena.match;

public enum RespawnType {
    INSTANT,
    DELAYED,
    WAVE;

    public static RespawnType fromString(String s) {
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INSTANT;
        }
    }
}
