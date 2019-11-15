package com.tramchester.unit.graph;

import com.tramchester.domain.exceptions.TramchesterException;
import com.tramchester.graph.JourneyState;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalTime;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Fail.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class JourneyStateTest {

    private LocalTime queryTime;

    @Before
    public void onceBeforeEachTestRuns() {
        queryTime = LocalTime.of(9, 15);
    }

    @Test
    public void shouldBeginJourney() {
        JourneyState state = new JourneyState(queryTime);
        assertFalse(state.isOnTram());

        int currentCost = 0;
        state.updateJourneyClock(currentCost);
        assertEquals(queryTime, state.getJourneyClock());
        assertFalse(state.isOnTram());

        currentCost = 14;
        state.updateJourneyClock(currentCost);
        assertEquals(LocalTime.of(9,29), state.getJourneyClock());
        assertFalse(state.isOnTram());

    }

    @Test
    public void shouldBoardATram() throws TramchesterException {
        JourneyState state = new JourneyState(queryTime);
        assertFalse(state.isOnTram());

        int currentCost = 10;
        LocalTime boardingTime = LocalTime.of(9, 30);
        state.boardTram();
        state.recordTramDetails(boardingTime,currentCost,"tripId1");

        assertTrue(state.isOnTram());
        assertEquals(boardingTime, state.getJourneyClock());
        assertEquals("tripId1", state.getTripId());
    }

    @Test
    public void shouldNotBoardATramIfAlreadyOnATram() throws TramchesterException {
        JourneyState state = new JourneyState(queryTime);

        state.boardTram();
        try {
            state.boardTram();
            fail("Should have thrown");
        }
        catch (TramchesterException exception) {
            // expected
        }
    }

    @Test
    public void shouldNotLeaveATramIfAlreadyOffATram() throws TramchesterException {
        JourneyState state = new JourneyState(queryTime);
        LocalTime boardingTime = LocalTime.of(9, 30);

        int currentCost = 14;
        state.boardTram();
        state.recordTramDetails(boardingTime,currentCost,"tripId");
        state.leaveTram(20);
        try {
            state.leaveTram(25);
            fail("Should have thrown");
        }
        catch (TramchesterException exception) {
            // expected
        }
    }

    @Test
    public void shouldHaveCorrectTripIdAndClockDuringATrip() throws TramchesterException {
        JourneyState state = new JourneyState(queryTime);

        LocalTime boardingTime = LocalTime.of(9, 30);
        state.boardTram();
        state.recordTramDetails(boardingTime,10, "tripId");
        assertEquals(boardingTime, state.getJourneyClock());

        state.updateJourneyClock(15); // 15 - 10
        assertEquals(boardingTime.plusMinutes(5), state.getJourneyClock());

        state.updateJourneyClock(20);  // 20 - 10
        assertEquals(boardingTime.plusMinutes(10), state.getJourneyClock());
    }

    @Test
    public void shouldHaveCorrectTimeWhenDepartingTram() throws TramchesterException {
        JourneyState state = new JourneyState(queryTime);
        assertFalse(state.isOnTram());

        state.boardTram();
        state.recordTramDetails(LocalTime.of(9,30),10,"tripId1");         // 10 mins cost
        assertTrue(state.isOnTram());
        assertEquals("tripId1", state.getTripId());

        state.leaveTram(25);                            // 25 mins cost, offset is 15 mins
        assertEquals(LocalTime.of(9,45), state.getJourneyClock()); // should be depart tram time
        assertFalse(state.isOnTram());
        assertEquals("", state.getTripId());

        state.updateJourneyClock(35);
        assertEquals(LocalTime.of(9,55), state.getJourneyClock()); // i.e not just queryTime + 35 minutes
    }

    @Test
    public void shouldHaveCorrectTimeWhenDepartingAndBoardingTram() throws TramchesterException {
        JourneyState state = new JourneyState(queryTime);

        state.boardTram();
        state.recordTramDetails(LocalTime.of(9,30),10,"tripId1");         // 10 mins cost
        assertEquals("tripId1", state.getTripId());

        state.leaveTram(25);                            // 25 mins cost, offset is 15 mins
        assertEquals(LocalTime.of(9,45), state.getJourneyClock()); // should be depart tram time

        state.boardTram();
        state.recordTramDetails(LocalTime.of(9,50),25,"tripId2");
        assertEquals(LocalTime.of(9,50), state.getJourneyClock()); // should be depart tram time
        assertEquals("tripId2", state.getTripId());

        state.leaveTram(35);                            // 35-25 = 10 mins
        assertEquals(LocalTime.of(10,0), state.getJourneyClock());
    }

    @Test
    public void shouldCreateNewState() throws TramchesterException {
        JourneyState journeyState = new JourneyState(LocalTime.of(7,55));

        JourneyState newStateA = JourneyState.fromPrevious(journeyState);
        assertEquals(LocalTime.of(7,55), journeyState.getJourneyClock());
        assertFalse(journeyState.isOnTram());
        assertTrue(journeyState.getTripId().isEmpty());

        newStateA.boardTram();
        newStateA.recordTramDetails(LocalTime.of(8,15), 15, "tripId1");
        assertEquals(LocalTime.of(8,15), newStateA.getJourneyClock());

        JourneyState newStateB = JourneyState.fromPrevious(newStateA);
        assertEquals(LocalTime.of(8,15), newStateB.getJourneyClock());
        assertTrue(newStateB.isOnTram());
        assertEquals("tripId1", newStateB.getTripId());
    }
}
