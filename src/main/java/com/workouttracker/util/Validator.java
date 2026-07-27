package com.workouttracker.util;

import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks user input against the rules that can be settled from the input
 * alone, collecting every problem instead of stopping at the first.
 *
 * <p>Rules that need the database — is this name already taken, does this
 * exercise exist — live in the service layer, because a validator that opens
 * connections is no longer something you can reason about in isolation.
 *
 * <p>Each method returns the parsed value and records a message when the
 * input is bad, so a caller reads straight through and then calls
 * {@link #throwIfInvalid()} once:
 *
 * <pre>{@code
 * Validator validator = new Validator();
 * int sets = validator.positiveInt(setsField.getText(), "Sets");
 * int reps = validator.positiveInt(repsField.getText(), "Reps");
 * validator.throwIfInvalid();
 * }</pre>
 *
 * <p>Values returned after a failed check are placeholders and must not be
 * used until {@code throwIfInvalid} has passed.
 */
public final class Validator {

    public static final int MAX_NAME_LENGTH = 60;

    private static final Pattern INTEGER = Pattern.compile("-?\\d+");
    private static final Pattern DECIMAL = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    /** {@code hh:mm:ss} or {@code mm:ss}; minutes and seconds cap at 59, hours do not. */
    private static final Pattern DURATION = Pattern.compile("(?:(\\d+):)?([0-5]?\\d):([0-5]?\\d)");

    private final List<String> errors = new ArrayList<>();

    /** Trims the name and checks it is present and not too long. */
    public String exerciseName(String raw) {
        if (raw == null || raw.isBlank()) {
            errors.add("Name is required.");
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            errors.add("Name must be " + MAX_NAME_LENGTH + " characters or fewer.");
        }
        return trimmed;
    }

    public Category category(Category category) {
        if (category == null) {
            errors.add("Category is required.");
        }
        return category;
    }

    /** A workout cannot be logged before it has happened. */
    public LocalDate pastOrPresentDate(LocalDate date) {
        if (date == null) {
            errors.add("Date is required.");
            return null;
        }
        if (date.isAfter(LocalDate.now())) {
            errors.add("Date cannot be in the future.");
        }
        return date;
    }

    /** A whole number of 1 or more — sets and reps. */
    public int positiveInt(String text, String field) {
        Integer value = parseInt(text, field);
        if (value == null) {
            return 0;
        }
        if (value < 1) {
            errors.add(field + " must be at least 1.");
            return 0;
        }
        return value;
    }

    /** A number greater than zero — distance. */
    public double positiveDouble(String text, String field) {
        Double value = parseDouble(text, field);
        if (value == null) {
            return 0;
        }
        if (value <= 0) {
            errors.add(field + " must be greater than zero.");
            return 0;
        }
        return value;
    }

    /** A number of zero or more — weight, so bodyweight and assisted lifts count. */
    public double nonNegativeDouble(String text, String field) {
        Double value = parseDouble(text, field);
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            errors.add(field + " cannot be negative.");
            return 0;
        }
        return value;
    }

    /**
     * Reads {@code hh:mm:ss} or {@code mm:ss} into whole seconds. Requiring
     * {@code 00:30:00} for a half-hour run would be tiresome, so {@code 30:00}
     * is accepted too.
     */
    public int duration(String text) {
        if (text == null || text.isBlank()) {
            errors.add("Duration is required.");
            return 0;
        }
        Matcher matcher = DURATION.matcher(text.trim());
        if (!matcher.matches()) {
            errors.add("Duration must look like hh:mm:ss or mm:ss, "
                    + "for example 00:30:00 or 30:00.");
            return 0;
        }
        try {
            long hours = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
            long total = hours * 3600
                    + Long.parseLong(matcher.group(2)) * 60
                    + Long.parseLong(matcher.group(3));

            if (total <= 0) {
                errors.add("Duration must be greater than zero.");
                return 0;
            }
            if (total > Integer.MAX_VALUE) {
                errors.add("Duration is too large.");
                return 0;
            }
            return (int) total;

        } catch (NumberFormatException e) {
            errors.add("Duration is too large.");
            return 0;
        }
    }

    /**
     * Checks that the kind of entry matches the exercise it is being logged
     * against. SQLite cannot enforce this: the category lives on another table
     * and a CHECK constraint cannot reach it.
     */
    public void categoryMatches(Category entryKind, Exercise exercise) {
        if (exercise == null || entryKind == exercise.getCategory()) {
            return;
        }
        errors.add("\"%s\" is a %s exercise, so it cannot take a %s entry.".formatted(
                exercise.getName(),
                exercise.getCategory().displayName().toLowerCase(Locale.US),
                entryKind.displayName().toLowerCase(Locale.US)));
    }

    /** Records a problem the service found, such as a name already in use. */
    public void add(String error) {
        errors.add(error);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public void throwIfInvalid() throws ValidationException {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private Integer parseInt(String text, String field) {
        if (text == null || text.isBlank()) {
            errors.add(field + " is required.");
            return null;
        }
        String trimmed = text.trim();
        if (!INTEGER.matcher(trimmed).matches()) {
            errors.add(field + " must be a whole number.");
            return null;
        }
        try {
            return Integer.valueOf(trimmed);
        } catch (NumberFormatException e) {
            errors.add(field + " is too large.");
            return null;
        }
    }

    /**
     * The pattern check is not redundant: {@link Double#parseDouble} also
     * accepts "NaN", "Infinity", hex literals and a trailing "d" or "f", none
     * of which anyone means to type into a weight field.
     */
    private Double parseDouble(String text, String field) {
        if (text == null || text.isBlank()) {
            errors.add(field + " is required.");
            return null;
        }
        String trimmed = text.trim();
        if (!DECIMAL.matcher(trimmed).matches()) {
            errors.add(field + " must be a number.");
            return null;
        }
        double value = Double.parseDouble(trimmed);
        if (!Double.isFinite(value)) {
            errors.add(field + " is too large.");
            return null;
        }
        return value;
    }
}
