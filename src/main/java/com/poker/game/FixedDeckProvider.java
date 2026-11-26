package com.poker.game;

import com.poker.model.Card;
import java.util.List;

public final class FixedDeckProvider implements DeckProvider {
    private final List<Card> cards;
    public FixedDeckProvider(List<Card> cards) { this.cards = List.copyOf(cards); }
    @Override public List<Card> newDeck() { return cards; }
}

