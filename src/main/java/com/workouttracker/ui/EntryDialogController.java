package com.workouttracker.ui;

import com.workouttracker.model.CardioEntry;
import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.model.LiftEntry;
import com.workouttracker.model.LogEntry;
import com.workouttracker.service.LogEntryService;
import com.workouttracker.util.DataAccessException;
import com.workouttracker.util.ValidationException;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * The add and edit form for a log entry.
 *
 * <p>Which measurements are asked for follows the exercise's category, so a
 * run is never prompted for sets and reps. As with the exercise form, saving
 * happens while the dialog is open and a refusal keeps it there.
 */
public class EntryDialogController {

    private static final String FXML = "/com/workouttracker/ui/EntryDialog.fxml";

    @FXML private VBox errorBanner;
    @FXML private Label errorLabel;
    @FXML private DatePicker datePicker;
    @FXML private VBox liftFields;
    @FXML private VBox cardioFields;
    @FXML private TextField setsField;
    @FXML private TextField repsField;
    @FXML private TextField weightField;
    @FXML private TextField distanceField;
    @FXML private TextField durationField;

    private LogEntryService service;
    private Exercise exercise;
    private LogEntry existing;
    private LogEntry saved;

    /** Which field each validation message belongs to, keyed by how it opens. */
    private Map<String, Node> fieldsByMessagePrefix;

    /**
     * Opens the form.
     *
     * @param existing the entry to edit, or null to log a new one
     * @return the saved entry, or empty if the user cancelled
     */
    public static Optional<LogEntry> open(Window owner, Exercise exercise, LogEntry existing,
            LogEntryService service) throws IOException {

        URL layout = EntryDialogController.class.getResource(FXML);
        if (layout == null) {
            throw new IOException("Missing resource on the classpath: " + FXML);
        }

        FXMLLoader loader = new FXMLLoader(layout);
        DialogPane pane = loader.load();
        EntryDialogController controller = loader.getController();
        controller.prepare(exercise, existing, service);

        boolean creating = existing == null;
        Dialog<LogEntry> dialog = new Dialog<>();
        dialog.setDialogPane(pane);
        dialog.initOwner(owner);
        dialog.setTitle(creating ? "Log a workout" : "Edit entry");
        pane.setHeaderText(creating
                ? "Log a workout for \"" + exercise.getName() + "\""
                : "Edit this " + exercise.getName() + " entry");
        Alerts.applyTheme(dialog);

        Button confirm = (Button) pane.lookupButton(ButtonType.OK);
        confirm.setText(creating ? "Log it" : "Save");
        confirm.getStyleClass().add("button-filled");
        confirm.setId("dialogConfirm");
        confirm.addEventFilter(ActionEvent.ACTION, event -> {
            if (controller.save() == null) {
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == ButtonType.OK ? controller.saved : null);
        return dialog.showAndWait();
    }

    private void prepare(Exercise exercise, LogEntry existing, LogEntryService service) {
        this.exercise = exercise;
        this.existing = existing;
        this.service = service;

        boolean lifting = exercise.getCategory() == Category.WEIGHTLIFTING;
        showOnly(lifting ? liftFields : cardioFields, lifting ? cardioFields : liftFields);

        fieldsByMessagePrefix = new LinkedHashMap<>();
        fieldsByMessagePrefix.put("Date", datePicker);
        fieldsByMessagePrefix.put("Sets", setsField);
        fieldsByMessagePrefix.put("Reps", repsField);
        fieldsByMessagePrefix.put("Weight", weightField);
        fieldsByMessagePrefix.put("Distance", distanceField);
        fieldsByMessagePrefix.put("Duration", durationField);

        datePicker.setValue(existing == null ? LocalDate.now() : existing.getDate());

        switch (existing) {
            case LiftEntry lift -> {
                setsField.setText(String.valueOf(lift.getSets()));
                repsField.setText(String.valueOf(lift.getReps()));
                weightField.setText(lift.formattedWeight());
            }
            case CardioEntry cardio -> {
                distanceField.setText(String.format(Locale.US, "%.2f", cardio.getDistance()));
                durationField.setText(cardio.formattedDuration());
            }
            case null -> {
                // Logging a new workout; the fields stay empty behind their prompts.
            }
        }
    }

    /**
     * @return the saved entry, or null if it was refused and the problems are
     *         now showing
     */
    private LogEntry save() {
        clearErrors();
        boolean lifting = exercise.getCategory() == Category.WEIGHTLIFTING;
        LocalDate date = datePicker.getValue();

        try {
            if (existing == null) {
                saved = lifting
                        ? service.addLift(exercise.getId(), date,
                                setsField.getText(), repsField.getText(), weightField.getText())
                        : service.addCardio(exercise.getId(), date,
                                distanceField.getText(), durationField.getText());
            } else {
                saved = lifting
                        ? service.updateLift(existing.getId(), date,
                                setsField.getText(), repsField.getText(), weightField.getText())
                        : service.updateCardio(existing.getId(), date,
                                distanceField.getText(), durationField.getText());
            }
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
     * Marks the fields the messages are about. Validator writes each message
     * starting with the field's own name, which is what this matches on — a
     * coupling worth knowing about if those messages are ever reworded. A
     * message that matches nothing still shows in the banner.
     */
    private void showErrors(List<String> errors) {
        errorLabel.setText(String.join("\n", errors));
        errorBanner.setVisible(true);
        errorBanner.setManaged(true);

        for (String error : errors) {
            fieldsByMessagePrefix.entrySet().stream()
                    .filter(entry -> error.startsWith(entry.getKey()))
                    .findFirst()
                    .ifPresent(entry -> mark(entry.getValue()));
        }
    }

    private void clearErrors() {
        errorBanner.setVisible(false);
        errorBanner.setManaged(false);
        fieldsByMessagePrefix.values()
                .forEach(field -> field.getStyleClass().remove("field-error"));
    }

    private void mark(Node field) {
        if (!field.getStyleClass().contains("field-error")) {
            field.getStyleClass().add("field-error");
        }
    }

    private void showOnly(VBox wanted, VBox hidden) {
        wanted.setVisible(true);
        wanted.setManaged(true);
        hidden.setVisible(false);
        hidden.setManaged(false);
    }
}
