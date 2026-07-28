package com.workouttracker.ui;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Window;

/**
 * Layout fixes shared by the forms.
 */
final class Dialogs {

    private Dialogs() {
        // Utility class.
    }

    /**
     * Makes a wrapping label tall enough for text set after the dialog was
     * laid out, and grows the dialog to match.
     *
     * <p>A dialog sizes itself when it is built. A validation banner is filled
     * in later, so its label keeps the height it was given when empty and
     * clips whatever does not fit — losing the third of three messages, and
     * ending the second in an ellipsis. Asking for the preferred height and
     * then resizing the window fits the text that is actually there.
     */
    static void growToFit(Label label) {
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.applyCss();
        label.requestLayout();

        Scene scene = label.getScene();
        if (scene == null) {
            return;
        }
        Window window = scene.getWindow();
        if (window != null) {
            window.sizeToScene();
        }
    }
}
