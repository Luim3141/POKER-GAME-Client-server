package com.poker.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.poker.client.network.ClientEventListener;
import com.poker.client.network.PokerClient;
import com.poker.game.Street;
import com.poker.model.Card;
import com.poker.model.AccountRole;
import com.poker.model.PlayerStatus;
import com.poker.model.Rank;
import com.poker.model.Role;
import com.poker.model.Suit;
import com.poker.model.TableStatus;
import com.poker.model.GlorySummary;
import com.poker.protocol.Message;
import com.poker.protocol.MessageType;
import com.poker.protocol.ProtocolCodec;
import com.poker.server.table.SeatSnapshot;
import com.poker.server.table.TableSnapshot;
import com.poker.server.table.TableAction;
import com.poker.game.HandPhase;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainController {
    private static final NumberFormat NUMBERS = NumberFormat.getIntegerInstance(Locale.US);

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField tableIdField;
    @FXML private TextField smallBlindField;
    @FXML private TextField betAmountField;
    @FXML private TextField inviteUserField;
    @FXML private TextField seatsField;
    @FXML private TextField coinAmountField;
    @FXML private TextField adminSearchField;
    @FXML private Button coinSubmitButton;
    @FXML private Label statusLabel;
    @FXML private Label userLabel;
    @FXML private Label walletLabel;
    @FXML private Label tableTitleLabel;
    @FXML private Label tableStatusLabel;
    @FXML private Label roleLabel;
    @FXML private Label playersCountLabel;
    @FXML private Label blindsLabel;
    @FXML private Label streetLabel;
    @FXML private Label timerLabel;
    @FXML private Label resultBannerLabel;
    @FXML private Label actionInfoLabel;
    @FXML private Label turnHintLabel;
    @FXML private Label amountLabel;
    @FXML private VBox tablesList;
    @FXML private VBox onlineList;
    @FXML private VBox rankingList;
    @FXML private VBox historyList;
    @FXML private VBox coinRequestsList;
    @FXML private VBox adminRequestsList;
    @FXML private VBox adminUsersList;
    @FXML private VBox centerBoard;
    @FXML private HBox boardCards;
    @FXML private FlowPane potsRow;
    @FXML private FlowPane actionsRow;
    @FXML private Label totalPotLabel;
    @FXML private VBox bettingBox;
    @FXML private HBox wagerBox;
    @FXML private Slider wagerSlider;
    @FXML private Label wagerHintLabel;
    @FXML private Pane tableCanvas;
    @FXML private Pane loginPage;
    @FXML private Pane homePage;
    @FXML private Pane tablePage;
    @FXML private Pane historyPage;
    @FXML private Pane coinPage;
    @FXML private Pane adminPage;
    @FXML private HBox createTableBar;
    @FXML private HBox joinBar;
    @FXML private HBox inviteBox;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Button homeNavButton;
    @FXML private Button historyNavButton;
    @FXML private Button walletNavButton;
    @FXML private Button adminNavButton;
    @FXML private Button logoutButton;
    @FXML private Button readyButton;
    @FXML private Button foldButton;
    @FXML private Button checkButton;
    @FXML private Button callButton;
    @FXML private Button betButton;
    @FXML private Button raiseButton;
    @FXML private Button allInButton;

    private final Map<Integer, Region> seatNodes = new LinkedHashMap<>();
    private final Map<Long, List<Card>> revealedCards = new LinkedHashMap<>();
    private volatile PokerClient client;
    private final java.util.concurrent.ExecutorService connectionWorker = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "poker-ui-connect"); thread.setDaemon(true); return thread;
    });
    private final java.util.concurrent.atomic.AtomicBoolean connecting = new java.util.concurrent.atomic.AtomicBoolean();
    private Timeline reconnectTimer;
    private volatile String resumeToken;
    private volatile boolean shuttingDown;
    private String connectedHost;
    private int connectedPort;
    private long actionPromptVersion = -1;
    private Timeline timer;
    private int remainingSeconds;
    private boolean selfReady;
    private boolean leavingTable;
    private boolean controlledLogout;
    private boolean actionPending;
    private JsonNode pendingHandResult;
    private Timeline payoutAnimation;
    private long toCall;
    private long minimumRaiseTo;
    private long currentBet;
    private long actionStack;
    private long streetContribution;
    private boolean canRaise;
    private boolean updatingWager;
    private WagerRange wagerRange;
    private long turnDeadlineEpochMs;
    private long tableSnapshotReceivedAt;
    private boolean authInProgress;
    private boolean seatReleasePending;
    private TableSnapshot renderedTable;
    private final ProtocolCodec uiCodec = new ProtocolCodec();
    private JsonNode adminDashboard;
    private TableEffects effects;
    private boolean dealRequested;
    private String lastResultKey;
    private final Map<Long, TableAction> visibleActions = new LinkedHashMap<>();
    private final Map<Long, Long> winnerAmounts = new LinkedHashMap<>();
    private long lastActionSequence;
    private String dealKey;

    @FXML private void initialize() {
        try (var input = getClass().getResourceAsStream("/com/poker/ui/client.properties")) {
            var properties = new java.util.Properties(); if (input != null) properties.load(input);
            hostField.setText(properties.getProperty("server.host", "127.0.0.1"));
            portField.setText(properties.getProperty("server.port", "19090"));
        } catch (java.io.IOException ignored) { hostField.setText("127.0.0.1"); }
        tableCanvas.widthProperty().addListener((ignored, oldValue, newValue) -> layoutTable());
        tableCanvas.heightProperty().addListener((ignored, oldValue, newValue) -> layoutTable());
        effects = new TableEffects(tableCanvas);
        tableCanvas.sceneProperty().addListener((ignored, previous, scene) -> {
            if (scene != null) scene.windowProperty().addListener((observable, oldWindow, window) -> {
                if (window instanceof javafx.stage.Stage stage) stage.iconifiedProperty().addListener(
                    (property, wasMinimized, minimized) -> { effects.setMinimized(minimized); if (minimized) stopPayoutDecoration(); });
            });
        });
        renderBoard(null);
        renderPots(null);
        showEmpty(tablesList, "Chưa có bàn nào. Hãy tạo bàn đầu tiên.");
        showEmpty(onlineList, "Đăng nhập để xem người chơi.");
        showEmpty(rankingList, "Bảng xếp hạng sẽ xuất hiện tại đây.");
        showEmpty(historyList, "Bạn chưa tải lịch sử ván đấu.");
        showPage(loginPage);
        setAuthenticatedNavigation(false);
        bettingBox.setDisable(true);
        wagerSlider.valueProperty().addListener((ignored, oldValue, newValue) -> {
            if (updatingWager || wagerRange == null || wagerSlider.isDisabled()) return;
            updatingWager = true;
            betAmountField.setText(Long.toString(wagerRange.amountAt((int) Math.round(newValue.doubleValue()))));
            updatingWager = false;
            validateWagerText();
        });
        betAmountField.textProperty().addListener((ignored, oldValue, newValue) -> {
            if (!updatingWager) validateWagerText();
        });
        passwordField.setOnAction(event -> login());
        statusLabel.setTooltip(new javafx.scene.control.Tooltip());
        adminSearchField.textProperty().addListener((ignored, oldValue, newValue) -> {
            if (adminDashboard != null) renderAdminDashboard(adminDashboard);
        });
        reconnectTimer = new Timeline(new KeyFrame(Duration.seconds(5), event -> {
            if (loginPage.isVisible() && !controlledLogout && (client == null || !client.isConnected())) connectAutomatically();
            else if (homePage.isVisible() && seatReleasePending && client != null && client.isConnected()) lobby();
        }));
        reconnectTimer.setCycleCount(Timeline.INDEFINITE); reconnectTimer.play();
        Platform.runLater(this::connectAutomatically);
    }

    @FXML private void register() {
        authenticate(true);
    }
    @FXML private void login() {
        authenticate(false);
    }
    private void authenticate(boolean register) {
        run(() -> {
            String username = required(usernameField, "Username"), password = passwordField.getText();
            if (password == null || password.isEmpty()) throw new IllegalArgumentException("Mật khẩu không được để trống");
            String host = required(hostField, "Host"); int port = integer(portField, "Port", 1, 65_535);
            loginButton.setDisable(true); registerButton.setDisable(true);
            authInProgress = true;
            showStatus("Đang kết nối và xác thực…", "status-warn");
            connectionWorker.execute(() -> {
                try {
                    PokerClient active = connectOnWorker(host, port);
                    if (register) active.register(username, password); else active.login(username, password);
                } catch (Exception error) {
                    Platform.runLater(() -> { authInProgress = false; loginButton.setDisable(false); registerButton.setDisable(false);
                        showStatus("Không thể đăng nhập: " + message(error), "status-error"); });
                }
            });
        });
    }
    private void connectAutomatically() {
        if (shuttingDown || !connecting.compareAndSet(false, true)) return;
        String host; int port;
        try { host = required(hostField, "Host"); port = integer(portField, "Port", 1, 65_535); }
        catch (Exception error) { connecting.set(false); showStatus(message(error), "status-error"); return; }
        showStatus("Đang kết nối " + host + ":" + port + "…", "status-warn");
        connectionWorker.execute(() -> {
            try {
                PokerClient active = connectOnWorker(host, port);
                String token = resumeToken;
                if (token != null) active.resume(token);
                Platform.runLater(() -> {
                    if (active == client && !active.state().authenticated() && !authInProgress)
                        showStatus(token == null ? "Đã kết nối — đăng nhập để chơi" : "Đang khôi phục phiên…", "status-ok");
                });
            } catch (Exception error) {
                Platform.runLater(() -> { loginButton.setDisable(false); registerButton.setDisable(false);
                    showStatus("Chưa kết nối được server — sẽ tự thử lại", "status-error"); });
            } finally { connecting.set(false); }
        });
    }
    private PokerClient connectOnWorker(String host, int port) throws Exception {
        PokerClient current = client;
        if (current != null && current.isConnected() && host.equals(connectedHost) && port == connectedPort) return current;
        var source = new java.util.concurrent.atomic.AtomicReference<PokerClient>();
        PokerClient next = new PokerClient(new ClientEventListener() {
            @Override public void onMessage(Message message) { acceptMessage(source.get(), message); }
            @Override public void onDisconnected(Throwable cause) { acceptDisconnection(source.get(), cause); }
        });
        source.set(next);
        try { next.connect(host, port); }
        catch (Exception error) { next.close(); throw error; }
        if (shuttingDown) { next.close(); throw new IllegalStateException("Application is closing"); }
        client = next; connectedHost = host; connectedPort = port;
        if (current != null) current.close();
        return next;
    }
    @FXML private void lobby() { sendToServer(() -> requireClient().requestLobby()); }
    @FXML private void history() { sendToServer(() -> requireClient().requestHistory()); }
    @FXML private void showHistory() {
        showPage(historyPage);
        history();
    }
    @FXML private void showWallet() { showPage(coinPage); loadCoinRequests(); }
    @FXML private void showAdmin() { showPage(adminPage); loadAdminDashboard(); }
    @FXML private void loadCoinRequests() { sendToServer(() -> requireClient().requestCoinRequests()); }
    @FXML private void submitCoinRequest() {
        run(() -> { long amount = number(coinAmountField, "Số coin", 1, 100_000); coinSubmitButton.setDisable(true);
            sendToServer(() -> requireClient().submitCoinRequest(amount)); });
    }
    @FXML private void loadAdminDashboard() { sendToServer(() -> requireClient().requestAdminDashboard()); }

    @FXML private void goHome() {
        if (client != null && client.state().table() != null) { backToLobby(); return; }
        showPage(homePage);
        lobby();
    }
    @FXML private void backToContext() {
        TableSnapshot table = client == null ? null : client.state().table();
        if (table != null && !leavingTable) renderTable(table, true);
        else goHome();
    }

    @FXML private void createTable() {
        run(() -> { long blind = number(smallBlindField, "Small blind", 1, Long.MAX_VALUE / 2); int seats = integer(seatsField, "Số ghế", 2, 8);
            sendToServer(() -> requireClient().createTable(blind, seats)); });
    }
    @FXML private void joinTable() { run(() -> { String id = required(tableIdField, "Mã bàn"); sendToServer(() -> requireClient().joinTable(id)); }); }
    @FXML private void spectate() { run(() -> { String id = required(tableIdField, "Mã bàn"); sendToServer(() -> requireClient().spectate(id)); }); }
    @FXML private void invite() { run(() -> { String name = required(inviteUserField, "Username"); sendToServer(() -> requireClient().invite(name)); }); }
    @FXML private void acceptInvite() { run(() -> { String id = required(tableIdField, "Mã bàn"); sendToServer(() -> requireClient().answerInvitation(id, true)); }); }

    @FXML private void ready() { boolean ready = !selfReady; readyButton.setDisable(true); sendToServer(() -> requireClient().ready(ready)); }
    @FXML private void fold() { action("FOLD", 0); }
    @FXML private void check() { action("CHECK", 0); }
    @FXML private void call() { action("CALL", 0); }
    @FXML private void allIn() { action("ALL_IN", 0); }
    @FXML private void bet() { actionWithWager("BET"); }
    @FXML private void raise() { actionWithWager("RAISE"); }

    @FXML private void backToLobby() {
        TableSnapshot table = client == null ? null : client.state().table();
        if (table == null) { showPage(homePage); lobby(); return; }
        if (table.viewerRole() == Role.PLAYER && table.status() == TableStatus.IN_HAND &&
            !confirm("Rời bàn?", "Rời lúc ván đang chạy sẽ tự động Fold. Chip đã cược vẫn nằm trong pot.")) return;
        run(() -> {
            leavingTable = true;
            effects.cancel(); stopPayoutDecoration();
            bettingBox.setDisable(true);
            showStatus("Đang rời bàn…", "status-warn");
            sendToServer(() -> {
                if (table.viewerRole() == Role.OBSERVER) requireClient().stopSpectating();
                else requireClient().leaveTable();
            });
        });
    }

    @FXML private void logout() {
        TableSnapshot table = client == null ? null : client.state().table();
        if (table != null && table.status() == TableStatus.IN_HAND &&
            !confirm("Đăng xuất?", "Đăng xuất sẽ Fold và rời bàn. Phần cược đã được theo vẫn nằm trong pot.")) return;
        controlledLogout = true;
        sendToServer(() -> requireClient().logout());
    }

    private void action(String type, long amount) {
        if (actionPending) return;
        run(() -> {
            if (renderedTable == null || client.state().table() == null || actionPromptVersion != client.state().table().stateVersion())
                throw new IllegalStateException("Bàn đang cập nhật — vui lòng chờ lượt mới");
            actionPending = true;
            bettingBox.setDisable(true);
            showStatus("Đang gửi " + type + "…", "status-warn");
            TableSnapshot expected = renderedTable;
            sendToServer(() -> requireClient().action(type, amount, expected));
        });
    }
    private void actionWithWager(String type) { run(() -> action(type, wagerAmount())); }
    private long wagerAmount() {
        long value = number(betAmountField, "Mức cược", 1, Long.MAX_VALUE);
        long max = streetContribution + actionStack;
        if (value > max || value < minimumRaiseTo && value < max)
            throw new IllegalArgumentException("Mức hợp lệ: " + money(minimumRaiseTo) + "–" + money(max) + " (hoặc all-in)");
        return value;
    }

    private void validateWagerText() {
        if (wagerRange == null) return;
        boolean valid = false;
        try {
            long amount = Long.parseLong(betAmountField.getText().trim());
            if (amount >= wagerRange.minimum() && amount <= wagerRange.maximum()) {
                updatingWager = true;
                wagerSlider.setValue(wagerRange.positionOf(amount));
                updatingWager = false;
                valid = true;
            }
        } catch (NumberFormatException ignored) { /* Preserve partially typed input. */ }
        wagerHintLabel.setText(valid ? "Hợp lệ: " + money(wagerRange.minimum()) + "–" + money(wagerRange.maximum())
            : "Nhập số nguyên từ " + money(wagerRange.minimum()) + " đến " + money(wagerRange.maximum()));
        wagerHintLabel.getStyleClass().remove("loss-label");
        if (!valid) wagerHintLabel.getStyleClass().add("loss-label");
    }

    private void acceptMessage(PokerClient source, Message message) {
        Platform.runLater(() -> {
            if (shuttingDown || source != client) return;
            switch (message.type()) {
                case LOGIN_SUCCESS -> loginSucceeded();
                case LOBBY_SNAPSHOT -> {
                    renderLobby(message.payload());
                    if (leavingTable) {
                        leavingTable = false; renderedTable = null; client.state().clearTable(); clearHandResult(); showPage(homePage);
                        showStatus(message.payload().path("seatReleasePending").asBoolean()
                            ? "Đã Fold và về sảnh; chờ ván hiện tại kết thúc trước khi vào bàn khác"
                            : "Đã về sảnh", "status-ok");
                    }
                }
                case HISTORY_SNAPSHOT -> renderHistory(message.payload());
                case COIN_REQUEST_SNAPSHOT -> renderCoinRequests(message.payload());
                case ADMIN_DASHBOARD -> renderAdminDashboard(message.payload());
                case WALLET_UPDATED -> { walletLabel.setText(money(client.state().chips()) + " chips"); renderProfile(); }
                case ACCOUNT_LOCKED -> { resumeToken = null; showStatus(message.payload().path("message").asText("Tài khoản đã bị khóa"), "status-error"); }
                case TABLE_STATE, STATE_SNAPSHOT -> {
                    tableSnapshotReceivedAt = System.currentTimeMillis();
                    renderTable(uiCodec.mapper().convertValue(message.payload(), TableSnapshot.class), !historyPage.isVisible() && !coinPage.isVisible());
                }
                case ACTION_REQUIRED -> applyActionPrompt(message);
                case ACTION_ACCEPTED -> {
                    actionPending = false;
                    if (renderedTable != null && renderedTable.status() == TableStatus.IN_HAND && renderedTable.phase() != HandPhase.DEALING)
                        showStatus("Hành động hợp lệ", "status-ok");
                }
                case TABLE_ACTION -> acceptTableAction(message);
                case ACTION_REJECTED, ERROR -> {
                    String command = message.payload().path("requestType").asText();
                    if (command.equals("LEAVE_TABLE") || command.equals("STOP_SPECTATE")) {
                        leavingTable = false;
                        if (renderedTable != null) renderTable(renderedTable, true);
                    }
                    if (command.equals("LOGOUT")) controlledLogout = false;
                    if (command.equals("SUBMIT_COIN_REQUEST")) coinSubmitButton.setDisable(false);
                    actionPending = false; configureActions();
                    if (client.state().table() != null && client.state().table().status() != TableStatus.IN_HAND)
                        readyButton.setDisable(false);
                    if (message.type() == MessageType.ERROR && !client.state().authenticated()) {
                        resumeToken = null; authInProgress = false; loginButton.setDisable(false); registerButton.setDisable(false);
                    }
                    showStatus(message.payload().path("message").asText("Yêu cầu bị từ chối"), "status-error");
                }
                case INVITE_RECEIVED -> {
                    tableIdField.setText(message.payload().path("tableId").asText());
                    showStatus("Lời mời từ " + message.payload().path("from").asText(), "status-warn");
                }
                case HAND_STARTED -> { clearHandResult(); dealRequested = true; showStatus("Ván mới bắt đầu", "status-ok"); }
                case HAND_RESULT -> {
                    String key = message.tableId() + ":" + message.handId();
                    if (!key.equals(lastResultKey) && renderedTable != null &&
                        renderedTable.tableId().equals(message.tableId()) && java.util.Objects.equals(renderedTable.handId(), message.handId())) {
                        lastResultKey = key;
                        renderHandResult(message.payload());
                    }
                }
                default -> { }
            }
        });
    }

    private void loginSucceeded() {
        controlledLogout = false;
        authInProgress = false;
        resumeToken = client.state().sessionId();
        loginButton.setDisable(false); registerButton.setDisable(false);
        passwordField.clear();
        userLabel.setText("@" + client.state().username());
        renderProfile();
        walletLabel.setText(client.state().accountRole() == AccountRole.PLAYER ? money(client.state().chips()) + " chips" : client.state().accountRole().name());
        setAuthenticatedNavigation(true);
        showStatus("Đã đăng nhập", "status-ok");
        if (client.state().accountRole() == AccountRole.ADMIN) { showPage(adminPage); loadAdminDashboard(); }
        else { showPage(homePage); lobby(); }
    }

    private void renderLobby(JsonNode payload) {
        boolean observerAccount = client.state().accountRole() == AccountRole.OBSERVER;
        boolean wasWaitingForSeat = seatReleasePending;
        seatReleasePending = payload.path("seatReleasePending").asBoolean();
        if (wasWaitingForSeat && !seatReleasePending) showStatus("Ghế đã được giải phóng — bạn có thể vào bàn mới", "status-ok");
        createTableBar.setVisible(!observerAccount && !seatReleasePending); createTableBar.setManaged(createTableBar.isVisible());
        joinBar.setVisible(!observerAccount && !seatReleasePending); joinBar.setManaged(joinBar.isVisible());
        tablesList.getChildren().clear();
        for (JsonNode table : payload.path("tables")) {
            String id = table.path("tableId").asText();
            String status = table.path("status").asText();
            VBox info = new VBox(3,
                styled("Bàn " + shortId(id), "row-title"),
                styled("SB " + money(table.path("smallBlind").asLong()) + "  •  " +
                    table.path("occupiedSeats").asInt() + "/" + table.path("maxPlayers").asInt() + " ghế  •  " +
                    statusText(status), "row-meta"));
            HBox.setHgrow(info, Priority.ALWAYS);
            Button open = observerAccount
                ? button("Xem bàn", "ghost-button", () -> { tableIdField.setText(id); spectate(); })
                : button("Vào chơi", "primary-button", () -> { tableIdField.setText(id); joinTable(); });
            if (!observerAccount) open.setDisable(seatReleasePending || "IN_HAND".equals(status) ||
                table.path("occupiedSeats").asInt() >= table.path("maxPlayers").asInt());
            HBox row = new HBox(9, info, open);
            row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("table-row");
            row.setOnMouseClicked(event -> tableIdField.setText(id));
            tablesList.getChildren().add(row);
        }
        if (tablesList.getChildren().isEmpty()) showEmpty(tablesList, seatReleasePending
            ? "Đang chờ ván cũ kết thúc để giải phóng ghế."
            : observerAccount ? "Chưa có bàn đang mở để quan sát." : "Chưa có bàn nào. Hãy tạo bàn đầu tiên.");

        onlineList.getChildren().clear();
        for (JsonNode user : payload.path("onlineUsers")) {
            HBox row = new HBox(8, styled("●", "online-dot"), styled(user.path("username").asText(), "row-title"));
            row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("online-row"); onlineList.getChildren().add(row);
        }
        if (onlineList.getChildren().isEmpty()) showEmpty(onlineList, "Không có người chơi online.");

        rankingList.getChildren().clear();
        int place = 1;
        for (JsonNode user : payload.path("ranking")) {
            String username = user.path("username").asText();
            Label rank = styled("#" + place++, "rank-number");
            VBox info = new VBox(1, styled(username, "row-title"),
                styled(user.path("wins").asLong() + " thắng / " + (user.path("gamesPlayed").asLong() - user.path("wins").asLong()) + " thua", "row-meta"));
            GlorySummary glory = user.hasNonNull("glory") ? uiCodec.mapper().convertValue(user.path("glory"), GlorySummary.class) : null;
            Label tiers = styled(glory == null ? "Chưa có thống kê" : "Tài sản: " + glory.wealthTier() + " · Thành tích: " + glory.achievementTier(), "glory-badge");
            if (glory != null) tiers.setTooltip(new javafx.scene.control.Tooltip(gloryDescription(glory)));
            info.getChildren().add(tiers);
            HBox.setHgrow(info, Priority.ALWAYS);
            HBox row = new HBox(9, rank, info, styled(money(user.path("chips").asLong()), "wallet-label"));
            row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("ranking-row"); rankingList.getChildren().add(row);
        }
        if (rankingList.getChildren().isEmpty()) showEmpty(rankingList, "Chưa có dữ liệu xếp hạng.");
    }

    private void renderTable(TableSnapshot table, boolean navigate) {
        boolean playerAccount = client.state().accountRole() == AccountRole.PLAYER;
        inviteBox.setVisible(playerAccount); inviteBox.setManaged(playerAccount);
        if (table == null || leavingTable) return;
        TableSnapshot previous = renderedTable;
        boolean sameHand = previous != null && previous.tableId().equals(table.tableId()) && java.util.Objects.equals(previous.handId(), table.handId());
        if (!sameHand) {
            effects.cancel(); revealedCards.clear(); visibleActions.clear(); winnerAmounts.clear();
            lastActionSequence = 0; dealKey = null; actionsRow.getChildren().clear();
        }
        renderedTable = table;
        if (!table.winnerPayouts().isEmpty()) { winnerAmounts.clear(); winnerAmounts.putAll(table.winnerPayouts()); }
        for (TableAction action : table.recentActions()) {
            visibleActions.put(action.sequence(), action);
            lastActionSequence = Math.max(lastActionSequence, action.sequence());
        }
        trimActions(); renderActions();
        tableIdField.setText(table.tableId());
        tableTitleLabel.setText("Bàn " + shortId(table.tableId()) + "  ·  NLHE");
        tableStatusLabel.setText(statusText(table.status().name()));
        roleLabel.setText(table.viewerRole() == Role.OBSERVER ? "QUAN SÁT" : "NGƯỜI CHƠI");
        playersCountLabel.setText(table.seats().size() + " / " + table.maxPlayers() + " người chơi");
        blindsLabel.setText(money(table.smallBlind()) + " / " + money(table.bigBlind()));
        streetLabel.setText(table.street() == null ? "CHỜ VÁN MỚI" : streetText(table.street()));
        renderBoard(table); renderPots(table);

        var occupied = table.seats().stream().map(SeatSnapshot::seat).collect(java.util.stream.Collectors.toSet());
        seatNodes.entrySet().removeIf(entry -> {
            if (occupied.contains(entry.getKey())) return false;
            tableCanvas.getChildren().remove(entry.getValue()); return true;
        });
        for (SeatSnapshot seat : table.seats()) {
            Region old = seatNodes.get(seat.seat());
            Region node = seatNode(seat, table); seatNodes.put(seat.seat(), node);
            if (old != node) { tableCanvas.getChildren().remove(old); tableCanvas.getChildren().add(node); }
        }
        if (navigate) showPage(tablePage); // Resume/spectate must reveal the table before starting remaining deal motion.
        layoutTable();
        effects.toFront();
        String currentDeal = table.tableId() + ":" + table.handId() + ":" + table.street();
        boolean newDeal = table.phase() == HandPhase.DEALING && !currentDeal.equals(dealKey);
        if ((newDeal || dealRequested) && table.status() == TableStatus.IN_HAND && table.street() == Street.PRE_FLOP) {
            dealRequested = false;
            dealKey = currentDeal;
            List<Node> dealt = new ArrayList<>();
            List<SeatSnapshot> order = table.seats().stream().filter(seat -> seat.handStatus() != PlayerStatus.WAITING)
                .sorted(java.util.Comparator.comparingInt(seat -> {
                    int distance = Math.floorMod(seat.seat() - table.dealerSeat(), table.maxPlayers());
                    return distance == 0 ? table.maxPlayers() : distance;
                })).toList();
            for (int pass = 0; pass < 2; pass++) for (SeatSnapshot seat : order) {
                HBox cards = (HBox) seatNodes.get(seat.seat()).getProperties().get("cards");
                if (cards.getChildren().size() > pass) dealt.add(cards.getChildren().get(pass));
            }
            effects.deal(dealt, () -> deckOrigin(), Math.max(0, table.dealDurationMillis() - table.dealRemainingMillis()));
        } else if (newDeal || (sameHand && table.board().size() > previous.board().size())) {
            dealKey = currentDeal;
            int start = table.phase() == HandPhase.DEALING ? Math.max(0, table.board().size() - table.dealCardCount()) : previous.board().size();
            effects.deal(new ArrayList<>(boardCards.getChildren().subList(start, table.board().size())), () -> deckOrigin(),
                Math.max(0, table.dealDurationMillis() - table.dealRemainingMillis()));
        } else if (table.phase() != HandPhase.DEALING) {
            effects.finishDeal(); // A late client converges immediately when the server begins BETTING.
        }
        renderProfile();

        SeatSnapshot me = table.seats().stream().filter(seat -> seat.userId() == client.state().userId()).findFirst().orElse(null);
        selfReady = me != null && me.ready();
        if (me != null && table.status() != TableStatus.IN_HAND) walletLabel.setText(money(me.stack()) + " chips");
        boolean observer = table.viewerRole() == Role.OBSERVER;
        boolean myTurn = !observer && table.status() == TableStatus.IN_HAND && table.currentTurnUserId() != null &&
            table.currentTurnUserId() == client.state().userId();
        if (!myTurn) actionPending = false;
        if (actionPromptVersion != table.stateVersion()) actionPromptVersion = -1;
        bettingBox.setVisible(myTurn && table.phase() != HandPhase.DEALING);
        bettingBox.setManaged(bettingBox.isVisible());
        bettingBox.setDisable(!myTurn || actionPending || actionPromptVersion != table.stateVersion());
        readyButton.setVisible(!observer && table.status() != TableStatus.IN_HAND);
        readyButton.setManaged(!observer && table.status() != TableStatus.IN_HAND);
        readyButton.setText(me != null && me.stack() <= 0 ? "Không đủ chip" : selfReady ? "Đã sẵn sàng · Hủy" :
            table.status() == TableStatus.BETWEEN_HANDS ? "Chơi ván tiếp theo" : "Sẵn sàng");
        readyButton.setDisable(me != null && me.stack() <= 0);
        actionInfoLabel.setText(observer ? "Chế độ quan sát" : table.status() == TableStatus.IN_HAND ? "Ván đang diễn ra" : readySummary(table));
        if (table.phase() == HandPhase.DEALING) actionInfoLabel.setText("Đang chia bài · Chia xong mới tính lượt cược");
        turnHintLabel.setText(observer ? "Chế độ giám sát · thấy bài của mọi người chơi · không được cược" :
            me != null && me.stack() == 0 && table.status() != TableStatus.IN_HAND
                ? "Bạn đã hết chip. Rời bàn, vào Ví coin và gửi yêu cầu; sau khi được duyệt có thể vào chơi lại."
                : turnHint(table, myTurn));
        int remaining = table.currentTurnUserId() == null ? 0 :
            (int) Math.max(0, table.turnSeconds() - (System.currentTimeMillis() - tableSnapshotReceivedAt) / 1000);
        if (myTurn && actionPromptVersion == table.stateVersion())
            remaining = (int) Math.max(0, (turnDeadlineEpochMs - System.currentTimeMillis() + 999) / 1000);
        updateTimer(remaining);
        if (table.status() == TableStatus.BETWEEN_HANDS && pendingHandResult != null) {
            JsonNode result = pendingHandResult;
            pendingHandResult = null;
            playPayoutAnimation(result, table);
        }
    }

    private Region seatNode(SeatSnapshot seat, TableSnapshot table) {
        Label avatar = styled(seat.username().isBlank() ? "?" : seat.username().substring(0, 1).toUpperCase(Locale.ROOT), "avatar");
        Label name = styled(seat.username(), "seat-name"); name.setMaxWidth(104); name.setMinWidth(0);
        name.setTooltip(new javafx.scene.control.Tooltip(seat.username()));
        VBox identity = new VBox(1, name, styled(money(seat.stack()) + " chips", "seat-stack"));
        identity.setMinWidth(0);
        HBox.setHgrow(identity, Priority.ALWAYS);
        HBox header = new HBox(7, avatar, identity); header.setAlignment(Pos.CENTER_LEFT);
        List<String> positions = new ArrayList<>();
        if (seat.seat() == table.dealerSeat()) positions.add("D");
        if (seat.seat() == table.smallBlindSeat()) positions.add("SB");
        if (seat.seat() == table.bigBlindSeat()) positions.add("BB");
        if (!positions.isEmpty()) header.getChildren().add(styled(String.join("·", positions), seat.seat() == table.dealerSeat() ? "dealer-chip" : "blind-chip"));

        Region existing = seatNodes.get(seat.seat());
        VBox panel = existing instanceof VBox box && java.util.Objects.equals(existing.getUserData(), seat.userId()) ? box : new VBox(3);
        HBox cards = (HBox) panel.getProperties().computeIfAbsent("cards", ignored -> new HBox(5)); cards.setAlignment(Pos.CENTER);
        List<Card> visibleCards = revealedCards.getOrDefault(seat.userId(), seat.holeCards());
        String signature = visibleCards.toString() + (table.status() == TableStatus.IN_HAND && seat.handStatus() != PlayerStatus.WAITING ? ":dealt" : ":waiting");
        if (!signature.equals(cards.getUserData())) {
            cards.getChildren().clear();
            if (!visibleCards.isEmpty()) visibleCards.forEach(card -> cards.getChildren().add(card(card)));
            else if (table.status() == TableStatus.IN_HAND && seat.handStatus() != PlayerStatus.WAITING) cards.getChildren().addAll(cardBack(), cardBack());
            cards.setUserData(signature);
        }
        String state = !seat.connected() ? "MẤT KẾT NỐI" : seat.ready() ? "SẴN SÀNG" : statusText(seat.handStatus().name());
        String bet = seat.streetContribution() > 0 ? "  ·  Bet " + money(seat.streetContribution()) : "";
        TableAction last = visibleActions.values().stream().filter(action -> action.userId() == seat.userId() && action.street() == table.street())
            .reduce((a, b) -> b).orElse(null);
        Long won = winnerAmounts.get(seat.userId());
        String badge = won != null ? "THẮNG +" + money(won) : last != null ? actionText(last) : state + bet;
        Label details = styled(badge, last != null || won != null ? "seat-action" : "seat-status"); details.setMaxWidth(168);
        if (last != null) details.getStyleClass().add("action-" + last.action().toLowerCase(Locale.ROOT));
        details.setTooltip(new javafx.scene.control.Tooltip(badge + "\n" + state + bet));
        Label glory = styled(seat.glory() == null ? "Chưa có thống kê" : "Tài sản " + seat.glory().wealthTier() + " · " + seat.glory().achievementTier(), "glory-badge");
        if (seat.glory() != null) glory.setTooltip(new javafx.scene.control.Tooltip(gloryDescription(seat.glory())));
        panel.getChildren().setAll(header, cards, details, glory);
        panel.setUserData(seat.userId());
        panel.setAlignment(Pos.CENTER); panel.setPrefSize(174, 142); panel.setMinSize(174, 142); panel.setMaxSize(174, 142);
        panel.getStyleClass().setAll("seat-panel");
        if (seat.glory() != null) panel.getStyleClass().add("glory-" + switch (seat.glory().achievementTier()) {
            case "Huyền thoại" -> "legend"; case "Kim cương" -> "diamond"; case "Vàng" -> "gold";
            case "Bạc" -> "silver"; case "Đồng" -> "bronze"; default -> "rookie";
        });
        if (seat.userId() == client.state().userId()) panel.getStyleClass().add("hero-seat");
        if (table.currentTurnUserId() != null && seat.userId() == table.currentTurnUserId()) panel.getStyleClass().add("active-turn");
        if (seat.handStatus() == PlayerStatus.FOLDED) panel.getStyleClass().add("folded-seat");
        if (won != null) { panel.getStyleClass().add("winner-seat"); avatar.getStyleClass().add("winner-avatar"); }
        return panel;
    }

    private String actionText(TableAction action) {
        String text = switch (action.action()) {
            case "CHECK", "FOLD" -> action.action();
            case "CALL" -> "CALL +" + money(action.paid());
            case "BET" -> "BET " + money(action.streetContribution());
            case "RAISE" -> "RAISE TO " + money(action.streetContribution()) + " · +" + money(action.paid());
            case "ALL_IN" -> "ALL-IN +" + money(action.paid());
            case "SMALL_BLIND" -> "SB +" + money(action.paid());
            case "BIG_BLIND" -> "BB +" + money(action.paid());
            default -> action.action();
        };
        return text + (action.automatic() && !action.action().endsWith("BLIND") ? " · TỰ ĐỘNG" : "");
    }

    private void acceptTableAction(Message message) {
        if (renderedTable == null || !renderedTable.tableId().equals(message.tableId()) ||
            !java.util.Objects.equals(renderedTable.handId(), message.handId())) return;
        TableAction action = uiCodec.mapper().convertValue(message.payload(), TableAction.class);
        if (action.sequence() <= lastActionSequence) return;
        lastActionSequence = action.sequence(); visibleActions.put(action.sequence(), action); trimActions(); renderActions();
        SeatSnapshot seat = renderedTable.seats().stream().filter(candidate -> candidate.userId() == action.userId()).findFirst().orElse(null);
        if (seat != null) {
            Region old = seatNodes.get(seat.seat()); Region panel = seatNode(seat, renderedTable);
            if (old != panel) { tableCanvas.getChildren().remove(old); tableCanvas.getChildren().add(panel); }
            seatNodes.put(seat.seat(), panel); layoutTable();
            if (action.paid() > 0) effects.betCoins(panel, () -> new javafx.geometry.Point2D(tableCanvas.getWidth() / 2, tableCanvas.getHeight() / 2 + 58), action.paid());
        }
    }

    private void trimActions() { while (visibleActions.size() > 16) visibleActions.remove(visibleActions.keySet().iterator().next()); }

    private void renderActions() {
        actionsRow.getChildren().clear();
        visibleActions.values().stream().skip(Math.max(0, visibleActions.size() - 6)).forEach(action -> {
            String name = renderedTable.seats().stream().filter(seat -> seat.userId() == action.userId()).map(SeatSnapshot::username).findFirst().orElse("Người đã rời bàn");
            Label line = styled(name + " · " + action.street().name() + " · " + actionText(action), "action-log");
            line.setMaxWidth(330); line.setTooltip(new javafx.scene.control.Tooltip(line.getText()));
            actionsRow.getChildren().add(line);
        });
    }

    private void renderBoard(TableSnapshot table) {
        while (boardCards.getChildren().size() < 5) boardCards.getChildren().add(cardSlot());
        for (int i = 0; i < 5; i++) {
            Card value = table != null && i < table.board().size() ? table.board().get(i) : null;
            Node old = boardCards.getChildren().get(i);
            if (!java.util.Objects.equals(old.getUserData(), value)) {
                Node next = value == null ? cardSlot() : new PlayingCardView(value, 64);
                next.setUserData(value); boardCards.getChildren().set(i, next);
            }
        }
    }

    private void renderPots(TableSnapshot table) {
        potsRow.getChildren().clear();
        long total = table == null ? 0 : table.pots().stream().mapToLong(pot -> pot.amount()).sum();
        totalPotLabel.setText("TỔNG POT   " + money(total));
        if (table == null || table.pots().isEmpty()) { potsRow.getChildren().add(styled("POT  0", "pot-label")); return; }
        for (int i = 0; i < table.pots().size(); i++) {
            String name = i == 0 ? "MAIN POT" : "SIDE POT " + i;
            potsRow.getChildren().add(styled(name + "  " + money(table.pots().get(i).amount()), "pot-label"));
        }
    }

    private Node card(Card value) {
        return new PlayingCardView(value, 46);
    }

    private Node cardBack() {
        return new PlayingCardView(null, 46);
    }
    private Node cardSlot() { Region node = new Region(); node.getStyleClass().add("card-slot"); return node; }

    private javafx.geometry.Point2D deckOrigin() {
        Region dealer = renderedTable == null ? null : seatNodes.get(renderedTable.dealerSeat());
        return dealer == null ? new javafx.geometry.Point2D(tableCanvas.getWidth() / 2, 30)
            : new javafx.geometry.Point2D(dealer.getLayoutX() + (dealer.getLayoutX() < tableCanvas.getWidth() / 2 ? dealer.getWidth() + 22 : -22), dealer.getLayoutY() + 30);
    }

    private String gloryDescription(GlorySummary glory) {
        return "Tài sản: " + glory.wealthTier() + " · Wallet đã chốt " + money(glory.walletChips()) + " chips\n"
            + "Thành tích: " + glory.achievementTier() + " · " + glory.wins() + " thắng / " + (glory.gamesPlayed() - glory.wins()) + " thua\n"
            + "Bậc tiếp: " + glory.nextWealth() + "\n" + glory.nextAchievement()
            + "\nThắng = nhận ít nhất một pot (kể cả hòa), không nhất thiết lãi ròng. Đây không phải Elo. Bậc có thể tăng hoặc giảm.";
    }

    private void renderProfile() {
        if (client == null || client.state().accountRole() != AccountRole.PLAYER) { userLabel.setTooltip(null); return; }
        GlorySummary glory = client.state().glory();
        userLabel.setText("@" + client.state().username() + (glory == null ? "" : " · " + glory.achievementTier()));
        userLabel.setTooltip(new javafx.scene.control.Tooltip(glory == null ? "Chưa có thống kê" : gloryDescription(glory)));
    }

    private void applyActionPrompt(Message message) {
        TableSnapshot table = renderedTable;
        if (table == null || table.status() != TableStatus.IN_HAND || table.viewerRole() != Role.PLAYER ||
            table.currentTurnUserId() == null || table.currentTurnUserId() != client.state().userId() ||
            !table.tableId().equals(message.tableId()) || table.stateVersion() != message.seq()) return;
        JsonNode payload = message.payload();
        if (table.phase() == HandPhase.DEALING) return;
        effects.finishDeal(); // Only finishes stale decoration after server-authorized BETTING.
        actionPromptVersion = message.seq();
        toCall = payload.path("toCall").asLong();
        minimumRaiseTo = payload.path("minimumRaiseTo").asLong();
        currentBet = payload.path("currentBet").asLong();
        canRaise = payload.path("canRaise").asBoolean();
        actionStack = payload.path("stack").asLong();
        streetContribution = payload.path("streetContribution").asLong();
        wagerRange = new WagerRange(Math.max(1, Math.min(minimumRaiseTo, streetContribution + actionStack)),
            Math.max(1, streetContribution + actionStack));
        updatingWager = true;
        betAmountField.setText(Long.toString(wagerRange.minimum()));
        wagerSlider.setValue(0);
        updatingWager = false;
        validateWagerText();
        actionPending = false;
        turnDeadlineEpochMs = payload.path("deadlineEpochMs").asLong();
        updateTimer((int) Math.max(0, (turnDeadlineEpochMs - System.currentTimeMillis() + 999) / 1000));
        configureActions();
        showStatus("Đến lượt bạn", "status-warn");
    }

    private void configureActions() {
        TableSnapshot table = renderedTable;
        boolean myTurn = table != null && table.viewerRole() == Role.PLAYER && table.status() == TableStatus.IN_HAND &&
            table.currentTurnUserId() != null && table.currentTurnUserId() == client.state().userId() &&
            actionPromptVersion == table.stateVersion() && !actionPending;
        bettingBox.setDisable(!myTurn);
        if (!myTurn) return;
        boolean opening = currentBet == 0;
        long callAmount = Math.min(toCall, actionStack);
        long maxRaiseTo = streetContribution + actionStack;
        foldButton.setVisible(toCall > 0); foldButton.setManaged(toCall > 0);
        checkButton.setVisible(toCall == 0); checkButton.setManaged(toCall == 0);
        callButton.setVisible(toCall > 0); callButton.setManaged(toCall > 0);
        callButton.setText(actionStack <= toCall ? "Call all-in " + money(callAmount) : "Call " + money(callAmount));
        boolean canBet = opening && actionStack > 0;
        boolean canMakeRaise = !opening && canRaise && actionStack > toCall;
        betButton.setVisible(canBet); betButton.setManaged(canBet);
        raiseButton.setVisible(canMakeRaise); raiseButton.setManaged(canMakeRaise);
        wagerBox.setVisible(canBet || canMakeRaise); wagerBox.setManaged(wagerBox.isVisible());
        wagerHintLabel.setVisible(wagerBox.isVisible()); wagerHintLabel.setManaged(wagerBox.isVisible());
        amountLabel.setText(opening ? "Bet" : "Raise to · tổng cược trong vòng");
        wagerSlider.setDisable(wagerRange == null || wagerRange.minimum() == wagerRange.maximum());
        betAmountField.setDisable(wagerRange != null && wagerRange.minimum() == wagerRange.maximum());
        boolean canAllIn = actionStack > 0 && (opening || canRaise || actionStack <= toCall);
        allInButton.setVisible(canAllIn); allInButton.setManaged(canAllIn);
        actionInfoLabel.setText("Đến lượt bạn · Cần theo " + money(callAmount) + " · Còn " + money(actionStack) + " chips");
        turnHintLabel.setText(canBet || canMakeRaise ? "Mức " + (opening ? "Bet" : "Raise to") + " " + money(minimumRaiseTo) + "–" + money(maxRaiseTo) + " · Raise to là tổng cược ở vòng này" : "Quyền raise chưa mở lại — chỉ có thể theo hoặc bỏ bài");
    }

    private void renderHandResult(JsonNode payload) {
        revealedCards.clear();
        winnerAmounts.clear();
        for (JsonNode winner : payload.path("winners")) winnerAmounts.put(winner.path("userId").asLong(), winner.path("amount").asLong());
        for (JsonNode player : payload.path("showdown")) {
            List<Card> cards = new ArrayList<>();
            for (JsonNode value : player.path("holeCards")) cards.add(new Card(Rank.valueOf(value.path("rank").asText()), Suit.valueOf(value.path("suit").asText())));
            revealedCards.put(player.path("userId").asLong(), List.copyOf(cards));
        }
        List<String> winners = new ArrayList<>();
        for (JsonNode winner : payload.path("winners")) winners.add(winner.path("username").asText() + " +" + money(winner.path("amount").asLong()));
        List<String> returned = new ArrayList<>();
        for (JsonNode refund : payload.path("uncalledReturns")) returned.add(refund.path("username").asText() + " " + money(refund.path("amount").asLong()));
        boolean selfWon = java.util.stream.StreamSupport.stream(payload.path("winners").spliterator(), false)
            .anyMatch(winner -> winner.path("userId").asLong() == client.state().userId());
        List<String> names = new ArrayList<>();
        for (JsonNode winner : payload.path("winners")) names.add(winner.path("username").asText());
        resultBannerLabel.setText((winners.isEmpty() ? "VÁN ĐÃ KẾT THÚC" : (selfWon ? "Chúc mừng bạn!  ·  " : "Chúc mừng " + String.join(", ", names) + "!  ·  ") + String.join("  |  ", winners)) +
            (returned.isEmpty() ? "" : "\nHoàn cược chưa được theo: " + String.join(" · ", returned)));
        resultBannerLabel.setVisible(true); resultBannerLabel.setManaged(true);
        pendingHandResult = payload.deepCopy();
        showStatus("Đang chia pot…", "status-warn");
    }

    private void playPayoutAnimation(JsonNode result, TableSnapshot table) {
        List<Region> winnerNodes = new ArrayList<>();
        for (JsonNode winner : result.path("winners")) table.seats().stream()
            .filter(seat -> seat.userId() == winner.path("userId").asLong()).findFirst()
            .map(seat -> seatNodes.get(seat.seat())).ifPresent(winnerNodes::add);
        effects.celebrate(winnerNodes);
        showStatus("Kết quả đã chốt — chip đã được cộng", "status-ok");
        if (!effects.fullMotion()) return;
        List<Label> awards = new ArrayList<>();
        List<Region> targets = new ArrayList<>();
        for (JsonNode pot : result.path("pots")) {
            for (JsonNode winner : pot.path("winners")) {
                SeatSnapshot seat = table.seats().stream()
                    .filter(candidate -> candidate.userId() == winner.path("userId").asLong()).findFirst().orElse(null);
                Region target = seat == null ? null : seatNodes.get(seat.seat());
                if (target == null) continue;
                Label chips = styled(pot.path("name").asText("POT") + "  +" + money(winner.path("amount").asLong()), "payout-chip");
                chips.setManaged(false); chips.setVisible(false); chips.setMouseTransparent(true);
                tableCanvas.getChildren().add(chips);
                awards.add(chips); targets.add(target);
            }
        }
        if (awards.isEmpty()) return;
        long started = System.nanoTime();
        payoutAnimation = new Timeline(new KeyFrame(Duration.millis(16), event -> {
            if (!effects.fullMotion()) { stopPayoutDecoration(); return; }
            double elapsed = (System.nanoTime() - started) / 1_000_000.0;
            for (int i = 0; i < awards.size(); i++) {
                Label chip = awards.get(i); Region target = targets.get(i);
                double progress = Math.max(0, Math.min(1, (elapsed - i * 40) / 700));
                chip.setVisible(elapsed >= i * 40 && progress < 1); chip.autosize();
                double eased = 1 - Math.pow(1 - progress, 3);
                double x = tableCanvas.getWidth() / 2 * (1 - eased) + (target.getLayoutX() + target.getWidth() / 2) * eased;
                double y = (tableCanvas.getHeight() / 2 + 58) * (1 - eased) + (target.getLayoutY() + target.getHeight() / 2) * eased;
                chip.relocate(x - chip.getWidth() / 2, y); chip.setOpacity(1 - .82 * progress);
            }
            if (elapsed >= (awards.size() - 1) * 40 + 700) stopPayoutDecoration();
        }));
        payoutAnimation.setCycleCount(Timeline.INDEFINITE); payoutAnimation.play();
    }

    private void clearHandResult() {
        effects.cancel(); dealRequested = false; winnerAmounts.clear();
        stopPayoutDecoration();
        pendingHandResult = null;
        revealedCards.clear(); resultBannerLabel.setText(""); resultBannerLabel.setVisible(false); resultBannerLabel.setManaged(false);
    }

    private void stopPayoutDecoration() {
        if (payoutAnimation != null) payoutAnimation.stop();
        payoutAnimation = null;
        tableCanvas.getChildren().removeIf(node -> node.getStyleClass().contains("payout-chip"));
        layoutTable();
    }

    private void layoutTable() {
        double width = tableCanvas.getWidth() > 0 ? tableCanvas.getWidth() : tableCanvas.getPrefWidth();
        double height = tableCanvas.getHeight() > 0 ? tableCanvas.getHeight() : tableCanvas.getPrefHeight();
        boolean compact = height < 450;
        centerBoard.autosize(); centerBoard.relocate((width - centerBoard.getWidth()) / 2,
            height * (compact ? .46 : .5) - centerBoard.prefHeight(-1) / 2);
        TableSnapshot table = renderedTable;
        int maxSeats = table == null ? Math.max(2, seatNodes.size()) : table.maxPlayers();
        int heroSeat = table == null ? 0 : table.seats().stream().filter(seat -> seat.userId() == client.state().userId()).mapToInt(SeatSnapshot::seat).findFirst().orElse(0);
        seatNodes.forEach((seat, node) -> {
            node.setScaleX(compact ? .9 : 1); node.setScaleY(compact ? .9 : 1);
            int position = Math.floorMod(seat - heroSeat, maxSeats);
            double angle = Math.PI / 2 + 2 * Math.PI * position / maxSeats;
            double x = width / 2 + Math.cos(angle) * (width / 2 - node.getPrefWidth() / 2 - 12) - node.getPrefWidth() / 2;
            double y = height / 2 + Math.sin(angle) * (height / 2 - node.getPrefHeight() / 2 - 8) - node.getPrefHeight() / 2;
            if (maxSeats >= 7) {
                double[][] slots = {{.5,.88},{.27,.85},{.09,.64},{.09,.29},{.32,.12},{.68,.12},{.91,.29},{.91,.64},{.73,.85}};
                // Spread 7–9 seats over the perimeter; prevent polar-layout crowding at either end of the oval.
                int slot = (int) Math.round(position * 9.0 / maxSeats) % 9;
                x = slots[slot][0] * width - node.getPrefWidth() / 2;
                y = slots[slot][1] * height - node.getPrefHeight() / 2;
            }
            node.relocate(Math.max(0, Math.min(width - node.getPrefWidth(), x)), Math.max(0, Math.min(height - node.getPrefHeight(), y)));
        });
        effects.positionDeck(deckOrigin(), table != null && table.status() == TableStatus.IN_HAND);
    }

    private void renderHistory(JsonNode payload) {
        historyList.getChildren().clear();
        for (JsonNode hand : payload) {
            long delta = hand.path("finalStack").asLong() - hand.path("startingStack").asLong();
            VBox info = new VBox(3,
                styled(("WIN".equals(hand.path("result").asText()) ? "THẮNG" : "THUA") + "  •  Bàn " + shortId(hand.path("tableId").asText()), "row-title"),
                styled("Cược " + money(hand.path("contribution").asLong()) + "  •  " + hand.path("startedAt").asText("—"), "row-meta"));
            HBox.setHgrow(info, Priority.ALWAYS);
            Label change = styled((delta >= 0 ? "+" : "") + money(delta), delta >= 0 ? "wallet-label" : "loss-label");
            HBox row = new HBox(12, info, change); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("history-row");
            historyList.getChildren().add(row);
        }
        if (historyList.getChildren().isEmpty()) showEmpty(historyList, "Bạn chưa chơi ván nào.");
    }

    private void renderCoinRequests(JsonNode payload) {
        coinRequestsList.getChildren().clear();
        boolean pending = false;
        for (JsonNode request : payload) {
            pending |= "PENDING".equals(request.path("status").asText());
            VBox info = new VBox(3, styled(money(request.path("amount").asLong()) + " coin · " + request.path("status").asText(), "row-title"),
                styled(request.path("requestedAt").asText("—") + (request.path("note").asText("").isBlank() ? "" : " · " + request.path("note").asText()), "row-meta"));
            info.getStyleClass().add("history-row"); coinRequestsList.getChildren().add(info);
        }
        if (coinRequestsList.getChildren().isEmpty()) showEmpty(coinRequestsList, "Chưa có yêu cầu coin.");
        coinSubmitButton.setDisable(pending);
    }

    private void renderAdminDashboard(JsonNode payload) {
        adminDashboard = payload;
        adminRequestsList.getChildren().clear();
        for (JsonNode request : payload.path("requests")) {
            VBox info = new VBox(2, styled(request.path("username").asText() + " · " + money(request.path("amount").asLong()), "row-title"),
                styled(request.path("status").asText() + " · " + request.path("requestedAt").asText("—"), "row-meta"));
            HBox.setHgrow(info, Priority.ALWAYS); HBox row = new HBox(7, info); row.setAlignment(Pos.CENTER_LEFT); row.getStyleClass().add("table-row");
            if ("PENDING".equals(request.path("status").asText())) {
                long id = request.path("id").asLong();
                row.getChildren().add(button("Duyệt", "accent-button", () -> sendToServer(() -> requireClient().reviewCoinRequest(id, true, "Approved"))));
                row.getChildren().add(button("Từ chối", "danger-button", () -> prompt("Lý do từ chối", "Nhập 3–200 ký tự").ifPresent(note ->
                    sendToServer(() -> requireClient().reviewCoinRequest(id, false, note)))));
            }
            adminRequestsList.getChildren().add(row);
        }
        if (adminRequestsList.getChildren().isEmpty()) showEmpty(adminRequestsList, "Chưa có yêu cầu coin.");

        adminUsersList.getChildren().clear();
        String search = adminSearchField.getText().trim().toLowerCase(Locale.ROOT);
        for (JsonNode user : payload.path("users")) {
            if (!user.path("username").asText().toLowerCase(Locale.ROOT).contains(search)) continue;
            long id = user.path("id").asLong(); String role = user.path("role").asText(); String status = user.path("status").asText();
            VBox info = new VBox(2, styled(user.path("username").asText() + " · " + role, "row-title"),
                styled(status + " · " + money(user.path("chips").asLong()) + " coin · " + (user.path("online").asBoolean() ? "ONLINE" : "OFFLINE"), "row-meta"));
            info.getChildren().forEach(node -> ((Label) node).setWrapText(true));
            FlowPane actions = new FlowPane(6, 6); actions.setPrefWrapLength(350);
            VBox row = new VBox(10, info, actions); row.getStyleClass().add("table-row");
            if (!"ADMIN".equals(role)) {
                String statusAction = "ACTIVE".equals(status) ? "LOCK" : "UNLOCK";
                actions.getChildren().add(button("LOCK".equals(statusAction) ? "Khóa" : "Mở", "ghost-button",
                    () -> { if (!"LOCK".equals(statusAction) || confirm("Khóa tài khoản?", "Người chơi sẽ rời bàn và bị đăng xuất ngay.")) adminAction(id, statusAction, null, null, 0, null); }));
                String nextRole = "PLAYER".equals(role) ? "OBSERVER" : "PLAYER";
                actions.getChildren().add(button("→ " + nextRole, "ghost-button", () -> adminAction(id, "SET_ROLE", nextRole, null, 0, null)));
                actions.getChildren().add(button("Reset pass", "ghost-button", () -> promptPassword().ifPresent(password ->
                    adminAction(id, "RESET_PASSWORD", null, password, 0, null))));
                actions.getChildren().add(button("± Coin", "primary-button", () -> prompt("Điều chỉnh coin", "Nhập delta, ví dụ 5000 hoặc -2000").ifPresent(delta ->
                    prompt("Lý do", "Bắt buộc 3–200 ký tự").ifPresent(reason -> run(() -> {
                        long amount = Long.parseLong(delta.trim()); sendToServer(() -> requireClient().adminAccountAction(id, "ADJUST_COINS", null, null, amount, reason)); })))));
                if (user.path("online").asBoolean()) actions.getChildren().stream().skip(1).forEach(node -> {
                    node.setDisable(true); javafx.scene.control.Tooltip.install(node, new javafx.scene.control.Tooltip("Chỉ chỉnh role, mật khẩu hoặc coin khi tài khoản đã offline và rời bàn"));
                });
            }
            adminUsersList.getChildren().add(row);
        }
        if (adminUsersList.getChildren().isEmpty()) showEmpty(adminUsersList, "Không có tài khoản phù hợp.");
    }

    private void adminAction(long userId, String action, String role, String password, long delta, String reason) {
        sendToServer(() -> requireClient().adminAccountAction(userId, action, role, password, delta, reason));
    }
    private java.util.Optional<String> prompt(String title, String text) {
        TextInputDialog dialog = new TextInputDialog(); dialog.setTitle(title); dialog.setHeaderText(title); dialog.setContentText(text);
        return dialog.showAndWait().map(String::trim).filter(value -> !value.isBlank());
    }
    private java.util.Optional<String> promptPassword() {
        var dialog = new javafx.scene.control.Dialog<String>();
        dialog.setTitle("Mật khẩu tạm"); dialog.setHeaderText("Đặt mật khẩu mới · tối thiểu 8 ký tự");
        PasswordField input = new PasswordField(); input.setPromptText("Mật khẩu tạm");
        dialog.getDialogPane().setContent(input); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(button -> button == ButtonType.OK ? input.getText() : null);
        return dialog.showAndWait().filter(value -> !value.isEmpty());
    }

    private String readySummary(TableSnapshot table) {
        long eligible = table.seats().stream().filter(seat -> seat.connected() && seat.stack() > 0).count();
        long ready = table.seats().stream().filter(seat -> seat.connected() && seat.stack() > 0 && seat.ready()).count();
        if (eligible < 2) return "Cần ít nhất 2 người chơi còn chip để bắt đầu";
        return ready + "/" + eligible + " người đủ chip đã sẵn sàng";
    }
    private String turnHint(TableSnapshot table, boolean myTurn) {
        if (table.status() != TableStatus.IN_HAND) return table.status() == TableStatus.BETWEEN_HANDS
            ? "Xem kết quả, rồi Ready để tiếp tục; ván mới bắt đầu khi mọi người đủ chip sẵn sàng"
            : "Tất cả người chơi hợp lệ phải Ready để bắt đầu";
        if (myTurn) return "Đang nhận thông tin action hợp lệ từ server…";
        return table.seats().stream().filter(seat -> table.currentTurnUserId() != null && seat.userId() == table.currentTurnUserId())
            .findFirst().map(seat -> "Đang chờ " + seat.username() + " hành động").orElse("Đang chờ lượt tiếp theo");
    }

    private void updateTimer(int seconds) {
        if (timer != null) timer.stop();
        remainingSeconds = Math.max(0, seconds);
        timerLabel.setVisible(remainingSeconds > 0); timerLabel.setManaged(remainingSeconds > 0); timerLabel.setText(remainingSeconds + "s");
        if (remainingSeconds == 0) return;
        timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            remainingSeconds = Math.max(0, remainingSeconds - 1); timerLabel.setText(remainingSeconds + "s");
            if (remainingSeconds == 0) timer.stop();
        }));
        timer.setCycleCount(Timeline.INDEFINITE); timer.play();
    }

    private void acceptDisconnection(PokerClient source, Throwable cause) {
        Platform.runLater(() -> {
            if (shuttingDown || source != client) return;
            actionPending = false; actionPromptVersion = -1; bettingBox.setDisable(true);
            renderedTable = null;
            authInProgress = false;
            clearHandResult();
            if (controlledLogout) {
                controlledLogout = false; resumeToken = null; client.state().reset(); client = null;
                userLabel.setText("Khách"); walletLabel.setText("0 chips"); setAuthenticatedNavigation(false); showPage(loginPage);
                loginButton.setDisable(false); registerButton.setDisable(false);
                showStatus("Đã đăng xuất", "status-ok");
            } else {
                resumeToken = source.state().accountStatus() == com.poker.model.AccountStatus.LOCKED
                    ? null : source.state().sessionId();
                setAuthenticatedNavigation(false); showPage(loginPage);
                boolean resuming = resumeToken != null && !resumeToken.isBlank();
                loginButton.setDisable(resuming); registerButton.setDisable(resuming);
                showStatus("Mất kết nối — ứng dụng đang tự kết nối lại", "status-error");
                connectAutomatically();
            }
        });
    }

    public void shutdown() {
        shuttingDown = true;
        clearHandResult();
        if (reconnectTimer != null) reconnectTimer.stop();
        connectionWorker.shutdownNow();
        if (timer != null) timer.stop();
        if (client != null) try { client.close(); } catch (Exception ignored) { }
    }

    private PokerClient requireClient() {
        if (client == null || !client.isConnected()) throw new IllegalStateException("Chưa kết nối server");
        return client;
    }

    private void showPage(Pane page) {
        if (page != tablePage && effects != null) { effects.cancel(); stopPayoutDecoration(); }
        for (Pane candidate : List.of(loginPage, homePage, tablePage, historyPage, coinPage, adminPage)) {
            boolean selected = candidate == page; candidate.setVisible(selected); candidate.setManaged(selected);
        }
    }
    private void setAuthenticatedNavigation(boolean authenticated) {
        AccountRole role = client == null ? null : client.state().accountRole();
        homeNavButton.setVisible(authenticated && role != AccountRole.ADMIN); homeNavButton.setManaged(homeNavButton.isVisible());
        historyNavButton.setVisible(authenticated && role == AccountRole.PLAYER); historyNavButton.setManaged(historyNavButton.isVisible());
        walletNavButton.setVisible(authenticated && role == AccountRole.PLAYER); walletNavButton.setManaged(walletNavButton.isVisible());
        adminNavButton.setVisible(authenticated && role == AccountRole.ADMIN); adminNavButton.setManaged(adminNavButton.isVisible());
        logoutButton.setVisible(authenticated); logoutButton.setManaged(authenticated);
    }
    private boolean confirm(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title); alert.setHeaderText(title); return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private void run(CheckedAction action) {
        try { action.run(); }
        catch (Exception error) { showStatus(message(error), "status-error"); }
    }
    /** Read form values on the FX thread; serialize network writes off it. Never retry a wager. */
    private void sendToServer(CheckedAction action) {
        run(() -> {
            PokerClient source = requireClient();
            connectionWorker.execute(() -> {
                try {
                    if (source != client || !source.isConnected()) throw new IllegalStateException("Kết nối đã thay đổi — hãy thử lại");
                    action.run();
                } catch (Exception error) {
                    Platform.runLater(() -> {
                        if (shuttingDown || source != client) return;
                        actionPending = false; leavingTable = false; controlledLogout = false;
                        configureActions(); readyButton.setDisable(false); coinSubmitButton.setDisable(false); showStatus(message(error), "status-error");
                    });
                }
            });
        });
    }
    private String message(Exception error) { return error.getMessage() == null ? "Thao tác thất bại" : error.getMessage(); }
    private String required(TextField field, String name) {
        String value = field.getText() == null ? "" : field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(name + " không được để trống"); return value;
    }
    private long number(TextField field, String name, long min, long max) {
        try {
            long value = Long.parseLong(required(field, name));
            if (value < min || value > max) throw new NumberFormatException(); return value;
        } catch (NumberFormatException error) { throw new IllegalArgumentException(name + " không hợp lệ"); }
    }
    private int integer(TextField field, String name, int min, int max) { return Math.toIntExact(number(field, name, min, max)); }
    private void showStatus(String text, String style) {
        statusLabel.setText(text); statusLabel.getStyleClass().removeAll("status-ok", "status-error", "status-warn");
        if (statusLabel.getTooltip() != null) statusLabel.getTooltip().setText(text);
        if (style != null) statusLabel.getStyleClass().add(style);
    }
    private void showEmpty(VBox container, String text) { container.getChildren().setAll(styled(text, "empty-state")); }
    private Label styled(String text, String style) { Label label = new Label(text); label.getStyleClass().add(style); return label; }
    private Button button(String text, String style, Runnable action) {
        Button button = new Button(text); button.getStyleClass().add(style); button.setOnAction(event -> action.run()); return button;
    }
    private String money(long value) { return NUMBERS.format(value); }
    private String shortId(String id) { return id == null || id.length() <= 8 ? id : id.substring(0, 8).toUpperCase(Locale.ROOT); }
    private String statusText(String value) {
        return switch (value) {
            case "WAITING" -> "ĐANG CHỜ"; case "IN_HAND", "ACTIVE" -> "ĐANG CHƠI";
            case "BETWEEN_HANDS" -> "GIỮA CÁC VÁN"; case "FOLDED" -> "ĐÃ BỎ BÀI";
            case "ALL_IN" -> "ALL-IN"; case "DISCONNECTED" -> "MẤT KẾT NỐI"; case "OUT" -> "HẾT CHIP";
            default -> value.replace('_', ' ');
        };
    }
    private String streetText(Street street) {
        return switch (street) { case PRE_FLOP -> "PRE-FLOP"; case FLOP -> "FLOP"; case TURN -> "TURN"; case RIVER -> "RIVER"; case SHOWDOWN -> "SHOWDOWN"; };
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
