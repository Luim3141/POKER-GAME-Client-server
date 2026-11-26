package com.poker;

import com.poker.ui.MainController;
import com.poker.ui.ClientWindow;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;

public final class PokerJavaFxClientMain extends Application {
    private MainController controller;
    @Override public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/poker/ui/poker-client.fxml"));
        ClientWindow.configure(stage, loader.load()); controller = loader.getController();
        stage.setTitle("River Poker Room"); stage.show();
    }
    @Override public void stop() { if (controller != null) controller.shutdown(); }
    public static void main(String[] args) { launch(args); }
}
