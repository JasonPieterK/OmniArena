package com.omniarena.gui;

/** Pure slot math for chest menus (9 columns). No Bukkit types, so it is unit tested. */
public final class MenuLayout {

    public static final int WIDTH = 9;

    private MenuLayout() {}

    /**
     * Slots for {@code count} buttons centered in one row. Up to 5 buttons are spread with one empty slot
     * between them (1 -> 4; 2 -> 3,5; 3 -> 2,4,6; 4 -> 1,3,5,7; 5 -> 0,2,4,6,8); 6..9 are contiguous and
     * centered (as close as 9 columns allow). More than 9 is clamped to 9.
     */
    public static int[] centeredSlots(int row, int count) {
        int n = Math.max(0, Math.min(WIDTH, count));
        int[] out = new int[n];
        if (n == 0) return out;
        int base = Math.max(0, row) * WIDTH;
        if (n <= 5) {
            int span = n * 2 - 1;
            int start = (WIDTH - span) / 2;
            for (int i = 0; i < n; i++) out[i] = base + start + i * 2;
        } else {
            int start = (WIDTH - n) / 2;
            for (int i = 0; i < n; i++) out[i] = base + start + i;
        }
        return out;
    }

    /** Rows needed to show {@code count} items in a flowing grid (at least 1). */
    public static int rowsFor(int count) {
        return Math.max(1, (count + WIDTH - 1) / WIDTH);
    }

    /**
     * Slots for {@code count} items flowing over rows starting at row 0: full rows left to right, the last
     * partial row centered. At most {@code maxRows * 9} slots are returned.
     */
    public static int[] flowSlots(int count, int maxRows) {
        int n = Math.max(0, Math.min(count, Math.max(0, maxRows) * WIDTH));
        int[] out = new int[n];
        int idx = 0;
        int row = 0;
        while (idx < n) {
            int inRow = Math.min(WIDTH, n - idx);
            if (inRow == WIDTH) {
                for (int c = 0; c < WIDTH; c++) out[idx++] = row * WIDTH + c;
            } else {
                for (int s : centeredSlots(row, inRow)) out[idx++] = s;
            }
            row++;
        }
        return out;
    }

    /** First slot of the last row (footer back button). */
    public static int footerBack(int rows)  { return rows * WIDTH - WIDTH; }
    /** Center slot of the last row (footer help button). */
    public static int footerHelp(int rows)  { return rows * WIDTH - WIDTH + 4; }
    /** Last slot of the last row (footer close button). */
    public static int footerClose(int rows) { return rows * WIDTH - 1; }
    /** Footer slot for a "previous page" arrow. */
    public static int footerPrev(int rows)  { return rows * WIDTH - WIDTH + 3; }
    /** Footer slot for a "next page" arrow. */
    public static int footerNext(int rows)  { return rows * WIDTH - WIDTH + 5; }
}
