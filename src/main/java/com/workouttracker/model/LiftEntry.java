package com.workouttracker.model;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * A weightlifting session: sets and reps at a given weight.
 *
 * <p>Weight is in pounds.
 */
public final class LiftEntry extends LogEntry {

    private int sets;
    private int reps;
    private double weight;

    /** A new entry that has not been saved yet. */
    public LiftEntry(long exerciseId, LocalDate date, int sets, int reps, double weight) {
        this(0, exerciseId, date, sets, reps, weight);
    }

    public LiftEntry(long id, long exerciseId, LocalDate date, int sets, int reps, double weight) {
        super(id, exerciseId, date);
        this.sets = sets;
        this.reps = reps;
        this.weight = weight;
    }

    @Override
    public Category category() {
        return Category.WEIGHTLIFTING;
    }

    @Override
    public String summary() {
        return String.format(Locale.US, "%d × %d @ %s lbs", sets, reps, formattedWeight());
    }

    /** Whole weights lose the pointless ".0": 135 rather than 135.0, but 137.5 keeps it. */
    public String formattedWeight() {
        return weight == Math.rint(weight)
                ? String.format(Locale.US, "%.0f", weight)
                : String.format(Locale.US, "%.1f", weight);
    }

    /** The total weight moved, useful for comparing sessions of different shapes. */
    public double volume() {
        return sets * reps * weight;
    }

    public int getSets() {
        return sets;
    }

    public void setSets(int sets) {
        this.sets = sets;
    }

    public int getReps() {
        return reps;
    }

    public void setReps(int reps) {
        this.reps = reps;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LiftEntry entry
                && getId() == entry.getId()
                && getExerciseId() == entry.getExerciseId()
                && Objects.equals(getDate(), entry.getDate())
                && sets == entry.sets
                && reps == entry.reps
                && Double.compare(weight, entry.weight) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getExerciseId(), getDate(), sets, reps, weight);
    }
}
