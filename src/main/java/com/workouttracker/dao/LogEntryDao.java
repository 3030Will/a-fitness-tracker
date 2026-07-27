package com.workouttracker.dao;

import com.workouttracker.model.CardioEntry;
import com.workouttracker.model.Category;
import com.workouttracker.model.LiftEntry;
import com.workouttracker.model.LogEntry;
import com.workouttracker.util.DataAccessException;
import com.workouttracker.util.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All SQL for the {@code log_entries} table.
 *
 * <p>Entries are stored in one table with nullable columns for each kind, so
 * reading one back means deciding which subclass to build. The category lives
 * on {@code exercises}, so every read joins to it rather than making callers
 * supply the category themselves.
 */
public class LogEntryDao {

    private static final String SELECT_BASE = """
            SELECT le.id, le.exercise_id, le.date, le.sets, le.reps, le.weight,
                   le.distance, le.duration, e.category
            FROM log_entries le
            JOIN exercises e ON e.id = le.exercise_id
            """;

    private static final String SELECT_BY_ID = SELECT_BASE + " WHERE le.id = ?";

    private static final String SELECT_BY_EXERCISE = SELECT_BASE + """
             WHERE le.exercise_id = ?
             ORDER BY le.date DESC, le.id DESC
            """;

    private static final String SELECT_BY_EXERCISE_OLDEST_FIRST = SELECT_BASE + """
             WHERE le.exercise_id = ?
             ORDER BY le.date ASC, le.id ASC
            """;

    private static final String INSERT = """
            INSERT INTO log_entries
                (exercise_id, date, sets, reps, weight, distance, duration)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String UPDATE = """
            UPDATE log_entries
            SET date = ?, sets = ?, reps = ?, weight = ?, distance = ?, duration = ?
            WHERE id = ?
            """;

    private static final String DELETE = "DELETE FROM log_entries WHERE id = ?";

    private static final String COUNT_BY_EXERCISE =
            "SELECT COUNT(*) FROM log_entries WHERE exercise_id = ?";

    /**
     * Saves a new entry and assigns it the id the database generated.
     *
     * @return the same instance, now carrying its id
     */
    public LogEntry insert(LogEntry entry) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            statement.setLong(1, entry.getExerciseId());
            statement.setString(2, entry.getDate().toString());
            bindMeasurements(statement, entry, 3);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    entry.setId(keys.getLong(1));
                }
            }
            return entry;

        } catch (SQLException e) {
            throw new DataAccessException("Could not save the log entry.", e);
        }
    }

    /**
     * @return true if a row was updated, false if no entry has that id
     */
    public boolean update(LogEntry entry) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {

            statement.setString(1, entry.getDate().toString());
            bindMeasurements(statement, entry, 2);
            statement.setLong(7, entry.getId());
            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DataAccessException("Could not update the log entry.", e);
        }
    }

    /**
     * @return true if a row was deleted
     */
    public boolean delete(long id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {

            statement.setLong(1, id);
            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the log entry.", e);
        }
    }

    public Optional<LogEntry> findById(long id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {

            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Could not load the log entry.", e);
        }
    }

    /** Entries for one exercise, most recent first — the order the log view wants. */
    public List<LogEntry> findByExercise(long exerciseId) {
        return query(SELECT_BY_EXERCISE, exerciseId);
    }

    /**
     * Entries for one exercise, oldest first — the order a progress history
     * reads in, since improvement runs forwards in time.
     */
    public List<LogEntry> findByExerciseOldestFirst(long exerciseId) {
        return query(SELECT_BY_EXERCISE_OLDEST_FIRST, exerciseId);
    }

    /**
     * How many entries an exercise has, without loading them. Used to warn
     * before a delete cascades, and to block a category change that would
     * strand the entries already recorded.
     */
    public int countByExercise(long exerciseId) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(COUNT_BY_EXERCISE)) {

            statement.setLong(1, exerciseId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }

        } catch (SQLException e) {
            throw new DataAccessException("Could not count the log entries.", e);
        }
    }

    private List<LogEntry> query(String sql, long exerciseId) {
        List<LogEntry> entries = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setLong(1, exerciseId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(map(rs));
                }
            }
            return entries;

        } catch (SQLException e) {
            throw new DataAccessException("Could not load the log entries.", e);
        }
    }

    /**
     * Writes the measurement columns, leaving the ones belonging to the other
     * kind of entry null. Exhaustive over the sealed type, so a new subclass
     * would break the build here rather than quietly writing nothing.
     */
    private void bindMeasurements(PreparedStatement statement, LogEntry entry, int offset)
            throws SQLException {

        switch (entry) {
            case LiftEntry lift -> {
                statement.setInt(offset, lift.getSets());
                statement.setInt(offset + 1, lift.getReps());
                statement.setDouble(offset + 2, lift.getWeight());
                statement.setNull(offset + 3, Types.REAL);      // distance
                statement.setNull(offset + 4, Types.INTEGER);   // duration
            }
            case CardioEntry cardio -> {
                statement.setNull(offset, Types.INTEGER);       // sets
                statement.setNull(offset + 1, Types.INTEGER);   // reps
                statement.setNull(offset + 2, Types.REAL);      // weight
                statement.setDouble(offset + 3, cardio.getDistance());
                statement.setInt(offset + 4, cardio.getDuration());
            }
        }
    }

    /** Builds the subclass matching the parent exercise's category. */
    private LogEntry map(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        long exerciseId = rs.getLong("exercise_id");
        LocalDate date = LocalDate.parse(rs.getString("date"));

        return switch (Category.valueOf(rs.getString("category"))) {
            case CARDIO -> new CardioEntry(
                    id, exerciseId, date, rs.getDouble("distance"), rs.getInt("duration"));
            case WEIGHTLIFTING -> new LiftEntry(
                    id, exerciseId, date, rs.getInt("sets"), rs.getInt("reps"), rs.getDouble("weight"));
        };
    }
}
