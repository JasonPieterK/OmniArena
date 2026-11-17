package com.omniarena.platform;

import com.omniarena.listener.ChatListener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Paper chat hook (AsyncChatEvent). Only loaded on Paper. */
final class PaperChatListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (ChatListener.route(event.getPlayer(), text)) event.setCancelled(true);
    }
}
