package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import com.workouttracker.model.Exercise;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The navigation rail and the progress view.
 */
class ProgressUiTest extends UiTest {

    private String textOf(String query) {
        return ((Labeled) lookup(query).query()).getText();
    }

    /**
     * Picks an exercise from the chooser through its selection model rather
     * than by clicking the popup.
     *
     * <p>Clicking a cell in a combo box popup races with the popup opening:
     * the same test passed twice and failed once in three runs, always with
     * the selection simply not having happened. A test that fails at random
     * is worse than no test, and what these tests are actually about is what
     * the chart does once an exercise is chosen.
     */
    private void chooseExercise(String name) {
        ComboBox<Exercise> chooser = lookup("#progressChooser").query();
        interact(() -> chooser.getItems().stream()
                .filter(exercise -> exercise.getName().equals(name))
                .findFirst()
                .ifPresent(chooser.getSelectionModel()::select));
        settle();
    }

    @Test
    @DisplayName("the rail switches between the two destinations")
    void switchesDestinations() {
        assertTrue(lookup("#exercisesPage").query().isVisible());
        assertFalse(lookup("#progressPage").query().isVisible());

        clickOn("Progress");
        settle();
        assertTrue(lookup("#progressPage").query().isVisible());
        assertFalse(lookup("#exercisesPage").query().isVisible());

        clickOn("Exercises");
        settle();
        assertTrue(lookup("#exercisesPage").query().isVisible());
        assertFalse(lookup("#progressPage").query().isVisible());
    }

    @Test
    @DisplayName("a destination cannot be deselected into nothing")
    void railAlwaysKeepsADestination() {
        clickOn("Progress");
        settle();
        clickOn("Progress");
        settle();

        assertTrue(lookup("#progressPage").query().isVisible(),
                "clicking the selected destination again should not clear it");
    }

    @Test
    @DisplayName("with no exercises the progress page explains itself")
    void emptyProgressExplainsItself() {
        clickOn("Progress");
        settle();

        assertTrue(lookup("#progressMessage").query().isVisible());
        assertFalse(lookup("#progressChart").query().isVisible(), "there is nothing to chart");
    }

    @Test
    @DisplayName("lifting records read as heaviest set, best volume and session count")
    void showsLiftingRecords() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("3", "10", "135");
        logLift("4", "8", "155");

        clickOn("Progress");
        settle();

        assertEquals("Heaviest set", textOf("#metricOneLabel"));
        assertEquals("155", textOf("#metricOneValue"));
        assertEquals("Best session volume", textOf("#metricTwoLabel"));
        assertEquals("4,960", textOf("#metricTwoValue"), "4 x 8 x 155 beats 3 x 10 x 135");
        assertEquals("2", textOf("#metricThreeValue"));
    }

    @Test
    @DisplayName("cardio records read as longest session, best pace and total distance")
    void showsCardioRecords() {
        createExercise("Long Run", true);
        clickOn("Long Run");
        logCardio("5.00", "44:00");

        clickOn("Progress");
        settle();

        assertEquals("Longest session", textOf("#metricOneLabel"));
        assertEquals("5.00", textOf("#metricOneValue"));
        assertEquals("8:48", textOf("#metricTwoValue"), "2640 seconds over 5 miles");
        assertEquals("5.0", textOf("#metricThreeValue"));
    }

    @Test
    @DisplayName("cardio charts pace, not distance, since that is what improves")
    void chartsPaceForCardio() {
        createExercise("Long Run", true);
        clickOn("Long Run");
        logCardio("5.00", "44:00");   // 8:48 per mile, 528 seconds
        logCardio("3.00", "30:00");   // 10:00 per mile, 600 seconds

        clickOn("Progress");
        settle();

        LineChart<?, ?> chart = lookup("#progressChart").query();
        List<Double> plotted = chart.getData().getFirst().getData().stream()
                .map(point -> ((Number) point.getYValue()).doubleValue())
                .toList();

        assertTrue(plotted.contains(528.0) && plotted.contains(600.0),
                "expected paces in seconds per mile, got " + plotted);
        assertFalse(plotted.contains(5.0), "distance should no longer be the plotted value");
    }

    @Test
    @DisplayName("the chart plots one point per session, oldest first")
    void chartsEverySession() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("3", "10", "135");
        logLift("4", "8", "155");

        clickOn("Progress");
        settle();

        LineChart<?, ?> chart = lookup("#progressChart").query();
        assertTrue(chart.isVisible());
        assertEquals(1, chart.getData().size(), "one series");

        // Both sessions were logged today, so they share a date label. A
        // CategoryAxis keeps one category per distinct label, so without
        // distinct labels the second session would vanish from the axis even
        // though the series still holds its data.
        CategoryAxis xAxis = (CategoryAxis) chart.getXAxis();
        assertEquals(2, xAxis.getCategories().size(),
                "both same-day sessions should occupy their own category");
    }

    @Test
    @DisplayName("switching from cardio back to a lift restores a plain number axis")
    void axisResetsWhenLeavingCardio() {
        createExercise("Bench Press", false);
        clickOn("Bench Press");
        logLift("3", "10", "135");
        createExercise("Long Run", true);
        clickOn("Long Run");
        logCardio("5.00", "44:00");

        clickOn("Progress");
        settle();
        chooseExercise("Long Run");

        NumberAxis yAxis = (NumberAxis) ((LineChart<?, ?>) lookup("#progressChart").query())
                .getYAxis();
        assertTrue(yAxis.getTickLabelFormatter() != null, "cardio should format ticks as m:ss");

        chooseExercise("Bench Press");

        // Left in place, a weight of 135 would render on the axis as "2:15".
        assertTrue(yAxis.getTickLabelFormatter() == null,
                "a weight axis should show plain numbers");
    }

    @Test
    @DisplayName("an exercise with no entries says so rather than charting nothing")
    void exerciseWithoutEntries() {
        createExercise("Deadlift", false);

        clickOn("Progress");
        settle();

        assertTrue(lookup("#progressMessage").query().isVisible());
        assertFalse(lookup("#progressChart").query().isVisible());
    }
}
