package com.omniarena.banner;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class BannerRendererTest {

    private static final Pattern ANSI = Pattern.compile("\u001b\\[[0-9;?]*[A-Za-z]");

    private static BannerData data() {
        return new BannerData("2.1.0", "Paper", "26.2", "25.0.1", 3, 10, 42, 812,
                List.of(new BannerData.Hook("vault", true), new BannerData.Hook("placeholderapi", true),
                        new BannerData.Hook("worldguard", false), new BannerData.Hook("luckperms", true),
                        new BannerData.Hook("decentholograms", false), new BannerData.Hook("fancyholograms", false),
                        new BannerData.Hook("citizens", true), new BannerData.Hook("multiverse", false)),
                2, "http://127.0.0.1:8123");
    }

    private static int visible(String line) { return ANSI.matcher(line).replaceAll("").length(); }

    @Test
    void fullBannerIsTallAndEveryLineHasTheSameWidth() {
        for (ColorMode m : ColorMode.values()) {
            BannerRenderer r = new BannerRenderer(m, 72);
            for (int f = 0; f < r.frameCount(); f++) {
                List<String> lines = r.full(data(), f);
                assertTrue(lines.size() >= 20, "frame " + f);
                assertEquals(BannerRenderer.FULL_HEIGHT, lines.size());
                for (String l : lines) assertEquals(72, visible(l), "mode " + m + " frame " + f + ": " + l);
            }
        }
    }

    @Test
    void noAnsiWhenColourDisabled() {
        BannerRenderer r = new BannerRenderer(ColorMode.NONE, 72);
        for (String l : r.fullFinal(data())) assertFalse(l.contains("\u001b"));
        for (String l : r.compact(data())) assertFalse(l.contains("\u001b"));
        for (String l : r.shutdown("2.1.0", 5000)) assertFalse(l.contains("\u001b"));
        assertTrue(new BannerRenderer(ColorMode.TRUECOLOR, 72).fullFinal(data()).get(3).contains("\u001b[0;1;38;2;"));
        assertTrue(new BannerRenderer(ColorMode.ANSI16, 72).fullFinal(data()).get(3).contains("\u001b["));
    }

    @Test
    void compactAndShutdownSizes() {
        BannerRenderer r = new BannerRenderer(ColorMode.ANSI256, 72);
        List<String> compact = r.compact(data());
        assertEquals(8, compact.size());
        List<String> down = r.shutdown("2.1.0", 3_700_000);
        assertEquals(6, down.size());
        for (String l : compact) assertEquals(72, visible(l));
        for (String l : down) assertEquals(72, visible(l));
    }

    @Test
    void animationChangesFramesAndEndsStatic() {
        BannerRenderer r = new BannerRenderer(ColorMode.TRUECOLOR, 72);
        assertNotEquals(r.full(data(), 0), r.full(data(), 5));
        assertNotEquals(r.full(data(), 20), r.full(data(), 21));
        assertEquals(r.fullFinal(data()), r.full(data(), r.finalFrame() + 50));
        assertEquals(r.full(data(), r.finalFrame()), r.full(data(), r.finalFrame()));
    }

    @Test
    void finalFrameContainsTheFacts() {
        String all = String.join("\n", new BannerRenderer(ColorMode.NONE, 72).fullFinal(data()));
        for (String s : new String[]{"v2.1.0", "Paper", "26.2", "25.0.1", "Vault", "PlaceholderAPI", "http://127.0.0.1:8123",
                "run /oa guide start", "812 ms", "[ VERSION ]", "[ LOADED ]", "✔", "✖", "2 repaired"}) {
            assertTrue(all.contains(s), "missing " + s);
        }
    }

    @Test
    void widerConsoleCentresAndStillAligns() {
        BannerRenderer r = new BannerRenderer(ColorMode.TRUECOLOR, 100);
        for (String l : r.fullFinal(data())) assertEquals(100, visible(l));
        assertEquals(72, new BannerRenderer(ColorMode.NONE, 10).width());
    }

    @Test
    void untrustedTextCannotInjectEscapes() {
        BannerData evil = new BannerData("1\u001b[31m", "Pa\u001bper", "26.2", "25", 1, 1, 1, 1,
                List.of(new BannerData.Hook("x\u001by", true)), 0, "http://h/\u001b[2J");
        for (String l : new BannerRenderer(ColorMode.NONE, 72).fullFinal(evil)) assertFalse(l.contains("\u001b"));
        assertFalse(BannerRenderer.plainSummary(evil).contains("\u001b[2J"));
    }

    @Test
    void plainSummaryIsOneLineWithoutColour() {
        String s = BannerRenderer.plainSummary(data());
        assertFalse(s.contains("\n"));
        assertFalse(s.contains("\u001b"));
        assertTrue(s.contains("3 arena(s)") && s.contains("vault on") && s.contains("citizens on") && s.contains("worldguard off"));
    }

    @Test
    void colourFallbacks() {
        assertEquals("38;2;255;230;0", Canvas.code(ColorMode.TRUECOLOR, Tone.YELLOW, false));
        assertEquals("103", Canvas.code(ColorMode.ANSI16, Tone.YELLOW, true));
        assertEquals("92", Canvas.code(ColorMode.ANSI16, Tone.LIME, false));
        assertEquals("40", Canvas.code(ColorMode.ANSI16, Tone.BLACK, true));
        assertEquals(16, Canvas.to256(0x000000));
        assertEquals(231, Canvas.to256(0xFFFFFF));
    }

    @Test
    void uptimeFormatting() {
        assertEquals("5s", BannerRenderer.formatUptime(5_000));
        assertEquals("1h 1m", BannerRenderer.formatUptime(3_700_000));
    }
}
