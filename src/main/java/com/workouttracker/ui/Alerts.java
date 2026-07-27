package com.workouttracker.ui;

import java.net.URL;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;

/**
 * Dialogs that carry the application's theme.
 *
 * <p>A dialog opens in its own scene, so it does not inherit the main window's
 * stylesheet and would otherwise appear in the default light theme against a
 * dark app.
 */
public final class Alerts {

    static final String STYLESHEET = "/com/workouttracker/ui/app.css";

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

    static void applyTheme(Dialog<?> dialog) {
        URL stylesheet = Alerts.class.getResource(STYLESHEET);
        if (stylesheet != null) {
            dialog.getDialogPane().getStylesheets().add(stylesheet.toExternalForm());
        }
    }
}
