package com.workouttracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.workouttracker.model.CardioEntry;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pace, the measurement a cardio session is judged by.
 */
class CardioEntryTest {

    private CardioEntry session(double miles, int seconds) {
        return new CardioEntry(1, LocalDate.of(2026, 7, 26), miles, seconds);
    }

    @ParameterizedTest
    @CsvSource({
            "5.0,  2640, 8:48",
            "3.1,  1680, 9:02",
            "1.0,   360, 6:00",
            "2.0,   720, 6:00",
            "12.4, 3120, 4:12"
    })
    @DisplayName("pace reads as minutes and seconds per mile")
    void formatsPace(double miles, int seconds, String expected) {
        assertEquals(expected, session(miles, seconds).formattedPace());
    }

    @Test
    @DisplayName("the same pace over different distances compares equal")
    void paceIsComparableAcrossDistances() {
        assertEquals(session(1.0, 360).secondsPerMile(), session(3.0, 1080).secondsPerMile());
    }

    @Test
    @DisplayName("a faster session has the lower pace")
    void fasterMeansLower() {
        double faster = session(5.0, 2400).secondsPerMile();
        double slower = session(5.0, 3000).secondsPerMile();

        assertEquals(true, faster < slower, "8:00 per mile should sort below 10:00");
    }

    @Test
    @DisplayName("a session with no distance can never be the fastest")
    void zeroDistanceIsNotAPersonalBest() {
        // Validation rejects this, so it is a guard rather than a real case.
        CardioEntry impossible = session(0, 600);

        assertFalse(Double.isFinite(impossible.secondsPerMile()),
                "an infinite pace can never win a minimum");
        assertEquals("—", impossible.formattedPace());
    }
}
