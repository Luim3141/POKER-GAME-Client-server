package com.poker.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/** Local decoration only: no socket, game state, card lookup or gameplay deadlines. */
final class TableEffects {
    enum Motion { FULL, REDUCED, OFF }
    private final Pane table;
    private final Canvas overlay = new Canvas();
    private final Node deck = new PlayingCardView(null, 30);
    private final Preferences preferences = Preferences.userNodeForPackage(TableEffects.class);
    private final List<Node> dealing = new ArrayList<>();
    private Timeline deal, win;
    private Canvas celebration;
    private final List<Timeline> coinAnimations = new ArrayList<>();
    private final List<Node> coinNodes = new ArrayList<>();
    private Motion motion;
    private boolean minimized;

    TableEffects(Pane table) {
        this.table = table;
        try { motion = Motion.valueOf(preferences.get("motion", "FULL")); }
        catch (IllegalArgumentException error) { motion = Motion.FULL; }
        overlay.setManaged(false); overlay.setMouseTransparent(true);
        overlay.getStyleClass().add("celebration-overlay");
        overlay.widthProperty().bind(table.widthProperty()); overlay.heightProperty().bind(table.heightProperty());
        table.getChildren().add(overlay);
        deck.setManaged(false); deck.setVisible(false); deck.setRotate(-10);
        deck.getStyleClass().add("deck-stack"); table.getChildren().add(deck);
        table.getProperties().put("effects", this);
    }

    void configureMenu(MenuButton menu) {
        menu.getItems().add(new SeparatorMenuItem());
        ToggleGroup group = new ToggleGroup();
        for (Motion mode : Motion.values()) {
            RadioMenuItem item = new RadioMenuItem(switch (mode) {
                case FULL -> "Hiệu ứng · Đầy đủ"; case REDUCED -> "Hiệu ứng · Giảm chuyển động"; case OFF -> "Hiệu ứng · Tắt";
            });
            item.setToggleGroup(group); item.setSelected(mode == motion);
            item.setOnAction(event -> { item.setSelected(true); motion = mode; preferences.put("motion", mode.name()); cancel(); });
            menu.getItems().add(item);
        }
    }
    boolean fullMotion() {
        if (motion != Motion.FULL || minimized) return false;
        for (Node node = table; node != null; node = node.getParent()) if (!node.isVisible()) return false;
        return true;
    }
    void toFront() { deck.toFront(); overlay.toFront(); }
    void attachCelebration(Canvas canvas) { celebration = canvas; }
    void positionDeck(Point2D origin, boolean visible) { deck.relocate(origin.getX() - 15, origin.getY() - 21); deck.setVisible(visible); }
    void setMinimized(boolean value) { minimized = value; if (value) cancel(); }

    void deal(List<Node> cards, Supplier<Point2D> origin, long elapsedMillis) {
        finishDeal();
        if (!fullMotion() || cards.isEmpty()) return;
        dealing.addAll(cards);
        cards.forEach(node -> { node.setOpacity(0); node.setMouseTransparent(true); });
        long started = System.nanoTime();
        deal = new Timeline(new KeyFrame(Duration.millis(16), event -> {
            double elapsed = elapsedMillis + (System.nanoTime() - started) / 1_000_000.0;
            Point2D source = origin.get();
            for (int i = 0; i < dealing.size(); i++) {
                Node node = dealing.get(i);
                double progress = Math.max(0, Math.min(1, (elapsed - i * com.poker.common.DealTiming.CARD_INTERVAL_MS) / com.poker.common.DealTiming.CARD_FLIGHT_MS));
                node.setTranslateX(0); node.setTranslateY(0);
                Point2D target = table.sceneToLocal(node.localToScene(0, 0));
                double eased = 1 - Math.pow(1 - progress, 3);
                node.setTranslateX((source.getX() - target.getX()) * (1 - eased));
                node.setTranslateY((source.getY() - target.getY()) * (1 - eased));
                node.setRotate((1 - eased) * -14); node.setScaleX(.86 + .14 * eased); node.setScaleY(.86 + .14 * eased);
                node.setOpacity(elapsed >= i * com.poker.common.DealTiming.CARD_INTERVAL_MS ? 1 : 0);
            }
            if (elapsed >= (cards.size() - 1) * com.poker.common.DealTiming.CARD_INTERVAL_MS + com.poker.common.DealTiming.CARD_FLIGHT_MS) finishDeal();
        }));
        deal.setCycleCount(Timeline.INDEFINITE); deal.play();
    }

    void finishDeal() {
        if (deal != null) deal.stop(); deal = null;
        for (Node node : dealing) {
            node.setOpacity(1); node.setTranslateX(0); node.setTranslateY(0); node.setRotate(0); node.setScaleX(1); node.setScaleY(1);
        }
        dealing.clear();
    }

