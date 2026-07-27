package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.service.ExerciseService;
import com.workouttracker.util.Database;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * Drives the real window the way a user does.
 *
 * <p>Events go into the JavaFX event queue rather than through the operating
 * system, so this needs no accessibility permission and runs as part of the
 * build.
 */
class CreateExerciseUiTest extends ApplicationTest {

    private Path databaseFile;

    @Override
    public void start(Stage stage) throws Exception {
        databaseFile = Files.createTempFile("workout-ui-test", ".db");
        Files.deleteIfExists(databaseFile);
        Database.configure("jdbc:sqlite:" + databaseFile);
        Database.initialize();

        Parent root = FXMLLoader.load(
                getClass().getResource("/com/workouttracker/ui/MainView.fxml"));
        Scene scene = new Scene(root, 1220, 780);
        scene.getStylesheets().add(
                getClass().getResource("/com/workouttracker/ui/app.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    @Test
    @DisplayName("creating an exercise through the form adds it to the library")
    void createsAnExercise() {
        clickOn("New exercise");
        clickOn("#nameField").write("Overhead Press");
        clickOn("Create");

        List<Exercise> saved = new ExerciseService().findAll();
        assertEquals(1, saved.size(), "expected exactly one exercise");
        assertEquals("Overhead Press", saved.getFirst().getName());
        assertEquals(Category.WEIGHTLIFTING, saved.getFirst().getCategory());
    }

    @Test
    @DisplayName("a blank name is refused and the form stays open")
    void refusesABlankName() {
        clickOn("New exercise");
        clickOn("Create");

        assertTrue(new ExerciseService().findAll().isEmpty(), "nothing should have been saved");
        // Still on screen, so the typed input is not lost.
        assertTrue(lookup("Create").tryQuery().isPresent(), "the form should have stayed open");
    }
}
