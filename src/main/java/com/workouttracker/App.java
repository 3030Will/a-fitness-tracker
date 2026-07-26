package com.workouttracker;

import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
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
        Label title = new Label("Workout Progress Tracker");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitle = new Label("Build scaffolding is working.");

        VBox root = new VBox(10, title, subtitle);
        root.setAlignment(Pos.CENTER);

        stage.setTitle("Workout Progress Tracker");
        stage.setScene(new Scene(root, 480, 320));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
