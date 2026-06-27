package com.omniarena.match;

public enum GoalType {
    KILLS,
    LAST_ALIVE,
    OBJECTIVE,
    TIME_LIMIT;

    public static GoalType fromString(String s) {
        try {
            return valueOf(s.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return KILLS;
        }
    }
}
