package com.poker.game;

import com.poker.model.Card;
import com.poker.model.Rank;
import com.poker.model.Suit;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RandomDeckProvider implements DeckProvider {
    private final SecureRandom random = new SecureRandom();

    @Override public List<Card> newDeck() {
        List<Card> cards = new ArrayList<>(52);
        for (Suit suit : Suit.values()) for (Rank rank : Rank.values()) cards.add(new Card(rank, suit));
        Collections.shuffle(cards, random);
        return cards;
    }
}

