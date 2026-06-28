package com.omniarena.web.model;

public record LivePlayerStat(
    String name,
    String uuid,
    int kills,
    int deaths,
    int streak,
    int combo,
    String team,
    boolean alive
) {}