    void betCoins(Node seat, Supplier<Point2D> destination, long amount) {
        if (!fullMotion() || amount <= 0) return;
        Canvas coins = new Canvas(110, 44); coins.setManaged(false); coins.setMouseTransparent(true);
        coins.getStyleClass().add("bet-coins");
        GraphicsContext g = coins.getGraphicsContext2D();
        for (int i = 0; i < 5; i++) {
            g.setFill(Color.web("#80551c")); g.fillOval(32 + i * 8, 3 + i % 2 * 3, 14, 14);
            g.setFill(Color.web("#f5cf71")); g.fillOval(32 + i * 8, 1 + i % 2 * 3, 14, 14);
            g.setStroke(Color.web("#fff0bd")); g.strokeOval(35 + i * 8, 4 + i % 2 * 3, 8, 8);
        }
        g.setFill(Color.web("#ffebac")); g.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        g.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        g.fillText("+" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(amount), 55, 37, 108);
        table.getChildren().add(coins); coinNodes.add(coins);
        long started = System.nanoTime();
        Timeline animation = new Timeline();
        animation.getKeyFrames().add(new KeyFrame(Duration.millis(16), event -> {
            double p = Math.min(1, (System.nanoTime() - started) / 550_000_000.0);
            if (!fullMotion() || p >= 1) {
                animation.stop(); coinAnimations.remove(animation); coinNodes.remove(coins); table.getChildren().remove(coins); return;
            }
            Node avatar = seat.lookup(".avatar");
            var bounds = table.sceneToLocal((avatar == null ? seat : avatar).localToScene((avatar == null ? seat : avatar).getLayoutBounds()));
            Point2D target = destination.get(); double eased = 1 - Math.pow(1 - p, 2);
            coins.relocate((bounds.getCenterX() * (1 - eased) + target.getX() * eased) - 55,
                bounds.getCenterY() * (1 - eased) + target.getY() * eased - 22 - Math.sin(p * Math.PI) * 45);
            coins.setOpacity(1 - .4 * p); coins.toFront();
        }));
        coinAnimations.add(animation); animation.setCycleCount(Timeline.INDEFINITE); animation.play();
    }

    void celebrate(List<? extends Node> winners) {
        stopWin();
        if (!fullMotion() || winners.isEmpty()) return;
        long started = System.nanoTime();
        win = new Timeline(new KeyFrame(Duration.millis(33), event -> {
            double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
            GraphicsContext g = overlay.getGraphicsContext2D(); g.clearRect(0, 0, overlay.getWidth(), overlay.getHeight());
            if (!fullMotion() || seconds >= 5) { stopWin(); return; }
            double fade = Math.min(1, (5 - seconds) * 2);
            g.setGlobalAlpha(fade);
            // Flames stay on the outside of winner frames, not on their cards/text.
            for (Node node : winners) {
                var bounds = table.sceneToLocal(node.localToScene(node.getLayoutBounds()));
                g.setStroke(Color.web("#ecab46")); g.setLineWidth(2);
                g.strokeRoundRect(bounds.getMinX() - 3, bounds.getMinY() - 3, bounds.getWidth() + 6, bounds.getHeight() + 6, 10, 10);
                for (int i = 0; i < 18; i++) {
                    double x = bounds.getMinX() + bounds.getWidth() * i / 17;
                    double y = bounds.getMinY() - 4;
                    double flame = 4 + 7 * (.5 + .5 * Math.sin(seconds * 12 + i * 2.3));
                    g.setFill(Color.web(i % 2 == 0 ? "#e87930" : "#f5cc6d"));
                    g.fillPolygon(new double[]{x - 3, x, x + 3}, new double[]{y, y - flame, y}, 3);
                }
            }
            GraphicsContext sky = celebration == null ? g : celebration.getGraphicsContext2D();
            double width = celebration == null ? overlay.getWidth() : celebration.getWidth();
            double height = celebration == null ? overlay.getHeight() : celebration.getHeight();
            if (celebration != null) sky.clearRect(0, 0, width, height);
            sky.save(); sky.setGlobalAlpha(.65 * fade);
            // Travel across the whole viewport, staying below navigation and above the action tray.
            double x = width * (.5 + .32 * Math.sin(seconds * 1.45 - 1.3));
            double y = height * (.35 + .14 * Math.sin(seconds * 2.2));
            sky.translate(x, y); sky.rotate(Math.sin(seconds * 1.4) * 13);
            double scale = width * .45 / 135;
            sky.scale(scale, scale); dragon(sky, 0, 0, seconds); sky.restore();
            if (celebration != null) {
                // Composite behind important table content, even though the canvas spans the window.
                List<Node> protectedNodes = new ArrayList<>(table.lookupAll(".seat-panel"));
                Node board = table.lookup("#centerBoard"); if (board != null) protectedNodes.add(board);
                for (Node node : protectedNodes) {
                    var bounds = celebration.sceneToLocal(node.localToScene(node.getLayoutBounds()));
                    sky.clearRect(bounds.getMinX() - 3, bounds.getMinY() - 3, bounds.getWidth() + 6, bounds.getHeight() + 6);
                }
            }
            g.setGlobalAlpha(1);
        }));
        win.setCycleCount(Timeline.INDEFINITE); win.play(); toFront();
    }

