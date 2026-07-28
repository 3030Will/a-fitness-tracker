package com.workouttracker.ui;

import com.workouttracker.model.CardioEntry;
import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.model.LiftEntry;
import com.workouttracker.model.LogEntry;
import com.workouttracker.service.ExerciseService;
import com.workouttracker.service.LogEntryService;
import com.workouttracker.util.DataAccessException;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

/**
 * The main window: the navigation rail and the exercise list.
 */
public class MainController {

    /**
     * One row of the exercise table. The entry count is read once when the
     * table is filled rather than from a cell value factory, which JavaFX
     * calls repeatedly while scrolling and would turn into a query per
     * repaint.
     */
    public record ExerciseRow(Exercise exercise, int entryCount) {
    }

    private static final DateTimeFormatter ENTRY_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    /** Chart tick labels drop the year; the axis is short and crowds easily. */
    private static final DateTimeFormatter CHART_DATE =
            DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private final ExerciseService exercises = new ExerciseService();
    private final LogEntryService logEntries = new LogEntryService();
    private final ObservableList<ExerciseRow> rows = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> entries = FXCollections.observableArrayList();

    @FXML private ToggleButton exercisesNav;
    @FXML private TableView<ExerciseRow> exerciseTable;
    @FXML private TableColumn<ExerciseRow, String> nameColumn;
    @FXML private TableColumn<ExerciseRow, Category> categoryColumn;
    @FXML private Button editButton;
    @FXML private Button deleteButton;

    @FXML private Label logTitle;
    @FXML private Label logChip;
    @FXML private Label logCount;
    @FXML private Label noSelectionMessage;
    @FXML private Button editEntryButton;
    @FXML private Button deleteEntryButton;
    @FXML private Button addEntryButton;
    @FXML private TableView<LogEntry> entryTable;
    @FXML private TableColumn<LogEntry, String> dateColumn;
    @FXML private TableColumn<LogEntry, String> setsColumn;
    @FXML private TableColumn<LogEntry, String> repsColumn;
    @FXML private TableColumn<LogEntry, String> weightColumn;
    @FXML private TableColumn<LogEntry, String> distanceColumn;
    @FXML private TableColumn<LogEntry, String> durationColumn;
    @FXML private TableColumn<LogEntry, String> paceColumn;

    @FXML private ToggleButton progressNav;
    @FXML private VBox exercisesPage;
    @FXML private VBox progressPage;
    @FXML private ComboBox<Exercise> progressChooser;
    @FXML private Label progressChip;
    @FXML private Label prChip;
    @FXML private Label progressMessage;
    @FXML private LineChart<String, Number> progressChart;
    @FXML private NumberAxis progressYAxis;
    @FXML private Label metricOneLabel;
    @FXML private Label metricOneValue;
    @FXML private Label metricOneUnit;
    @FXML private Label metricTwoLabel;
    @FXML private Label metricTwoValue;
    @FXML private Label metricTwoUnit;
    @FXML private Label metricThreeLabel;
    @FXML private Label metricThreeValue;
    @FXML private Label metricThreeUnit;

    @FXML
    private void initialize() {
        setUpNavigation();
        setUpTable();
        setUpEntryTable();
        setUpSelection();
        refresh();
    }


