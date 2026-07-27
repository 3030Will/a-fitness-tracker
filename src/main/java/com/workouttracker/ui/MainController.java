package com.workouttracker.ui;

import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.service.ExerciseService;
import com.workouttracker.util.DataAccessException;
import java.io.IOException;
import java.util.Optional;
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

    private final ExerciseService exercises = new ExerciseService();
    private final ObservableList<ExerciseRow> rows = FXCollections.observableArrayList();

    @FXML private ToggleButton exercisesNav;
    @FXML private TableView<ExerciseRow> exerciseTable;
    @FXML private TableColumn<ExerciseRow, String> nameColumn;
    @FXML private TableColumn<ExerciseRow, Category> categoryColumn;
    @FXML private TableColumn<ExerciseRow, Number> entriesColumn;
    @FXML private Button editButton;
    @FXML private Button deleteButton;

    @FXML
    private void initialize() {
        setUpNavigation();
        setUpTable();
        setUpSelection();
        refresh();
    }

    /** Edit and Delete act on the selected row, so they are dimmed without one. */
    private void setUpSelection() {
        BooleanBinding nothingSelected =
                exerciseTable.getSelectionModel().selectedItemProperty().isNull();
        editButton.disableProperty().bind(nothingSelected);
        deleteButton.disableProperty().bind(nothingSelected);

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

        entriesColumn.setCellValueFactory(row ->
                new ReadOnlyObjectWrapper<>(row.getValue().entryCount()));
        entriesColumn.getStyleClass().add("table-cell-numeric");
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
