package com.workouttracker.model;

import java.time.LocalDate;

/**
 * One recorded session against an exercise.
 *
 * <p>Sealed rather than merely abstract: the two subclasses correspond exactly
 * to the two {@link Category} values, and sealing lets the compiler check that
 * every switch over an entry handles both. Adding a third kind of entry then
 * fails to compile everywhere it needs attention, instead of silently falling
 * through at runtime.
 */
public abstract sealed class LogEntry permits CardioEntry, LiftEntry {

    private long id;
    private long exerciseId;
    private LocalDate date;

    protected LogEntry(long id, long exerciseId, LocalDate date) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.date = date;
    }

    /** The category of exercise this kind of entry belongs to. */
    public abstract Category category();

    /**
     * A one-line description of what was recorded, for list cells and the
     * history view. Each subclass renders its own measurements, so the UI can
     * display an entry without knowing which kind it is.
     */
    public abstract String summary();

    public boolean isNew() {
        return id == 0;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return date + " — " + summary();
    }
}
