package com.poker.game;

import org.junit.jupiter.api.Test;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class DeckTest {
    @Test void createsExactly52UniqueCards() {
        Deck deck = new Deck(new RandomDeckProvider());
        var cards = deck.deal(52);
        assertEquals(52, new HashSet<>(cards).size());
        assertEquals(0, deck.remaining());
        assertThrows(IllegalStateException.class, deck::deal);
    }
}

