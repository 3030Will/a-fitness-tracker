package com.workouttracker.dao;

import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.util.DataAccessException;
import com.workouttracker.util.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All SQL for the {@code exercises} table.
 */
public class ExerciseDao {

    private static final String INSERT =
            "INSERT INTO exercises (name, category) VALUES (?, ?)";

    private static final String UPDATE =
            "UPDATE exercises SET name = ?, category = ? WHERE id = ?";

    private static final String DELETE =
            "DELETE FROM exercises WHERE id = ?";

    private static final String SELECT_BY_ID =
            "SELECT id, name, category FROM exercises WHERE id = ?";

    private static final String SELECT_BY_NAME =
            "SELECT id, name, category FROM exercises WHERE name = ? COLLATE NOCASE";

    private static final String SELECT_ALL =
            "SELECT id, name, category FROM exercises ORDER BY name COLLATE NOCASE";

    /**
     * Saves a new exercise and assigns it the id the database generated.
     *
     * @return the same instance, now carrying its id
     */
    public Exercise insert(Exercise exercise) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, exercise.getName());
            statement.setString(2, exercise.getCategory().name());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    exercise.setId(keys.getLong(1));
                }
            }
            return exercise;

        } catch (SQLException e) {
            throw new DataAccessException(
                    "Could not save the exercise \"" + exercise.getName() + "\".", e);
        }
    }

    /**
     * @return true if a row was updated, false if no exercise has that id
     */
    public boolean update(Exercise exercise) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE)) {

            statement.setString(1, exercise.getName());
            statement.setString(2, exercise.getCategory().name());
            statement.setLong(3, exercise.getId());
            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DataAccessException(
                    "Could not update the exercise \"" + exercise.getName() + "\".", e);
        }
    }

    /**
     * Deletes an exercise and, by way of the cascade, all of its log entries.
     *
     * @return true if a row was deleted
     */
    public boolean delete(long id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE)) {

            statement.setLong(1, id);
            return statement.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the exercise.", e);
        }
    }

    public Optional<Exercise> findById(long id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID)) {

            statement.setLong(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Could not load the exercise.", e);
        }
    }

    /**
     * Looks an exercise up by name, ignoring case. Used to report a duplicate
     * before the database rejects it with a constraint violation.
     */
    public Optional<Exercise> findByName(String name) {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_NAME)) {

            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DataAccessException("Could not look up the exercise \"" + name + "\".", e);
        }
    }

    /** Every exercise, ordered by name. */
    public List<Exercise> findAll() {
        List<Exercise> exercises = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_ALL);
             ResultSet rs = statement.executeQuery()) {

            while (rs.next()) {
                exercises.add(map(rs));
            }
            return exercises;

        } catch (SQLException e) {
            throw new DataAccessException("Could not load the exercise list.", e);
        }
    }

    private Exercise map(ResultSet rs) throws SQLException {
        return new Exercise(
                rs.getLong("id"),
                rs.getString("name"),
                Category.valueOf(rs.getString("category")));
    }
}
