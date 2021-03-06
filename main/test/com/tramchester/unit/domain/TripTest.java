package com.tramchester.unit.domain;

import com.tramchester.domain.*;
import com.tramchester.domain.input.TramStopCall;
import com.tramchester.domain.input.Trip;
import com.tramchester.domain.places.Station;
import com.tramchester.domain.presentation.LatLong;
import com.tramchester.domain.time.TramTime;
import com.tramchester.testSupport.Stations;
import com.tramchester.testSupport.TestEnv;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static com.tramchester.domain.Platform.from;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.*;

public class TripTest {

    private Station stationA;
    private Station stationB;
    private Trip trip;
    private Station stationC;

    @Before
    public void beforeEachTestRuns() {
        Service service = new Service("svcId", TestEnv.getTestRoute());

        trip = new Trip("tripId","headSign", service, TestEnv.getTestRoute());
        stationA = new Station("statA","areaA", "stopNameA", new LatLong(1.0, -1.0), false);
        stationB = new Station("statB","areaA", "stopNameB", new LatLong(2.0, -2.0), false);
        stationC = new Station("statC","areaA", "stopNameB", new LatLong(2.0, -2.0), false);
    }

    @Test
    public void shouldKnowIfTramTrip() {
        Service service = new Service("svcId", TestEnv.getTestRoute());

        Trip tripA = new Trip("tripId", "headSign", service, TestEnv.getTestRoute());
        assertTrue(tripA.getTram());
        Route busRoute = new Route("busRouteId", "busRouteCode", "busRouteName", new Agency("BUS"), TransportMode.Bus);
        Trip tripB = new Trip("tripId", "headSign", service, busRoute);
        assertFalse(tripB.getTram());
    }

    @Test
    public void shouldModelCircularTripsCorrectly() {

        TramStopCall firstStop = new TramStopCall(from("statA1"), stationA, (byte) 1, TramTime.of(10, 00), TramTime.of(10, 01));
        TramStopCall secondStop = new TramStopCall(from("statB1"), stationB, (byte) 2, TramTime.of(10, 05), TramTime.of(10, 06));
        TramStopCall thirdStop = new TramStopCall(from("statA1"), stationA, (byte) 3, TramTime.of(10, 10), TramTime.of(10, 10));

        trip.addStop(firstStop);
        trip.addStop(secondStop);
        trip.addStop(thirdStop);

        assertEquals(TramTime.of(10,01), trip.earliestDepartTime());

        // sequence respected
        List<Byte> seqNums = new LinkedList<>();
        trip.getStops().forEach(stop -> { seqNums.add(stop.getGetSequenceNumber()); });
        assertEquals(1, seqNums.get(0).intValue());
        assertEquals(2, seqNums.get(1).intValue());
        assertEquals(3, seqNums.get(2).intValue());
    }

    @Test
    public void shouldFindEarliestDepartCorrectlyCrossingMidnight() {

        TramStopCall firstStop = new TramStopCall(from("stop1"), stationA, (byte) 2, TramTime.of(23, 45), TramTime.of(23, 46));
        TramStopCall secondStop = new TramStopCall(from("stop2"), stationB, (byte) 3, TramTime.of(23, 59), TramTime.of(0, 1));
        TramStopCall thirdStop = new TramStopCall(from("stop3"), stationC, (byte) 4, TramTime.of(0,10), TramTime.of(00, 11));
        TramStopCall fourthStop = new TramStopCall(from("stop4"), stationC, (byte) 1, TramTime.of(6,30), TramTime.of(6, 30));

        trip.addStop(firstStop);
        trip.addStop(secondStop);
        trip.addStop(thirdStop);
        trip.addStop(fourthStop);

        assertEquals(TramTime.of(6,30), trip.earliestDepartTime());
    }

    @Test
    public void shouldFindEarliestDepartCorrectly() {

        TramStopCall thirdStop = new TramStopCall(from("stop3"), stationC, (byte) 3, TramTime.of(0,10), TramTime.of(00, 11));
        TramStopCall fourthStop = new TramStopCall(from("stop4"), stationC, (byte) 1, TramTime.of(6,30), TramTime.of(6, 31));

        trip.addStop(thirdStop);
        trip.addStop(fourthStop);

        assertEquals(TramTime.of(6,31), trip.earliestDepartTime());
    }

    @Test
    public void shouldFindLatestDepartCorrectly() {
        trip.addStop(new TramStopCall(from("stopId3"), Stations.Deansgate, (byte) 3, TramTime.of(10,25), TramTime.of(10,26)));
        trip.addStop(new TramStopCall(from("stopId4"), Stations.Deansgate, (byte) 4, TramTime.of(0,1), TramTime.of(0,1)));

        assertEquals(TramTime.of(0,1), trip.latestDepartTime());

    }

}
