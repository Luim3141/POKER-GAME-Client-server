package com.poker.game;

import com.poker.model.Card;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class Deck {
    private final Deque<Card> cards;
    public Deck(DeckProvider provider) { cards = new ArrayDeque<>(provider.newDeck()); }
    public Card deal() {
        Card card = cards.pollFirst();
        if (card == null) throw new IllegalStateException("Deck is empty");
        return card;
    }
    public int remaining() { return cards.size(); }
    public List<Card> deal(int count) {
        if (count < 0 || count > cards.size()) throw new IllegalArgumentException("Invalid card count: " + count);
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> deal()).toList();
    }
}

