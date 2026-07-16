package com.bmi.test;
import com.bmi.model.ai.BodyFatEstimator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyFatEstimatorTest {

    @Test
    void testEstimateMale() {
        double result = BodyFatEstimator.estimate(175, 70, 80, 30, true);
        assertTrue(result > 10 && result < 25);
    }

    @Test
    void testEstimateFemale() {
        double result = BodyFatEstimator.estimate(165, 60, 70, 28, false);
        assertTrue(result > 15 && result < 35);
    }

    @Test
    void testInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            BodyFatEstimator.estimate(0, 70, 80, 30, true);
        });
    }
}