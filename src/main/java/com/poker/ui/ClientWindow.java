package com.poker.ui;

import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.Region;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Window sizing is independent of the game state and socket lifecycle. */
public final class ClientWindow {
    private ClientWindow() { }

    public static void configure(Stage stage, Region content) {
        stage.initStyle(StageStyle.DECORATED); // Native Windows minimize, maximize/restore and close controls.
        Viewport viewport = new Viewport(content);
        stage.setScene(new Scene(viewport));
        stage.setResizable(true);
        stage.setMinWidth(360);
        stage.setMinHeight(280);
        stage.setFullScreenExitHint("Nhấn Esc hoặc F11 để thoát toàn màn hình");
        Runnable toggleFullScreen = () -> stage.setFullScreen(!stage.isFullScreen());
        stage.getScene().getAccelerators().put(new KeyCodeCombination(KeyCode.F11), toggleFullScreen);

        MenuButton menu = (MenuButton) content.lookup("#displayMenu");
        menu.getItems().setAll(
            item("Cửa sổ nhỏ · 960 × 640", () -> windowed(stage, 960, 640)),
            item("Cửa sổ vừa · 1280 × 800", () -> windowed(stage, 1280, 800)),
            item("Cửa sổ lớn · 1600 × 900", () -> windowed(stage, 1600, 900)),
            new SeparatorMenuItem(),
            item("Phóng to (giữ thanh tác vụ)", () -> {
                stage.setFullScreen(false);
                stage.setMaximized(true);
            }),
            item("Toàn màn hình / Thoát · F11", toggleFullScreen));
        var table = content.lookup("#tableCanvas");
        if (table != null && table.getProperties().get("effects") instanceof TableEffects effects) {
            effects.configureMenu(menu); effects.attachCelebration(viewport.celebration);
        }
        windowed(stage, 1280, 800);
    }

    private static MenuItem item(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> action.run());
        return item;
    }

    private static void windowed(Stage stage, double width, double height) {
        Screen screen = Screen.getScreensForRectangle(stage.getX(), stage.getY(),
            Math.max(1, stage.getWidth()), Math.max(1, stage.getHeight()))
            .stream().findFirst().orElse(Screen.getPrimary());
        Rectangle2D area = screen.getVisualBounds(); // Logical units already account for OS DPI.
        stage.setFullScreen(false);
        stage.setMaximized(false);
        stage.setWidth(Math.min(width, area.getWidth()));
        stage.setHeight(Math.min(height, area.getHeight()));
        stage.setX(area.getMinX() + (area.getWidth() - stage.getWidth()) / 2);
        stage.setY(area.getMinY() + (area.getHeight() - stage.getHeight()) / 2);
    }

    /** Preserve a readable table layout, then shrink uniformly only when space is scarce. */
    private static final class Viewport extends Region {
        private final Region content;
        private final Scale scale = new Scale(1, 1, 0, 0);
        private final javafx.scene.canvas.Canvas celebration = new javafx.scene.canvas.Canvas();

        private Viewport(Region content) {
            this.content = content;
            setMinSize(0, 0);
            setPrefSize(1280, 800);
            setStyle("-fx-background-color: #141b18;");
            content.setManaged(false);
            content.setSnapToPixel(false); // Avoid rounding the scaled shell beyond its viewport.
            content.getStyleClass().add("root");
            content.getTransforms().add(scale);
            getChildren().add(content);
            celebration.setManaged(false); celebration.setMouseTransparent(true);
            celebration.getStyleClass().add("window-celebration-overlay");
            celebration.widthProperty().bind(widthProperty()); celebration.heightProperty().bind(heightProperty());
            getChildren().add(celebration);
        }

        @Override protected void layoutChildren() {
            double factor = Math.min(1, Math.min(getWidth() / 1280, getHeight() / 900));
            if (factor <= 0) return;
            scale.setX(factor);
            scale.setY(factor);
            content.resizeRelocate(0, 0, getWidth() / factor, getHeight() / factor);
            content.layout(); // Unmanaged content must finish its own layout in this resize pass.
        }
    }
}
