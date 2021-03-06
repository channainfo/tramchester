package com.tramchester.integration.livedata;

import com.tramchester.Dependencies;
import com.tramchester.testSupport.LiveDataMessagesCategory;
import com.tramchester.testSupport.LiveDataTestCategory;
import com.tramchester.domain.places.Station;
import com.tramchester.domain.time.ProvidesLocalNow;
import com.tramchester.domain.time.TramServiceDate;
import com.tramchester.domain.time.TramTime;
import com.tramchester.graph.RouteReachable;
import com.tramchester.integration.IntegrationTramTestConfig;
import com.tramchester.testSupport.Stations;
import com.tramchester.livedata.TramPosition;
import com.tramchester.livedata.TramPositionInference;
import com.tramchester.repository.LiveDataRepository;
import com.tramchester.repository.StationAdjacenyRepository;
import com.tramchester.repository.StationRepository;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

public class TramPositionInferenceTest {

    private static Dependencies dependencies;

    private TramPositionInference positionInference;
    private StationRepository stationRepository;
    private TramServiceDate date;
    private TramTime time;

    @BeforeClass
    public static void onceBeforeAnyTestsRun() throws IOException {
        dependencies = new Dependencies();
        IntegrationTramTestConfig testConfig = new IntegrationTramTestConfig();
        dependencies.initialise(testConfig);

    }

    @Before
    public void onceBeforeEachTestRuns() {
        ProvidesLocalNow providesLocalNow = new ProvidesLocalNow();

        LiveDataRepository liveDataSource = dependencies.get(LiveDataRepository.class);
        liveDataSource.refreshRespository();
        RouteReachable routeReachable = dependencies.get(RouteReachable.class);
        StationAdjacenyRepository adjacenyMatrix = dependencies.get(StationAdjacenyRepository.class);
        positionInference = new TramPositionInference(liveDataSource, adjacenyMatrix, routeReachable);
        stationRepository = dependencies.get(StationRepository.class);
        date = TramServiceDate.of(providesLocalNow.getDate());
        time = providesLocalNow.getNow();
    }

    @AfterClass
    public static void OnceAfterAllTestsAreFinished() {
        dependencies.close();
    }

    // TODO rework this test to account for new 12 minutes frequency which is causing frequent intermittent failures
    @Test
    @Category({LiveDataTestCategory.class, LiveDataMessagesCategory.class})
    public void shouldInferTramPosition() {
        // NOTE: costs are not symmetric between two stations, i.e. one direction might cost more than the other
        // Guess this is down to signalling, track, etc.
        int cost = 3; // cost between the stations, no due trams outside this limit should appear

        Station first = stationRepository.getStation(Stations.Deansgate.getId());
        Station second = stationRepository.getStation(Stations.Cornbrook.getId());

        TramPosition between = positionInference.findBetween(first, second, date, time);
        assertEquals(first, between.getFirst());
        assertEquals(second, between.getSecond());
        assertTrue("trams between", between.getTrams().size()>=1);
        assertEquals(cost, between.getCost());
        between.getTrams().forEach(dueTram -> assertFalse(Integer.toString(dueTram.getWait()), (dueTram.getWait())> cost));

        TramPosition otherDirection = positionInference.findBetween(second, first, date, time);
        assertTrue("no trams in other direction", otherDirection.getTrams().size()>=1);
        assertEquals(cost, between.getCost());
        otherDirection.getTrams().forEach(dueTram -> assertFalse(Integer.toString(dueTram.getWait()), (dueTram.getWait())> cost));
    }

    @Test
    @Category(LiveDataTestCategory.class)
    public void shouldInferAllTramPositions() {
        List<TramPosition> results = positionInference.inferWholeNetwork(date, time);
        long hasTrams = results.stream().filter(position -> !position.getTrams().isEmpty()).count();

        assertTrue(hasTrams>0);
    }
}