    /** Edit and Delete act on the selected row, so they are dimmed without one. */
    private void setUpSelection() {
        BooleanBinding nothingSelected =
                exerciseTable.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(nothingSelected);
        deleteButton.disableProperty().bind(nothingSelected);

        exerciseTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, previous, current) -> showLogFor(current));

        // Logging needs an exercise; editing and deleting need an entry.
        addEntryButton.disableProperty().bind(nothingSelected);
        BooleanBinding noEntrySelected =
                entryTable.getSelectionModel().selectedItemProperty().isNull();
        editEntryButton.disableProperty().bind(noEntrySelected);
        deleteEntryButton.disableProperty().bind(noEntrySelected);

        entryTable.setRowFactory(table -> {
            TableRow<LogEntry> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    editEntry(row.getItem());
                }
            });
            return row;
        });

        exerciseTable.setRowFactory(table -> {
            TableRow<ExerciseRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    edit(row.getItem().exercise());
                }
            });
            return row;
        });
    }

    @FXML
    private void handleNew() {
        edit(null);
    }

    @FXML
    private void handleEdit() {
        selected().ifPresent(row -> edit(row.exercise()));
    }

    @FXML
    private void handleDelete() {
        selected().ifPresent(this::delete);
    }

    private void edit(Exercise existing) {
        try {
            ExerciseDialogController
                    .open(exerciseTable.getScene().getWindow(), existing, exercises)
                    .ifPresent(saved -> {
                        refresh();
                        selectById(saved.getId());
                    });
        } catch (IOException e) {
            Alerts.error(window(), "The exercise form could not be opened.", e.getMessage());
        }
    }

    private void delete(ExerciseRow row) {
        Exercise exercise = row.exercise();
        String consequence = row.entryCount() == 0
                ? "This cannot be undone."
                : "Its %d log %s will be deleted as well. This cannot be undone."
                        .formatted(row.entryCount(), row.entryCount() == 1 ? "entry" : "entries");

        if (!Alerts.confirmDestructive(window(),
                "Delete \"%s\"?".formatted(exercise.getName()), consequence, "Delete")) {
            return;
        }

        try {
            exercises.delete(exercise.getId());
            refresh();
        } catch (DataAccessException e) {
            Alerts.error(window(), "\"%s\" could not be deleted.".formatted(exercise.getName()),
                    e.getMessage());
        }
    }

    @FXML
    private void handleAddEntry() {
        selected().ifPresent(row -> editEntry(null));
    }

    @FXML
    private void handleEditEntry() {
        LogEntry entry = entryTable.getSelectionModel().getSelectedItem();
        if (entry != null) {
            editEntry(entry);
        }
    }

    @FXML
    private void handleDeleteEntry() {
        LogEntry entry = entryTable.getSelectionModel().getSelectedItem();
        if (entry == null) {
            return;
        }

        // summary() renders the entry the way its own kind should read, so the
        // prompt names what is about to go rather than saying "this entry".
        boolean confirmed = Alerts.confirmDestructive(window(),
                "Delete this entry?",
                "%s on %s will be removed. This cannot be undone."
                        .formatted(entry.summary(), entry.getDate().format(ENTRY_DATE)),
                "Delete");
        if (!confirmed) {
            return;
        }

        try {
            logEntries.delete(entry.getId());
            reloadKeepingSelection();
        } catch (DataAccessException e) {
            Alerts.error(window(), "The entry could not be deleted.", e.getMessage());
        }
    }

    private void editEntry(LogEntry existing) {
        selected().ifPresent(row -> {
            try {
                EntryDialogController
                        .open(entryTable.getScene().getWindow(), row.exercise(), existing,
                                logEntries)
                        .ifPresent(saved -> reloadKeepingSelection());
            } catch (IOException e) {
                Alerts.error(window(), "The workout form could not be opened.", e.getMessage());
            }
        });
    }

    /**
     * Reloads both tables after an entry changes. The library is rebuilt too,
     * because an exercise's entry count is part of its row and a delete now
     * cascades differently.
     */
    private void reloadKeepingSelection() {
        selected().map(row -> row.exercise().getId()).ifPresent(exerciseId -> {
            refresh();
            selectById(exerciseId);
        });
    }

    /** The window these dialogs belong to, so they stay inside full screen. */
    private Window window() {
        return exerciseTable.getScene() == null ? null : exerciseTable.getScene().getWindow();
    }

    private Optional<ExerciseRow> selected() {
        return Optional.ofNullable(exerciseTable.getSelectionModel().getSelectedItem());
    }

    /** Keeps the row the user just worked on selected after the list reloads. */
    private void selectById(long exerciseId) {
        rows.stream()
                .filter(row -> row.exercise().getId() == exerciseId)
                .findFirst()
                .ifPresent(row -> {
                    exerciseTable.getSelectionModel().select(row);
                    exerciseTable.scrollTo(row);
                });
    }

    /**
     * Keeps a destination selected. A ToggleButton in a group deselects when
     * clicked a second time, which would leave the rail showing nothing
     * selected while the page beneath stayed put.
     */
    private void setUpNavigation() {
        ToggleGroup group = new ToggleGroup();
        exercisesNav.setToggleGroup(group);
        progressNav.setToggleGroup(group);
        exercisesNav.setSelected(true);

        group.selectedToggleProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                group.selectToggle(previous);
                return;
            }
            boolean progress = current == progressNav;
            setShown(progressPage, progress);
            setShown(exercisesPage, !progress);
            if (progress) {
                refreshChooser();
                showProgressFor(progressChooser.getValue());
            }
        });

        progressChooser.valueProperty().addListener(
                (observable, previous, current) -> showProgressFor(current));
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private void setUpTable() {
        exerciseTable.setItems(rows);
        exerciseTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        exerciseTable.setPlaceholder(
                new Label("No exercises yet. Add one to start logging workouts."));

        nameColumn.setCellValueFactory(row ->
                new ReadOnlyStringWrapper(row.getValue().exercise().getName()));

        categoryColumn.setCellValueFactory(row ->
                new ReadOnlyObjectWrapper<>(row.getValue().exercise().getCategory()));
        categoryColumn.setCellFactory(column -> new CategoryChipCell());

    }

    /**
     * Both kinds of entry share one table. Every column is created up front and
     * the ones belonging to the other category are hidden, which keeps the
     * layout stable when the selection moves between a lift and a run.
     */
    private void setUpEntryTable() {
        entryTable.setItems(entries);
        // Spread spare width across every column. Giving it all to the last one
        // strands a right-aligned number far from the header above it.
        entryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        entryTable.setPlaceholder(new Label("Nothing logged for this exercise yet."));

        dateColumn.setCellValueFactory(row ->
                new ReadOnlyStringWrapper(row.getValue().getDate().format(ENTRY_DATE)));

        setsColumn.setCellValueFactory(row -> lift(row.getValue(),
                lift -> String.valueOf(lift.getSets())));
        repsColumn.setCellValueFactory(row -> lift(row.getValue(),
                lift -> String.valueOf(lift.getReps())));
        weightColumn.setCellValueFactory(row -> lift(row.getValue(),
                LiftEntry::formattedWeight));

        distanceColumn.setCellValueFactory(row -> cardio(row.getValue(),
                entry -> String.format(Locale.US, "%.2f", entry.getDistance())));
        durationColumn.setCellValueFactory(row -> cardio(row.getValue(),
                CardioEntry::formattedDuration));
        paceColumn.setCellValueFactory(row -> cardio(row.getValue(),
                CardioEntry::formattedPace));

        for (TableColumn<LogEntry, String> column : List.of(
                setsColumn, repsColumn, weightColumn, distanceColumn, durationColumn, paceColumn)) {
            column.getStyleClass().add("table-cell-numeric");
        }
    }

    private ReadOnlyStringWrapper lift(LogEntry entry, Function<LiftEntry, String> read) {
        return new ReadOnlyStringWrapper(entry instanceof LiftEntry l ? read.apply(l) : "");
    }

    private ReadOnlyStringWrapper cardio(LogEntry entry, Function<CardioEntry, String> read) {
        return new ReadOnlyStringWrapper(entry instanceof CardioEntry c ? read.apply(c) : "");
    }

    /** Loads the log for the selected exercise, or empties it when none is. */
    private void showLogFor(ExerciseRow row) {
        if (row == null) {
            entries.clear();
            logTitle.setText("Log");
            logCount.setText("");
            logChip.setVisible(false);
            logChip.setManaged(false);
            entryTable.setVisible(false);
            noSelectionMessage.setVisible(true);
            return;
        }

        entryTable.setVisible(true);
        noSelectionMessage.setVisible(false);

        Exercise exercise = row.exercise();
        boolean lifting = exercise.getCategory() == Category.WEIGHTLIFTING;

        setsColumn.setVisible(lifting);
        repsColumn.setVisible(lifting);
        weightColumn.setVisible(lifting);
        distanceColumn.setVisible(!lifting);
        durationColumn.setVisible(!lifting);
        paceColumn.setVisible(!lifting);

        logTitle.setText(exercise.getName());
        logChip.setText(exercise.getCategory().displayName());
        logChip.getStyleClass().removeAll("chip-cardio", "chip-strength");
        logChip.getStyleClass().add(lifting ? "chip-strength" : "chip-cardio");
        logChip.setVisible(true);
        logChip.setManaged(true);
        entryTable.setPlaceholder(new Label("Nothing logged for this exercise yet."));

        try {
            entries.setAll(logEntries.findByExercise(exercise.getId()));
            logCount.setText(entries.size() == 1 ? "1 entry" : entries.size() + " entries");
        } catch (DataAccessException e) {
            entries.clear();
            logCount.setText("");
            Alerts.error(window(), "The log for \"%s\" could not be loaded.".formatted(exercise.getName()),
                    e.getMessage());
        }
    }

    /** Reloads the list from the database. */
    private void refresh() {
        try {
            rows.setAll(exercises.findAll().stream()
                    .map(exercise -> new ExerciseRow(exercise, exercises.entryCount(exercise.getId())))
                    .toList());
        } catch (DataAccessException e) {
            rows.clear();
            Alerts.error(window(), "Your exercises could not be loaded.", e.getMessage());
        }
    }

    /** Keeps the progress picker in step with the library, preserving its choice. */
    private void refreshChooser() {
        Exercise chosen = progressChooser.getValue();
        progressChooser.getItems().setAll(rows.stream().map(ExerciseRow::exercise).toList());

        if (chosen != null) {
            progressChooser.getItems().stream()
                    .filter(exercise -> exercise.getId() == chosen.getId())
                    .findFirst()
                    .ifPresent(progressChooser::setValue);
        }
        if (progressChooser.getValue() == null && !progressChooser.getItems().isEmpty()) {
            progressChooser.setValue(progressChooser.getItems().getFirst());
        }
    }

    /**
     * Fills the progress page for one exercise: its records as readouts, and
     * the measurement that matters plotted oldest to newest, which is the
     * direction improvement runs in.
     */
    private void showProgressFor(Exercise exercise) {
        if (exercise == null) {
            showProgressMessage("Add an exercise to start tracking progress.");
            return;
        }

        setShown(progressChip, true);
        progressChip.setText(exercise.getCategory().displayName());
        progressChip.getStyleClass().removeAll("chip-cardio", "chip-strength");
        progressChip.getStyleClass().add(
                exercise.getCategory() == Category.CARDIO ? "chip-cardio" : "chip-strength");

        List<LogEntry> history;
        try {
            history = logEntries.history(exercise.getId());
        } catch (DataAccessException e) {
            showProgressMessage("The history for \"%s\" could not be loaded."
                    .formatted(exercise.getName()));
            return;
        }

        if (history.isEmpty()) {
            showProgressMessage("Nothing logged for \"%s\" yet. Log a workout and it will chart here."
                    .formatted(exercise.getName()));
            return;
        }

        progressMessage.setVisible(false);
        progressChart.setVisible(true);

        if (exercise.getCategory() == Category.WEIGHTLIFTING) {
            showLiftProgress(history);
        } else {
            showCardioProgress(history);
        }
    }

    private void showLiftProgress(List<LogEntry> history) {
        List<LiftEntry> lifts = history.stream().map(LiftEntry.class::cast).toList();

        LiftEntry heaviest = lifts.stream()
                .max(Comparator.comparingDouble(LiftEntry::getWeight))
                .orElseThrow();
        double bestVolume = lifts.stream().mapToDouble(LiftEntry::volume).max().orElse(0);

        setMetric(metricOneLabel, metricOneValue, metricOneUnit,
                "Heaviest set", heaviest.formattedWeight(), "lb", "metric-green");
        setMetric(metricTwoLabel, metricTwoValue, metricTwoUnit,
                "Best session volume", String.format(Locale.US, "%,.0f", bestVolume), "lb",
                "metric-purple");
        setMetric(metricThreeLabel, metricThreeValue, metricThreeUnit,
                "Sessions logged", String.valueOf(lifts.size()), "", "metric-blue");

        showPersonalRecord("Heaviest set " + heaviest.formattedWeight() + " lb on "
                + heaviest.getDate().format(ENTRY_DATE));

        paceAxis(false);
        plot(history, "Weight (lb)", "chart-lift",
                entry -> ((LiftEntry) entry).getWeight());
    }

    private void showCardioProgress(List<LogEntry> history) {
        List<CardioEntry> sessions = history.stream().map(CardioEntry.class::cast).toList();

        CardioEntry longest = sessions.stream()
                .max(Comparator.comparingDouble(CardioEntry::getDistance))
                .orElseThrow();
        CardioEntry fastest = sessions.stream()
                .min(Comparator.comparingDouble(CardioEntry::secondsPerMile))
                .orElseThrow();
        double totalMiles = sessions.stream().mapToDouble(CardioEntry::getDistance).sum();

        setMetric(metricOneLabel, metricOneValue, metricOneUnit,
                "Longest session", String.format(Locale.US, "%.2f", longest.getDistance()), "mi",
                "metric-red");
        setMetric(metricTwoLabel, metricTwoValue, metricTwoUnit,
                "Best pace", fastest.formattedPace(), "/mi", "metric-purple");
        setMetric(metricThreeLabel, metricThreeValue, metricThreeUnit,
                "Total distance", String.format(Locale.US, "%.1f", totalMiles), "mi",
                "metric-blue");

        showPersonalRecord("Best pace %s /mi on %s"
                .formatted(fastest.formattedPace(), fastest.getDate().format(ENTRY_DATE)));

        // Pace, not distance: a longer run is not a better one, but a faster
        // mile is. The axis therefore improves downwards.
        paceAxis(true);
        plot(history, "Pace (min/mi) — lower is better", "chart-cardio",
                entry -> ((CardioEntry) entry).secondsPerMile());
    }

    /**
     * Switches the vertical axis between plain numbers and {@code m:ss}. Pace
     * is held in seconds so it can be plotted, but "528" means nothing to a
     * runner and "8:48" does.
     */
    private void paceAxis(boolean pace) {
        progressYAxis.setTickLabelFormatter(pace ? new StringConverter<Number>() {
            @Override
            public String toString(Number seconds) {
                long total = Math.round(seconds.doubleValue());
                return String.format(Locale.US, "%d:%02d", total / 60, Math.abs(total % 60));
            }

            @Override
            public Number fromString(String text) {
                throw new UnsupportedOperationException("the axis is display only");
            }
        } : null);
    }

    private void plot(List<LogEntry> history, String axisLabel, String categoryClass,
            ToDoubleFunction<LogEntry> measurement) {

        progressYAxis.setLabel(axisLabel);
        progressChart.getStyleClass().removeAll("chart-lift", "chart-cardio");
        progressChart.getStyleClass().add(categoryClass);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        Set<String> used = new HashSet<>();
        for (LogEntry entry : history) {
            // A CategoryAxis merges repeated labels, which would silently drop
            // a second session logged on the same day.
            String label = entry.getDate().format(CHART_DATE);
            String unique = label;
            for (int repeat = 2; !used.add(unique); repeat++) {
                unique = label + " (" + repeat + ")";
            }
            series.getData().add(
                    new XYChart.Data<>(unique, measurement.applyAsDouble(entry)));
        }

        progressChart.getData().setAll(List.of(series));
    }

    private void showPersonalRecord(String text) {
        prChip.setText(text);
        setShown(prChip, true);
    }

    private void showProgressMessage(String message) {
        progressMessage.setText(message);
        progressMessage.setVisible(true);
        progressChart.setVisible(false);
        progressChart.getData().clear();
        setShown(prChip, false);
        setShown(progressChip, false);
        blankMetric(metricOneLabel, metricOneValue, metricOneUnit);
        blankMetric(metricTwoLabel, metricTwoValue, metricTwoUnit);
        blankMetric(metricThreeLabel, metricThreeValue, metricThreeUnit);
    }

    private void blankMetric(Label label, Label value, Label unit) {
        setMetric(label, value, unit, "—", "—", "", "metric-blue");
    }

    private void setMetric(Label label, Label value, Label unit,
            String labelText, String valueText, String unitText, String accent) {

        label.setText(labelText);
        value.setText(valueText);
        value.getStyleClass().removeAll(
                "metric-green", "metric-red", "metric-blue", "metric-purple", "metric-yellow");
        value.getStyleClass().add(accent);
        unit.setText(unitText);
        setShown(unit, !unitText.isEmpty());
    }

    /** Renders the category as a colored badge rather than bare text. */
    private static class CategoryChipCell extends TableCell<ExerciseRow, Category> {

        @Override
        protected void updateItem(Category category, boolean empty) {
            super.updateItem(category, empty);

            if (empty || category == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label chip = new Label(category.displayName());
            chip.getStyleClass().addAll("chip",
                    category == Category.CARDIO ? "chip-cardio" : "chip-strength");
            setText(null);
            setGraphic(chip);
        }
    }
}
