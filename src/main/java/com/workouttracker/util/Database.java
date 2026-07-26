package com.workouttracker.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import org.sqlite.SQLiteConfig;

/**
 * Owns the SQLite connection settings and creates the schema on first run.
 *
 * <p>Every connection handed out has foreign key enforcement switched on.
 * SQLite disables foreign keys by default and the setting is per-connection,
 * not per-database, so {@code ON DELETE CASCADE} would silently do nothing
 * without this.
 */
public final class Database {

    private static final String SCHEMA_RESOURCE = "/db/schema.sql";
    private static final String DEFAULT_URL = "jdbc:sqlite:workout.db";

    private static String url = DEFAULT_URL;

    private Database() {
        // Utility class.
    }

    /**
     * Points the application at a different database, for tests that need a
     * throwaway file instead of the real {@code workout.db}.
     */
    public static synchronized void configure(String jdbcUrl) {
        url = jdbcUrl;
    }

    public static synchronized String url() {
        return url;
    }

    /**
     * Opens a new connection with foreign keys enforced. Callers are
     * responsible for closing it — use try-with-resources.
     */
    public static Connection getConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        return DriverManager.getConnection(url(), config.toProperties());
    }

    /**
     * Creates the database file and its tables if they do not already exist.
     * Safe to call on every startup.
     */
    public static void initialize() throws SQLException {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            for (String sql : splitStatements(readSchema())) {
                statement.execute(sql);
            }
        }
    }

    private static String readSchema() {
        try (InputStream in = Database.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Schema resource not found on the classpath: " + SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + SCHEMA_RESOURCE, e);
        }
    }

    /**
     * Splits the schema file into individual statements. sqlite-jdbc executes
     * only the first statement of a multi-statement string, so the file has to
     * be fed in one statement at a time.
     *
     * <p>Line comments are stripped first, otherwise a trailing comment would
     * become a statement of its own. This assumes no {@code --} appears inside
     * a string literal, which holds for the schema and is not worth a real
     * parser here.
     */
    private static List<String> splitStatements(String schema) {
        return Arrays.stream(schema.replaceAll("(?m)--.*$", "").split(";"))
                .map(String::trim)
                .filter(sql -> !sql.isEmpty())
                .toList();
    }
}
