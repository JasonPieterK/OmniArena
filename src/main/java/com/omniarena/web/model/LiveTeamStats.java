package com.omniarena.web.model;

public record LiveTeamStats(
    String name,
    int score,
    int kills,
    int deaths,
    int members
) {}
