package com.workouttracker;

import com.workouttracker.service.ExerciseService;
import com.workouttracker.service.LogEntryService;
import com.workouttracker.util.Database;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    protected Stage stage;

    private Path databaseFile;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
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

    /**
     * Waits for something to appear before touching it.
     *
     * <p>A dialog opens on its own window and is not there the instant the
     * button that opens it is clicked. Typing straight away passes most of the
     * time and fails the rest, which is worse than failing always.
     */
    protected void waitForNode(String query) {
        try {
            WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                    () -> lookup(query).tryQuery().isPresent());
        } catch (TimeoutException e) {
            throw new AssertionError(query + " never appeared", e);
        }
        settle();
    }

    /** Waits for something to go away, so the next click cannot land on it. */
    protected void waitUntilGone(String query) {
        try {
            WaitForAsyncUtils.waitFor(10, TimeUnit.SECONDS,
                    () -> lookup(query).tryQuery().isEmpty());
        } catch (TimeoutException e) {
            throw new AssertionError(query + " never went away", e);
        }
        settle();
    }

    // --- building state the way a user would -------------------------------
    //
    // Going through the UI rather than the services keeps the tables in step:
    // the window loads once, so anything written straight to the database
    // afterwards would never appear on screen.

    /** Opens a form and waits until one of its fields is actually there. */
    protected void openForm(String buttonText, String firstField) {
        clickOn(buttonText);
        waitForNode(firstField);
    }

    /** Confirms a form and waits for it to close. */
    protected void submitForm() {
        clickOn("#dialogConfirm");
        waitUntilGone("#dialogConfirm");
    }

    /** Agrees to a delete prompt and waits for it to close. */
    protected void confirmDelete() {
        waitForNode("#confirmDestructive");
        clickOn("#confirmDestructive");
        waitUntilGone("#confirmDestructive");
    }

    /** Declines whichever prompt or form is open and waits for it to close. */
    protected void cancelDialog() {
        clickOn("Cancel");
        waitUntilGone("Cancel");
    }

    protected void createExercise(String name, boolean cardio) {
        openForm("New exercise", "#nameField");
        clickOn("#nameField").write(name);
        if (cardio) {
            clickOn("#cardioToggle");
        }
        submitForm();
    }

    protected void logLift(String sets, String reps, String weight) {
        openForm("Log workout", "#setsField");
        clickOn("#setsField").write(sets);
        clickOn("#repsField").write(reps);
        clickOn("#weightField").write(weight);
        submitForm();
    }

    protected void logCardio(String distance, String duration) {
        openForm("Log workout", "#distanceField");
        clickOn("#distanceField").write(distance);
        clickOn("#durationField").write(duration);
        submitForm();
    }

    /** Replaces a field's contents rather than appending to them. */
    protected void replaceText(String query, String text) {
        clickOn(query).push(KeyCode.SHORTCUT, KeyCode.A).write(text);
    }
}
