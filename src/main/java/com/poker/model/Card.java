package com.poker.model;

import java.util.Objects;

public record Card(Rank rank, Suit suit) {
    public Card {
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(suit, "suit");
    }
}

