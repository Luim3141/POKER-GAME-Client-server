package com.poker.common;

public final class DealTiming {
    public static final int CARD_FLIGHT_MS = 350;
    public static final int CARD_INTERVAL_MS = 450;
    public static final int GUARD_MS = 200;
    private DealTiming() { }
    public static long duration(int cards) { return GUARD_MS + (long) cards * CARD_INTERVAL_MS; }
}
