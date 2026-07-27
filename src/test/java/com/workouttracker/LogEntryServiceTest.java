package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workouttracker.model.CardioEntry;
import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.model.LiftEntry;
import com.workouttracker.model.LogEntry;
import com.workouttracker.service.ExerciseService;
import com.workouttracker.service.LogEntryService;
import com.workouttracker.util.Database;
import com.workouttracker.util.ValidationException;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogEntryServiceTest {

    @TempDir
    Path tempDir;

    private LogEntryService service;
    private Exercise benchPress;
    private Exercise longRun;

    @BeforeEach
    void useThrowawayDatabase() throws ValidationException {
        Database.configure("jdbc:sqlite:" + tempDir.resolve("test.db"));
        Database.initialize();
        service = new LogEntryService();

        ExerciseService exercises = new ExerciseService();
        benchPress = exercises.create("Bench Press", Category.WEIGHTLIFTING);
        longRun = exercises.create("Long Run", Category.CARDIO);
    }

    @Test
    @DisplayName("a lift is saved with its measurements parsed")
    void addLiftSaves() throws ValidationException {
        LogEntry entry = service.addLift(
                benchPress.getId(), LocalDate.of(2026, 7, 26), "3", "10", "137.5");

        LiftEntry lift = assertInstanceOf(LiftEntry.class, entry);
        assertEquals(3, lift.getSets());
        assertEquals(10, lift.getReps());
        assertEquals(137.5, lift.getWeight());
    }

    @Test
    @DisplayName("a cardio session is saved with its duration read as seconds")
    void addCardioSaves() throws ValidationException {
        LogEntry entry = service.addCardio(
                longRun.getId(), LocalDate.of(2026, 7, 26), "3.1", "30:00");

        CardioEntry cardio = assertInstanceOf(CardioEntry.class, entry);
        assertEquals(3.1, cardio.getDistance());
        assertEquals(1800, cardio.getDuration());
        assertEquals("00:30:00", cardio.formattedDuration());
    }

    @Test
    @DisplayName("bodyweight lifts are allowed at zero pounds")
    void addLiftAcceptsBodyweight() throws ValidationException {
        LogEntry entry = service.addLift(benchPress.getId(), LocalDate.now(), "3", "12", "0");

        assertEquals(0, assertInstanceOf(LiftEntry.class, entry).getWeight());
    }

    @Test
    @DisplayName("a cardio entry cannot be logged against a weightlifting exercise")
    void cardioRejectedOnLiftExercise() {
        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.addCardio(benchPress.getId(), LocalDate.now(), "3.1", "30:00"));

        assertTrue(thrown.getMessage().contains("cannot take a cardio entry"),
                "got: " + thrown.getMessage());
        assertTrue(service.findByExercise(benchPress.getId()).isEmpty(), "nothing should be saved");
    }

    @Test
    @DisplayName("a lift cannot be logged against a cardio exercise")
    void liftRejectedOnCardioExercise() {
        assertThrows(ValidationException.class,
                () -> service.addLift(longRun.getId(), LocalDate.now(), "3", "10", "135"));

        assertTrue(service.findByExercise(longRun.getId()).isEmpty());
    }

    @Test
    @DisplayName("an entry against a missing exercise is refused")
    void rejectsMissingExercise() {
        assertThrows(ValidationException.class,
                () -> service.addLift(999, LocalDate.now(), "3", "10", "135"));
    }

    @Test
    @DisplayName("a future date is refused")
    void rejectsFutureDate() {
        assertThrows(ValidationException.class,
                () -> service.addLift(
                        benchPress.getId(), LocalDate.now().plusDays(1), "3", "10", "135"));
    }

    @Test
    @DisplayName("every bad measurement is reported at once")
    void reportsAllProblemsTogether() {
        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.addLift(benchPress.getId(), LocalDate.now(), "nope", "0", "-5"));

        assertEquals(3, thrown.getErrors().size(), "got: " + thrown.getErrors());
    }

    @Test
    @DisplayName("nothing is saved when validation fails")
    void savesNothingOnFailure() {
        assertThrows(ValidationException.class,
                () -> service.addCardio(longRun.getId(), LocalDate.now(), "-1", "nonsense"));

        assertTrue(service.findByExercise(longRun.getId()).isEmpty());
    }

    @Test
    @DisplayName("updateLift changes the stored measurements")
    void updateLiftChangesRow() throws ValidationException {
        LogEntry entry = service.addLift(benchPress.getId(), LocalDate.now(), "3", "10", "135");

        service.updateLift(entry.getId(), LocalDate.of(2026, 7, 20), "5", "5", "185");

        LiftEntry loaded = assertInstanceOf(LiftEntry.class,
                service.findById(entry.getId()).orElseThrow());
        assertEquals(5, loaded.getSets());
        assertEquals(185, loaded.getWeight());
        assertEquals(LocalDate.of(2026, 7, 20), loaded.getDate());
    }

    @Test
    @DisplayName("updateCardio changes the stored measurements")
    void updateCardioChangesRow() throws ValidationException {
        LogEntry entry = service.addCardio(longRun.getId(), LocalDate.now(), "3.1", "30:00");

        service.updateCardio(entry.getId(), LocalDate.now(), "5.0", "1:00:00");

        CardioEntry loaded = assertInstanceOf(CardioEntry.class,
                service.findById(entry.getId()).orElseThrow());
        assertEquals(5.0, loaded.getDistance());
        assertEquals(3600, loaded.getDuration());
    }

    @Test
    @DisplayName("a lift entry cannot be updated as though it were cardio")
    void updateRejectsWrongKind() throws ValidationException {
        LogEntry entry = service.addLift(benchPress.getId(), LocalDate.now(), "3", "10", "135");

        assertThrows(ValidationException.class,
                () -> service.updateCardio(entry.getId(), LocalDate.now(), "3.1", "30:00"));
    }

    @Test
    @DisplayName("updating an entry that no longer exists is refused")
    void updateRejectsMissingEntry() {
        assertThrows(ValidationException.class,
                () -> service.updateLift(999, LocalDate.now(), "3", "10", "135"));
    }

    @Test
    @DisplayName("delete removes the entry")
    void deleteRemovesEntry() throws ValidationException {
        LogEntry entry = service.addCardio(longRun.getId(), LocalDate.now(), "3.1", "30:00");

        assertTrue(service.delete(entry.getId()));

        assertTrue(service.findByExercise(longRun.getId()).isEmpty());
    }

    @Test
    @DisplayName("history reads oldest first, the log list newest first")
    void orderingDiffersByPurpose() throws ValidationException {
        service.addCardio(longRun.getId(), LocalDate.of(2026, 7, 20), "3.0", "25:00");
        service.addCardio(longRun.getId(), LocalDate.of(2026, 7, 26), "5.0", "45:00");

        assertEquals(LocalDate.of(2026, 7, 26),
                service.findByExercise(longRun.getId()).getFirst().getDate());
        assertEquals(LocalDate.of(2026, 7, 20),
                service.history(longRun.getId()).getFirst().getDate());
    }
}
