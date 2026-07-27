package com.workouttracker;

import com.workouttracker.util.DataAccessException;
import com.workouttracker.util.Database;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX entry point for the Workout Progress Tracker.
 *
 * <p>Placeholder shell for build verification. The real UI is added in step 5.
 */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        try {
            Database.initialize();
        } catch (DataAccessException e) {
            showFatalError(e);
            return;
        }

        Label title = new Label("Workout Progress Tracker");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitle = new Label("Build scaffolding is working.");

        VBox root = new VBox(10, title, subtitle);
        root.setAlignment(Pos.CENTER);

        stage.setTitle("Workout Progress Tracker");
        stage.setScene(new Scene(root, 480, 320));
        stage.show();
    }

    /**
     * The application cannot do anything useful without its database, so a
     * failure here is reported and then shuts the app down rather than leaving
     * a window open on top of nothing.
     */
    private void showFatalError(DataAccessException cause) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Workout Progress Tracker");
        alert.setHeaderText("The workout database could not be opened.");
        alert.setContentText(cause.getMessage());
        alert.showAndWait();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
