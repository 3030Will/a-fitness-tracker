package com.workouttracker;

import com.workouttracker.ui.Alerts;
import com.workouttracker.util.DataAccessException;
import com.workouttracker.util.Database;
import java.io.IOException;
import java.net.URL;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX entry point for the Workout Progress Tracker.
 */
public class App extends Application {

    private static final String MAIN_VIEW = "/com/workouttracker/ui/MainView.fxml";
    private static final String STYLESHEET = "/com/workouttracker/ui/app.css";

    /**
     * The smallest the window may get. Below this the log panel cannot hold
     * four columns and its own row of actions without labels truncating.
     * Tests resize to exactly this to check nothing is cut off.
     */
    public static final double MIN_WIDTH = 1120;
    public static final double MIN_HEIGHT = 660;

    @Override
    public void start(Stage stage) {
        try {
            Database.initialize();
        } catch (DataAccessException e) {
            fail(stage, "The workout database could not be opened.", e);
            return;
        }

        try {
            stage.setTitle("Workout Progress Tracker");
            stage.setScene(buildScene());
            stage.setMinWidth(MIN_WIDTH);
            stage.setMinHeight(MIN_HEIGHT);
            stage.show();
        } catch (IOException | RuntimeException e) {
            fail(stage, "The application window could not be built.", e);
        }
    }

    private Scene buildScene() throws IOException {
        Parent root = FXMLLoader.load(resource(MAIN_VIEW));
        Scene scene = new Scene(root, 1280, 800);
        scene.getStylesheets().add(resource(STYLESHEET).toExternalForm());
        return scene;
    }

    /**
     * Resources are loaded through this rather than inline so a missing file
     * fails immediately and by name, instead of surfacing later as a
     * NullPointerException from somewhere inside the FXML loader.
     */
    private URL resource(String path) {
        URL url = App.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("Missing resource on the classpath: " + path);
        }
        return url;
    }

    /**
     * The application cannot do anything useful in this state, so it reports
     * the problem and shuts down rather than leaving an empty window open.
     */
    private void fail(Stage owner, String header, Throwable cause) {
        String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        Alerts.error(owner, header, detail);
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
