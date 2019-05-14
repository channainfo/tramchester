package com.tramchester.unit.domain;

import com.tramchester.domain.TramTime;
import com.tramchester.domain.exceptions.TramchesterException;
import org.junit.Test;

import java.time.LocalTime;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TramTimeTest {

    @Test
    public void shouldCreateTramTime() throws TramchesterException {
        TramTime timeA = TramTime.create(11,23);
        assertEquals(11, timeA.getHourOfDay());
        assertEquals(23, timeA.getMinuteOfHour());
    }

    @Test
    public void shouldCalculateMinsOfDay() throws TramchesterException {
        TramTime midnight = TramTime.create(0,0);
        assertEquals(0, midnight.minutesOfDay());

        TramTime timeA = TramTime.create(11,23);
        assertEquals((11*60)+23, timeA.minutesOfDay());

        TramTime timeB = TramTime.create(23,45);
        assertEquals((24*60)-15, timeB.minutesOfDay());
    }

    @Test
    public void shouldParseHMS() throws TramchesterException {
        checkCorrectTimePresent(TramTime.parse("11:23:00"), 11, 23);
        checkCorrectTimePresent(TramTime.parse("00:15:00"), 00, 15);
        checkCorrectTimePresent(TramTime.parse("23:35:00"), 23, 35);
    }

    @Test
    public void shouldParseHM() throws TramchesterException {
        checkCorrectTimePresent(TramTime.parse("11:23"), 11, 23);
        checkCorrectTimePresent(TramTime.parse("00:15"), 00, 15);
        checkCorrectTimePresent(TramTime.parse("23:35"), 23, 35);
    }

    @Test
    public void shouldParseEmptyIfInvalid() throws TramchesterException {
        assertFalse(TramTime.parse("43:12").isPresent());
        assertFalse(TramTime.parse("12:99").isPresent());
    }

    @Test
    public void shouldFormatCorrectly() throws TramchesterException {
        TramTime time = TramTime.create(18,56);

        assertEquals("18:56",time.toPattern());
        assertEquals("18:56:00", time.tramDataFormat());
    }

    @Test
    public void shouldBeComparableDuringDay() throws TramchesterException {
        TramTime timeA = TramTime.create(12,04);
        TramTime timeB =  TramTime.create(12,03);

        assertTrue(timeA.compareTo(timeB)>0);
        assertTrue(timeB.compareTo(timeA)<0);

        TramTime timeC = TramTime.create(12,03);
        assertEquals(0, timeC.compareTo(timeB));
        assertEquals(0, timeB.compareTo(timeC));

        SortedSet<TramTime> set = new TreeSet<>();
        set.add(timeA);
        set.add(timeB);

        assertEquals(timeB,set.first());
    }

    @Test
    public void shouldBeComparableAcrossMidnight() throws TramchesterException {
        TramTime timeA = TramTime.create(00,10);
        TramTime timeB =  TramTime.create(23,10);

        assertTrue(timeA.compareTo(timeB)>0);
        assertTrue(timeB.compareTo(timeA)<0);

        TramTime timeC = TramTime.create(00,10);
        assertEquals(0, timeC.compareTo(timeA));
        assertEquals(0, timeA.compareTo(timeC));

        SortedSet<TramTime> set = new TreeSet<>();
        set.add(timeA);
        set.add(timeB);

        assertEquals(timeB,set.first());
    }

    @Test
    public void shouldOrderTramTimesCorrectlyOverMidnight() throws TramchesterException {
        TramTime timeA = TramTime.create(00,10);
        TramTime timeB =  TramTime.create(23,10); // show first

        SortedSet<TramTime> set = new TreeSet<>();
        set.add(timeA);
        set.add(timeB);

        assertEquals(timeB,set.first());
    }

    @Test
    public void shouldCheckIfDepartsAfter() {
        TramTime timeA = TramTime.of(LocalTime.of(0,10));
        TramTime timeB =  TramTime.of(LocalTime.of(23,10));

        assertTrue(timeA.departsAfter(timeB));

        timeA = TramTime.of(LocalTime.of(6,12));
        timeB = TramTime.of(LocalTime.of(6,11));

        assertTrue(timeA.departsAfter(timeB));
        assertFalse(timeB.departsAfter(timeA));
    }

    @Test
    public void shouldHaveIsBefore() {
        assertTrue(TramTime.of(11,33).isBefore(TramTime.of(12,00)));
        assertTrue(TramTime.of(0,3).isBefore(TramTime.of(0,30)));
        assertTrue(TramTime.of(11,3).isBefore(TramTime.of(0,30)));

        assertFalse(TramTime.of(10,30).isBefore(TramTime.of(10,30)));
        assertFalse(TramTime.of(10,30).isBefore(TramTime.of(9,30)));
        assertFalse(TramTime.of(0,30).isBefore(TramTime.of(4,0))); // late night
    }

    @Test
    public void shouldHaveAfter() {
        assertTrue(TramTime.of(11,44).isAfter(TramTime.of(11,00)));
        assertTrue(TramTime.of(0,30).isAfter(TramTime.of(23,30)));
        assertTrue(TramTime.of(0,30).isAfter(TramTime.of(21,30)));
        assertTrue(TramTime.of(00,44).isAfter(TramTime.of(00,20)));
        assertTrue(TramTime.of(2,44).isAfter(TramTime.of(2,20)));

        assertFalse(TramTime.of(2,30).isAfter(TramTime.of(23,30)));
        assertFalse(TramTime.of(4,30).isAfter(TramTime.of(0,30)));
    }

    @Test
    public void shouldIfBetweenAccountingForMidnight() {
        TramTime morning = TramTime.of(11,30);

        assertTrue(morning.between(TramTime.of(9,0), TramTime.of(13,0)));
        assertTrue(morning.between(TramTime.of(11,30), TramTime.of(13,0)));
        assertTrue(morning.between(TramTime.of(10,30), TramTime.of(11,30)));

        assertFalse(morning.between(TramTime.of(9,0), TramTime.of(11,0)));
        assertFalse(morning.between(TramTime.of(12,0), TramTime.of(13,0)));

        assertTrue(morning.between(TramTime.of(5,0), TramTime.of(0,1)));
        assertTrue(morning.between(TramTime.of(5,0), TramTime.of(0,0)));
        assertTrue(morning.between(TramTime.of(5,0), TramTime.of(1,0)));

        TramTime earlyMorning = TramTime.of(0,20);
        assertTrue(earlyMorning.between(TramTime.of(0,1), TramTime.of(0,21)));
        assertTrue(earlyMorning.between(TramTime.of(0,0), TramTime.of(0,21)));
        assertTrue(earlyMorning.between(TramTime.of(0,1), TramTime.of(0,20)));
        assertTrue(earlyMorning.between(TramTime.of(0,0), TramTime.of(0,20)));
        assertTrue(earlyMorning.between(TramTime.of(5,0), TramTime.of(1,20)));
        assertFalse(earlyMorning.between(TramTime.of(3,0), TramTime.of(11,20)));
        assertFalse(earlyMorning.between(TramTime.of(23,0), TramTime.of(0,15)));

    }

    @Test
    public void shouldHaveCorrectDifferenceIncludingTimesAcrossMidnight() throws TramchesterException {
        TramTime first = TramTime.create(9,30);
        TramTime second = TramTime.create(10,45);

        int result = TramTime.diffenceAsMinutes(first, second);
        assertEquals(75, result);

        result = TramTime.diffenceAsMinutes(second, first);
        assertEquals(75, result);

        ////
        first = TramTime.create(00,5);
        second = TramTime.create(23,15);

        result = TramTime.diffenceAsMinutes(first, second);
        assertEquals(50, result);

        result = TramTime.diffenceAsMinutes(second, first);
        assertEquals(50, result);

        ////
        first = TramTime.create(00,5);
        second = TramTime.create(22,59);

        result = TramTime.diffenceAsMinutes(first, second);
        assertEquals(66, result);

        result = TramTime.diffenceAsMinutes(second, first);
        assertEquals(66, result);

        ////
        first = TramTime.create(23,59);
        second = TramTime.create(1,10);

        result = TramTime.diffenceAsMinutes(first, second);
        assertEquals(71, result);

        result = TramTime.diffenceAsMinutes(second, first);
        assertEquals(71, result);
    }

    @Test
    public void shouldSubstractMins() {
        LocalTime reference = LocalTime.of(12, 04);
        TramTime result = TramTime.of(reference.minusMinutes(30));

        assertEquals(11,result.getHourOfDay());
        assertEquals(34, result.getMinuteOfHour());
    }

    private void checkCorrectTimePresent(Optional<TramTime> resultA, int hours, int minutes) {
        assertTrue(resultA.isPresent());
        TramTime time = resultA.get();
        assertEquals(hours, time.getHourOfDay());
        assertEquals(minutes, time.getMinuteOfHour());
    }
}