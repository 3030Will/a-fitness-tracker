package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workouttracker.dao.ExerciseDao;
import com.workouttracker.dao.LogEntryDao;
import com.workouttracker.model.CardioEntry;
import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.model.LiftEntry;
import com.workouttracker.model.LogEntry;
import com.workouttracker.util.DataAccessException;
import com.workouttracker.util.Database;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogEntryDaoTest {

    @TempDir
    Path tempDir;

    private LogEntryDao dao;
    private Exercise benchPress;
    private Exercise longRun;

    @BeforeEach
    void useThrowawayDatabase() {
        Database.configure("jdbc:sqlite:" + tempDir.resolve("test.db"));
        Database.initialize();
        dao = new LogEntryDao();

        ExerciseDao exercises = new ExerciseDao();
        benchPress = exercises.insert(new Exercise("Bench Press", Category.WEIGHTLIFTING));
        longRun = exercises.insert(new Exercise("Long Run", Category.CARDIO));
    }

    @Test
    @DisplayName("insert assigns the generated id")
    void insertAssignsId() {
        LogEntry entry = new LiftEntry(benchPress.getId(), LocalDate.of(2026, 7, 26), 3, 10, 135);
        assertTrue(entry.isNew());

        dao.insert(entry);

        assertFalse(entry.isNew());
        assertNotEquals(0, entry.getId());
    }

    @Test
    @DisplayName("a lift entry reads back as a LiftEntry with its measurements intact")
    void liftEntryRoundTrip() {
        LiftEntry saved = (LiftEntry) dao.insert(
                new LiftEntry(benchPress.getId(), LocalDate.of(2026, 7, 26), 3, 10, 137.5));

        LogEntry loaded = dao.findById(saved.getId()).orElseThrow();

        LiftEntry lift = assertInstanceOf(LiftEntry.class, loaded);
        assertEquals(3, lift.getSets());
        assertEquals(10, lift.getReps());
        assertEquals(137.5, lift.getWeight());
        assertEquals(LocalDate.of(2026, 7, 26), lift.getDate());
        assertEquals(benchPress.getId(), lift.getExerciseId());
        assertEquals(saved, lift);
    }

    @Test
    @DisplayName("a cardio entry reads back as a CardioEntry with its measurements intact")
    void cardioEntryRoundTrip() {
        CardioEntry saved = (CardioEntry) dao.insert(
                new CardioEntry(longRun.getId(), LocalDate.of(2026, 7, 26), 3.1, 1800));

        LogEntry loaded = dao.findById(saved.getId()).orElseThrow();

        CardioEntry cardio = assertInstanceOf(CardioEntry.class, loaded);
        assertEquals(3.1, cardio.getDistance());
        assertEquals(1800, cardio.getDuration());
        assertEquals(LocalDate.of(2026, 7, 26), cardio.getDate());
        assertEquals(saved, cardio);
    }

    @Test
    @DisplayName("the subclass follows the exercise's category, not the data supplied")
    void subclassFollowsCategory() {
        dao.insert(new LiftEntry(benchPress.getId(), LocalDate.of(2026, 7, 26), 3, 10, 135));
        dao.insert(new CardioEntry(longRun.getId(), LocalDate.of(2026, 7, 26), 3.1, 1800));

        assertInstanceOf(LiftEntry.class, dao.findByExercise(benchPress.getId()).getFirst());
        assertInstanceOf(CardioEntry.class, dao.findByExercise(longRun.getId()).getFirst());
    }

    @Test
    @DisplayName("a lift entry leaves the cardio columns null, and the reverse")
    void unusedColumnsAreNull() throws SQLException {
        long liftId = dao.insert(
                new LiftEntry(benchPress.getId(), LocalDate.of(2026, 7, 26), 3, 10, 135)).getId();
        long cardioId = dao.insert(
                new CardioEntry(longRun.getId(), LocalDate.of(2026, 7, 26), 3.1, 1800)).getId();

        assertNull(rawValue(liftId, "distance"), "lift entry wrote a distance");
        assertNull(rawValue(liftId, "duration"), "lift entry wrote a duration");
        assertNull(rawValue(cardioId, "sets"), "cardio entry wrote sets");
        assertNull(rawValue(cardioId, "reps"), "cardio entry wrote reps");
        assertNull(rawValue(cardioId, "weight"), "cardio entry wrote a weight");
    }

    @Test
    @DisplayName("findById returns empty for an unknown id")
    void findByIdMissing() {
        assertEquals(Optional.empty(), dao.findById(999));
    }

    @Test
    @DisplayName("findByExercise returns the newest entry first")
    void findByExerciseNewestFirst() {
        dao.insert(new CardioEntry(longRun.getId(), LocalDate.of(2026, 7, 20), 3.0, 1500));
        dao.insert(new CardioEntry(longRun.getId(), LocalDate.of(2026, 7, 26), 5.0, 2700));
        dao.insert(new CardioEntry(longRun.getId(), LocalDate.of(2026, 7, 23), 4.0, 2100));

        List<LocalDate> dates = dao.findByExercise(longRun.getId()).stream()
                .map(LogEntry::getDate)
                .toList();

        assertEquals(List.of(
                LocalDate.of(2026, 7, 26),
                LocalDate.of(2026, 7, 23),
                LocalDate.of(2026, 7, 20)), dates);
    }

    @Test
    @DisplayName("findByExerciseOldestFirst reverses that order")
    void findByExerciseOldestFirst() {
        dao.insert(new CardioEntry(longRun.getId(), LocalDate.of(2026, 7, 20), 3.0, 1500));
        dao.insert(new CardioEntry(longRun.getId(), LocalDate.of(2026, 7, 26), 5.0, 2700));

        List<LocalDate> dates = dao.findByExerciseOldestFirst(longRun.getId()).stream()
                .map(LogEntry::getDate)
                .toList();

        assertEquals(List.of(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26)), dates);
    }

    @Test
    @DisplayName("findByExercise returns only that exercise's entries")
    void findByExerciseIsScoped() {
        dao.insert(new LiftEntry(benchPress.getId(), LocalDate.of(2026, 7, 26), 3, 10, 135));
        dao.insert(new CardioEntry(longRun.getId(), LocalDate.of(2026, 7, 26), 3.1, 1800));

        assertEquals(1, dao.findByExercise(benchPress.getId()).size());
        assertTrue(dao.findByExercise(999).isEmpty());
    }

    @Test
    @DisplayName("update changes the stored measurements")
    void updateChangesRow() {
        LiftEntry entry = (LiftEntry) dao.insert(
                new LiftEntry(benchPress.getId(), LocalDate.of(2026, 7, 26), 3, 10, 135));

        entry.setSets(5);
        entry.setReps(5);
        entry.setWeight(185);
        entry.setDate(LocalDate.of(2026, 7, 27));
        assertTrue(dao.update(entry));

        LiftEntry loaded = (LiftEntry) dao.findById(entry.getId()).orElseThrow();
        assertEquals(5, loaded.getSets());
        assertEquals(5, loaded.getReps());
        assertEquals(185, loaded.getWeight());
        assertEquals(LocalDate.of(2026, 7, 27), loaded.getDate());
    }

    @Test
    @DisplayName("update reports false when no entry has that id")
    void updateMissingReturnsFalse() {
        assertFalse(dao.update(
                new LiftEntry(999, benchPress.getId(), LocalDate.of(2026, 7, 26), 3, 10, 135)));
    }

    @Test
    @DisplayName("delete removes the entry")
    void deleteRemovesRow() {
        LogEntry entry = dao.insert(
                new CardioEntry(longRun.getId(), LocalDate.of(2026, 7, 26), 3.1, 1800));

        assertTrue(dao.delete(entry.getId()));

        assertEquals(Optional.empty(), dao.findById(entry.getId()));
    }

    @Test
    @DisplayName("delete reports false when no entry has that id")
    void deleteMissingReturnsFalse() {
        assertFalse(dao.delete(999));
    }

    @Test
    @DisplayName("an entry against a non-existent exercise is rejected")
    void orphanEntryRejected() {
        assertThrows(DataAccessException.class,
                () -> dao.insert(new CardioEntry(999, LocalDate.of(2026, 7, 26), 3.1, 1800)));
    }

    /** Reads a column straight from the table, bypassing the mapping, to check for nulls. */
    private Object rawValue(long entryId, String column) throws SQLException {
        try (Connection connection = Database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT " + column + " FROM log_entries WHERE id = " + entryId)) {
            return rs.getObject(1);
        }
    }
}
