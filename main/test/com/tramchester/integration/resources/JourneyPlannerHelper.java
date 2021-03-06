package com.tramchester.integration.resources;

import com.tramchester.domain.places.Location;
import com.tramchester.domain.time.TramServiceDate;
import com.tramchester.domain.time.TramTime;
import com.tramchester.domain.TransportMode;
import com.tramchester.domain.exceptions.TramchesterException;
import com.tramchester.domain.presentation.DTO.JourneyDTO;
import com.tramchester.domain.presentation.DTO.JourneyPlanRepresentation;
import com.tramchester.domain.presentation.DTO.StageDTO;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public abstract class JourneyPlannerHelper {

    static void checkDepartsAfterPreviousArrival(String message, Set<JourneyDTO> journeys) {
        for(JourneyDTO journey: journeys) {
            TramTime previousArrive = null;
            for(StageDTO stage : journey.getStages()) {
                if (previousArrive!=null) {
                    TramTime firstDepartureTime = stage.getFirstDepartureTime();
                    String prefix  = String.format("Check first departure time %s is after arrival time %s for %s" ,
                            firstDepartureTime, previousArrive, stage);
                    if (stage.getMode()!= TransportMode.Walk) {
                        assertTrue(prefix + message, firstDepartureTime.isAfter(previousArrive));
                    }
                }
                previousArrive = stage.getExpectedArrivalTime();
            }
        }
    }

    JourneyPlanRepresentation validateAtLeastOneJourney(Location start, Location end, LocalDate date, TramTime queryTime) throws TramchesterException {
        TramServiceDate queryDate = new TramServiceDate(date);
        JourneyPlanRepresentation results = getJourneyPlan(start, end, queryDate, queryTime, false, 3);
        Set<JourneyDTO> journeys = results.getJourneys();

        String message = String.format("from %s to %s at %s on %s", start, end, queryTime, queryDate);
        assertTrue("Unable to find journey " + message, journeys.size() > 0);
        checkDepartsAfterPreviousArrival(message, journeys);
        journeys.forEach(journey -> assertFalse("Missing stages for journey"+journey, journey.getStages().isEmpty()));
        return results;
    }

    protected JourneyPlanRepresentation getJourneyPlan(Location start, Location end, TramTime queryTime, LocalDate queryDate) throws TramchesterException {
        return getJourneyPlan(start, end, new TramServiceDate(queryDate), queryTime, false, 3);
    }

    abstract JourneyPlanRepresentation getJourneyPlan(Location start, Location end, TramServiceDate queryDate, TramTime queryTime,
                                                      boolean arriveBy, int maxChanges) throws TramchesterException;


}
