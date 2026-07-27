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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

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
            Alerts.error("The exercise form could not be opened.", e.getMessage());
        }
    }

    private void delete(ExerciseRow row) {
        Exercise exercise = row.exercise();
        String consequence = row.entryCount() == 0
                ? "This cannot be undone."
                : "Its %d log %s will be deleted as well. This cannot be undone."
                        .formatted(row.entryCount(), row.entryCount() == 1 ? "entry" : "entries");

        if (!Alerts.confirmDestructive(
                "Delete \"%s\"?".formatted(exercise.getName()), consequence, "Delete")) {
            return;
        }

        try {
            exercises.delete(exercise.getId());
            refresh();
        } catch (DataAccessException e) {
            Alerts.error("\"%s\" could not be deleted.".formatted(exercise.getName()),
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
        boolean confirmed = Alerts.confirmDestructive(
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
            Alerts.error("The entry could not be deleted.", e.getMessage());
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
                Alerts.error("The workout form could not be opened.", e.getMessage());
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
        exercisesNav.setSelected(true);
        group.selectedToggleProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                group.selectToggle(previous);
            }
        });
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

        for (TableColumn<LogEntry, String> column :
                List.of(setsColumn, repsColumn, weightColumn, distanceColumn, durationColumn)) {
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
            Alerts.error("The log for \"%s\" could not be loaded.".formatted(exercise.getName()),
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
            Alerts.error("Your exercises could not be loaded.", e.getMessage());
        }
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
