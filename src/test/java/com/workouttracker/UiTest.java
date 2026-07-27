package com.workouttracker;

import com.workouttracker.service.ExerciseService;
import com.workouttracker.service.LogEntryService;
import com.workouttracker.util.Database;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.testfx.framework.junit5.ApplicationTest;
import org.testfx.util.WaitForAsyncUtils;

/**
 * Base for tests that drive the real window.
 *
 * <p>Each test gets its own throwaway database, seeded before the window loads
 * because the controller reads it as it initializes.
 */
abstract class UiTest extends ApplicationTest {

    /** Matches the format the entry table renders, so rows can be found by date. */
    protected static final DateTimeFormatter ROW_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    protected ExerciseService exercises;
    protected LogEntryService logEntries;

    private Path databaseFile;

    @Override
    public void start(Stage stage) throws Exception {
        databaseFile = Files.createTempFile("workout-ui", ".db");
        Files.deleteIfExists(databaseFile);
        Database.configure("jdbc:sqlite:" + databaseFile);
        Database.initialize();

        exercises = new ExerciseService();
        logEntries = new LogEntryService();
        seed();

        Parent root = FXMLLoader.load(
                getClass().getResource("/com/workouttracker/ui/MainView.fxml"));
        Scene scene = new Scene(root, 1220, 780);
        scene.getStylesheets().add(
                getClass().getResource("/com/workouttracker/ui/app.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        Files.deleteIfExists(databaseFile);
    }

    /** Puts data in place before the window opens. Empty unless overridden. */
    protected void seed() throws Exception {
        // Nothing by default.
    }

    /** A date safely in the past, so validation never rejects it as future. */
    protected static LocalDate daysAgo(int days) {
        return LocalDate.now().minusDays(days);
    }

    protected void settle() {
        WaitForAsyncUtils.waitForFxEvents();
    }

    // --- building state the way a user would -------------------------------
    //
    // Going through the UI rather than the services keeps the tables in step:
    // the window loads once, so anything written straight to the database
    // afterwards would never appear on screen.

    protected void createExercise(String name, boolean cardio) {
        clickOn("New exercise");
        clickOn("#nameField").write(name);
        if (cardio) {
            clickOn("#cardioToggle");
        }
        clickOn("#dialogConfirm");
        settle();
    }

    protected void logLift(String sets, String reps, String weight) {
        clickOn("Log workout");
        clickOn("#setsField").write(sets);
        clickOn("#repsField").write(reps);
        clickOn("#weightField").write(weight);
        clickOn("#dialogConfirm");
        settle();
    }

    protected void logCardio(String distance, String duration) {
        clickOn("Log workout");
        clickOn("#distanceField").write(distance);
        clickOn("#durationField").write(duration);
        clickOn("#dialogConfirm");
        settle();
    }

    /** Replaces a field's contents rather than appending to them. */
    protected void replaceText(String query, String text) {
        clickOn(query).push(KeyCode.SHORTCUT, KeyCode.A).write(text);
    }
}
