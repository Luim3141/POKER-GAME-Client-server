package com.poker.ui;

import com.poker.protocol.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/** Headful, opt-in: real FXML and client socket, deterministic protocol peer (not a rules test). */
class UiSmokeIT {
    private final ProtocolCodec codec = new ProtocolCodec();
    private Parent root;
    private Stage stage;
    private MainController controller;

    @Test void loginNavigationBettingAndObserverControls() throws Exception {
        Platform.startup(() -> Platform.setImplicitExit(false));
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            fx(() -> {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/poker/ui/poker-client.fxml"));
                root = loader.load(); controller = loader.getController();
                assertEquals("8", field("seatsField").getText());
                field("hostField").setText("127.0.0.1"); field("portField").setText("" + server.getLocalPort());
                stage = new Stage(); ClientWindow.configure(stage, (Region) root); stage.show();
                ((MenuButton) root.lookup("#displayMenu")).getItems().get(7).fire(); // deterministic full-motion fixture
                assertEquals(StageStyle.DECORATED, stage.getStyle(), "Keep native title-bar controls");
                assertTrue(stage.isResizable());
                assertTrue(stage.getMinWidth() < 960 && stage.getMinHeight() < 640);
                String forcedScale = System.getProperty("glass.win.uiScale");
                if (forcedScale != null) assertEquals(Double.parseDouble(forcedScale), stage.getOutputScaleX(), .01,
                    "The DPI test must actually use the requested JavaFX output scale");
                screenshot("login"); return null;
            });
            exerciseWindowSizes("login", "usernameField", "passwordField", "loginButton", "hostField", "portField");
            server.setSoTimeout(15000);
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(10000);
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                fx(() -> {
                    field("usernameField").setText("river_test"); field("passwordField").setText(" test pass ");
                    button("loginButton").fire(); return null;
                });
                Message login = codec.read(in);
                assertEquals(MessageType.LOGIN, login.type());
                assertEquals(" test pass ", login.payload().path("password").asText(), "Passwords must not be trimmed");
                send(out, MessageType.LOGIN_SUCCESS, codec.mapper().createObjectNode().put("sessionId", "test-session")
                    .put("userId", 1).put("username", "river_test").put("chips", 10000).put("accountRole", "PLAYER")
                    .set("glory", codec.mapper().valueToTree(com.poker.model.GlorySummary.from(10000, 30, 100))));
                assertEquals(MessageType.GET_LOBBY, codec.read(in).type());
                awaitVisible("homePage");
                exerciseWindowSizes("lobby", "createTableBar", "joinBar");
                fx(() -> { button("historyNavButton").fire(); return null; });
                assertEquals(MessageType.GET_HISTORY, codec.read(in).type());
                awaitVisible("historyPage");
                exerciseWindowSizes("history", "historyPage");
                fx(() -> { button("walletNavButton").fire(); return null; });
                assertEquals(MessageType.GET_COIN_REQUESTS, codec.read(in).type());
                awaitVisible("coinPage");
                exerciseWindowSizes("wallet", "coinAmountField", "coinSubmitButton");
                sendArray(out, MessageType.COIN_REQUEST_SNAPSHOT, "[{\"amount\":5000,\"status\":\"PENDING\"}]");
                awaitDisabled("coinSubmitButton");
                sendArray(out, MessageType.COIN_REQUEST_SNAPSHOT, "[{\"amount\":5000,\"status\":\"APPROVED\"}]");
                awaitEnabled("coinSubmitButton");
                fx(() -> { button("homeNavButton").fire(); return null; });
                assertEquals(MessageType.GET_LOBBY, codec.read(in).type());

                String table = "{\"tableId\":\"ui-test\",\"viewerRole\":\"PLAYER\",\"status\":\"IN_HAND\","
                    + "\"smallBlind\":50,\"bigBlind\":100,\"maxPlayers\":6,\"dealerSeat\":0,\"smallBlindSeat\":1,\"bigBlindSeat\":2,\"street\":\"FLOP\","
                    + "\"board\":[{\"rank\":\"ACE\",\"suit\":\"SPADES\"},{\"rank\":\"TEN\",\"suit\":\"HEARTS\"},{\"rank\":\"KING\",\"suit\":\"CLUBS\"}],"
                    + "\"seats\":[{\"userId\":1,\"username\":\"river_test\",\"seat\":0,\"stack\":9500,\"streetContribution\":100,\"totalContribution\":500,"
                    + "\"ready\":false,\"connected\":true,\"handStatus\":\"ACTIVE\",\"holeCards\":[{\"rank\":\"QUEEN\",\"suit\":\"DIAMONDS\"},{\"rank\":\"JACK\",\"suit\":\"DIAMONDS\"}]},"
                    + "{\"userId\":2,\"username\":\"Mai\",\"seat\":1,\"stack\":0,\"streetContribution\":140,\"totalContribution\":540,\"ready\":false,\"connected\":true,\"handStatus\":\"ALL_IN\",\"holeCards\":[]}],"
                    + "\"pots\":[{\"amount\":1000,\"eligibleUserIds\":[\"1\",\"2\"]},{\"amount\":40,\"eligibleUserIds\":[\"2\"]}],\"currentTurnUserId\":1,\"turnSeconds\":20,\"stateVersion\":7,\"handId\":\"hand\"}";
                ObjectNode withGlory = (ObjectNode) codec.mapper().readTree(table);
                ((ObjectNode) withGlory.path("seats").get(0)).set("glory", codec.mapper().valueToTree(com.poker.model.GlorySummary.from(10000, 30, 100)));
                ((ObjectNode) withGlory.path("seats").get(1)).set("glory", codec.mapper().valueToTree(com.poker.model.GlorySummary.from(500000, 350, 1000)));
                table = withGlory.toString();
                codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", "hand", 7, codec.mapper().readTree(table)));
                ObjectNode prompt = codec.mapper().createObjectNode().put("toCall", 40).put("minimumRaiseTo", 240)
                    .put("currentBet", 140).put("canRaise", false).put("stack", 9500).put("streetContribution", 100)
                    .put("deadlineEpochMs", System.currentTimeMillis() + 20000);
                codec.write(out, Message.event(MessageType.ACTION_REQUIRED, "ui-test", "hand", 7, prompt));
                awaitVisible("tablePage");
                java.util.Set<javafx.scene.Node> retainedCards = fx(() -> new java.util.HashSet<>(root.lookupAll(".seat-panel").stream()
                    .flatMap(node -> node.lookupAll("PlayingCardView").stream()).toList()));
                fx(() -> {
                    root.applyCss(); root.layout();
                    assertTrue(((Region) root.lookup(".action-tray")).getHeight() <= 170,
                        "In-hand status and controls must fit a compact tray, not consume the table");
                    assertFalse(button("allInButton").isVisible(), "Closed raising forbids a raising all-in");
                    assertFalse(button("raiseButton").isVisible());
                    assertEquals("Call 40", button("callButton").getText());
                    screenshot("table"); button("callButton").fire(); return null;
                });
                Message action = codec.read(in);
                assertEquals(MessageType.PLAYER_ACTION, action.type());
                assertEquals("CALL", action.payload().path("action").asText());
                assertEquals(7, action.seq());
                assertEquals("hand", action.handId());

                long seq = 8;
                for (String kind : new String[]{"CHECK", "BET", "RAISE", "ALL_IN", "FOLD"}) {
                    ObjectNode nextTable = (ObjectNode) codec.mapper().readTree(table);
                    nextTable.put("stateVersion", seq);
                    codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", "hand", seq, nextTable));
                    boolean opening = kind.equals("CHECK") || kind.equals("BET");
                    prompt.put("toCall", opening ? 0 : 40).put("currentBet", opening ? 0 : 140)
                        .put("minimumRaiseTo", opening ? 100 : 240).put("canRaise", true);
                    codec.write(out, Message.event(MessageType.ACTION_REQUIRED, "ui-test", "hand", seq, prompt));
                    awaitEnabled("bettingBox");
                    fx(() -> {
                        assertTrue(root.lookupAll(".seat-panel").stream().flatMap(node -> node.lookupAll("PlayingCardView").stream())
                            .allMatch(retainedCards::contains), "Action snapshots must not rebuild hole card nodes");
                        if (kind.equals("RAISE")) {
                            Slider slider = (Slider) root.lookup("#wagerSlider");
                            slider.setValue(500);
                            assertEquals("4920", field("betAmountField").getText());
                            field("betAmountField").setText("9600"); assertEquals(1000, slider.getValue());
                            field("betAmountField").setText("12x"); assertEquals("12x", field("betAmountField").getText());
                            assertTrue(((Label) root.lookup("#wagerHintLabel")).getText().contains("Nhập số nguyên"));
                            field("betAmountField").setText(""); assertEquals("", field("betAmountField").getText());
                            slider.setValue(0); assertEquals("240", field("betAmountField").getText());
                            screenshot("slider");
                        }
                        if (kind.equals("BET")) {
                            root.applyCss(); root.layout();
                            assertTrue(((Region) root.lookup(".action-tray")).getHeight() <= 170,
                                "The wager slider must not expand the status tray into a large bottom panel");
                            field("betAmountField").setText("0"); button("betButton").fire();
                            assertTrue(((Label) root.lookup("#statusLabel")).getText().contains("không hợp lệ"));
                        }
                        field("betAmountField").setText("300");
                        button(switch (kind) { case "ALL_IN" -> "allInButton"; default -> kind.toLowerCase() + "Button"; }).fire(); return null;
                    });
                    Message sent = codec.read(in);
                    assertEquals(kind, sent.payload().path("action").asText());
                    assertEquals(seq++, sent.seq());
                    if (kind.equals("BET") || kind.equals("RAISE")) assertEquals(300, sent.payload().path("amount").asLong());
                }

                ObjectNode waitingTurn = (ObjectNode) codec.mapper().readTree(table);
                waitingTurn.put("stateVersion", seq).put("currentTurnUserId", 2);
                codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", "hand", seq++, waitingTurn));
                awaitHidden("bettingBox");
                fx(() -> {
                    root.applyCss(); root.layout();
                    assertFalse(root.lookup("#bettingBox").isVisible(), "Other players' turns need status, not inactive wager controls");
                    screenshot("waiting-compact-status");
                    double trayHeight = ((Region) root.lookup(".action-tray")).getHeight();
                    assertTrue(trayHeight <= 110, "Waiting status tray is too tall: " + trayHeight);
                    assertInViewport(root.lookup("#actionInfoLabel"), "waiting/status");
                    assertInViewport(root.lookup("#turnHintLabel"), "waiting/turn");
                    for (var control : root.lookup(".action-tray").lookupAll(".text-button"))
                        if (control.isVisible()) assertInViewport(control, "waiting/action");
                    return null;
                });
                ObjectNode dealing = (ObjectNode) codec.mapper().readTree(table);
                dealing.put("stateVersion", seq).put("phase", "DEALING").putNull("currentTurnUserId")
                    .put("dealDurationMillis", 1550).put("dealRemainingMillis", 1550).put("dealCardCount", 3);
                codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", "hand", seq++, dealing));
                awaitDisabled("bettingBox"); Thread.sleep(180);
                fx(() -> {
                    var cards = ((javafx.scene.layout.HBox) root.lookup("#boardCards")).getChildren();
                    assertEquals(0, cards.get(1).getOpacity(), "Second flop card must wait for its own slot");
                    assertEquals(0, cards.get(2).getOpacity(), "Third flop card must not appear with the first");
                    assertTrue(((Label) root.lookup("#actionInfoLabel")).getText().contains("Đang chia bài"));
                    return null; // Exporting a 200%-DPI PNG here would consume the next animation slot.
                });
                Thread.sleep(350);
                fx(() -> {
                    var cards = ((javafx.scene.layout.HBox) root.lookup("#boardCards")).getChildren();
                    assertEquals(1, cards.get(0).getOpacity()); assertEquals(0, cards.get(2).getOpacity());
                    screenshot("dealing-sequential"); return null;
                });
                ObjectNode betting = (ObjectNode) codec.mapper().readTree(table);
                betting.put("stateVersion", seq).put("phase", "BETTING");
                codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", "hand", seq, betting));
                prompt.put("deadlineEpochMs", System.currentTimeMillis() + 20000);
                codec.write(out, Message.event(MessageType.ACTION_REQUIRED, "ui-test", "hand", seq++, prompt));
                awaitEnabled("bettingBox");
                ObjectNode check = codec.mapper().createObjectNode().put("sequence", 1).put("userId", 2)
                    .put("street", "FLOP").put("action", "CHECK").put("paid", 0).put("streetContribution", 140).put("automatic", false);
                codec.write(out, Message.event(MessageType.TABLE_ACTION, "ui-test", "hand", seq, check));
                fx(() -> null); Thread.sleep(60);
                fx(() -> { assertTrue(root.lookupAll(".bet-coins").isEmpty(), "CHECK never pays coins"); return null; });
                ObjectNode raise = check.deepCopy().put("sequence", 2).put("userId", 1).put("action", "RAISE").put("paid", 300).put("streetContribution", 800);
                codec.write(out, Message.event(MessageType.TABLE_ACTION, "ui-test", "hand", seq, raise));
                Thread.sleep(90);
                fx(() -> {
                    assertEquals(1, root.lookupAll(".bet-coins").size());
                    assertTrue(root.lookupAll(".seat-action").stream().anyMatch(node -> ((Label) node).getText().contains("RAISE TO 800")));
                    assertEquals(2, ((javafx.scene.layout.FlowPane) root.lookup("#actionsRow")).getChildren().size());
                    Slider slider = (Slider) root.lookup("#wagerSlider");
                    assertTrue(slider.getWidth() <= 220); assertTrue(slider.getBoundsInParent().getMaxY() <= field("betAmountField").getBoundsInParent().getMinY());
                    screenshot("raise-and-coins"); return null;
                });
                codec.write(out, Message.event(MessageType.TABLE_ACTION, "ui-test", "hand", seq, raise));
                Thread.sleep(60); fx(() -> { assertEquals(1, root.lookupAll(".bet-coins").size(), "Duplicate event cannot pay twice visually"); return null; });
                Thread.sleep(600);
                betting.set("recentActions", codec.mapper().createArrayNode().add(check).add(raise));
                codec.write(out, Message.event(MessageType.STATE_SNAPSHOT, "ui-test", "hand", seq, betting));
                Thread.sleep(70);
                fx(() -> { assertTrue(root.lookupAll(".bet-coins").isEmpty(), "Resume snapshot never replays historical payments"); return null; });

                ObjectNode waiting = (ObjectNode) codec.mapper().readTree(table);
                waiting.put("status", "BETWEEN_HANDS").put("stateVersion", seq).putNull("currentTurnUserId");
                ((ObjectNode) waiting.path("seats").get(0)).put("stack", 10500);
                codec.write(out, Message.event(MessageType.HAND_RESULT, "ui-test", "hand", seq,
                    codec.mapper().readTree("{\"winners\":[{\"userId\":1,\"username\":\"river_test\",\"amount\":1000}],"
                        + "\"pots\":[{\"name\":\"MAIN POT\",\"amount\":1000,\"winners\":[{\"userId\":1,\"amount\":1000}]}],"
                        + "\"uncalledReturns\":[{\"userId\":1,\"username\":\"river_test\",\"amount\":50}]}")));
                codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", "hand", seq, waiting));
                awaitEnabled("readyButton");
                Thread.sleep(140); // Inspect a rendered frame, not the moment before the first animation pulse.
                fx(() -> { screenshot("winner-celebration"); return null; });
                long animationDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!fx(() -> root.lookupAll(".payout-chip").isEmpty())) {
                    if (System.nanoTime() > animationDeadline) fail("Payout decoration did not clean up"); Thread.sleep(20);
                }
                fx(() -> {
                    assertEquals(1, root.lookupAll(".winner-seat").size());
                    assertEquals(1, root.lookupAll(".winner-avatar").size());
                    var canvas = (javafx.scene.canvas.Canvas) stage.getScene().getRoot().lookup(".window-celebration-overlay");
                    assertTrue(canvas.isMouseTransparent()); assertEquals(stage.getScene().getWidth(), canvas.getWidth(), .01);
                    assertTrue(((Label) root.lookup("#resultBannerLabel")).getText().contains("Hoàn cược chưa được theo"));
                    assertEquals("10,500 chips", ((Label) root.lookup("#walletLabel")).getText());
                    assertTrue(root.lookupAll(".payout-chip").isEmpty(), "Payout decoration must clean up independently of Ready");
                    assertTrue(((Label) root.lookup("#resultBannerLabel")).getText().contains("Chúc mừng bạn"));
                    screenshot("payout"); button("readyButton").fire(); return null;
                });
                assertEquals(MessageType.READY, codec.read(in).type());
                ((ObjectNode) waiting.path("seats").get(0)).put("ready", true);
                codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", "hand", ++seq, waiting));
                awaitEnabled("readyButton");
                fx(() -> { button("readyButton").fire(); return null; });
                assertEquals(MessageType.UNREADY, codec.read(in).type());

