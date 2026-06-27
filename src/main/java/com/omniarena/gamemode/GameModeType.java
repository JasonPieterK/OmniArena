package com.omniarena.gamemode;

import com.omniarena.match.GoalType;
import org.bukkit.entity.Player;

/**
 * The 16 OmniArena combat game types.
 *
 * Each type is a fully-designed mode: a display name, a one-line description,
 * a win condition ({@link GoalType} + default target), whether players respawn,
 * and the special combat rules that define it (natural regen, ring-out, team).
 * The actual loadout each type hands a player lives in {@link GameModeKits}.
 */
public enum GameModeType {

    DEATHMATCH("Deathmatch", "Free-for-all. First to the kill target wins.",
            GoalType.KILLS, 15, true, true, false, false),

    TEAM_DEATHMATCH("Team Deathmatch", "Two teams, shared kill count. Highest team wins.",
            GoalType.KILLS, 30, true, true, false, true),

    LAST_ONE_STANDING("Last One Standing", "One life each. The last player alive takes it.",
            GoalType.LAST_ALIVE, 0, false, true, false, false),

    SUMO("Sumo", "Knockback stick, no armor. Knock everyone off the platform.",
            GoalType.LAST_ALIVE, 0, false, true, true, false),

    CLASSIC_PVP("Classic PvP", "Iron armor, iron sword, a bow. Old-school 1.8 brawling.",
            GoalType.KILLS, 10, true, true, false, false),

    CRYSTAL_PVP("Crystal PvP", "Netherite, end crystals, obsidian and totems. cPvP chaos.",
            GoalType.KILLS, 5, true, true, false, false),

    MACE("Mace", "Mace, elytra and wind charges. Slam from the sky.",
            GoalType.KILLS, 10, true, true, false, false),

    SWORD_DUEL("Sword Duel", "Netherite sword, gapples, one life. Pure blade work.",
            GoalType.LAST_ALIVE, 0, false, true, false, false),

    AXE_SHIELD("Axe & Shield", "Axe and shield, one life. Time your blocks.",
            GoalType.LAST_ALIVE, 0, false, true, false, false),

    UHC("UHC", "No natural regen. Golden apples are your only heal. Last alive wins.",
            GoalType.LAST_ALIVE, 0, false, false, false, false),

    POTION_PVP("Potion PvP", "Splash health pots and buffs. Out-heal your enemy.",
            GoalType.KILLS, 10, true, true, false, false),

    NODEBUFF("NoDebuff", "Gapples, health and buff pots — no debuffs allowed.",
            GoalType.KILLS, 10, true, true, false, false),

    ARCHER("Archer", "Power bow, infinite arrows, light armor. Snipe them.",
            GoalType.KILLS, 12, true, true, false, false),

    NETHERITE_OP("Netherite OP", "Fully enchanted netherite. Maximum overkill FFA.",
            GoalType.KILLS, 20, true, true, false, false),

    SPLEEF("Spleef", "Efficiency shovel, no armor. Drop them through the floor.",
            GoalType.LAST_ALIVE, 0, false, true, true, false),

    GLADIATOR("Gladiator", "No armor, stone sword, a shield. Raw, brutal duels.",
            GoalType.KILLS, 5, true, true, false, false);

    private final String displayName;
    private final String description;
    private final GoalType goalType;
    private final int defaultGoalValue;
    private final boolean respawns;
    private final boolean naturalRegen;
    private final boolean ringOut;
    private final boolean teamBased;

    GameModeType(String displayName, String description, GoalType goalType, int defaultGoalValue,
                 boolean respawns, boolean naturalRegen, boolean ringOut, boolean teamBased) {
        this.displayName = displayName;
        this.description = description;
        this.goalType = goalType;
        this.defaultGoalValue = defaultGoalValue;
        this.respawns = respawns;
        this.naturalRegen = naturalRegen;
        this.ringOut = ringOut;
        this.teamBased = teamBased;
    }

    public String getDisplayName()    { return displayName; }
    public String getDescription()    { return description; }
    public GoalType getGoalType()     { return goalType; }
    public int getDefaultGoalValue()  { return defaultGoalValue; }
    public boolean respawns()         { return respawns; }
    public boolean naturalRegen()     { return naturalRegen; }
    public boolean ringOut()          { return ringOut; }
    public boolean isTeamBased()      { return teamBased; }

    /** Clears the player's inventory and equips this mode's full loadout. */
    public void applyLoadout(Player player) {
        GameModeKits.equip(player, this);
    }

    public static GameModeType fromString(String s) {
        if (s == null) return DEATHMATCH;
        try {
            return valueOf(s.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return DEATHMATCH;
        }
    }
}
