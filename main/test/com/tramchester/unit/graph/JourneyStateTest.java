package com.tramchester.unit.graph;

import com.tramchester.domain.time.TramTime;
import com.tramchester.domain.exceptions.TramchesterException;
import com.tramchester.graph.CachedNodeOperations;
import com.tramchester.graph.search.JourneyState;
import com.tramchester.graph.NodeIdLabelMap;
import com.tramchester.graph.states.NotStartedState;
import com.tramchester.testSupport.TestEnv;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Fail.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class JourneyStateTest {

    private TramTime queryTime;
    private NotStartedState traversalState;

    // TODO ON BUS

    @Before
    public void onceBeforeEachTestRuns() {
        List<String> destinationStationIds = Arrays.asList("destinationStationId");
        traversalState = new NotStartedState(new CachedNodeOperations(new NodeIdLabelMap()),
                42, destinationStationIds, TestEnv.GET());
        queryTime = TramTime.of(9, 15);
    }

    @Test
    public void shouldBeginJourney() {
        JourneyState state = new JourneyState(queryTime, traversalState);
        assertFalse(state.onTram());

        int currentCost = 0;
        state.updateJourneyClock(currentCost);
        assertEquals(queryTime, state.getJourneyClock());
        assertFalse(state.onTram());

        currentCost = 14;
        state.updateJourneyClock(currentCost);
        assertEquals(TramTime.of(9,29), state.getJourneyClock());
        assertFalse(state.onTram());

    }

    @Test
    public void shouldBoardATram() throws TramchesterException {
        JourneyState state = new JourneyState(queryTime, traversalState);
        assertFalse(state.onTram());

        int currentCost = 10;
        TramTime boardingTime = TramTime.of(9, 30);
        state.boardTram();
        state.recordTramDetails(boardingTime,currentCost);

        assertTrue(state.onTram());
        assertEquals(boardingTime, state.getJourneyClock());
//        assertEquals("tripId1", state.getTripId());
    }

    @Test
    public void shouldNotBoardATramIfAlreadyOnATram() throws TramchesterException {
        JourneyState state = new JourneyState(queryTime, traversalState);

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
        JourneyState state = new JourneyState(queryTime, traversalState);
        TramTime boardingTime = TramTime.of(9, 30);

        int currentCost = 14;
        state.boardTram();
        state.recordTramDetails(boardingTime,currentCost);
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
        JourneyState state = new JourneyState(queryTime, traversalState);

        TramTime boardingTime = TramTime.of(9, 30);
        state.boardTram();
        state.recordTramDetails(boardingTime,10);
        assertEquals(boardingTime, state.getJourneyClock());

        state.updateJourneyClock(15); // 15 - 10
        assertEquals(boardingTime.plusMinutes(5), state.getJourneyClock());

        state.updateJourneyClock(20);  // 20 - 10
        assertEquals(boardingTime.plusMinutes(10), state.getJourneyClock());
    }

    @Test
    public void shouldHaveCorrectTimeWhenDepartingTram() throws TramchesterException {
        JourneyState state = new JourneyState(queryTime, traversalState);
        assertFalse(state.onTram());

        state.boardTram();
        state.recordTramDetails(TramTime.of(9,30),10);         // 10 mins cost
        assertTrue(state.onTram());
//        assertEquals("tripId1", state.getTripId());

        state.leaveTram(25);                            // 25 mins cost, offset is 15 mins
        assertEquals(TramTime.of(9,45), state.getJourneyClock()); // should be depart tram time
        assertFalse(state.onTram());
//        assertEquals("", state.getTripId());

        state.updateJourneyClock(35);
        assertEquals(TramTime.of(9,55), state.getJourneyClock()); // i.e not just queryTime + 35 minutes
    }

    @Test
    public void shouldHaveCorrectTimeWhenDepartingAndBoardingTram() throws TramchesterException {
        JourneyState state = new JourneyState(queryTime, traversalState);

        state.boardTram();
        state.recordTramDetails(TramTime.of(9,30),10);         // 10 mins cost
//        assertEquals("tripId1", state.getTripId());

        state.leaveTram(25);                            // 25 mins cost, offset is 15 mins
        assertEquals(TramTime.of(9,45), state.getJourneyClock()); // should be depart tram time

        state.boardTram();
        state.recordTramDetails(TramTime.of(9,50),25);
        assertEquals(TramTime.of(9,50), state.getJourneyClock()); // should be depart tram time
//        assertEquals("tripId2", state.getTripId());

        state.leaveTram(35);                            // 35-25 = 10 mins
        assertEquals(TramTime.of(10,0), state.getJourneyClock());
    }

    @Test
    public void shouldCreateNewState() throws TramchesterException {
        JourneyState journeyState = new JourneyState(TramTime.of(7,55), traversalState);

        JourneyState newStateA = JourneyState.fromPrevious(journeyState);
        assertEquals(TramTime.of(7,55), journeyState.getJourneyClock());
        assertFalse(journeyState.onTram());
//        assertTrue(journeyState.getTripId().isEmpty());

        newStateA.boardTram();
        newStateA.recordTramDetails(TramTime.of(8,15), 15);
        assertEquals(TramTime.of(8,15), newStateA.getJourneyClock());

        JourneyState newStateB = JourneyState.fromPrevious(newStateA);
        assertEquals(TramTime.of(8,15), newStateB.getJourneyClock());
        assertTrue(newStateB.onTram());
//        assertEquals("tripId1", newStateB.getTripId());
    }
}
