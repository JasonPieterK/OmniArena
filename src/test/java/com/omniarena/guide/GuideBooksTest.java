package com.omniarena.guide;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the bundled guides.yml: topics present, every page within the book limits, valid MiniMessage, no stale commands. */
class GuideBooksTest {

    private static YamlConfiguration bundled;
    private static Map<String, GuideBooks.Topic> topics;

    private static final List<String> REQUIRED = List.of("start", "arena", "play", "kits", "ranked", "tournament",
            "betting", "regen", "leaderboards", "npc", "pvp", "doctor", "config");

    @BeforeAll
    static void load() throws Exception {
        try (Reader r = new InputStreamReader(GuideBooksTest.class.getResourceAsStream("/guides.yml"), StandardCharsets.UTF_8)) {
            bundled = YamlConfiguration.loadConfiguration(r);
        }
        topics = GuideBooks.parse(bundled, new YamlConfiguration());
    }

    @Test
    void allRequiredTopicsExistInOrder() {
        assertEquals(REQUIRED, List.copyOf(topics.keySet()));
    }

    @Test
    void adminTopicsAreFlagged() {
        for (String id : List.of("start", "arena", "regen", "leaderboards", "npc", "doctor", "config")) {
            assertTrue(topics.get(id).admin(), id + " should be admin-only");
        }
        for (String id : List.of("play", "kits", "ranked", "tournament", "betting", "pvp")) {
            assertFalse(topics.get(id).admin(), id + " should be public");
        }
    }

    @Test
    void everyPageFitsAndParses() {
        MiniMessage mm = MiniMessage.miniMessage();
        for (GuideBooks.Topic t : topics.values()) {
            List<String> pages = t.pages();
            assertFalse(pages.isEmpty(), t.id());
            assertTrue(pages.size() <= GuidePaginator.MAX_PAGES, t.id());
            for (String page : pages) {
                int rows = 0;
                for (String l : page.split("\n", -1)) rows += GuidePaginator.rows(l);
                assertTrue(rows <= GuidePaginator.ROWS, t.id() + " has a page of " + rows + " rows");
                assertDoesNotThrow(() -> mm.deserialize(page), t.id() + " page does not parse");
                assertTrue(page.length() < 1000, t.id() + " page is huge");
            }
            assertFalse(t.title().isBlank(), t.id());
            assertTrue(t.title().length() <= 32, t.id() + " title too long for a book");
        }
    }

    @Test
    void noRemovedCommandsAreMentioned() {
        Pattern removed = Pattern.compile("/oa (create|delete|info|elo|ready|override|force|forcewin|match|debug|hologram|holo|wand|region|spawn"
                + "|config|access|start|stop|restart|version|website|adminkit|kit|party|enable|disable|tp|goal|chat|module|announcements)\\b"
                + "|/pvptag|/pvpglobal|/newbie|/untag|/pvpstatus|/oa queue join");
        for (GuideBooks.Topic t : topics.values()) {
            assertFalse(removed.matcher(t.text()).find(), t.id() + " mentions a removed command: " + removed.matcher(t.text()).results().findFirst().map(m -> m.group()).orElse(""));
        }
    }

    @Test
    void indexListsEveryVisibleTopicAndHidesAdminOnesFromPlayers() {
        List<GuideBooks.Topic> all = List.copyOf(topics.values());
        String admin = GuideBooks.indexText(all);
        for (String id : REQUIRED) assertTrue(admin.contains("/oa guide " + id + "'"), id);
        List<GuideBooks.Topic> players = all.stream().filter(t -> !t.admin()).toList();
        String player = GuideBooks.indexText(players);
        assertTrue(player.contains("/oa guide play'"));
        assertFalse(player.contains("/oa guide arena'"));
        assertFalse(player.toLowerCase().contains("for admins"));
        for (String page : GuidePaginator.paginate(admin)) assertDoesNotThrow(() -> MiniMessage.miniMessage().deserialize(page));
    }

    @Test
    void ownerEditsOverrideAndMissingTopicsAreBackfilled() {
        YamlConfiguration user = new YamlConfiguration();
        user.set("topics.play.title", "Custom title");
        user.set("topics.extra.text", "My own page");
        user.set("topics.extra.title", "Extra");
        Map<String, GuideBooks.Topic> merged = GuideBooks.parse(bundled, user);
        assertEquals("Custom title", merged.get("play").title());
        assertEquals(topics.get("play").text(), merged.get("play").text()); // key missing in the file: from the jar
        assertTrue(merged.containsKey("kits"));                                // topic missing in the file: from the jar
        assertEquals("My own page", merged.get("extra").text());
        assertEquals("extra", List.copyOf(merged.keySet()).get(merged.size() - 1));
    }

    @Test
    void emptyUserFileFallsBackToBundled() {
        assertEquals(topics.keySet(), GuideBooks.parse(bundled, null).keySet());
        assertTrue(GuideBooks.parse(null, null).isEmpty());
    }
}
