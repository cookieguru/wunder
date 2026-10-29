package app.wunder.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FormatTest {
    private val la = ZoneId.of("America/Los_Angeles")

    @Test fun formatsInBothUnits() {
        assertEquals("50°", Fmt.fmt(Kind.TEMP, 10.0, Units.IMPERIAL))
        assertEquals("10°", Fmt.fmt(Kind.TEMP, 10.0, Units.METRIC))
        assertEquals("0.04", Fmt.fmt(Kind.PRECIP, 1.0, Units.IMPERIAL))
        assertEquals("0", Fmt.fmt(Kind.PRECIP, 0.0, Units.IMPERIAL))
        assertEquals("1.5", Fmt.fmt(Kind.PRECIP, 1.5, Units.METRIC))
        assertEquals("29.92", Fmt.fmt(Kind.PRES, 1013.25, Units.IMPERIAL))
        assertEquals("6", Fmt.fmt(Kind.SPEED, 10.0, Units.IMPERIAL))
        assertEquals("--", Fmt.fmt(Kind.TEMP, null, Units.IMPERIAL))
    }

    @Test fun rewritesNarrativeNumbersForImperial() {
        assertEquals("High 55F. Winds 6 to 12 mph.", Fmt.narrative("High 13C. Winds 10 to 20 km/h.", Units.IMPERIAL))
        assertEquals("Rain around 0.2 in.", Fmt.narrative("Rain around 5 mm.", Units.IMPERIAL))
        assertEquals("Low 2C.", Fmt.narrative("Low 2C.", Units.METRIC))
    }

    @Test fun mapsIconsAndMoonPhases() {
        assertEquals("partly-cloudy-day", Fmt.iconName(30))
        assertEquals("not-available", Fmt.iconName(null))
        assertEquals("moon-last-quarter", Fmt.moonIcon("WANING_HALF_LAST_QTR"))
        assertEquals("moon-waning-gibbous", Fmt.moonIcon("WANING_GIBBOUS"))
        assertEquals("Waning Gibbous", Fmt.moonName("WANING_GIBBOUS"))
    }

    @Test fun formatsTimesInThePlacesZone() {
        assertEquals("07:49", Fmt.time(1793198940, la))
        assertEquals("7:49 AM", Fmt.time(1793198940, la, is24 = false))
        assertEquals("--", Fmt.time(null, la))
    }

    @Test fun quickieUsesTheOfficialThresholds() {
        assertEquals(QuickieKind.NEARLY_THE_SAME, quickieKind(2, Units.IMPERIAL))
        assertEquals(QuickieKind.WARMER, quickieKind(3, Units.IMPERIAL))
        assertEquals(QuickieKind.MUCH_WARMER, quickieKind(8, Units.IMPERIAL))
        assertEquals(QuickieKind.COOLER, quickieKind(-2, Units.METRIC))
        assertEquals(QuickieKind.MUCH_COOLER, quickieKind(-5, Units.METRIC))
        val q = Quickie(QuickieTime.TOMORROW, QuickieKind.NEARLY_THE_SAME).sentence
        assertEquals("Tomorrow's temperature is forecast to be nearly the same as today.", q.first + "nearly the same" + q.second)
    }

    @Test fun spreadPushesLabelsApart() {
        val out = spread(listOf(10f, 12f, 50f), { it }, 18f).map { it.second }
        assertEquals(listOf(10f, 28f, 50f), out)
    }

    @Test fun nightBandsRunFromSunsetToSunrise() {
        val day = { start: Long, rise: Long, set: Long -> StripDay(java.time.LocalDate.EPOCH, "", "", 24, start, start + 86400, null, null, "", "", null, rise, set) }
        val bands = nightBands(listOf(day(0, 25200, 64800), day(86400, 111600, 151200)), 0, 172800)
        assertEquals(listOf(0L to 25200L, 64800L to 111600L, 151200L to 172800L), bands)
    }
}
