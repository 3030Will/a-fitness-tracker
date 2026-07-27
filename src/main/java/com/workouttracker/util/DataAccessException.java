package com.workouttracker.util;

/**
 * Thrown when a database operation fails.
 *
 * <p>Unchecked, so DAO signatures stay readable and the layers above are not
 * forced to handle a failure they cannot recover from. The message is written
 * for a person, because it ends up in front of one: controllers show it in an
 * alert. The underlying {@link java.sql.SQLException} is kept as the cause.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
