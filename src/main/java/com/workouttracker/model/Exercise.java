package com.workouttracker.model;

import java.util.Objects;

/**
 * An exercise the user has defined, such as "Bench Press" or "Long Run".
 *
 * <p>An exercise with an id of {@code 0} has not been saved yet; the DAO
 * assigns the real id on insert.
 */
public class Exercise {

    private long id;
    private String name;
    private Category category;

    /** A new exercise that has not been saved yet. */
    public Exercise(String name, Category category) {
        this(0, name, category);
    }

    public Exercise(long id, String name, Category category) {
        this.id = id;
        this.name = name;
        this.category = category;
    }

    public boolean isNew() {
        return id == 0;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Exercise exercise
                && id == exercise.id
                && Objects.equals(name, exercise.name)
                && category == exercise.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, category);
    }

    @Override
    public String toString() {
        return name;
    }
}
