package com.workouttracker.service;

import com.workouttracker.dao.ExerciseDao;
import com.workouttracker.dao.LogEntryDao;
import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.util.ValidationException;
import com.workouttracker.util.Validator;
import java.util.List;
import java.util.Optional;

/**
 * Business rules for exercises: validate, then delegate to the DAO.
 *
 * <p>Controllers call this rather than the DAO, so the rules hold no matter
 * which screen the change came from.
 */
public class ExerciseService {

    private final ExerciseDao exercises;
    private final LogEntryDao entries;

    public ExerciseService() {
        this(new ExerciseDao(), new LogEntryDao());
    }

    public ExerciseService(ExerciseDao exercises, LogEntryDao entries) {
        this.exercises = exercises;
        this.entries = entries;
    }

    /**
     * @throws ValidationException if the name is missing, too long, or already
     *         taken, or if no category was chosen
     */
    public Exercise create(String name, Category category) throws ValidationException {
        Validator validator = new Validator();
        String cleanName = validator.exerciseName(name);
        validator.category(category);
        validator.throwIfInvalid();

        if (exercises.findByName(cleanName).isPresent()) {
            throw new ValidationException(
                    "An exercise named \"" + cleanName + "\" already exists.");
        }
        return exercises.insert(new Exercise(cleanName, category));
    }

    /**
     * Renames an exercise and, when it has no entries yet, allows its category
     * to change.
     *
     * <p>Once entries exist the category is fixed. The entry columns are
     * populated to suit the category, so switching it would leave the recorded
     * sets and reps unreadable — the DAO would map them as a cardio session
     * with no distance and no time.
     *
     * @throws ValidationException if the input is bad, the name is taken by
     *         another exercise, or the category change would strand entries
     */
    public Exercise update(long id, String name, Category category) throws ValidationException {
        Exercise existing = exercises.findById(id)
                .orElseThrow(() -> new ValidationException("That exercise no longer exists."));

        Validator validator = new Validator();
        String cleanName = validator.exerciseName(name);
        validator.category(category);
        validator.throwIfInvalid();

        Optional<Exercise> clash = exercises.findByName(cleanName);
        if (clash.isPresent() && clash.get().getId() != id) {
            throw new ValidationException(
                    "An exercise named \"" + cleanName + "\" already exists.");
        }

        if (category != existing.getCategory()) {
            int count = entries.countByExercise(id);
            if (count > 0) {
                throw new ValidationException(
                        ("\"%s\" already has %d log %s, so its category cannot be changed. "
                                + "Delete those entries first, or add a separate exercise.")
                                .formatted(existing.getName(), count, count == 1 ? "entry" : "entries"));
            }
        }

        existing.setName(cleanName);
        existing.setCategory(category);
        exercises.update(existing);
        return existing;
    }

    /** Deletes an exercise and, through the cascade, every entry logged against it. */
    public boolean delete(long id) {
        return exercises.delete(id);
    }

    /** How many entries a delete would take with it, for the confirmation prompt. */
    public int entryCount(long exerciseId) {
        return entries.countByExercise(exerciseId);
    }

    public List<Exercise> findAll() {
        return exercises.findAll();
    }

    public Optional<Exercise> findById(long id) {
        return exercises.findById(id);
    }
}
