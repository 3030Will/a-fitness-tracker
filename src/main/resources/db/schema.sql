-- Workout Progress Tracker — SQLite schema.
-- Executed by com.workouttracker.util.Database on first run.
-- Every statement is IF NOT EXISTS, so re-running on an existing database
-- is a no-op and never destroys data.

-- Names are unique case-insensitively: "Squats" and "squats" are the same
-- exercise to a user, so the database should not hold both.
CREATE TABLE IF NOT EXISTS exercises (
    id       INTEGER PRIMARY KEY,
    name     TEXT NOT NULL COLLATE NOCASE UNIQUE,
    category TEXT NOT NULL CHECK (category IN ('CARDIO', 'WEIGHTLIFTING'))
);

-- One table for both kinds of entry. The weightlifting columns and the cardio
-- columns are each nullable; which set is populated depends on the parent
-- exercise's category. SQLite CHECK constraints cannot reach into another
-- table, so that pairing is enforced by Validator, not here.
--
-- Units: weight in pounds, distance in miles, duration in whole seconds
-- (displayed as hh:mm:ss).
CREATE TABLE IF NOT EXISTS log_entries (
    id          INTEGER PRIMARY KEY,
    exercise_id INTEGER NOT NULL,
    date        TEXT NOT NULL,
    sets        INTEGER,
    reps        INTEGER,
    weight      REAL,
    distance    REAL,
    duration    INTEGER,
    FOREIGN KEY (exercise_id) REFERENCES exercises (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_log_entries_exercise_id
    ON log_entries (exercise_id);
