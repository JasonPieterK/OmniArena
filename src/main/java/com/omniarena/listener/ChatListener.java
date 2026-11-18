package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ChatMode;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.match.MatchTeam;
import com.omniarena.util.ColorUtil;
import com.omniarena.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Platform-independent arena chat routing. The platform layer hooks the actual chat event
 * (Paper: AsyncChatEvent, Spigot: AsyncPlayerChatEvent) and calls {@link #route}.
 */
public final class ChatListener {

    private ChatListener() {}

    /** @return true when the chat event must be cancelled because OmniArena delivered the message itself. */
    public static boolean route(Player player, String text) {
        OmniArena plugin = OmniArena.getInstance();
        if (plugin == null || plugin.isStopped()) return false;
        MatchManager mm = MatchManager.getInstance();

        if (!mm.isInArena(player) || mm.isSpectating(player)) return false;

        Optional<String> arenaName = mm.getArenaName(player);
        if (arenaName.isEmpty()) return false;

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(arenaName.get());
        if (arenaOpt.isEmpty()) return false;

        Arena arena = arenaOpt.get();
        ChatMode chatMode = arena.getChatMode();

        if (chatMode == ChatMode.GLOBAL) return false; // let it propagate normally

        Optional<Match> matchOpt = mm.getMatch(arena);
        if (matchOpt.isEmpty()) return true; // chat is arena-scoped; nothing to deliver to

        Match match = matchOpt.get();

        if (chatMode == ChatMode.TEAM) {
            MatchTeam team = match.getTeam(player.getUniqueId());
            if (team == null) {
                sendArena(match, player, "[Arena] ", text);
            } else {
                Component msg = ColorUtil.parse(team.getColor().getMiniMessageTag() + "[Team] <white>")
                        .append(safeText(player.getName() + ": ", text));
                for (UUID uid : team.getMembers()) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) MessageUtil.send(p, msg);
                }
            }
        } else { // ARENA
            sendArena(match, player, "[Arena] ", text);
        }
        return true;
    }

    /**
     * Builds "name: message" from plain text only. Player input is never parsed as MiniMessage or
     * legacy codes, so it cannot inject tags (click/hover events, colours, translatable keys).
     */
    private static Component safeText(String namePart, String text) {
        return Component.text(namePart, NamedTextColor.WHITE)
                .append(Component.text(text == null ? "" : text, NamedTextColor.GRAY));
    }

    private static void sendArena(Match match, Player sender, String prefix, String text) {
        Component msg = ColorUtil.parse("<dark_aqua>" + prefix + "<white>")
                .append(safeText(sender.getName() + ": ", text));
        for (UUID uid : match.getPlayers()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) MessageUtil.send(p, msg);
        }
        for (UUID uid : match.getSpectators()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) MessageUtil.send(p, msg);
        }
    }
}
