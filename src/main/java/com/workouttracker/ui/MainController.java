package com.workouttracker.ui;

import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.service.ExerciseService;
import com.workouttracker.util.DataAccessException;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
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

    @FXML
    private void initialize() {
        setUpNavigation();
        setUpTable();
        refresh();
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
        exerciseTable.setPlaceholder(new Label("No exercises yet."));

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
