package com.omniarena.guide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GuidePaginatorTest {

    @Test
    void shortTextIsOnePage() {
        List<String> pages = GuidePaginator.paginate("<bold>Title</bold>\nHello world");
        assertEquals(1, pages.size());
        assertTrue(pages.get(0).contains("Hello world"));
    }

    @Test
    void emptyTextStillGivesAPage() {
        assertEquals(1, GuidePaginator.paginate("").size());
        assertEquals(1, GuidePaginator.paginate(null).size());
    }

    @Test
    void rowsCountWrappingAndIgnoreTags() {
        assertEquals(1, GuidePaginator.rows(""));
        assertEquals(1, GuidePaginator.rows("short"));
        assertEquals(1, GuidePaginator.rows("<click:run_command:'/oa menu'><hover:show_text:'a b c d e f g h i j k l m'>/oa menu</hover></click>"));
        // four 4-letter words fill exactly one 19-column row; twelve words need 3 rows
        assertEquals(1, GuidePaginator.rows("aaaa bbbb cccc dddd"));
        assertEquals(3, GuidePaginator.rows("aaaa bbbb cccc dddd eeee ffff gggg hhhh iiii jjjj kkkk llll"));
        // one word wider than a row is broken across rows
        assertEquals(2, GuidePaginator.rows("x".repeat(25)));
    }

    @Test
    void boldTextCountsWider() {
        String plain = "aaaa bbbb cccc dddd";            // 19 chars: fits one row
        assertEquals(1, GuidePaginator.rows(plain));
        assertEquals(2, GuidePaginator.rows("<bold>" + plain + "</bold>"));
    }

    @Test
    void escapedBracketIsOneVisibleCharacter() {
        assertEquals("a<b", GuidePaginator.visibleText("a\\<b"));
        assertEquals("hi there", GuidePaginator.visibleText("<red>hi</red> there"));
    }

    @Test
    void quotedGreaterThanDoesNotEndATag() {
        String line = "<hover:show_text:'a > b'>x</hover>";
        assertEquals("x", GuidePaginator.visibleText(line));
    }

    @Test
    void manualPageBreakSplits() {
        List<String> pages = GuidePaginator.paginate("one\n<page>\ntwo\n<page>\nthree");
        assertEquals(List.of("one", "two", "three"), pages);
    }

    @Test
    void fullPageOverflowsToNextPage() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= GuidePaginator.ROWS + 1; i++) sb.append("line ").append(i).append('\n');
        List<String> pages = GuidePaginator.paginate(sb.toString());
        assertEquals(2, pages.size());
        assertEquals(GuidePaginator.ROWS, pages.get(0).split("\n").length);
        assertTrue(pages.get(1).startsWith("line " + (GuidePaginator.ROWS + 1)));
    }

    @Test
    void everyPageFitsTheRowBudget() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) sb.append("This is paragraph ").append(i).append(" with some words in it to wrap around.\n\n");
        for (String page : GuidePaginator.paginate(sb.toString())) {
            int rows = 0;
            for (String l : page.split("\n", -1)) rows += GuidePaginator.rows(l);
            assertTrue(rows <= GuidePaginator.ROWS, "page too tall: " + rows);
        }
    }

    @Test
    void aSingleHugeLineIsCutAtSpaces() {
        String huge = ("word ".repeat(400)).trim();
        List<String> pages = GuidePaginator.paginate(huge);
        assertTrue(pages.size() > 1);
        for (String page : pages) assertTrue(GuidePaginator.rows(page) <= GuidePaginator.ROWS);
        // nothing lost
        assertEquals(400, String.join(" ", pages).split(" ").length);
    }

    @Test
    void noBlankLineAtTopOrBottomOfPage() {
        List<String> pages = GuidePaginator.paginate("a\n\n\n<page>\n\n\nb\n\n");
        assertEquals(List.of("a", "b"), pages);
    }

    @Test
    void pageCountIsCapped() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 150; i++) sb.append("p").append(i).append("\n<page>\n");
        assertEquals(GuidePaginator.MAX_PAGES, GuidePaginator.paginate(sb.toString()).size());
    }

    @Test
    void markupExpandsToClickAndHover() {
        String s = GuideMarkup.expand("Run {{/oa setup <arena> region}} now");
        assertTrue(s.contains("<click:suggest_command:'/oa setup '>"));
        assertTrue(s.contains("\\<arena>"));
        assertTrue(s.contains("<hover:show_text:"));
        String run = GuideMarkup.expand("{{!/oa menu}}");
        assertTrue(run.contains("<click:run_command:'/oa menu'>"));
        assertEquals("plain text", GuideMarkup.expand("plain text"));
    }

    @Test
    void markupCommandKeepsVisibleTextIntact() {
        String s = GuideMarkup.expand("{{/oa leaderboard create <id> [size]}}");
        assertEquals("/oa leaderboard create <id> [size]", GuidePaginator.visibleText(s));
    }
}
