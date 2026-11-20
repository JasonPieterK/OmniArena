package com.omniarena.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuLayoutTest {

    @Test
    void spreadsUpToFiveButtonsSymmetrically() {
        assertArrayEquals(new int[]{4}, MenuLayout.centeredSlots(0, 1));
        assertArrayEquals(new int[]{3, 5}, MenuLayout.centeredSlots(0, 2));
        assertArrayEquals(new int[]{2, 4, 6}, MenuLayout.centeredSlots(0, 3));
        assertArrayEquals(new int[]{1, 3, 5, 7}, MenuLayout.centeredSlots(0, 4));
        assertArrayEquals(new int[]{0, 2, 4, 6, 8}, MenuLayout.centeredSlots(0, 5));
    }

    @Test
    void contiguousForSixToNineAndRowOffset() {
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6}, MenuLayout.centeredSlots(0, 6));
        assertArrayEquals(new int[]{18, 19, 20, 21, 22, 23, 24, 25, 26}, MenuLayout.centeredSlots(2, 9));
        assertArrayEquals(new int[]{22}, MenuLayout.centeredSlots(2, 1));
        assertEquals(9, MenuLayout.centeredSlots(0, 20).length);
        assertEquals(0, MenuLayout.centeredSlots(0, 0).length);
    }

    @Test
    void flowCentersOnlyThePartialLastRow() {
        int[] s = MenuLayout.flowSlots(11, 5);
        assertEquals(11, s.length);
        assertEquals(0, s[0]);
        assertEquals(8, s[8]);
        assertArrayEquals(new int[]{12, 14}, new int[]{s[9], s[10]});
        assertEquals(9, MenuLayout.flowSlots(99, 1).length);
    }

    @Test
    void footerSlotsAreConsistent() {
        assertEquals(45, MenuLayout.footerBack(6));
        assertEquals(49, MenuLayout.footerHelp(6));
        assertEquals(53, MenuLayout.footerClose(6));
        assertEquals(18, MenuLayout.footerBack(3));
        assertEquals(26, MenuLayout.footerClose(3));
    }
}
