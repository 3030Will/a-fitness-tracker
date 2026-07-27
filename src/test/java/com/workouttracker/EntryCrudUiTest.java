package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workouttracker.model.CardioEntry;
import com.workouttracker.model.Exercise;
import com.workouttracker.model.LiftEntry;
import com.workouttracker.model.LogEntry;
import java.time.LocalDate;
import java.util.List;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every control on the log side, clicked.
 */
class EntryCrudUiTest extends UiTest {

    private String today() {
        return LocalDate.now().format(ROW_DATE);
    }

    private List<LogEntry> entriesOfFirstExercise() {
        Exercise exercise = exercises.findAll().getFirst();
        return logEntries.findByExercise(exercise.getId());
    }

    @Test
    @DisplayName("Log workout records a lift")
    void logsALift() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("4", "8", "155");

        LogEntry entry = entriesOfFirstExercise().getFirst();
        LiftEntry lift = assertInstanceOf(LiftEntry.class, entry);
        assertEquals(4, lift.getSets());
        assertEquals(8, lift.getReps());
        assertEquals(155, lift.getWeight());
        assertEquals(LocalDate.now(), lift.getDate(), "the picker should default to today");
    }

    @Test
    @DisplayName("Log workout records a cardio session, reading mm:ss as seconds")
    void logsACardioSession() {
        createExercise("Long Run", true);
        clickOn("Long Run");
        logCardio("3.10", "28:00");

        CardioEntry cardio = assertInstanceOf(CardioEntry.class,
                entriesOfFirstExercise().getFirst());
        assertEquals(3.10, cardio.getDistance());
        assertEquals(1680, cardio.getDuration(), "28:00 is 1680 seconds");
    }

    @Test
    @DisplayName("a malformed time is refused and the form stays open")
    void refusesAMalformedTime() {
        createExercise("Long Run", true);
        clickOn("Long Run");

        clickOn("Log workout");
        clickOn("#distanceField").write("3.1");
        clickOn("#durationField").write("30:70");
        clickOn("#dialogConfirm");
        settle();

        assertTrue(entriesOfFirstExercise().isEmpty(), "nothing should have been saved");
        assertTrue(lookup("#dialogConfirm").tryQuery().isPresent(), "the form should stay open");
    }

    @Test
    @DisplayName("a negative weight is refused")
    void refusesANegativeWeight() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");

        clickOn("Log workout");
        clickOn("#setsField").write("3");
        clickOn("#repsField").write("10");
        clickOn("#weightField").write("-5");
        clickOn("#dialogConfirm");
        settle();

        assertTrue(entriesOfFirstExercise().isEmpty(), "nothing should have been saved");
    }

    @Test
    @DisplayName("bodyweight lifts at zero pounds are accepted")
    void acceptsBodyweight() {
        createExercise("Pull Ups", false);
        clickOn("Pull Ups");
        logLift("3", "12", "0");

        assertEquals(0, assertInstanceOf(LiftEntry.class,
                entriesOfFirstExercise().getFirst()).getWeight());
    }

    @Test
    @DisplayName("Edit entry changes the stored measurements")
    void editsAnEntry() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("3", "10", "135");

        clickOn(today());
        clickOn("Edit entry");
        replaceText("#weightField", "185");
        clickOn("#dialogConfirm");
        settle();

        assertEquals(185, assertInstanceOf(LiftEntry.class,
                entriesOfFirstExercise().getFirst()).getWeight());
    }

    @Test
    @DisplayName("Delete entry removes it once confirmed")
    void deletesAnEntry() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("3", "10", "135");
        assertEquals(1, entriesOfFirstExercise().size());

        clickOn(today());
        clickOn("Delete entry");
        settle();
        clickOn("#confirmDestructive");
        settle();

        assertTrue(entriesOfFirstExercise().isEmpty());
    }

    @Test
    @DisplayName("cancelling the delete prompt keeps the entry")
    void cancellingDeleteKeepsTheEntry() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("3", "10", "135");

        clickOn(today());
        clickOn("Delete entry");
        settle();
        clickOn("Cancel");
        settle();

        assertEquals(1, entriesOfFirstExercise().size());
    }

    @Test
    @DisplayName("the date column keeps a full date at the smallest window size")
    void dateColumnSurvivesTheSmallestWindow() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("3", "10", "135");

        // Four columns compete for the log panel here, where cardio has three.
        // Without a minimum the date column gave way first and lost its year.
        interact(() -> {
            stage.setWidth(1040);
            stage.setHeight(660);
        });
        settle();

        TableView<?> table = lookup("#entryTable").query();
        TableColumn<?, ?> dateColumn = table.getColumns().getFirst();
        assertTrue(dateColumn.getWidth() >= 155,
                "the date column shrank to " + dateColumn.getWidth()
                        + ", which cuts the year off");
    }

    @Test
    @DisplayName("logging needs an exercise, editing and deleting need an entry")
    void actionsNeedTheRightSelection() {
        assertTrue(lookup("#addEntryButton").query().isDisabled(),
                "Log workout should be dimmed with no exercise selected");
        assertTrue(lookup("#editEntryButton").query().isDisabled());
        assertTrue(lookup("#deleteEntryButton").query().isDisabled());

        createExercise("Bench Press", false);
        clickOn("Bench Press");
        settle();

        assertTrue(lookup("#addEntryButton").query().isDisabled() == false,
                "Log workout should be available once an exercise is selected");
        assertTrue(lookup("#editEntryButton").query().isDisabled(),
                "editing still needs an entry");
    }
}
