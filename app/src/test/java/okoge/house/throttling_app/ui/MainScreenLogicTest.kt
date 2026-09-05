package okoge.house.throttling_app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenLogicTest {

    // ── sliderToKbps ──

    @Test
    fun sliderToKbps_zero_returnsMin() {
        assertEquals(50, sliderToKbps(0f))
    }

    @Test
    fun sliderToKbps_one_returnsMax() {
        assertEquals(5000, sliderToKbps(1f))
    }

    @Test
    fun sliderToKbps_isMonotonicallyIncreasing() {
        val samples = (0..10).map { sliderToKbps(it / 10f) }
        for (i in 1 until samples.size) {
            assertTrue(samples[i] >= samples[i - 1])
        }
    }

    @Test
    fun sliderToKbps_outOfRangeInput_isClamped() {
        assertEquals(50, sliderToKbps(-1f))
        assertEquals(5000, sliderToKbps(2f))
    }

    // ── kbpsToSlider ──

    @Test
    fun kbpsToSlider_min_returnsZero() {
        assertEquals(0f, kbpsToSlider(50), 0.001f)
    }

    @Test
    fun kbpsToSlider_max_returnsOne() {
        assertEquals(1f, kbpsToSlider(5000), 0.001f)
    }

    @Test
    fun kbpsToSlider_isInverseOfSliderToKbps() {
        for (i in 0..10) {
            val slider = i / 10f
            val kbps = sliderToKbps(slider)
            val roundTripped = sliderToKbps(kbpsToSlider(kbps))
            // rounding to an Int kbps loses some precision; allow off-by-a-few
            assertTrue(Math.abs(roundTripped - kbps) <= 1)
        }
    }

    @Test
    fun kbpsToSlider_outOfRangeInput_isClamped() {
        assertEquals(0f, kbpsToSlider(1), 0.001f)
        assertEquals(1f, kbpsToSlider(10000), 0.001f)
    }

    // ── kbpsToKBps ──

    @Test
    fun kbpsToKBps_convertsBitsToBytes() {
        assertEquals(100, kbpsToKBps(800))
    }

    @Test
    fun kbpsToKBps_roundsToNearestInt() {
        assertEquals(13, kbpsToKBps(100)) // 12.5 -> 13
    }

    @Test
    fun kbpsToKBps_neverReturnsLessThanOne() {
        assertEquals(1, kbpsToKBps(0))
        assertEquals(1, kbpsToKBps(1))
    }

    // ── formatKbps ──

    @Test
    fun formatKbps_belowOneMbps_showsKbps() {
        assertEquals("500 kbps", formatKbps(500))
    }

    @Test
    fun formatKbps_atOneMbps_showsMbps() {
        assertEquals("1.0 Mbps", formatKbps(1000))
    }

    @Test
    fun formatKbps_aboveOneMbps_showsMbpsWithOneDecimal() {
        assertEquals("5.0 Mbps", formatKbps(5000))
        assertEquals("1.5 Mbps", formatKbps(1500))
    }

    // ── networkGeneration ──

    @Test
    fun networkGeneration_labelsEachBand() {
        assertEquals("≈ 2G", networkGeneration(50))
        assertEquals("≈ 2G", networkGeneration(100))
        assertEquals("≈ Slow 3G", networkGeneration(101))
        assertEquals("≈ Slow 3G", networkGeneration(500))
        assertEquals("≈ Fast 3G", networkGeneration(501))
        assertEquals("≈ Fast 3G", networkGeneration(2000))
        assertEquals("≈ 3G/HSPA+", networkGeneration(2001))
        assertEquals("≈ 3G/HSPA+", networkGeneration(5000))
    }
}
