package com.omniarena.banner;

/** The neubrutalist palette: flat, loud colours on black. Each has a hand-picked 16-colour fallback. */
final class Tone {
    private Tone() {}

    static final int BLACK = 0x000000;
    static final int YELLOW = 0xFFE600;
    static final int PINK = 0xFF2E93;
    static final int CYAN = 0x00E5FF;
    static final int LIME = 0xA6FF00;
    static final int WHITE = 0xFFFFFF;
    static final int SHADOW = 0x6E6E6E;
    /** Dark plum: a hard logo shadow that stays clearly separate from the yellow letters. */
    static final int LOGO_SHADOW = 0x5C1A3E;

    static final int[] CYCLE = {YELLOW, PINK, CYAN, LIME};

    /** 16-colour index; hand-picked for the palette so yellow and lime stay distinguishable. */
    static int ansi16(int rgb) {
        return switch (rgb) {
            case BLACK -> 0;
            case YELLOW -> 11;
            case PINK -> 13;
            case CYAN -> 14;
            case LIME -> 10;
            case WHITE -> 15;
            case SHADOW -> 8;
            case LOGO_SHADOW -> 5;
            default -> Canvas.nearest16(rgb);
        };
    }
}
