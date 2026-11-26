package com.poker.ui;

import com.poker.model.Card;
import com.poker.model.Suit;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/** Native vector deck: sharp at any screen scale, no downloaded assets or card-font dependency. */
final class PlayingCardView extends Canvas {
    PlayingCardView(Card card, double width) {
        super(width, width * 1.4);
        setAccessibleText(card == null ? "Bài úp" : card.rank() + " " + card.suit());
        setMouseTransparent(true);
        setEffect(new javafx.scene.effect.DropShadow(4, 0, 2, Color.color(0, 0, 0, .32)));
        GraphicsContext g = getGraphicsContext2D();
        g.scale(width / 60, width / 60);
        g.setFill(Color.web("#faf7ee")); g.fillRoundRect(0, 0, 60, 84, 7, 7);
        g.setStroke(Color.web("#d4cebd")); g.strokeRoundRect(.5, .5, 59, 83, 7, 7);
        if (card == null) { back(g); return; }
        Color ink = Color.web(card.suit() == Suit.HEARTS || card.suit() == Suit.DIAMONDS ? "#ac3039" : "#202924");
        g.setFill(ink);
        String rank = switch (card.rank()) {
            case ACE -> "A"; case KING -> "K"; case QUEEN -> "Q"; case JACK -> "J";
            default -> Integer.toString(card.rank().value());
        };
        String suit = switch (card.suit()) { case SPADES -> "♠"; case HEARTS -> "♥"; case DIAMONDS -> "♦"; case CLUBS -> "♣"; };
        corner(g, rank, suit); g.save(); g.translate(60, 84); g.rotate(180); corner(g, rank, suit); g.restore();
        int value = card.rank().value();
        if (value >= 11 && value <= 13) court(g, rank, suit, ink);
        else {
            double[][] pips = switch (value) {
                case 14 -> new double[][]{{30, 45}};
                case 2 -> new double[][]{{30, 25}, {30, 62}};
                case 3 -> new double[][]{{30, 25}, {30, 43}, {30, 62}};
                case 4 -> new double[][]{{21,25},{39,25},{21,62},{39,62}};
                case 5 -> new double[][]{{21,25},{39,25},{30,43},{21,62},{39,62}};
                case 6 -> new double[][]{{21,25},{39,25},{21,43},{39,43},{21,62},{39,62}};
                case 7 -> new double[][]{{21,25},{39,25},{30,34},{21,43},{39,43},{21,62},{39,62}};
                case 8 -> new double[][]{{21,25},{39,25},{30,34},{21,43},{39,43},{30,53},{21,62},{39,62}};
                case 9 -> new double[][]{{21,23},{39,23},{21,36},{39,36},{30,43},{21,50},{39,50},{21,63},{39,63}};
                case 10 -> new double[][]{{21,23},{39,23},{30,29},{21,36},{39,36},{21,50},{39,50},{30,57},{21,63},{39,63}};
                default -> throw new IllegalArgumentException("Invalid card rank");
            };
            g.setTextAlign(TextAlignment.CENTER); g.setFont(Font.font("Georgia", value == 14 ? 34 : 16));
            for (double[] pip : pips) {
                g.save(); g.translate(pip[0], pip[1]); if (pip[1] > 45) g.rotate(180);
                g.fillText(suit, 0, 5); g.restore();
            }
        }
    }

    private void corner(GraphicsContext g, String rank, String suit) {
        g.setTextAlign(TextAlignment.CENTER); g.setFont(Font.font("Georgia", FontWeight.BOLD, 14)); g.fillText(rank, 9, 15);
        g.setFont(Font.font("Georgia", 12)); g.fillText(suit, 9, 27);
    }
    private void back(GraphicsContext g) {
        g.setFill(Color.web("#29463f")); g.fillRoundRect(4, 4, 52, 76, 4, 4);
        g.setStroke(Color.web("#77948a")); g.setLineWidth(.6);
        for (int i = -70; i < 70; i += 7) {
            g.save(); g.beginPath(); g.rect(7, 7, 46, 70); g.clip();
            g.strokeLine(i, 7, i + 70, 77); g.strokeLine(i + 70, 7, i, 77); g.restore();
        }
        g.setStroke(Color.web("#d4cfb7")); g.strokeRoundRect(8, 8, 44, 68, 3, 3);
    }
    private void court(GraphicsContext g, String rank, String suit, Color ink) {
        g.setFill(Color.web("#ede3cd")); g.fillRect(17, 19, 26, 47);
        g.setStroke(ink); g.setLineWidth(.7); g.strokeRect(17, 19, 26, 47);
        for (int mirror = 0; mirror < 2; mirror++) {
            g.save(); if (mirror == 1) { g.translate(60, 84); g.rotate(180); }
            g.setFill(Color.web("#b68c42"));
            g.fillPolygon(new double[]{21,22,27,30,33,38,39}, new double[]{28,21,26,20,26,21,28}, 7);
            g.setFill(Color.web("#f6d8ae")); g.fillOval(25, 29, 10, 12);
            g.setFill(ink); g.fillPolygon(new double[]{21,30,39}, new double[]{44,36,44}, 3);
            g.setFont(Font.font("Georgia", 8)); g.setTextAlign(TextAlignment.CENTER); g.fillText(suit,30, 34);
            g.restore();
        }
        g.setFill(ink); g.setFont(Font.font("Georgia", FontWeight.BOLD, 11)); g.setTextAlign(TextAlignment.CENTER); g.fillText(rank,30,46);
    }
}
