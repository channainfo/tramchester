package com.tramchester.integration.repository;

import com.tramchester.Dependencies;
import com.tramchester.integration.IntegrationTramTestConfig;
import com.tramchester.testSupport.RouteCodesForTesting;
import com.tramchester.testSupport.Stations;
import com.tramchester.repository.TramReachabilityRepository;
import org.junit.*;

import java.io.IOException;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assume.assumeTrue;

public class TramReachabilityRepositoryTest {
    private TramReachabilityRepository repository;
    private static Dependencies dependencies;

    @BeforeClass
    public static void onceBeforeAnyTestsRun() throws IOException {
        dependencies = new Dependencies();
        dependencies.initialise(new IntegrationTramTestConfig());
    }

    @AfterClass
    public static void OnceAfterAllTestsAreFinished() {
        dependencies.close();
    }

    @Before
    public void beforeEachTestRuns() {
        repository = dependencies.get(TramReachabilityRepository.class);
    }

    @Test
    public void shouldCreateReachabilityMatrix() {

        // wrong direction
        assertFalse(repository.stationReachable(Stations.NavigationRoad.getId()+ RouteCodesForTesting.BURY_TO_ALTY,
                Stations.TraffordBar));
        // right direction
        assertTrue(repository.stationReachable(Stations.NavigationRoad.getId()+ RouteCodesForTesting.ALTY_TO_BURY,
                Stations.TraffordBar));
        // wrong direction
        assertFalse(repository.stationReachable(Stations.NavigationRoad.getId()+ RouteCodesForTesting.BURY_TO_ALTY,
                Stations.ManAirport));
        // right direction with interchange
        assertTrue(repository.stationReachable(Stations.NavigationRoad.getId()+ RouteCodesForTesting.ALTY_TO_BURY,
                Stations.ManAirport));
        // self reachable
        assertTrue(repository.stationReachable(Stations.NavigationRoad.getId()+ RouteCodesForTesting.ALTY_TO_BURY,
                Stations.NavigationRoad));

        // right direction
        assertTrue(repository.stationReachable(Stations.RochdaleRail.getId() + RouteCodesForTesting.ROCH_TO_DIDS,
                Stations.Monsall));
        // wrong direction
        assertFalse(repository.stationReachable(Stations.RochdaleRail.getId() + RouteCodesForTesting.DIDS_TO_ROCH,
                Stations.Monsall));
        // towards victoria, so find an interchange
        assertTrue(repository.stationReachable(Stations.Monsall.getId() + RouteCodesForTesting.ROCH_TO_DIDS,
                Stations.RochdaleRail));

    }

    @Test
    public void shouldRepoIssueAltyToDeangates() {
        assertTrue(repository.stationReachable(Stations.Altrincham.getId()+ RouteCodesForTesting.ALTY_TO_BURY,
                Stations.Deansgate));
        assertTrue(repository.stationReachable(Stations.Altrincham.getId()+ RouteCodesForTesting.ALTY_TO_PICC,
                Stations.Deansgate));
        assertTrue(repository.stationReachable(Stations.StPetersSquare.getId()+ RouteCodesForTesting.ALTY_TO_BURY,
                Stations.Deansgate));
        assertTrue(repository.stationReachable(Stations.StPetersSquare.getId()+ RouteCodesForTesting.ALTY_TO_PICC,
                Stations.Deansgate));
    }


}