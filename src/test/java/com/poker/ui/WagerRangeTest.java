package com.poker.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WagerRangeTest {
    @Test void mapsEndpointsAndHugeBalancesWithoutOverflow() {
        WagerRange normal = new WagerRange(240, 9600);
        assertEquals(240, normal.amountAt(0));
        assertEquals(4920, normal.amountAt(500));
        assertEquals(9600, normal.amountAt(1000));
        WagerRange huge = new WagerRange(1, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, huge.amountAt(1000));
        assertEquals(4611686018427387904L, huge.amountAt(500));
        assertEquals(1000, huge.positionOf(Long.MAX_VALUE));
        assertEquals(0, new WagerRange(150, 150).positionOf(150));
        assertThrows(IllegalArgumentException.class, () -> normal.amountAt(1001));
    }
}
