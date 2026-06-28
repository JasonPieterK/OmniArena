package com.omniarena.web.model;

import java.util.List;

public record LiveMatchData(
    String arena,
    String status,
    String gameMode,
    String time,
    int elapsedSeconds,
    int countdownSeconds,
    LiveTeamStats team1,
    LiveTeamStats team2,
    List<LivePlayerStat> leaderboard,
    int playersAlive,
    int totalPlayers
) {}
