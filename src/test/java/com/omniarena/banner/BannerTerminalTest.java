package com.omniarena.banner;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.omniarena.banner.BannerTerminal.Style.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BannerTerminalTest {

    @Test
    void animatesOnlyOnARealPlainTerminal() {
        Map<String, String> env = Map.of("TERM", "xterm-256color", "COLORTERM", "truecolor");
        assertEquals(FULL_ANIMATED, BannerTerminal.decide("full", env, true, 0, 0).style());
        assertEquals(FULL_STATIC, BannerTerminal.decide("full", env, false, 0, 0).style());
        assertEquals(FULL_STATIC, BannerTerminal.decide("full", Map.of("TERM", "dumb"), true, 0, 0).style());
        assertEquals(FULL_STATIC, BannerTerminal.decide("full", Map.of("P_SERVER_UUID", "abc"), true, 0, 0).style());
        assertEquals(FULL_STATIC, BannerTerminal.decide("full", Map.of("NO_COLOR", "1", "TERM", "xterm"), true, 0, 0).style());
        assertEquals(FULL_STATIC, BannerTerminal.decide("full", env, true, 24, 0).style(), "short terminal");
        assertEquals(FULL_STATIC, BannerTerminal.decide("full", env, true, 0, 60).style(), "narrow terminal");
        assertEquals(FULL_ANIMATED, BannerTerminal.decide("full", env, true, 50, 120).style());
    }

    @Test
    void configValues() {
        Map<String, String> env = Map.of("TERM", "xterm");
        assertEquals(OFF, BannerTerminal.decide("off", env, true, 0, 0).style());
        assertEquals(COMPACT, BannerTerminal.decide(" Compact ", env, true, 0, 0).style());
        assertEquals(FULL_ANIMATED, BannerTerminal.decide("nonsense", env, true, 0, 0).style());
        assertEquals(FULL_ANIMATED, BannerTerminal.decide(null, env, true, 0, 0).style());
    }

    @Test
    void colourDetection() {
        assertEquals(ColorMode.NONE, BannerTerminal.colorMode(Map.of("NO_COLOR", "1", "COLORTERM", "truecolor")));
        assertEquals(ColorMode.TRUECOLOR, BannerTerminal.colorMode(Map.of("COLORTERM", "24bit")));
        assertEquals(ColorMode.ANSI256, BannerTerminal.colorMode(Map.of("TERM", "screen-256color")));
        assertEquals(ColorMode.ANSI16, BannerTerminal.colorMode(Map.of("TERM", "linux")));
        assertEquals(ColorMode.ANSI16, BannerTerminal.colorMode(Map.of()));
        assertEquals(ColorMode.TRUECOLOR, BannerTerminal.colorMode(Map.of("P_SERVER_UUID", "x")));
        assertEquals(ColorMode.ANSI16, BannerTerminal.decide("full", Map.of("TERM", "dumb"), true, 0, 0).color());
    }
}
