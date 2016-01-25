package com.tramchester.graph;

import com.tramchester.Dependencies;
import com.tramchester.IntegrationTramTestConfig;
import com.tramchester.Stations;
import com.tramchester.domain.*;
import com.tramchester.domain.exceptions.UnknownStationException;
import com.tramchester.domain.presentation.Journey;
import com.tramchester.domain.presentation.Stage;
import com.tramchester.graph.Relationships.TramRelationship;
import com.tramchester.services.DateTimeService;
import org.joda.time.LocalDate;
import org.junit.*;
import org.neo4j.graphdb.GraphDatabaseService;

import java.util.*;

import static org.junit.Assert.*;

public class RouteCalculatorTest {

    private static Dependencies dependencies;

    private RouteCalculator calculator;
    private DateTimeService dateTimeService;

    @BeforeClass
    public static void onceBeforeAnyTestsRun() throws Exception {
        dependencies = new Dependencies();
        dependencies.initialise(new IntegrationTramTestConfig());
    }

    @Before
    public void beforeEachTestRuns() {
        calculator = dependencies.get(RouteCalculator.class);
        dateTimeService = dependencies.get(DateTimeService.class);
    }

    @AfterClass
    public static void OnceAfterAllTestsAreFinished() {
        dependencies.close();
    }

    @Test
    public void testJourneyFromAltyToAirport() throws Exception {
        int minutes = dateTimeService.getMinutesFromMidnight("11:43:00");
        TramServiceDate today = new TramServiceDate(LocalDate.now());
        Set<RawJourney> results = calculator.calculateRoute(Stations.Altrincham, Stations.ManAirport, minutes, DaysOfWeek.Sunday, today);

        assertEquals(1, results.size());    // results is iterator
        for (RawJourney result : results) {
            List<RawStage> stages = result.getStages();
            assertEquals(2, stages.size());
            RawStage firstStage = stages.get(0);
            assertEquals(Stations.Altrincham, firstStage.getFirstStation());
            assertEquals(Stations.TraffordBar, firstStage.getLastStation());
            assertEquals(TransportMode.Tram, firstStage.getMode());
            RawStage secondStage = stages.get(1);
            assertEquals(Stations.TraffordBar, secondStage.getFirstStation());
            assertEquals(Stations.ManAirport, secondStage.getLastStation());
            assertEquals(TransportMode.Tram, secondStage.getMode());
        }
    }

    @Test
    public void shouldGetToRouteStopsAtVelopark() throws UnknownStationException {
        List<TramRelationship> boarding = calculator.getOutboundStationRelationships(Stations.VeloPark);
        assertEquals(2*3, boarding.size()); // 2 platforms * 3 routes
        assertTrue(boarding.get(0).isBoarding());  // we can get to either platform
        assertTrue(boarding.get(1).isBoarding());
    }

}