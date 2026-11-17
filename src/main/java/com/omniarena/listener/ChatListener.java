package com.omniarena.listener;

import com.omniarena.OmniArena;
import com.omniarena.arena.Arena;
import com.omniarena.arena.ArenaManager;
import com.omniarena.arena.ChatMode;
import com.omniarena.match.Match;
import com.omniarena.match.MatchManager;
import com.omniarena.match.MatchTeam;
import com.omniarena.util.ColorUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;
import java.util.UUID;

public class ChatListener implements Listener {

    private final OmniArena plugin;

    public ChatListener(OmniArena plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (plugin.isStopped()) return;
        Player player = event.getPlayer();
        MatchManager mm = MatchManager.getInstance();

        if (!mm.isInArena(player) || mm.isSpectating(player)) return;

        Optional<String> arenaName = mm.getArenaName(player);
        if (arenaName.isEmpty()) return;

        Optional<Arena> arenaOpt = ArenaManager.getInstance().get(arenaName.get());
        if (arenaOpt.isEmpty()) return;

        Arena arena = arenaOpt.get();
        ChatMode chatMode = arena.getChatMode();

        if (chatMode == ChatMode.GLOBAL) return; // let it propagate normally

        event.setCancelled(true);

        Optional<Match> matchOpt = mm.getMatch(arena);
        if (matchOpt.isEmpty()) return;

        Match match = matchOpt.get();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (chatMode == ChatMode.TEAM) {
            MatchTeam team = match.getTeam(player.getUniqueId());
            if (team == null) {
                sendArena(match, player, "[Arena] ", text);
            } else {
                Component msg = ColorUtil.parse(
                        team.getColor().getMiniMessageTag() + "[Team] <white>" + player.getName() + ": <gray>" + text);
                for (UUID uid : team.getMembers()) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) p.sendMessage(msg);
                }
            }
        } else { // ARENA
            sendArena(match, player, "[Arena] ", text);
        }
    }

    private void sendArena(Match match, Player sender, String prefix, String text) {
        Component msg = ColorUtil.parse(
                "<dark_aqua>" + prefix + "<white>" + sender.getName() + ": <gray>" + text);
        for (UUID uid : match.getPlayers()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.sendMessage(msg);
        }
        for (UUID uid : match.getSpectators()) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) p.sendMessage(msg);
        }
    }
}
