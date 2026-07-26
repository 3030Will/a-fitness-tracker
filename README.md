# Workout Progress Tracker

**Student:** William Schaffer

A JavaFX desktop application for logging workouts and tracking progress over
time. Users define their own exercises — each categorized as **cardio** or
**weightlifting** — and record log entries against them to monitor improvement
across sessions.

Repository: <https://github.com/3030Will/a-fitness-tracker>

## Features

> Status: project scaffolding complete. Features below are the planned scope;
> this list is updated as each is implemented.

- Create, view, edit, and delete exercises
- Create, view, edit, and delete log entries against an exercise
- Cardio entries record distance and duration
- Weightlifting entries record sets, reps, and weight
- Progress history view per exercise
- Input validation with user-friendly error messages
- Data persisted locally in a SQLite database

## Technologies Used

| Component | Choice |
|---|---|
| Language | Java 25 |
| UI | JavaFX 25 |
| Build | Maven |
| Database | SQLite (`org.xerial:sqlite-jdbc`) |
| Testing | JUnit 5 |

## Requirements

- JDK 25 or newer
- Maven 3.9 or newer

No separate JavaFX SDK install is needed — Maven pulls the JavaFX artifacts as
regular dependencies.

## Compile and Run

Run the application:

```bash
mvn clean javafx:run
```

Compile and package without running:

```bash
mvn clean package
```

Run the tests:

```bash
mvn test
```

The SQLite database file (`workout.db`) is created automatically in the project
directory on first run.

## Project Structure

```
src/main/java/com/workouttracker/
├── App.java          JavaFX entry point
├── model/            Exercise, Category, LogEntry, CardioEntry, LiftEntry
├── dao/              ExerciseDao, LogEntryDao — all SQL lives here
├── service/          business rules, coordinates DAOs
├── ui/               controllers
└── util/             Database, Validator, ValidationException

src/main/resources/com/workouttracker/ui/   FXML files
src/test/java/com/workouttracker/           JUnit tests
```

The application is layered `ui` → `service` → `dao` → database. The UI never
touches SQL directly, and DAOs never reference JavaFX types.
