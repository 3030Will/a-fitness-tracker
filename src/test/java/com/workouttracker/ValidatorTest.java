package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workouttracker.model.Category;
import com.workouttracker.model.Exercise;
import com.workouttracker.util.ValidationException;
import com.workouttracker.util.Validator;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidatorTest {

    private final Validator validator = new Validator();

    @Nested
    @DisplayName("exercise name")
    class Names {

        @Test
        void trimsSurroundingWhitespace() {
            assertEquals("Bench Press", validator.exerciseName("  Bench Press  "));
            assertTrue(validator.isValid());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "\t"})
        void rejectsBlank(String raw) {
            validator.exerciseName(raw);
            assertEquals(List.of("Name is required."), validator.errors());
        }

        @Test
        void rejectsNull() {
            validator.exerciseName(null);
            assertFalse(validator.isValid());
        }

        @Test
        void acceptsExactlyTheMaximumLength() {
            validator.exerciseName("x".repeat(Validator.MAX_NAME_LENGTH));
            assertTrue(validator.isValid());
        }

        @Test
        void rejectsOneCharacterTooMany() {
            validator.exerciseName("x".repeat(Validator.MAX_NAME_LENGTH + 1));
            assertFalse(validator.isValid());
        }
    }

    @Nested
    @DisplayName("date")
    class Dates {

        @Test
        void acceptsToday() {
            validator.pastOrPresentDate(LocalDate.now());
            assertTrue(validator.isValid());
        }

        @Test
        void acceptsThePast() {
            validator.pastOrPresentDate(LocalDate.now().minusYears(1));
            assertTrue(validator.isValid());
        }

        @Test
        void rejectsTomorrow() {
            validator.pastOrPresentDate(LocalDate.now().plusDays(1));
            assertEquals(List.of("Date cannot be in the future."), validator.errors());
        }

        @Test
        void rejectsMissing() {
            validator.pastOrPresentDate(null);
            assertEquals(List.of("Date is required."), validator.errors());
        }
    }

    @Nested
    @DisplayName("whole numbers")
    class Integers {

        @Test
        void acceptsOne() {
            assertEquals(1, validator.positiveInt("1", "Sets"));
            assertTrue(validator.isValid());
        }

        @Test
        void rejectsZero() {
            validator.positiveInt("0", "Sets");
            assertEquals(List.of("Sets must be at least 1."), validator.errors());
        }

        @Test
        void rejectsNegative() {
            validator.positiveInt("-3", "Reps");
            assertEquals(List.of("Reps must be at least 1."), validator.errors());
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "3.5", "3 sets", "", "  "})
        void rejectsNonIntegers(String text) {
            validator.positiveInt(text, "Sets");
            assertFalse(validator.isValid());
        }

        @Test
        void rejectsValuesTooLargeForAnInt() {
            validator.positiveInt("99999999999999", "Sets");
            assertEquals(List.of("Sets is too large."), validator.errors());
        }
    }

    @Nested
    @DisplayName("decimal numbers")
    class Decimals {

        @Test
        void weightAcceptsZeroForBodyweight() {
            assertEquals(0, validator.nonNegativeDouble("0", "Weight"));
            assertTrue(validator.isValid());
        }

        @Test
        void weightRejectsNegative() {
            validator.nonNegativeDouble("-5", "Weight");
            assertEquals(List.of("Weight cannot be negative."), validator.errors());
        }

        @Test
        void distanceRejectsZero() {
            validator.positiveDouble("0", "Distance");
            assertEquals(List.of("Distance must be greater than zero."), validator.errors());
        }

        @Test
        void acceptsDecimals() {
            assertEquals(137.5, validator.nonNegativeDouble("137.5", "Weight"));
            assertTrue(validator.isValid());
        }

        /** Double.parseDouble would swallow every one of these. */
        @ParameterizedTest
        @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "135d", "135f", "0x1p3", "1e5", "abc"})
        void rejectsWhatParseDoubleWouldAccept(String text) {
            validator.nonNegativeDouble(text, "Weight");
            assertFalse(validator.isValid(), text + " should have been rejected");
        }

        @Test
        void rejectsValuesTooLargeToRepresent() {
            validator.nonNegativeDouble("9".repeat(400), "Weight");
            assertEquals(List.of("Weight is too large."), validator.errors());
        }
    }

    @Nested
    @DisplayName("duration")
    class Durations {

        @ParameterizedTest
        @CsvSource({
                "00:00:01, 1",
                "00:01:00, 60",
                "30:00, 1800",
                "00:30:00, 1800",
                "1:00:00, 3600",
                "01:30:45, 5445",
                "59:59, 3599",
                "100:00:00, 360000"
        })
        void readsBothFormats(String text, int expectedSeconds) {
            assertEquals(expectedSeconds, validator.duration(text));
            assertTrue(validator.isValid(), "unexpected errors: " + validator.errors());
        }

        @Test
        void rejectsZero() {
            validator.duration("00:00");
            assertEquals(List.of("Duration must be greater than zero."), validator.errors());
        }

        @ParameterizedTest
        @ValueSource(strings = {"1800", "30", "30:60", "30:99", "99:99:99", "1:2:3:4", "abc", "", "-30:00", "30:"})
        void rejectsMalformed(String text) {
            validator.duration(text);
            assertFalse(validator.isValid(), text + " should have been rejected");
        }
    }

    @Nested
    @DisplayName("category pairing")
    class Pairing {

        private final Exercise benchPress = new Exercise(1, "Bench Press", Category.WEIGHTLIFTING);
        private final Exercise longRun = new Exercise(2, "Long Run", Category.CARDIO);

        @Test
        void acceptsMatchingKinds() {
            validator.categoryMatches(Category.WEIGHTLIFTING, benchPress);
            validator.categoryMatches(Category.CARDIO, longRun);
            assertTrue(validator.isValid());
        }

        @Test
        void rejectsCardioEntryOnALift() {
            validator.categoryMatches(Category.CARDIO, benchPress);
            assertEquals(
                    List.of("\"Bench Press\" is a weightlifting exercise, "
                            + "so it cannot take a cardio entry."),
                    validator.errors());
        }

        @Test
        void rejectsLiftEntryOnCardio() {
            validator.categoryMatches(Category.WEIGHTLIFTING, longRun);
            assertFalse(validator.isValid());
        }
    }

    @Nested
    @DisplayName("collecting problems")
    class Collecting {

        @Test
        void reportsEveryBadFieldAtOnce() {
            validator.positiveInt("nope", "Sets");
            validator.positiveInt("0", "Reps");
            validator.nonNegativeDouble("-1", "Weight");
            validator.pastOrPresentDate(LocalDate.now().plusDays(1));

            ValidationException thrown =
                    assertThrows(ValidationException.class, validator::throwIfInvalid);

            assertEquals(4, thrown.getErrors().size());
            assertTrue(thrown.getMessage().contains("Sets must be a whole number."));
            assertTrue(thrown.getMessage().contains("Date cannot be in the future."));
        }

        @Test
        void staysSilentWhenEverythingIsFine() {
            validator.positiveInt("3", "Sets");
            validator.nonNegativeDouble("135", "Weight");

            assertDoesNotThrow(validator::throwIfInvalid);
        }

        @Test
        void carriesServiceSuppliedProblems() {
            validator.add("An exercise named \"Squats\" already exists.");
            assertFalse(validator.isValid());
        }
    }
}
