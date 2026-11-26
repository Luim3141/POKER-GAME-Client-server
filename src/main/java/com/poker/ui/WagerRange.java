package com.poker.ui;

import java.math.BigInteger;

/** Slider resolution is independent of chip precision; the text field remains exact. */
record WagerRange(long minimum, long maximum) {
    WagerRange {
        if (minimum < 1 || maximum < minimum) throw new IllegalArgumentException("Invalid wager range");
    }
    long amountAt(int position) {
        if (position < 0 || position > 1000) throw new IllegalArgumentException("Invalid slider position");
        return minimum + BigInteger.valueOf(maximum - minimum).multiply(BigInteger.valueOf(position))
            .divide(BigInteger.valueOf(1000)).longValueExact();
    }
    int positionOf(long amount) {
        if (amount < minimum || amount > maximum) throw new IllegalArgumentException("Invalid wager amount");
        return minimum == maximum ? 0 : BigInteger.valueOf(amount - minimum).multiply(BigInteger.valueOf(1000))
            .divide(BigInteger.valueOf(maximum - minimum)).intValueExact();
    }
}
