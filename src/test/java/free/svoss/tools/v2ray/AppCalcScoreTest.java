package free.svoss.tools.v2ray;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppCalcScoreTest {

    @Test
    void normalValuesReturnPositiveScore() {
        double score = App.calcScore(50.0, 100);
        assertTrue(score > 0);
    }

    @Test
    void lowerPingGivesHigherScore() {
        double scoreLowPing = App.calcScore(50.0, 50);
        double scoreHighPing = App.calcScore(50.0, 200);
        assertTrue(scoreLowPing > scoreHighPing);
    }

    @Test
    void higherSpeedGivesHigherScore() {
        double scoreSlow = App.calcScore(10.0, 100);
        double scoreFast = App.calcScore(100.0, 100);
        assertTrue(scoreFast > scoreSlow);
    }

    @Test
    void nullPingReturnsZero() {
        assertEquals(0, App.calcScore(50.0, null));
    }

    @Test
    void zeroPingReturnsZero() {
        assertEquals(0, App.calcScore(50.0, 0));
    }

    @Test
    void negativePingReturnsZero() {
        assertEquals(0, App.calcScore(50.0, -10));
    }

    @Test
    void pingAboveCeilingReturnsZero() {
        assertEquals(0, App.calcScore(50.0, 5001));
    }

    @Test
    void pingAtCeilingReturnsZero() {
        assertEquals(0, App.calcScore(50.0, 5000));
    }

    @Test
    void pingAtCeilingMinusOneReturnsPositive() {
        assertTrue(App.calcScore(50.0, 4999) > 0);
    }

    @Test
    void negligibleSpeedReturnsZero() {
        assertEquals(0, App.calcScore(0.0001, 100));
    }

    @Test
    void zeroSpeedReturnsZero() {
        assertEquals(0, App.calcScore(0.0, 100));
    }

    @Test
    void negativeSpeedReturnsZero() {
        assertEquals(0, App.calcScore(-5.0, 100));
    }

    @Test
    void nullSpeedReturnsZero() {
        assertEquals(0, App.calcScore(null, 100));
    }

    @Test
    void bothNullPingAndZeroSpeedReturnsZero() {
        assertEquals(0, App.calcScore(0.0, null));
    }

    @Test
    void scoreCalculationMatchesFormula() {
        // SCORE_PING_CEILING = 5000, ping = 100, speed = 10.0
        // Expected: (5000 - 100) * 10.0 = 49000.0
        assertEquals(49000.0, App.calcScore(10.0, 100), 0.001);
    }
}
