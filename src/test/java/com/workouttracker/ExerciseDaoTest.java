package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workouttracker.dao.ExerciseDao;
import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.util.DataAccessException;
import com.workouttracker.util.Database;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExerciseDaoTest {

    @TempDir
    Path tempDir;

    private ExerciseDao dao;

    @BeforeEach
    void useThrowawayDatabase() {
        Database.configure("jdbc:sqlite:" + tempDir.resolve("test.db"));
        Database.initialize();
        dao = new ExerciseDao();
    }

    @Test
    @DisplayName("insert assigns the generated id")
    void insertAssignsId() {
        Exercise exercise = new Exercise("Bench Press", Category.WEIGHTLIFTING);
        assertTrue(exercise.isNew());

        dao.insert(exercise);

        assertFalse(exercise.isNew());
        assertNotEquals(0, exercise.getId());
    }

    @Test
    @DisplayName("an inserted exercise reads back unchanged")
    void roundTrip() {
        Exercise saved = dao.insert(new Exercise("Long Run", Category.CARDIO));

        Exercise loaded = dao.findById(saved.getId()).orElseThrow();

        assertEquals(saved.getId(), loaded.getId());
        assertEquals("Long Run", loaded.getName());
        assertEquals(Category.CARDIO, loaded.getCategory());
        assertEquals(saved, loaded);
    }

    @Test
    @DisplayName("findById returns empty for an unknown id")
    void findByIdMissing() {
        assertEquals(Optional.empty(), dao.findById(999));
    }

    @Test
    @DisplayName("findAll returns exercises ordered by name, ignoring case")
    void findAllOrdersByName() {
        dao.insert(new Exercise("squats", Category.WEIGHTLIFTING));
        dao.insert(new Exercise("Bench Press", Category.WEIGHTLIFTING));
        dao.insert(new Exercise("long run", Category.CARDIO));

        List<Exercise> all = dao.findAll();

        assertEquals(List.of("Bench Press", "long run", "squats"),
                all.stream().map(Exercise::getName).toList());
    }

    @Test
    @DisplayName("findAll is empty before anything is saved")
    void findAllEmpty() {
        assertTrue(dao.findAll().isEmpty());
    }

    @Test
    @DisplayName("update changes the stored name and category")
    void updateChangesRow() {
        Exercise exercise = dao.insert(new Exercise("Bench Pres", Category.CARDIO));

        exercise.setName("Bench Press");
        exercise.setCategory(Category.WEIGHTLIFTING);
        assertTrue(dao.update(exercise));

        Exercise loaded = dao.findById(exercise.getId()).orElseThrow();
        assertEquals("Bench Press", loaded.getName());
        assertEquals(Category.WEIGHTLIFTING, loaded.getCategory());
    }

    @Test
    @DisplayName("update reports false when no exercise has that id")
    void updateMissingReturnsFalse() {
        assertFalse(dao.update(new Exercise(999, "Ghost", Category.CARDIO)));
    }

    @Test
    @DisplayName("delete removes the exercise")
    void deleteRemovesRow() {
        Exercise exercise = dao.insert(new Exercise("Squats", Category.WEIGHTLIFTING));

        assertTrue(dao.delete(exercise.getId()));

        assertEquals(Optional.empty(), dao.findById(exercise.getId()));
    }

    @Test
    @DisplayName("delete reports false when no exercise has that id")
    void deleteMissingReturnsFalse() {
        assertFalse(dao.delete(999));
    }

    @Test
    @DisplayName("findByName ignores case")
    void findByNameIgnoresCase() {
        dao.insert(new Exercise("Bench Press", Category.WEIGHTLIFTING));

        assertTrue(dao.findByName("bench press").isPresent());
        assertTrue(dao.findByName("BENCH PRESS").isPresent());
        assertTrue(dao.findByName("Deadlift").isEmpty());
    }

    @Test
    @DisplayName("a duplicate name is rejected as a DataAccessException")
    void duplicateNameRejected() {
        dao.insert(new Exercise("Squats", Category.WEIGHTLIFTING));

        assertThrows(DataAccessException.class,
                () -> dao.insert(new Exercise("Squats", Category.WEIGHTLIFTING)));
    }

    @Test
    @DisplayName("a duplicate name differing only in case is also rejected")
    void duplicateNameIgnoringCaseRejected() {
        dao.insert(new Exercise("Squats", Category.WEIGHTLIFTING));

        assertThrows(DataAccessException.class,
                () -> dao.insert(new Exercise("SQUATS", Category.WEIGHTLIFTING)));
    }
}
