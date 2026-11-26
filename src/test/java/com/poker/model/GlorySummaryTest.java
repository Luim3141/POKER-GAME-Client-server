package com.poker.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlorySummaryTest {
    @Test void wealthAndAchievementAreIndependentAndCanFall() {
        assertEquals("Đồng", GlorySummary.from(24_999, 0, 0).wealthTier());
        assertEquals("Bạc", GlorySummary.from(25_000, 0, 0).wealthTier());
        assertEquals("Vàng", GlorySummary.from(100_000, 0, 0).wealthTier());
        assertEquals("Bạch kim", GlorySummary.from(500_000, 0, 0).wealthTier());
        assertEquals("Kim cương", GlorySummary.from(2_000_000, 0, 0).wealthTier());
        assertEquals("Tân binh", GlorySummary.from(2_000_000, 19, 19).achievementTier());
        assertEquals("Đồng", GlorySummary.from(0, 0, 20).achievementTier());
        assertEquals("Bạc", GlorySummary.from(0, 10, 50).achievementTier());
        assertEquals("Vàng", GlorySummary.from(0, 25, 100).achievementTier());
        assertEquals("Kim cương", GlorySummary.from(0, 90, 300).achievementTier());
        assertEquals("Huyền thoại", GlorySummary.from(0, 350, 1000).achievementTier());
        assertEquals("Kim cương", GlorySummary.from(0, 350, 1001).achievementTier());
        assertEquals("Đồng", GlorySummary.from(0, 9, 50).achievementTier());
    }
}
