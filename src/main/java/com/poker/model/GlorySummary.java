package com.poker.model;

import java.math.BigInteger;

/** Public, server-derived statistics. Wallet is settled chips, never the in-hand stack. */
public record GlorySummary(long walletChips, long wins, long gamesPlayed, String wealthTier,
                           String achievementTier, String nextWealth, String nextAchievement) {
    public static GlorySummary from(long chips, long wins, long games) {
        if (chips < 0 || wins < 0 || games < wins) throw new IllegalArgumentException("Invalid public statistics");
        long[] wealth = {0, 25_000, 100_000, 500_000, 2_000_000};
        String[] wealthNames = {"Đồng", "Bạc", "Vàng", "Bạch kim", "Kim cương"};
        long[] hands = {0, 20, 50, 100, 300, 1000};
        int[] rate = {0, 0, 20, 25, 30, 35};
        String[] achievement = {"Tân binh", "Đồng", "Bạc", "Vàng", "Kim cương", "Huyền thoại"};
        int w = 0, a = 0;
        for (int i = 1; i < wealth.length; i++) if (chips >= wealth[i]) w = i;
        for (int i = 1; i < hands.length; i++) if (games >= hands[i] &&
            BigInteger.valueOf(wins).multiply(BigInteger.valueOf(100)).compareTo(
                BigInteger.valueOf(games).multiply(BigInteger.valueOf(rate[i]))) >= 0) a = i;
        return new GlorySummary(chips, wins, games, wealthNames[w], achievement[a],
            w == wealth.length - 1 ? "Đã đạt bậc cao nhất" : wealthNames[w + 1] + ": " + wealth[w + 1] + " chip đã chốt",
            a == hands.length - 1 ? "Đã đạt bậc cao nhất" : achievement[a + 1] + ": ≥" + hands[a + 1] + " ván, ≥" + rate[a + 1] + "% nhận pot");
    }
}
