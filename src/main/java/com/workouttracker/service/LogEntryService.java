package com.workouttracker.service;

import com.workouttracker.dao.ExerciseDao;
import com.workouttracker.dao.LogEntryDao;
import com.workouttracker.model.CardioEntry;
import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.model.LiftEntry;
import com.workouttracker.model.LogEntry;
import com.workouttracker.util.ValidationException;
import com.workouttracker.util.Validator;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Business rules for log entries.
 *
 * <p>The measurements arrive as the raw text the user typed. Parsing lives
 * here rather than in the controllers so that every field is checked the same
 * way, and so a controller's job stays "collect input, call this, show what
 * comes back".
 */
public class LogEntryService {

    private final LogEntryDao entries;
    private final ExerciseDao exercises;

    public LogEntryService() {
        this(new LogEntryDao(), new ExerciseDao());
    }

    public LogEntryService(LogEntryDao entries, ExerciseDao exercises) {
        this.entries = entries;
        this.exercises = exercises;
    }

    /**
     * Logs a cardio session.
     *
     * @throws ValidationException if the exercise is missing or is not a cardio
     *         exercise, or if any measurement is unusable
     */
    public LogEntry addCardio(long exerciseId, LocalDate date, String distance, String duration)
            throws ValidationException {

        Exercise exercise = requireExercise(exerciseId);

        Validator validator = new Validator();
        validator.categoryMatches(Category.CARDIO, exercise);
        LocalDate when = validator.pastOrPresentDate(date);
        double miles = validator.positiveDouble(distance, "Distance");
        int seconds = validator.duration(duration);
        validator.throwIfInvalid();

        return entries.insert(new CardioEntry(exerciseId, when, miles, seconds));
    }

    /**
     * Logs a weightlifting session.
     *
     * @throws ValidationException if the exercise is missing or is not a
     *         weightlifting exercise, or if any measurement is unusable
     */
    public LogEntry addLift(long exerciseId, LocalDate date, String sets, String reps, String weight)
            throws ValidationException {

        Exercise exercise = requireExercise(exerciseId);

        Validator validator = new Validator();
        validator.categoryMatches(Category.WEIGHTLIFTING, exercise);
        LocalDate when = validator.pastOrPresentDate(date);
        int setCount = validator.positiveInt(sets, "Sets");
        int repCount = validator.positiveInt(reps, "Reps");
        double pounds = validator.nonNegativeDouble(weight, "Weight");
        validator.throwIfInvalid();

        return entries.insert(new LiftEntry(exerciseId, when, setCount, repCount, pounds));
    }

    public LogEntry updateCardio(long entryId, LocalDate date, String distance, String duration)
            throws ValidationException {

        if (!(requireEntry(entryId) instanceof CardioEntry cardio)) {
            throw new ValidationException("That entry does not record a cardio session.");
        }

        Validator validator = new Validator();
        LocalDate when = validator.pastOrPresentDate(date);
        double miles = validator.positiveDouble(distance, "Distance");
        int seconds = validator.duration(duration);
        validator.throwIfInvalid();

        cardio.setDate(when);
        cardio.setDistance(miles);
        cardio.setDuration(seconds);
        entries.update(cardio);
        return cardio;
    }

    public LogEntry updateLift(long entryId, LocalDate date, String sets, String reps, String weight)
            throws ValidationException {

        if (!(requireEntry(entryId) instanceof LiftEntry lift)) {
            throw new ValidationException("That entry does not record a weightlifting session.");
        }

        Validator validator = new Validator();
        LocalDate when = validator.pastOrPresentDate(date);
        int setCount = validator.positiveInt(sets, "Sets");
        int repCount = validator.positiveInt(reps, "Reps");
        double pounds = validator.nonNegativeDouble(weight, "Weight");
        validator.throwIfInvalid();

        lift.setDate(when);
        lift.setSets(setCount);
        lift.setReps(repCount);
        lift.setWeight(pounds);
        entries.update(lift);
        return lift;
    }

    public boolean delete(long entryId) {
        return entries.delete(entryId);
    }

    public Optional<LogEntry> findById(long entryId) {
        return entries.findById(entryId);
    }

    /** Entries for one exercise, most recent first — the order the log list wants. */
    public List<LogEntry> findByExercise(long exerciseId) {
        return entries.findByExercise(exerciseId);
    }

    /** Entries oldest first, the order progress reads in. */
    public List<LogEntry> history(long exerciseId) {
        return entries.findByExerciseOldestFirst(exerciseId);
    }

    private Exercise requireExercise(long exerciseId) throws ValidationException {
        return exercises.findById(exerciseId)
                .orElseThrow(() -> new ValidationException("That exercise no longer exists."));
    }

    private LogEntry requireEntry(long entryId) throws ValidationException {
        return entries.findById(entryId)
                .orElseThrow(() -> new ValidationException("That log entry no longer exists."));
    }
}