    private void dragon(GraphicsContext g, double x, double y, double t) {
        g.save(); g.translate(x - 54, y);
        // Scalable eastern dragon: articulated body, dorsal fins, claws, horns and whiskers.
        g.beginPath();
        for (int side : new int[]{-1, 1}) for (int step = 0; step <= 100; step++) {
            double px = side == -1 ? step : 100 - step;
            double py = Math.sin(px * .1125 - t * 5) * 8 + side * (1.2 + px * .025);
            if (side == -1 && step == 0) g.moveTo(px, py); else g.lineTo(px, py);
        }
        g.closePath(); g.setFill(Color.web("#dab366")); g.fill();
        g.setStroke(Color.web("#8d5c30")); g.setLineWidth(.5); g.stroke();
        for (int i = 0; i < 25; i++) {
            double px = i * 4.0, py = Math.sin(i * .45 - t * 5) * 8;
            double size = 1.2 + px * .025;
            g.setStroke(Color.web("#946538")); g.setLineWidth(.4);
            g.strokeArc(px - 1, py - size + .5, 3, size * 1.8, -70, 140, javafx.scene.shape.ArcType.OPEN);
            g.setFill(Color.web("#ac6335")); g.fillPolygon(new double[]{px-1,px+1,px+3}, new double[]{py-size,py-size-3,py-size}, 3);
            if (i == 8 || i == 20) {
                g.setStroke(Color.web("#e6b66a")); g.setLineWidth(1.1);
                double leg = Math.sin(t * 5 + i) * 3;
                g.strokePolyline(new double[]{px+2,px-1,px+leg-4}, new double[]{py+size,py+size+6,py+size+8}, 3);
                for (int claw = 0; claw < 3; claw++) g.strokeLine(px+leg-4, py+size+8, px+leg-7+claw*2, py+size+10);
                g.strokePolyline(new double[]{px+2,px+6,px+9+leg}, new double[]{py-size,py-size-5,py-size-3},3);
            }
        }
        double headY = Math.sin(24 * .45 - t * 5) * 8;
        g.setFill(Color.web("#af6435")); g.fillPolygon(new double[]{96,91,98,93,103}, new double[]{headY-7,headY-13,headY-10,headY-16,headY-6},5);
        g.setFill(Color.web("#efd18d")); g.fillOval(97, headY - 6, 17, 12); g.fillRoundRect(109, headY, 13, 6, 3, 3);
        g.setStroke(Color.web("#f2d88b")); g.setLineWidth(2);
        g.strokePolyline(new double[]{101,99,96}, new double[]{headY,headY-10,headY-13}, 3);
        g.strokePolyline(new double[]{109,111,116}, new double[]{headY,headY-10,headY-13}, 3);
        g.strokeLine(118, headY + 5, 130, headY - 3); g.strokeLine(118, headY + 6, 131, headY + 13);
        g.setFill(Color.web("#27362c")); g.fillOval(110, headY + 1, 2.5, 2.5);
        g.setStroke(Color.web("#81502a")); g.setLineWidth(.7); g.strokeLine(107, headY, 112, headY-2);
        g.strokeLine(111, headY+6, 122, headY+5);
        g.setFill(Color.web("#fff5ca")); g.fillPolygon(new double[]{111,114,117}, new double[]{headY+7,headY+11,headY+7},3);
        g.restore();
    }

    private void stopWin() {
        if (win != null) win.stop(); win = null;
        overlay.getGraphicsContext2D().clearRect(0, 0, overlay.getWidth(), overlay.getHeight());
        if (celebration != null) celebration.getGraphicsContext2D().clearRect(0, 0, celebration.getWidth(), celebration.getHeight());
    }
    void cancel() {
        finishDeal(); stopWin(); coinAnimations.forEach(Timeline::stop); coinAnimations.clear();
        table.getChildren().removeAll(coinNodes); coinNodes.clear();
    }
}
