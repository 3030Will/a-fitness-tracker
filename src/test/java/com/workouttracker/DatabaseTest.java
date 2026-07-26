package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workouttracker.util.Database;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {

    @TempDir
    Path tempDir;

    private Path dbFile;

    @BeforeEach
    void useThrowawayDatabase() throws SQLException {
        dbFile = tempDir.resolve("test.db");
        Database.configure("jdbc:sqlite:" + dbFile);
        Database.initialize();
    }

    @Test
    @DisplayName("initialize() creates the database file")
    void createsDatabaseFile() {
        assertTrue(Files.exists(dbFile), "expected " + dbFile + " to be created");
    }

    @Test
    @DisplayName("initialize() creates both tables and the lookup index")
    void createsTablesAndIndex() throws SQLException {
        assertTrue(objectExists("table", "exercises"), "exercises table missing");
        assertTrue(objectExists("table", "log_entries"), "log_entries table missing");
        assertTrue(objectExists("index", "idx_log_entries_exercise_id"), "index missing");
    }

    @Test
    @DisplayName("initialize() is safe to run against an existing database")
    void initializeIsIdempotent() throws SQLException {
        insertExercise("Bench Press", "WEIGHTLIFTING");

        Database.initialize();

        assertEquals(1, countRows("exercises"), "re-initializing must not drop data");
    }

    @Test
    @DisplayName("deleting an exercise cascades to its log entries")
    void deleteCascadesToLogEntries() throws SQLException {
        long exerciseId = insertExercise("Long Run", "CARDIO");
        insertCardioEntry(exerciseId, "2026-07-26", 3.1, 1800);
        insertCardioEntry(exerciseId, "2026-07-27", 5.0, 2700);
        assertEquals(2, countRows("log_entries"));

        try (Connection connection = Database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM exercises WHERE id = " + exerciseId);
        }

        assertEquals(0, countRows("log_entries"), "cascade did not fire — foreign keys off?");
    }

    @Test
    @DisplayName("a log entry cannot reference a non-existent exercise")
    void rejectsOrphanLogEntry() {
        assertThrows(SQLException.class,
                () -> insertCardioEntry(999L, "2026-07-26", 1.0, 600));
    }

    @Test
    @DisplayName("category is restricted to CARDIO or WEIGHTLIFTING")
    void rejectsUnknownCategory() {
        assertThrows(SQLException.class, () -> insertExercise("Yoga", "STRETCHING"));
    }

    @Test
    @DisplayName("exercise names are unique")
    void rejectsDuplicateName() throws SQLException {
        insertExercise("Squats", "WEIGHTLIFTING");

        assertThrows(SQLException.class, () -> insertExercise("Squats", "WEIGHTLIFTING"));
    }

    // --- helpers -----------------------------------------------------------

    private long insertExercise(String name, String category) throws SQLException {
        String sql = "INSERT INTO exercises (name, category) VALUES ('%s', '%s')"
                .formatted(name, category);
        try (Connection connection = Database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private void insertCardioEntry(long exerciseId, String date, double distance, int duration)
            throws SQLException {
        String sql = ("INSERT INTO log_entries (exercise_id, date, distance, duration) "
                + "VALUES (%d, '%s', %f, %d)").formatted(exerciseId, date, distance, duration);
        try (Connection connection = Database.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private int countRows(String table) throws SQLException {
        try (Connection connection = Database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.getInt(1);
        }
    }

    private boolean objectExists(String type, String name) throws SQLException {
        String sql = "SELECT COUNT(*) FROM sqlite_master WHERE type = '%s' AND name = '%s'"
                .formatted(type, name);
        try (Connection connection = Database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.getInt(1) > 0;
        }
    }
}
