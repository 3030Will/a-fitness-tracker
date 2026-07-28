package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workouttracker.model.CardioEntry;
import com.workouttracker.model.Exercise;
import com.workouttracker.model.LiftEntry;
import com.workouttracker.model.LogEntry;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import javafx.scene.control.Label;
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

        openForm("Log workout", "#distanceField");
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

        openForm("Log workout", "#setsField");
        clickOn("#setsField").write("3");
        clickOn("#repsField").write("10");
        clickOn("#weightField").write("-5");
        clickOn("#dialogConfirm");
        settle();

        assertTrue(entriesOfFirstExercise().isEmpty(), "nothing should have been saved");
    }

    @Test
    @DisplayName("every validation message is legible, not clipped")
    void allValidationMessagesAreVisible() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");

        openForm("Log workout", "#setsField");
        clickOn("#setsField").write("three");
        clickOn("#repsField").write("0");
        clickOn("#weightField").write("-10");
        clickOn("#dialogConfirm");
        settle();

        // Three problems at once. The banner is filled in after the dialog was
        // laid out, so without growing it the third message is dropped and the
        // second ends in an ellipsis.
        Label banner = lookup("#errorLabel").query();
        assertEquals(3, banner.getText().lines().count(), "expected three messages");
        assertTrue(banner.getHeight() >= banner.prefHeight(banner.getWidth()),
                "the banner is %.0f tall but needs %.0f, so a message is clipped"
                        .formatted(banner.getHeight(), banner.prefHeight(banner.getWidth())));
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
        openForm("Edit entry", "#weightField");
        replaceText("#weightField", "185");
        submitForm();

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
        confirmDelete();

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
        waitForNode("#confirmDestructive");
        cancelDialog();

        assertEquals(1, entriesOfFirstExercise().size());
    }

    @Test
    @DisplayName("a lift's columns all fit at the smallest window size")
    void liftColumnsFitAtTheSmallestWindow() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("3", "10", "135");

        assertColumnsFitAtMinimumWidth();
    }

    @Test
    @DisplayName("a cardio session's columns all fit at the smallest window size")
    void cardioColumnsFitAtTheSmallestWindow() {
        createExercise("Long Run", true);
        clickOn("Long Run");
        logCardio("5.00", "44:00");

        assertColumnsFitAtMinimumWidth();
    }

    /**
     * Shrinks the window as far as the application allows and checks the log
     * table can still give every visible column the width it needs.
     *
     * <p>A constrained resize policy squeezes columns past their preferred
     * width when space is short, which is how a date lost its year and a time
     * lost its hours. Each column now states a minimum; this checks the panel
     * is wide enough to honour all of them at once.
     */
    private void assertColumnsFitAtMinimumWidth() {
        interact(() -> {
            stage.setWidth(App.MIN_WIDTH);
            stage.setHeight(App.MIN_HEIGHT);
        });
        settle();

        TableView<?> table = lookup("#entryTable").query();
        double needed = table.getColumns().stream()
                .filter(TableColumn::isVisible)
                .mapToDouble(TableColumn::getMinWidth)
                .sum();

        assertTrue(needed <= table.getWidth(),
                "the visible columns need %.0f pixels but the table is only %.0f wide, so something truncates"
                        .formatted(needed, table.getWidth()));

        for (TableColumn<?, ?> column : table.getColumns()) {
            if (column.isVisible()) {
                assertTrue(column.getWidth() >= column.getMinWidth(),
                        "\"%s\" shrank to %.0f, below its %.0f minimum"
                                .formatted(column.getText(), column.getWidth(),
                                        column.getMinWidth()));
            }
        }
    }

    @Test
    @DisplayName("clicking a numeric header sorts by value, not by digits")
    void sortsWeightNumerically() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("3", "10", "100");
        logLift("3", "10", "45");
        logLift("3", "10", "9");

        // The cells hold formatted text. Sorted as words these come out
        // 100, 45, 9 — which is what a table full of numbers must not do.
        clickOn("Weight (lb)");
        settle();

        TableView<?> table = lookup("#entryTable").query();
        TableColumn<?, ?> weight = table.getColumns().get(3);
        List<Double> shown = table.getItems().stream()
                .map(entry -> ((LiftEntry) entry).getWeight())
                .toList();

        assertEquals(List.of(9.0, 45.0, 100.0), shown,
                "expected ascending by value, got " + shown);
        assertEquals("Weight (lb)", weight.getText(), "sorted the wrong column");
    }

    @Test
    @DisplayName("clicking the date header sorts chronologically")
    void sortsDatesChronologically() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("3", "10", "100");

        TableColumn<?, ?> date = ((TableView<?>) lookup("#entryTable").query()).getColumns().getFirst();
        Comparator<String> order = (Comparator<String>) date.getComparator();

        // "Jul" sorts before "Jun" alphabetically; by date it must not.
        assertTrue(order.compare("Jun 30, 2026", "Jul 6, 2026") < 0,
                "June should sort before July");
        assertTrue(order.compare("Aug 1, 2026", "Jul 6, 2026") > 0,
                "August should sort after July");
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
