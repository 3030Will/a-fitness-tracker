package com.workouttracker.ui;

import java.net.URL;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.paint.Color;

/**
 * Dialogs that carry the application's theme.
 *
 * <p>A dialog opens in its own scene, so it does not inherit the main window's
 * stylesheet and would otherwise appear in the default light theme against a
 * dark app.
 */
public final class Alerts {

    static final String STYLESHEET = "/com/workouttracker/ui/app.css";

    /** Matches -surface-high in app.css, the background of .dialog-pane. */
    private static final Color DIALOG_SURFACE = Color.web("#1D1D23");

    private Alerts() {
        // Utility class.
    }

    /** Reports something that went wrong, in language aimed at the user. */
    public static void error(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Workout Progress Tracker");
        alert.setHeaderText(header);
        alert.setContentText(content);
        applyTheme(alert);
        alert.showAndWait();
    }

    /**
     * Asks before doing something that cannot be undone.
     *
     * <p>The confirming button is deliberately not the default one: a stray
     * Return should not delete anything.
     *
     * @return true if the user confirmed
     */
    public static boolean confirmDestructive(String header, String content, String confirmLabel) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Workout Progress Tracker");
        alert.setHeaderText(header);
        alert.setContentText(content);

        ButtonType confirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(ButtonType.CANCEL, confirm);
        applyTheme(alert);

        Button confirmButton = (Button) alert.getDialogPane().lookupButton(confirm);
        confirmButton.getStyleClass().add("button-danger");
        confirmButton.setDefaultButton(false);
        // The label is shared with the toolbar button that opened this prompt,
        // so tests need something unambiguous to aim at.
        confirmButton.setId("confirmDestructive");

        Button cancelButton = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setDefaultButton(true);

        return alert.showAndWait().filter(button -> button == confirm).isPresent();
    }

    static void applyTheme(Dialog<?> dialog) {
        URL stylesheet = Alerts.class.getResource(STYLESHEET);
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }

        // The dialog pane is rounded, and the scene behind it defaults to
        // white, which shows through the corner cutouts as two bright notches.
        // Filling the scene with the pane's own colour hides them.
        dialog.setOnShown(shown -> {
            Scene scene = dialog.getDialogPane().getScene();
            if (scene != null) {
                scene.setFill(DIALOG_SURFACE);
            }
        });
    }
}
