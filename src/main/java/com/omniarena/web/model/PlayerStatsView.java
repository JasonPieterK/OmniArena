package com.omniarena.web.model;

public record PlayerStatsView(
    String name,
    String uuid,
    int elo,
    int wins,
    int losses,
    double kd,
    int matches,
    int totalKills,
    int totalDeaths,
    int bestStreak,
    String lastPlayed
) {}
