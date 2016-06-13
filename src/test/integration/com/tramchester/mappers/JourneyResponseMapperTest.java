package com.tramchester.mappers;

import com.tramchester.domain.*;
import com.tramchester.domain.exceptions.TramchesterException;
import com.tramchester.domain.exceptions.UnknownStationException;
import com.tramchester.graph.RouteCalculator;
import org.joda.time.LocalDate;

import java.util.Set;

import static org.junit.Assert.assertEquals;

public class JourneyResponseMapperTest {
    protected RouteCalculator routeCalculator;
    protected TramServiceDate today = new TramServiceDate(LocalDate.now());

    protected String findServiceId(String firstId, String secondId, int queryTime) throws TramchesterException {
        Set<RawJourney> found = routeCalculator.calculateRoute(firstId, secondId, queryTime, today);
        RawJourney rawJourney = found.stream().findFirst().get();
        TransportStage rawStage = rawJourney.getStages().get(0);
        assertEquals(RawVehicleStage.class, rawStage.getClass());
        return ((RawVehicleStage)rawStage).getServiceId();
    }
}
