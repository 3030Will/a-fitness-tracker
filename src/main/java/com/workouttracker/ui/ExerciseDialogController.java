package com.workouttracker.ui;

import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.service.ExerciseService;
import com.workouttracker.util.DataAccessException;
import com.workouttracker.util.ValidationException;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * The add and edit form for an exercise.
 *
 * <p>Saving happens while the dialog is still open: if the service refuses the
 * input, the problems appear inline and the dialog stays put with what the
 * user typed, rather than closing and losing it.
 */
public class ExerciseDialogController {

    private static final String FXML = "/com/workouttracker/ui/ExerciseDialog.fxml";

    @FXML private VBox errorBanner;
    @FXML private Label errorLabel;
    @FXML private TextField nameField;
    @FXML private ToggleButton cardioToggle;
    @FXML private ToggleButton liftToggle;
    @FXML private Label categoryHint;

    private ExerciseService service;
    private Exercise existing;
    private Exercise saved;

    /**
     * Opens the form.
     *
     * @param existing the exercise to edit, or null to create a new one
     * @return the saved exercise, or empty if the user cancelled
     */
    public static Optional<Exercise> open(Window owner, Exercise existing, ExerciseService service)
            throws IOException {

        URL layout = ExerciseDialogController.class.getResource(FXML);
        if (layout == null) {
            throw new IOException("Missing resource on the classpath: " + FXML);
        }

        FXMLLoader loader = new FXMLLoader(layout);
        DialogPane pane = loader.load();
        ExerciseDialogController controller = loader.getController();
        controller.prepare(existing, service);

        boolean creating = existing == null;
        Dialog<Exercise> dialog = new Dialog<>();
        dialog.setDialogPane(pane);
        dialog.initOwner(owner);
        dialog.setTitle(creating ? "New exercise" : "Edit exercise");
        pane.setHeaderText(creating
                ? "Add an exercise to your library"
                : "Edit \"" + existing.getName() + "\"");
        Alerts.applyTheme(dialog);

        Button confirm = (Button) pane.lookupButton(ButtonType.OK);
        confirm.setText(creating ? "Create" : "Save");
        confirm.getStyleClass().add("button-filled");
        confirm.setId("dialogConfirm");

        // Runs before the dialog would close, so a refused save keeps it open.
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            if (controller.save() == null) {
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == ButtonType.OK ? controller.saved : null);
        return dialog.showAndWait();
    }

    @FXML
    private void initialize() {
        ToggleGroup categories = new ToggleGroup();
        cardioToggle.setToggleGroup(categories);
        liftToggle.setToggleGroup(categories);
        liftToggle.setSelected(true);

        // A segmented control always has one segment chosen; without this,
        // clicking the selected one would leave no category at all.
        categories.selectedToggleProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                categories.selectToggle(previous);
            }
        });
    }

    private void prepare(Exercise existing, ExerciseService service) {
        this.existing = existing;
        this.service = service;

        if (existing == null) {
            return;
        }

        nameField.setText(existing.getName());
        (existing.getCategory() == Category.CARDIO ? cardioToggle : liftToggle).setSelected(true);

        // The category is frozen once entries exist, because the columns they
        // occupy depend on it. Better to disable the control and say why than
        // to accept the change and reject it on save.
        int entries = service.entryCount(existing.getId());
        if (entries > 0) {
            cardioToggle.setDisable(true);
            liftToggle.setDisable(true);
            show(categoryHint, "Fixed while this exercise has %d log %s. Delete them first to change it."
                    .formatted(entries, entries == 1 ? "entry" : "entries"));
        }
    }

    /**
     * @return the saved exercise, or null if it was refused and the problems
     *         are now showing
     */
    private Exercise save() {
        clearErrors();
        Category category = cardioToggle.isSelected() ? Category.CARDIO : Category.WEIGHTLIFTING;

        try {
            saved = existing == null
                    ? service.create(nameField.getText(), category)
                    : service.update(existing.getId(), nameField.getText(), category);
            return saved;

        } catch (ValidationException e) {
            showErrors(e.getErrors());
            return null;
        } catch (DataAccessException e) {
            showErrors(List.of(e.getMessage()));
            return null;
        }
    }

    /**
     * Every rule that can fail here concerns the name — the category comes
     * from a control that cannot be left empty — so the name field is what
     * gets marked.
     */
    private void showErrors(List<String> errors) {
        show(errorBanner, null);
        errorLabel.setText(String.join("\n", errors));
        Dialogs.growToFit(errorLabel);
        if (!nameField.getStyleClass().contains("field-error")) {
            nameField.getStyleClass().add("field-error");
        }
        nameField.requestFocus();
    }

    private void clearErrors() {
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);
        nameField.getStyleClass().remove("field-error");
    }

    private void show(javafx.scene.Node node, String text) {
        if (text != null && node instanceof Label label) {
            label.setText(text);
        }
        node.setVisible(true);
        node.setManaged(true);
    }
}
