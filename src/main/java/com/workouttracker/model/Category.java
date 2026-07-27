package com.workouttracker.model;

/**
 * The kind of exercise, which decides what a log entry records.
 *
 * <p>{@link #name()} is what goes in the database; {@link #toString()} is what
 * the user sees.
 */
public enum Category {

    CARDIO("Cardio"),
    WEIGHTLIFTING("Weightlifting");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
