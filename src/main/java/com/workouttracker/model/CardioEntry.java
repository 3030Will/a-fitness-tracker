package com.workouttracker.model;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * A cardio session: a distance covered in a length of time.
 *
 * <p>Distance is in miles and duration is in whole seconds, displayed as
 * {@code hh:mm:ss}.
 */
public final class CardioEntry extends LogEntry {

    private double distance;
    private int duration;

    /** A new entry that has not been saved yet. */
    public CardioEntry(long exerciseId, LocalDate date, double distance, int duration) {
        this(0, exerciseId, date, distance, duration);
    }

    public CardioEntry(long id, long exerciseId, LocalDate date, double distance, int duration) {
        super(id, exerciseId, date);
        this.distance = distance;
        this.duration = duration;
    }

    @Override
    public Category category() {
        return Category.CARDIO;
    }

    @Override
    public String summary() {
        return String.format(Locale.US, "%.2f mi in %s", distance, formattedDuration());
    }

    /** The duration as {@code hh:mm:ss}. Hours are not capped at 24. */
    public String formattedDuration() {
        return String.format(Locale.US, "%02d:%02d:%02d",
                duration / 3600, (duration % 3600) / 60, duration % 60);
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    /** Duration in whole seconds. */
    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CardioEntry entry
                && getId() == entry.getId()
                && getExerciseId() == entry.getExerciseId()
                && Objects.equals(getDate(), entry.getDate())
                && Double.compare(distance, entry.distance) == 0
                && duration == entry.duration;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getExerciseId(), getDate(), distance, duration);
    }
}
