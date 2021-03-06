package com.tramchester.integration.graph;

import com.tramchester.Dependencies;
import com.tramchester.config.TramchesterConfig;
import com.tramchester.domain.places.Station;
import com.tramchester.graph.GraphDatabase;
import com.tramchester.graph.RouteReachable;
import com.tramchester.integration.IntegrationTramTestConfig;
import com.tramchester.repository.StationRepository;
import com.tramchester.testSupport.Stations;
import org.junit.*;
import org.neo4j.graphdb.Transaction;

import java.io.IOException;

import static com.tramchester.testSupport.RoutesForTesting.*;
import static junit.framework.TestCase.*;

public class TramRouteReachableTest {
    private static TramchesterConfig config;
    private static Dependencies dependencies;

    private RouteReachable reachable;
    private StationRepository stationRepository;
    private Transaction tx;

    @BeforeClass
    public static void onceBeforeAnyTestRuns() throws IOException {
        dependencies = new Dependencies();
        config = new IntegrationTramTestConfig();
        dependencies.initialise(config);
    }

    @AfterClass
    public static void OnceAfterAllTestsAreFinished() {
        dependencies.close();
    }

    @Before
    public void beforeEachTestRuns() {

        stationRepository = dependencies.get(StationRepository.class);
        reachable = dependencies.get(RouteReachable.class);
        GraphDatabase database = dependencies.get(GraphDatabase.class);
        tx = database.beginTx();
    }

    @After
    public void afterEachTestRuns() {
        tx.close();
    }

    @Test
    public void shouldHaveCorrectReachabilityOrInterchanges() {
        assertTrue(reachable.getRouteReachableWithInterchange(Stations.NavigationRoad.getId(), Stations.ManAirport.getId(),
                ALTY_TO_PICC));
        assertFalse(reachable.getRouteReachableWithInterchange(Stations.NavigationRoad.getId(), Stations.ManAirport.getId(),
                PICC_TO_ALTY));

        assertTrue(reachable.getRouteReachableWithInterchange(Stations.ManAirport.getId(), Stations.StWerburghsRoad.getId(),
                AIR_TO_VIC));
        assertFalse(reachable.getRouteReachableWithInterchange(Stations.ManAirport.getId(), Stations.StWerburghsRoad.getId(),
                VIC_TO_AIR));
    }

    @Test
    public void shouldHaveCorrectReachabilityMonsalToRochs() {
        assertTrue(reachable.getRouteReachableWithInterchange(Stations.RochdaleRail.getId(), Stations.Monsall.getId(),
                ROCH_TO_DIDS));
        assertTrue(reachable.getRouteReachableWithInterchange(Stations.Monsall.getId(), Stations.RochdaleRail.getId(),
                DIDS_TO_ROCH));
    }

    @Test
    public void shouldHaveAdjacentRoutesCorrectly() {

        // TODO Lockdown 2->1 for next two tests, only one route to alty now
        assertEquals(1,reachable.getRoutesFromStartToNeighbour(getReal(Stations.NavigationRoad), Stations.Altrincham.getId()).size());
        assertEquals(1, reachable.getRoutesFromStartToNeighbour(getReal(Stations.Altrincham), Stations.NavigationRoad.getId()).size());

        // 5 not the 7 on the map, only 6 routes modelled in timetable data, 1 of which does not go between these 2
        // TODO Lockdown 5->4
        assertEquals(4, reachable.getRoutesFromStartToNeighbour(getReal(Stations.Deansgate), Stations.StPetersSquare.getId()).size());

        assertEquals(2, reachable.getRoutesFromStartToNeighbour(getReal(Stations.StPetersSquare), Stations.PiccadillyGardens.getId()).size());

        // TODO Lockdown 2->1
        assertEquals(1, reachable.getRoutesFromStartToNeighbour(getReal(Stations.StPetersSquare), Stations.MarketStreet.getId()).size());

        assertEquals(0, reachable.getRoutesFromStartToNeighbour(getReal(Stations.Altrincham), Stations.Cornbrook.getId()).size());
    }

    @Test
    public void shouldComputeSimpleCostBetweenStations() {

        assertEquals(62, reachable.getApproxCostBetween(Stations.Bury.getId(), Stations.Altrincham.getId()));
        assertEquals(62, reachable.getApproxCostBetween(Stations.Altrincham.getId(), Stations.Bury.getId()));

        assertEquals(5, reachable.getApproxCostBetween(Stations.NavigationRoad.getId(), Stations.Altrincham.getId()));
        assertEquals(6, reachable.getApproxCostBetween(Stations.Altrincham.getId(), Stations.NavigationRoad.getId()));

        assertEquals(61, reachable.getApproxCostBetween(Stations.MediaCityUK.getId(), Stations.ManAirport.getId()));
        assertEquals(61, reachable.getApproxCostBetween(Stations.ManAirport.getId(), Stations.MediaCityUK.getId()));
    }


    private Station getReal(Station testStation) {
        return stationRepository.getStation(testStation.getId());
    }
}
