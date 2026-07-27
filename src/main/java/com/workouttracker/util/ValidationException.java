package com.workouttracker.util;

import java.util.List;

/**
 * Thrown when user input fails a business rule.
 *
 * <p>Checked, deliberately unlike {@link DataAccessException}. A validation
 * failure is expected and recoverable — the user corrects a field and tries
 * again — so the compiler should insist that callers handle it. A database
 * failure is neither, which is why that one is unchecked.
 *
 * <p>Carries every problem found rather than only the first, so a form with
 * three bad fields reports all three at once.
 */
public class ValidationException extends Exception {

    private final List<String> errors;

    public ValidationException(String error) {
        this(List.of(error));
    }

    public ValidationException(List<String> errors) {
        super(String.join("\n", errors));
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("A ValidationException needs at least one message.");
        }
        this.errors = List.copyOf(errors);
    }

    /** Every problem found, one per message, in the order they were checked. */
    public List<String> getErrors() {
        return errors;
    }
}
