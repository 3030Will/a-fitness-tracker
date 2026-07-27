package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workouttracker.util.Database;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Loads every FXML file the way the application does.
 *
 * <p>Most FXML mistakes — a misspelled fx:id, a handler that does not exist on
 * the controller, a missing import — are invisible until the file is loaded,
 * and then they throw. Loading each one here turns that into a test failure
 * instead of a crash in front of whoever is running the app.
 *
 * <p>This does not open a window or check that anything looks right. It checks
 * that the wiring holds.
 */
class FxmlSmokeTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(30, TimeUnit.SECONDS), "the JavaFX toolkit did not start");
    }

    @BeforeEach
    void useThrowawayDatabase() {
        // MainController loads the exercise list as it initializes, so it needs
        // a database — just not the real one.
        Database.configure("jdbc:sqlite:" + tempDir.resolve("test.db"));
        Database.initialize();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/com/workouttracker/ui/MainView.fxml",
            "/com/workouttracker/ui/ExerciseDialog.fxml",
            "/com/workouttracker/ui/EntryDialog.fxml"
    })
    @DisplayName("the layout loads and its controller wires up")
    void loads(String path) throws Exception {
        URL layout = getClass().getResource(path);
        assertNotNull(layout, path + " is not on the classpath");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                assertNotNull(new FXMLLoader(layout).load(), path + " loaded as null");
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });

        assertTrue(done.await(30, TimeUnit.SECONDS), "loading " + path + " timed out");
        if (failure.get() != null) {
            throw new AssertionError("failed to load " + path, failure.get());
        }
    }
}
