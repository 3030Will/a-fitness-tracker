package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import java.util.List;
import javafx.stage.Stage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every control on the exercise side, clicked.
 */
class ExerciseCrudUiTest extends UiTest {

    @Test
    @DisplayName("New exercise saves a weightlifting exercise")
    void createsALiftingExercise() {
        createExercise("Overhead Press", false);

        List<Exercise> saved = exercises.findAll();
        assertEquals(1, saved.size());
        assertEquals("Overhead Press", saved.getFirst().getName());
        assertEquals(Category.WEIGHTLIFTING, saved.getFirst().getCategory(),
                "weightlifting is the default segment");
    }

    @Test
    @DisplayName("the segmented control switches the category to cardio")
    void createsACardioExercise() {
        createExercise("Rowing", true);

        assertEquals(Category.CARDIO, exercises.findAll().getFirst().getCategory());
    }

    @Test
    @DisplayName("a blank name is refused and the form stays open")
    void refusesABlankName() {
        openForm("New exercise", "#nameField");
        clickOn("#dialogConfirm");
        settle();

        assertTrue(exercises.findAll().isEmpty(), "nothing should have been saved");
        assertTrue(lookup("#dialogConfirm").tryQuery().isPresent(),
                "the form should have stayed open");
    }

    @Test
    @DisplayName("a duplicate name is refused")
    void refusesADuplicateName() {
        createExercise("Squats", false);

        openForm("New exercise", "#nameField");
        clickOn("#nameField").write("Squats");
        clickOn("#dialogConfirm");
        settle();

        assertEquals(1, exercises.findAll().size(), "the duplicate should not have been saved");
        assertTrue(lookup("#dialogConfirm").tryQuery().isPresent(), "the form should stay open");
    }

    @Test
    @DisplayName("Edit renames the selected exercise")
    void renamesAnExercise() {
        createExercise("Bench Pres", false);

        clickOn("Bench Pres");
        openForm("Edit", "#nameField");
        replaceText("#nameField", "Bench Press");
        submitForm();

        assertEquals("Bench Press", exercises.findAll().getFirst().getName());
    }

    @Test
    @DisplayName("the category cannot be changed once entries exist")
    void categoryIsFrozenWhenEntriesExist() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("3", "10", "135");

        openForm("Edit", "#cardioToggle");

        assertTrue(lookup("#cardioToggle").query().isDisabled(),
                "the cardio segment should be disabled");
        assertTrue(lookup("#liftToggle").query().isDisabled(),
                "the weightlifting segment should be disabled");
    }

    @Test
    @DisplayName("Delete removes the exercise once confirmed")
    void deletesAfterConfirming() {
        createExercise("Squats", false);

        clickOn("Squats");
        clickOn("Delete");
        confirmDelete();

        assertTrue(exercises.findAll().isEmpty(), "the exercise should be gone");
    }

    @Test
    @DisplayName("cancelling the delete prompt keeps the exercise")
    void cancellingDeleteKeepsIt() {
        createExercise("Squats", false);

        clickOn("Squats");
        clickOn("Delete");
        waitForNode("#confirmDestructive");
        cancelDialog();

        assertEquals(1, exercises.findAll().size(), "the exercise should still be there");
    }

    @Test
    @DisplayName("deleting an exercise takes its entries with it")
    void deleteCascadesToEntries() {
        createExercise("Long Run", true);
        clickOn("Long Run");
        logCardio("3.1", "28:00");

        Exercise longRun = exercises.findAll().getFirst();
        assertEquals(1, logEntries.findByExercise(longRun.getId()).size());

        clickOn("Delete");
        confirmDelete();

        assertTrue(exercises.findAll().isEmpty());
        assertTrue(logEntries.findByExercise(longRun.getId()).isEmpty(),
                "the entries should have gone with it");
    }

    @Test
    @DisplayName("the delete prompt belongs to the main window")
    void deletePromptHasAnOwner() {
        createExercise("Squats", false);
        clickOn("Squats");
        clickOn("Delete");
        waitForNode("#confirmDestructive");

        // Without an owner the prompt is a top-level window of its own. On
        // macOS that puts it outside the application's full-screen space: it
        // takes over the whole display, and dismissing it leaves the user on
        // the desktop instead of back in the app.
        Stage prompt = (Stage) lookup("#confirmDestructive").query().getScene().getWindow();
        assertNotNull(prompt.getOwner(), "the delete prompt was opened without an owner");

        cancelDialog();
    }

    @Test
    @DisplayName("Edit and Delete are dimmed until a row is selected")
    void actionsNeedASelection() {
        assertTrue(lookup("#editButton").query().isDisabled());
        assertTrue(lookup("#deleteButton").query().isDisabled());
    }
}
