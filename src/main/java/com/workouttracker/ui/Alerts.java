package com.workouttracker.ui;

import java.net.URL;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Window;

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

    /**
     * Reports something that went wrong, in language aimed at the user.
     *
     * @param owner the window this belongs to; null only before one exists
     */
    public static void error(Window owner, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Workout Progress Tracker");
        alert.setHeaderText(header);
        alert.setContentText(content);
        attachTo(alert, owner);
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
    public static boolean confirmDestructive(Window owner, String header, String content,
            String confirmLabel) {

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Workout Progress Tracker");
        alert.setHeaderText(header);
        alert.setContentText(content);

        ButtonType confirm = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        alert.getButtonTypes().setAll(ButtonType.CANCEL, confirm);
        attachTo(alert, owner);

        Button confirmButton = (Button) alert.getDialogPane().lookupButton(confirm);
        confirmButton.getStyleClass().add("button-danger");
        confirmButton.setDefaultButton(false);
        // The label is shared with the toolbar button that opened this prompt,
        // so tests need something unambiguous to aim at.
        confirmButton.setId("confirmDestructive");

        Button cancelButton = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelButton.setDefaultButton(true);

        // A ButtonBar gives every button the same width, worked out before the
        // stylesheet widens their padding, so "Cancel" came out as "Can...".
        // Letting each keep its preferred width fits the label it actually has.
        for (Button button : List.of(confirmButton, cancelButton)) {
            button.setMinWidth(Region.USE_PREF_SIZE);
            ButtonBar.setButtonUniformSize(button, false);
        }

        return alert.showAndWait().filter(button -> button == confirm).isPresent();
    }

    /**
     * Ties a dialog to the window that opened it, then themes it.
     *
     * <p>Without an owner a dialog is a top-level window belonging to nothing.
     * On macOS that means it is not part of the application's full-screen
     * space: opening one leaves full screen, the dialog is handed the whole
     * display to itself, and closing it drops the user on the desktop rather
     * than back in the app.
     */
    static void attachTo(Dialog<?> dialog, Window owner) {
        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }
        applyTheme(dialog);
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
