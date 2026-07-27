package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
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

class ExerciseServiceTest {

    @TempDir
    Path tempDir;

    private ExerciseService service;
    private LogEntryService logEntries;

    @BeforeEach
    void useThrowawayDatabase() {
        Database.configure("jdbc:sqlite:" + tempDir.resolve("test.db"));
        Database.initialize();
        service = new ExerciseService();
        logEntries = new LogEntryService();
    }

    @Test
    @DisplayName("create saves a valid exercise")
    void createSaves() throws ValidationException {
        Exercise created = service.create("  Bench Press  ", Category.WEIGHTLIFTING);

        assertFalse(created.isNew());
        assertEquals("Bench Press", created.getName(), "name should have been trimmed");
        assertEquals(1, service.findAll().size());
    }

    @Test
    @DisplayName("create rejects a blank name before touching the database")
    void createRejectsBlankName() {
        assertThrows(ValidationException.class, () -> service.create("   ", Category.CARDIO));
        assertTrue(service.findAll().isEmpty());
    }

    @Test
    @DisplayName("create rejects a missing category")
    void createRejectsMissingCategory() {
        assertThrows(ValidationException.class, () -> service.create("Squats", null));
    }

    @Test
    @DisplayName("create reports a duplicate name in plain language")
    void createRejectsDuplicate() throws ValidationException {
        service.create("Squats", Category.WEIGHTLIFTING);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.create("Squats", Category.WEIGHTLIFTING));

        assertEquals("An exercise named \"Squats\" already exists.", thrown.getMessage());
    }

    @Test
    @DisplayName("create treats a name differing only in case as a duplicate")
    void createRejectsDuplicateIgnoringCase() throws ValidationException {
        service.create("Squats", Category.WEIGHTLIFTING);

        assertThrows(ValidationException.class,
                () -> service.create("SQUATS", Category.WEIGHTLIFTING));
    }

    @Test
    @DisplayName("update renames an exercise")
    void updateRenames() throws ValidationException {
        Exercise exercise = service.create("Bench Pres", Category.WEIGHTLIFTING);

        service.update(exercise.getId(), "Bench Press", Category.WEIGHTLIFTING);

        assertEquals("Bench Press", service.findById(exercise.getId()).orElseThrow().getName());
    }

    @Test
    @DisplayName("update lets an exercise keep its own name")
    void updateAllowsUnchangedName() throws ValidationException {
        Exercise exercise = service.create("Squats", Category.WEIGHTLIFTING);

        service.update(exercise.getId(), "Squats", Category.WEIGHTLIFTING);

        assertEquals("Squats", service.findById(exercise.getId()).orElseThrow().getName());
    }

    @Test
    @DisplayName("update rejects a name another exercise already has")
    void updateRejectsDuplicate() throws ValidationException {
        service.create("Squats", Category.WEIGHTLIFTING);
        Exercise deadlift = service.create("Deadlift", Category.WEIGHTLIFTING);

        assertThrows(ValidationException.class,
                () -> service.update(deadlift.getId(), "Squats", Category.WEIGHTLIFTING));
    }

    @Test
    @DisplayName("update rejects an exercise that no longer exists")
    void updateRejectsMissing() {
        assertThrows(ValidationException.class,
                () -> service.update(999, "Ghost", Category.CARDIO));
    }

    @Test
    @DisplayName("the category can change while the exercise has no entries")
    void categoryChangeAllowedWhenEmpty() throws ValidationException {
        Exercise exercise = service.create("Rowing", Category.WEIGHTLIFTING);

        service.update(exercise.getId(), "Rowing", Category.CARDIO);

        assertEquals(Category.CARDIO, service.findById(exercise.getId()).orElseThrow().getCategory());
    }

    @Test
    @DisplayName("the category cannot change once entries exist, which would strand them")
    void categoryChangeBlockedWhenEntriesExist() throws ValidationException {
        Exercise exercise = service.create("Bench Press", Category.WEIGHTLIFTING);
        logEntries.addLift(exercise.getId(), LocalDate.now(), "3", "10", "135");

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> service.update(exercise.getId(), "Bench Press", Category.CARDIO));

        assertTrue(thrown.getMessage().contains("1 log entry"),
                "expected the count in the message, got: " + thrown.getMessage());
        assertEquals(Category.WEIGHTLIFTING,
                service.findById(exercise.getId()).orElseThrow().getCategory());
    }

    @Test
    @DisplayName("renaming still works on an exercise that has entries")
    void renameAllowedWhenEntriesExist() throws ValidationException {
        Exercise exercise = service.create("Bench Pres", Category.WEIGHTLIFTING);
        logEntries.addLift(exercise.getId(), LocalDate.now(), "3", "10", "135");

        service.update(exercise.getId(), "Bench Press", Category.WEIGHTLIFTING);

        assertEquals("Bench Press", service.findById(exercise.getId()).orElseThrow().getName());
    }

    @Test
    @DisplayName("entryCount reports what a delete would take with it")
    void entryCountReportsCascadeSize() throws ValidationException {
        Exercise exercise = service.create("Long Run", Category.CARDIO);
        logEntries.addCardio(exercise.getId(), LocalDate.now(), "3.1", "30:00");
        logEntries.addCardio(exercise.getId(), LocalDate.now(), "5.0", "45:00");

        assertEquals(2, service.entryCount(exercise.getId()));
    }

    @Test
    @DisplayName("delete removes the exercise and its entries")
    void deleteCascades() throws ValidationException {
        Exercise exercise = service.create("Long Run", Category.CARDIO);
        logEntries.addCardio(exercise.getId(), LocalDate.now(), "3.1", "30:00");

        assertTrue(service.delete(exercise.getId()));

        assertTrue(service.findAll().isEmpty());
        assertTrue(logEntries.findByExercise(exercise.getId()).isEmpty());
    }
}
