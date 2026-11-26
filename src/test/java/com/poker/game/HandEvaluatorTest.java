package com.poker.game;

import com.poker.model.Card;
import com.poker.model.Rank;
import com.poker.model.Suit;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HandEvaluatorTest {
    private final HandEvaluator evaluator = new HandEvaluator();

    @Test void recognizesEveryCategory() {
        assertCategory(HandCategory.ROYAL_FLUSH, "AS KS QS JS TS 2D 3C");
        assertCategory(HandCategory.STRAIGHT_FLUSH, "9S 8S 7S 6S 5S 2D 3C");
        assertCategory(HandCategory.FOUR_OF_A_KIND, "AS AH AD AC 2S 3D 4C");
        assertCategory(HandCategory.FULL_HOUSE, "KS KH KD 2S 2D 3C 4C");
        assertCategory(HandCategory.FLUSH, "AS JS 8S 4S 2S KD 3C");
        assertCategory(HandCategory.STRAIGHT, "9S 8H 7D 6C 5S 2D 3C");
        assertCategory(HandCategory.THREE_OF_A_KIND, "QS QH QD 8C 5S 2D 3C");
        assertCategory(HandCategory.TWO_PAIR, "AS AH KD KC 5S 2D 3C");
        assertCategory(HandCategory.ONE_PAIR, "AS AH QD 9C 5S 2D 3C");
        assertCategory(HandCategory.HIGH_CARD, "AS QH 9D 7C 5S 2D 3C");
    }

    @Test void handlesWheelKickersBoardPlayAndExactTie() {
        assertEquals(List.of(5), evaluator.evaluate(cards("AS 2H 3D 4C 5S KD QC")).tieBreakRanks());
        assertTrue(evaluator.evaluate(cards("AS AH KD QC JS 8D 2C"))
            .compareTo(evaluator.evaluate(cards("AS AH QD JC TS 8D 2C"))) > 0);
        var board = cards("AS KS QS JS TS");
        assertEquals(evaluator.evaluate(join(board, cards("2D 3C"))), evaluator.evaluate(join(board, cards("9D 8C"))));
    }

    private void assertCategory(HandCategory expected, String value) {
        assertEquals(expected, evaluator.evaluate(cards(value)).category());
    }

    private static List<Card> join(List<Card> first, List<Card> second) {
        var result = new java.util.ArrayList<>(first); result.addAll(second); return result;
    }

    private static List<Card> cards(String value) {
        return java.util.Arrays.stream(value.split(" ")).map(HandEvaluatorTest::card).toList();
    }

    private static Card card(String value) {
        Rank rank = switch (value.charAt(0)) {
            case '2' -> Rank.TWO; case '3' -> Rank.THREE; case '4' -> Rank.FOUR; case '5' -> Rank.FIVE;
            case '6' -> Rank.SIX; case '7' -> Rank.SEVEN; case '8' -> Rank.EIGHT; case '9' -> Rank.NINE;
            case 'T' -> Rank.TEN; case 'J' -> Rank.JACK; case 'Q' -> Rank.QUEEN; case 'K' -> Rank.KING; case 'A' -> Rank.ACE;
            default -> throw new IllegalArgumentException(value);
        };
        Suit suit = switch (value.charAt(1)) {
            case 'C' -> Suit.CLUBS; case 'D' -> Suit.DIAMONDS; case 'H' -> Suit.HEARTS; case 'S' -> Suit.SPADES;
            default -> throw new IllegalArgumentException(value);
        };
        return new Card(rank, suit);
    }
}