                // A duplicate result must not recreate chips or replay the congratulations animation.
                codec.write(out, Message.event(MessageType.HAND_RESULT, "ui-test", "hand", seq,
                    codec.mapper().readTree("{\"winners\":[{\"userId\":1,\"username\":\"duplicate\",\"amount\":99999}]}")));
                fx(() -> null); Thread.sleep(60);
                fx(() -> {
                    assertFalse(((Label) root.lookup("#resultBannerLabel")).getText().contains("duplicate"));
                    assertTrue(root.lookupAll(".payout-chip").isEmpty()); return null;
                });

                ObjectNode eight = (ObjectNode) codec.mapper().readTree(table);
                eight.put("maxPlayers", 8).put("stateVersion", ++seq);
                var seats = eight.putArray("seats");
                for (int i = 0; i < 8; i++) {
                    ObjectNode seat = ((ObjectNode) waiting.path("seats").get(0)).deepCopy();
                    seat.put("userId", i + 1).put("seat", i).put("username", i == 0 ? "river_test" : "Player " + (i + 1));
                    seat.put("ready", false);
                    if (i != 0) seat.putArray("holeCards");
                    seats.add(seat);
                }
                codec.write(out, Message.event(MessageType.HAND_STARTED, "ui-test", "hand", seq, codec.mapper().createObjectNode()));
                codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", "hand", seq, eight));
                long eightDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (fx(() -> root.lookupAll(".seat-panel").size()) != 8) {
                    if (System.nanoTime() > eightDeadline) fail("Eight seats not rendered"); Thread.sleep(20);
                }
                codec.write(out, Message.event(MessageType.ACTION_REQUIRED, "ui-test", "hand", seq, prompt));
                awaitEnabled("bettingBox");
                fx(() -> {
                    for (var node : root.lookupAll(".seat-panel")) for (var value : node.lookupAll("PlayingCardView")) {
                        assertEquals(1, value.getOpacity(), "Early prompt must make cards immediately visible");
                        assertEquals(0, value.getTranslateX()); assertEquals(0, value.getTranslateY());
                    }
                    root.applyCss(); root.layout();
                    var nodes = new java.util.ArrayList<>(root.lookupAll(".seat-panel"));
                    for (int i = 0; i < nodes.size(); i++) for (int j = i + 1; j < nodes.size(); j++)
                        assertFalse(nodes.get(i).getBoundsInParent().intersects(nodes.get(j).getBoundsInParent()), "Seat panels overlap");
                    screenshot("table-eight");
                    javafx.scene.layout.FlowPane deck = new javafx.scene.layout.FlowPane(6, 6);
                    for (var suit : com.poker.model.Suit.values()) for (var rank : com.poker.model.Rank.values()) {
                        PlayingCardView card = new PlayingCardView(new com.poker.model.Card(rank, suit), 60);
                        assertFalse(card.getAccessibleText().isBlank()); deck.getChildren().add(card);
                    }
                    assertEquals(52, deck.getChildren().size());
                    deck.setPrefWrapLength(920); new Scene(deck, 920, 390); deck.applyCss(); deck.layout();
                    screenshot(deck, "deck"); return null;
                });
                var manyPots = eight.putArray("pots");
                for (int i = 0; i < 9; i++) manyPots.addObject().put("amount", 123456789).putArray("eligibleUserIds").add("1");
                eight.put("tableId", "many-pots").put("stateVersion", ++seq);
                codec.write(out, Message.event(MessageType.TABLE_STATE, "many-pots", "hand", seq, eight));
                long potsDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!fx(() -> ((Label) root.lookup("#tableTitleLabel")).getText().contains("MANY-POT"))) {
                    if (System.nanoTime() > potsDeadline) fail("Dense pots not rendered"); Thread.sleep(20);
                }
                fx(() -> {
                    stage.setWidth(1116); stage.setHeight(799); // Approximate the app's minimum window, including native chrome.
                    screenshot("table-many-pots");
                    for (var pot : root.lookupAll(".pot-label")) for (var seat : root.lookupAll(".seat-panel"))
                        assertFalse(pot.localToScene(pot.getBoundsInLocal()).intersects(seat.localToScene(seat.getBoundsInLocal())), "Pot amount is covered by a seat");
                    var seatsOnScreen = new java.util.ArrayList<>(root.lookupAll(".seat-panel"));
                    for (int i = 0; i < seatsOnScreen.size(); i++) for (int j = i + 1; j < seatsOnScreen.size(); j++)
                        assertFalse(seatsOnScreen.get(i).getBoundsInParent().intersects(seatsOnScreen.get(j).getBoundsInParent()), "Compact seats overlap");
                    for (var control : root.lookup("#bettingBox").lookupAll(".button")) if (control.isVisible())
                        assertTrue(control.localToScene(control.getBoundsInLocal()).getMaxY() <= root.getScene().getHeight(), "Action below window viewport");
                    return null;
                });
                exerciseWindowSizes("table-eight-many-pots", "centerBoard", "potsRow", "bettingBox", "tableTitleLabel");

                // Short all-in: one legal amount, slider and amount entry are fixed.
                ObjectNode shortTable = (ObjectNode) codec.mapper().readTree(table);
                shortTable.put("stateVersion", ++seq);
                ((ObjectNode) shortTable.path("seats").get(0)).put("stack", 80).put("streetContribution", 0);
                codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", "hand", seq, shortTable));
                prompt.put("toCall", 0).put("currentBet", 0).put("minimumRaiseTo", 100).put("stack", 80).put("streetContribution", 0);
                codec.write(out, Message.event(MessageType.ACTION_REQUIRED, "ui-test", "hand", seq, prompt));
                awaitEnabled("bettingBox");
                fx(() -> {
                    assertEquals("80", field("betAmountField").getText());
                    assertTrue(field("betAmountField").isDisabled()); assertTrue(root.lookup("#wagerSlider").isDisabled());
                    ((MenuButton) root.lookup("#displayMenu")).getItems().get(8).fire(); // reduced motion
                    assertTrue(((javafx.scene.control.RadioMenuItem) ((MenuButton) root.lookup("#displayMenu")).getItems().get(8)).isSelected());
                    ((MenuButton) root.lookup("#displayMenu")).getItems().get(9).fire(); // off
                    ((MenuButton) root.lookup("#displayMenu")).getItems().get(7).fire(); // full
                    return null;
                });

                // Twenty fixture hands: ties + side-pot awards, interrupted decorations and no node accumulation.
                for (int cycle = 0; cycle < 20; cycle++) {
                    String handId = "cycle-" + cycle;
                    ObjectNode live = (ObjectNode) codec.mapper().readTree(table);
                    live.put("handId", handId).put("stateVersion", ++seq);
                    codec.write(out, Message.event(MessageType.HAND_STARTED, "ui-test", handId, seq, codec.mapper().createObjectNode()));
                    codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", handId, seq, live));
                    ObjectNode result = codec.mapper().createObjectNode();
                    var winners = result.putArray("winners");
                    winners.addObject().put("userId", 1).put("username", "Cycle " + cycle).put("amount", 250);
                    winners.addObject().put("userId", 2).put("username", "Mai").put("amount", 350);
                    var mainPot = result.putArray("pots").addObject().put("name", "MAIN POT").put("amount", 500);
                    mainPot.putArray("winners").addObject().put("userId", 1).put("amount", 250);
                    ((com.fasterxml.jackson.databind.node.ArrayNode) mainPot.path("winners")).addObject().put("userId", 2).put("amount", 250);
                    var sidePot = ((com.fasterxml.jackson.databind.node.ArrayNode) result.path("pots")).addObject().put("name", "SIDE POT 1").put("amount", 100);
                    sidePot.putArray("winners").addObject().put("userId", 2).put("amount", 100);
                    codec.write(out, Message.event(MessageType.HAND_RESULT, "ui-test", handId, seq, result));
                    live.put("status", "BETWEEN_HANDS").putNull("currentTurnUserId");
                    codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", handId, ++seq, live));
                    int currentCycle = cycle;
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
                    while (!fx(() -> ((Label) root.lookup("#resultBannerLabel")).getText().contains("Cycle " + currentCycle + " +"))) {
                        if (System.nanoTime() > deadline) fail("Fixture hand not rendered"); Thread.sleep(10);
                    }
                    fx(() -> {
                        assertEquals(2, root.lookupAll(".seat-panel").size());
                        assertEquals(1, root.lookupAll(".celebration-overlay").size());
                        assertEquals(1, root.lookupAll(".deck-stack").size());
                        assertTrue(root.lookupAll(".payout-chip").size() <= 3);
                        assertFalse(button("readyButton").isDisabled(), "Decorations must not gate Ready");
                        return null;
                    });
                }
                fx(() -> { ((MenuButton) root.lookup("#displayMenu")).getItems().get(9).fire(); return null; });
                Thread.sleep(60);
                fx(() -> { assertTrue(root.lookupAll(".payout-chip").isEmpty(), "OFF must stop a running payout decoration too"); return null; });

                send(out, MessageType.LOGIN_SUCCESS, codec.mapper().createObjectNode().put("sessionId", "observer-session")
                    .put("userId", 3).put("username", "supervisor").put("accountRole", "OBSERVER"));
                assertEquals(MessageType.GET_LOBBY, codec.read(in).type());
                codec.write(out, Message.event(MessageType.TABLE_STATE, "ui-test", "hand", 8,
                    codec.mapper().readTree(table.replace("\"viewerRole\":\"PLAYER\"", "\"viewerRole\":\"OBSERVER\"").replace("\"stateVersion\":7", "\"stateVersion\":8"))));
                awaitVisible("tablePage");
                fx(() -> {
                    assertFalse(root.lookup("#bettingBox").isVisible());
                    assertFalse(button("readyButton").isVisible());
                    assertFalse(button("walletNavButton").isVisible());
                    screenshot("observer"); return null;
                });
                exerciseWindowSizes("observer", "centerBoard", "tableTitleLabel");
                fx(() -> { backButton().fire(); return null; });
                Message leave = codec.read(in); assertEquals(MessageType.STOP_SPECTATE, leave.type());
                codec.write(out, Message.reply(leave, MessageType.ERROR, codec.mapper().createObjectNode()
                    .put("code", "SERVER_ERROR").put("requestType", "STOP_SPECTATE").put("message", "Test leave rejected")));
                long leaveDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!fx(() -> ((Label) root.lookup("#statusLabel")).getText().equals("Test leave rejected"))) {
                    if (System.nanoTime() > leaveDeadline) fail("Leave failure not displayed"); Thread.sleep(20);
                }
                fx(() -> { assertTrue(root.lookup("#tablePage").isVisible()); backButton().fire(); return null; });
                leave = codec.read(in); assertEquals(MessageType.STOP_SPECTATE, leave.type());
                codec.write(out, Message.reply(leave, MessageType.LOBBY_SNAPSHOT, codec.mapper().createObjectNode()));
                awaitVisible("homePage");

                send(out, MessageType.LOGIN_SUCCESS, codec.mapper().createObjectNode().put("sessionId", "admin-session")
                    .put("userId", 99).put("username", "admin_test").put("accountRole", "ADMIN"));
                assertEquals(MessageType.GET_ADMIN_DASHBOARD, codec.read(in).type());
                sendArray(out, MessageType.ADMIN_DASHBOARD,
                    "{\"requests\":[{\"id\":1,\"username\":\"Mai\",\"amount\":5000,\"status\":\"PENDING\"}],"
                    + "\"users\":[{\"id\":1,\"username\":\"Mai\",\"role\":\"PLAYER\",\"status\":\"ACTIVE\",\"chips\":10000,\"online\":false},"
                    + "{\"id\":2,\"username\":\"Supervisor\",\"role\":\"OBSERVER\",\"status\":\"ACTIVE\",\"chips\":10000,\"online\":true},"
                    + "{\"id\":99,\"username\":\"admin_test\",\"role\":\"ADMIN\",\"status\":\"ACTIVE\",\"online\":true}]}" );
                awaitVisible("adminPage");
                long adminDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (fx(() -> ((javafx.scene.layout.VBox) root.lookup("#adminUsersList")).getChildren().size()) != 3) {
                    if (System.nanoTime() > adminDeadline) fail("Admin users not rendered"); Thread.sleep(20);
                }
                fx(() -> {
                    assertFalse(button("homeNavButton").isVisible()); assertFalse(button("walletNavButton").isVisible());
                    screenshot("admin"); field("adminSearchField").setText("mai");
                    assertEquals(1, ((javafx.scene.layout.VBox) root.lookup("#adminUsersList")).getChildren().size()); return null;
                });
                exerciseWindowSizes("admin", "adminPage", "adminSearchField");
                fx(() -> {
                    MenuButton display = (MenuButton) root.lookup("#displayMenu");
                    assertEquals(10, display.getItems().size());
                    for (int i = 0; i < 3; i++) {
                        display.getItems().get(i).fire();
                        var area = javafx.stage.Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                            stage.getWidth(), stage.getHeight()).getFirst().getVisualBounds();
                        assertTrue(stage.getX() >= area.getMinX() - 1 && stage.getY() >= area.getMinY() - 1);
                        assertTrue(stage.getX() + stage.getWidth() <= area.getMaxX() + 1);
                        assertTrue(stage.getY() + stage.getHeight() <= area.getMaxY() + 1);
                    }
                    assertFalse(stage.isMaximized());
                    assertFalse(stage.isFullScreen());
                    stage.getScene().getAccelerators().get(new KeyCodeCombination(KeyCode.F11)).run();
                    assertTrue(stage.isFullScreen());
                    return null;
                });
                Thread.sleep(250);
                fx(() -> {
                    stage.getScene().getAccelerators().get(new KeyCodeCombination(KeyCode.F11)).run();
                    assertFalse(stage.isFullScreen());
                    ((MenuButton) root.lookup("#displayMenu")).getItems().get(4).fire();
                    assertTrue(stage.isMaximized());
                    stage.setIconified(true); return null;
                });
                Thread.sleep(200);
                fx(() -> { assertTrue(stage.isIconified()); return null; });
                send(out, MessageType.PING, codec.mapper().createObjectNode());
                assertEquals(MessageType.PONG, codec.read(in).type(), "Minimized window must keep its connection alive");
                fx(() -> {
                    stage.setIconified(false);
                    assertFalse(stage.isIconified());
                    assertTrue(stage.isShowing());
                    assertTrue(((Label) root.lookup("#userLabel")).getText().contains("admin_test"));
                    return null;
                });
            }
        } finally {
            fx(() -> {
                if (root != null && root.lookup("#displayMenu") instanceof MenuButton menu && menu.getItems().size() == 10)
                    menu.getItems().get(7).fire(); // new feature defaults to full; do not leave fixture's OFF setting behind
                if (controller != null) controller.shutdown(); if (stage != null) stage.close(); return null;
            });
            Platform.exit();
        }
    }

    private void send(DataOutputStream out, MessageType type, ObjectNode payload) throws IOException {
        codec.write(out, Message.of(type, payload));
    }
    private void sendArray(DataOutputStream out, MessageType type, String json) throws IOException {
        codec.write(out, Message.of(type, codec.mapper().readTree(json)));
    }
    private Button button(String id) { return (Button) root.lookup("#" + id); }
    private Button backButton() {
        return (Button) root.lookup("#tablePage").lookupAll(".button").stream()
            .filter(node -> ((Button) node).getText().contains("Về sảnh")).findFirst().orElseThrow();
    }
    private TextField field(String id) { return (TextField) root.lookup("#" + id); }
    private void awaitVisible(String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(() -> root.lookup("#" + id).isVisible())) {
            if (System.nanoTime() > deadline) fail("Page not visible: " + id);
            Thread.sleep(20);
        }
        fx(() -> null); // Drain queued prompts after the snapshot.
    }
    private void awaitEnabled(String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(() -> root.lookup("#" + id).isVisible() && !root.lookup("#" + id).isDisabled())) {
            if (System.nanoTime() > deadline) fail("Control not enabled: " + id); Thread.sleep(20);
        }
    }
    private void awaitHidden(String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (fx(() -> root.lookup("#" + id).isVisible())) {
            if (System.nanoTime() > deadline) fail("Control still visible: " + id); Thread.sleep(20);
        }
    }
    private void awaitDisabled(String id) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(() -> root.lookup("#" + id).isDisabled())) {
            if (System.nanoTime() > deadline) fail("Control not disabled: " + id); Thread.sleep(20);
        }
    }
    private <T> T fx(Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task); Platform.runLater(future); return future.get(15, TimeUnit.SECONDS);
    }
    private void screenshot(String name) throws IOException {
        screenshot(stage.getScene().getRoot(), name);
    }
    private void exerciseWindowSizes(String context, String... importantIds) throws Exception {
        // Small logical viewports also model the space available at high OS display scaling.
        for (int[] size : new int[][]{{360, 280}, {640, 360}, {800, 450}, {960, 640}, {800, 1000}, {1600, 500}, {1280, 800}, {1600, 900}}) {
            fx(() -> { stage.setWidth(size[0]); stage.setHeight(size[1]); return null; });
            Thread.sleep(180); // Wait for the native resize and JavaFX layout pulse.
            fx(() -> {
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                var contentBounds = root.localToScene(root.getLayoutBounds());
                assertEquals(0, contentBounds.getMinX(), .5);
                assertEquals(0, contentBounds.getMinY(), .5);
                assertEquals(stage.getScene().getWidth(), contentBounds.getWidth(), .5);
                assertEquals(stage.getScene().getHeight(), contentBounds.getHeight(), .5);
                for (String id : importantIds) assertInViewport(root.lookup("#" + id), context + "/" + id);
                assertInViewport(root.lookup("#displayMenu"), context + "/displayMenu");
                for (var seat : root.lookupAll(".seat-panel")) if (root.lookup("#tablePage").isVisible())
                    assertInViewport(seat, context + "/seat");
                if (root.lookup("#tablePage").isVisible()) {
                    var seats = new java.util.ArrayList<>(root.lookupAll(".seat-panel"));
                    for (int i = 0; i < seats.size(); i++) for (int j = i + 1; j < seats.size(); j++)
                        assertFalse(seats.get(i).localToScene(seats.get(i).getBoundsInLocal()).intersects(
                            seats.get(j).localToScene(seats.get(j).getBoundsInLocal())), "Seats overlap after resize");
                    if (root.lookup("#bettingBox").isVisible())
                        for (var control : root.lookup("#bettingBox").lookupAll(".button")) if (control.isVisible())
                            assertInViewport(control, context + "/action");
                }
                if (size[0] == 960) screenshot(context + "-960x640");
                return null;
            });
        }
    }
    private void assertInViewport(javafx.scene.Node node, String name) {
        var bounds = node.localToScene(node.getLayoutBounds());
        assertTrue(bounds.getMinX() >= -.5 && bounds.getMinY() >= -.5
            && bounds.getMaxX() <= stage.getScene().getWidth() + .5
            && bounds.getMaxY() <= stage.getScene().getHeight() + .5, name + " clipped: " + bounds
            + " in " + stage.getScene().getWidth() + "x" + stage.getScene().getHeight());
    }
    private void screenshot(Parent view, String name) throws IOException {
        view.applyCss(); view.layout();
        WritableImage pixels = view.snapshot(null, null);
        BufferedImage png = new BufferedImage((int) pixels.getWidth(), (int) pixels.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < png.getHeight(); y++) for (int x = 0; x < png.getWidth(); x++)
            png.setRGB(x, y, pixels.getPixelReader().getArgb(x, y));
        Path output = Path.of(System.getProperty("poker.ui.screenshotDir", "target/ui-smoke"));
        Files.createDirectories(output);
        ImageIO.write(png, "png", output.resolve(name + ".png").toFile());
    }
}
